package com.betasafe.app.profiles;

import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityProfilesBinding;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.security.ControllerPinManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Named setting profiles plus an explicit, versioned JSON backup/restore surface. */
public final class ProfilesActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ActivityProfilesBinding binding;
    private ProfileManager profiles;
    private SettingsBackupManager backups;
    private ActivityResultLauncher<String> createBackup;
    private ActivityResultLauncher<String[]> openBackup;
    private ControllerEditMode editMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfilesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        profiles = new ProfileManager(this);
        backups = new SettingsBackupManager(this);
        createBackup = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), this::writeBackup);
        openBackup = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::confirmImport);

        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonSaveProfile.setOnClickListener(view -> saveProfile());
        binding.buttonExportSettings.setOnClickListener(view ->
                createBackup.launch("SubHub-settings.json"));
        binding.buttonImportSettings.setOnClickListener(view ->
                openBackup.launch(new String[]{"application/json", "text/json", "text/plain"}));
        editMode = ControllerEditMode.bind(
                this, binding.buttonEditLock, editing -> applyEditState());
        rebuild();
    }

    @Override protected void onResume() {
        super.onResume();
        if (editMode != null) editMode.refresh();
    }

    private void applyEditState() {
        if (binding == null) return;
        boolean editing = ControllerPinManager.isSessionUnlocked();
        binding.profileName.setEnabled(editing);
        binding.buttonSaveProfile.setEnabled(editing);
        binding.buttonExportSettings.setEnabled(editing);
        binding.buttonImportSettings.setEnabled(editing);
        rebuild();
    }

    private void saveProfile() {
        if (profiles.save(binding.profileName.getText().toString())) {
            binding.profileName.setText("");
            rebuild();
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.profile_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void rebuild() {
        binding.profileList.removeAllViews();
        for (String name : profiles.listProfiles()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(8), dp(8));
            row.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(8);
            row.setLayoutParams(rowParams);
            TextView label = new TextView(this);
            label.setText(name);
            label.setTextColor(getColor(R.color.text_primary));
            label.setTextSize(14);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));
            label.setGravity(Gravity.CENTER_VERTICAL);
            Button load = actionButton(R.string.profile_load);
            load.setEnabled(ControllerPinManager.isSessionUnlocked());
            load.setOnClickListener(view -> {
                if (profiles.load(name)) {
                    Toast.makeText(this, R.string.profile_loaded, Toast.LENGTH_SHORT).show();
                }
            });
            Button delete = actionButton(R.string.delete);
            delete.setEnabled(ControllerPinManager.isSessionUnlocked());
            delete.setOnClickListener(view -> new AlertDialog.Builder(this)
                    .setTitle(R.string.profile_delete_title)
                    .setMessage(name)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        profiles.delete(name);
                        rebuild();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
            row.addView(label);
            row.addView(load);
            row.addView(delete);
            binding.profileList.addView(row);
        }
        binding.profileEmpty.setVisibility(
                profiles.listProfiles().isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Button actionButton(int text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(getColor(R.color.accent));
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.bg_outline_button);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(76), dp(48)));
        return button;
    }

    private void writeBackup(Uri uri) {
        if (uri == null) return;
        worker.execute(() -> {
            boolean success = false;
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output != null) {
                    backups.exportTo(output);
                    success = true;
                }
            } catch (Exception ignored) {
                // Report a sanitized failure in the UI.
            }
            boolean finalSuccess = success;
            runOnUiThread(() -> Toast.makeText(this,
                    finalSuccess ? R.string.backup_exported : R.string.backup_failed,
                    Toast.LENGTH_LONG).show());
        });
    }

    private void confirmImport(Uri uri) {
        if (uri == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_import_title)
                .setMessage(R.string.backup_import_warning)
                .setPositiveButton(R.string.backup_import, (dialog, which) -> importBackup(uri))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void importBackup(Uri uri) {
        worker.execute(() -> {
            boolean success = false;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                success = input != null && backups.importFrom(input);
            } catch (Exception ignored) {
                // Report a sanitized failure in the UI.
            }
            boolean finalSuccess = success;
            runOnUiThread(() -> Toast.makeText(this,
                    finalSuccess ? R.string.backup_imported : R.string.backup_failed,
                    Toast.LENGTH_LONG).show());
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        binding = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
