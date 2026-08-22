package com.betasafe.app.pack;

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
import com.betasafe.app.databinding.ActivityPacksBinding;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Install, inspect, activate, deactivate, and remove private .bbpack bundles. */
public final class PacksActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ActivityPacksBinding binding;
    private PackManager manager;
    private ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPacksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        manager = new PackManager(this);
        picker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::importPack);
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonImportPack.setOnClickListener(view -> picker.launch(new String[]{
                "application/zip", "application/octet-stream", "application/x-zip-compressed"}));
        binding.buttonDeactivatePack.setOnClickListener(view -> {
            manager.deactivate();
            rebuild();
        });
        rebuild();
    }

    private void importPack(Uri uri) {
        if (uri == null) return;
        binding.packStatus.setText(R.string.pack_importing);
        worker.execute(() -> {
            String result;
            try {
                PackManager.PackInfo imported = manager.importPack(uri);
                result = getString(R.string.pack_imported, imported.getManifest().getName());
            } catch (Exception error) {
                result = getString(R.string.pack_import_failed, safeMessage(error));
            }
            String finalResult = result;
            runOnUiThread(() -> {
                if (binding == null) return;
                binding.packStatus.setText(finalResult);
                rebuild();
            });
        });
    }

    private void rebuild() {
        binding.packList.removeAllViews();
        List<PackManager.PackInfo> packs = manager.listInstalled();
        String active = manager.activePackId();
        binding.packEmpty.setVisibility(packs.isEmpty() ? View.VISIBLE : View.GONE);
        binding.buttonDeactivatePack.setVisibility(active == null ? View.GONE : View.VISIBLE);
        for (PackManager.PackInfo pack : packs) binding.packList.addView(packCard(pack, active));
    }

    private View packCard(PackManager.PackInfo pack, String active) {
        PackManifest manifest = pack.getManifest();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        TextView name = text(manifest.getName(), R.color.text_primary, 16);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(name);
        card.addView(text(getString(R.string.pack_byline, manifest.getAuthor(), manifest.getVersion()),
                R.color.text_secondary, 11));
        if (!manifest.getDescription().isEmpty()) {
            TextView description = text(manifest.getDescription(), R.color.text_secondary, 12);
            description.setPadding(0, dp(8), 0, 0);
            card.addView(description);
        }
        String integrity = manifest.hasIntegrityDigest()
                ? getString(R.string.pack_integrity_verified)
                : getString(R.string.pack_no_integrity_digest);
        TextView digest = text(integrity,
                manifest.hasIntegrityDigest() ? R.color.accent : R.color.text_muted, 10);
        digest.setPadding(0, dp(8), 0, 0);
        card.addView(digest);
        TextView locks = text(getString(
                R.string.pack_locked_count, manifest.getLockedSettings().size()),
                R.color.text_muted, 10);
        card.addView(locks);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(8), 0, 0);
        boolean isActive = manifest.getPackId().equals(active);
        Button activate = actionButton(isActive ? R.string.pack_active : R.string.pack_activate);
        activate.setEnabled(!isActive);
        activate.setOnClickListener(view -> confirmActivation(pack));
        Button delete = actionButton(R.string.delete);
        delete.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.pack_delete_title)
                .setMessage(manifest.getName())
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    manager.delete(manifest.getPackId());
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        actions.addView(activate);
        actions.addView(delete);
        card.addView(actions);
        return card;
    }

    private void confirmActivation(PackManager.PackInfo pack) {
        PackManifest manifest = pack.getManifest();
        new AlertDialog.Builder(this)
                .setTitle(R.string.pack_activate_title)
                .setMessage(getString(
                        R.string.pack_activate_warning,
                        manifest.getName(), manifest.getLockedSettings().size()))
                .setPositiveButton(R.string.pack_activate, (dialog, which) -> {
                    boolean success = manager.activate(manifest.getPackId());
                    Toast.makeText(this,
                            success ? R.string.pack_activated : R.string.pack_activation_failed,
                            Toast.LENGTH_LONG).show();
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private TextView text(String value, int color, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(color));
        view.setTextSize(size);
        return view;
    }

    private Button actionButton(int label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
        button.setTextColor(getColor(R.color.accent));
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        return button;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "invalid pack";
        String safe = message.replaceAll("[\\r\\n]", " ");
        return safe.length() <= 100 ? safe : safe.substring(0, 100);
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
