package com.betasafe.app.settings;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityGlobalSettingsBinding;
import com.betasafe.app.appmode.AppModeManager;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.diagnostics.DiagnosticsActivity;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.profiles.ProfilesActivity;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.security.ControllerPinGate;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.security.HardcoreModeManager;
import com.betasafe.app.service.ScreenCaptureService;
import com.betasafe.app.util.SubHubNavigation;

/** Always-available home for app-wide feature, safety, backup, and support settings. */
public final class GlobalSettingsActivity extends AppCompatActivity {
    private ActivityGlobalSettingsBinding binding;
    private FeatureModuleManager modules;
    private HardcoreModeManager hardcore;
    private ActivityResultLauncher<Intent> hardcoreActivation;
    private boolean updatingHardcore;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGlobalSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        modules = new FeatureModuleManager(this);
        hardcore = new HardcoreModeManager(this);
        hardcoreActivation = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), ignored -> {
                    boolean active = hardcore.finishActivation();
                    refreshHardcoreState();
                    if (active && !new AppModeManager(this).isAccessibilityEnabled()) {
                        Toast.makeText(this, R.string.hardcore_status_accessibility,
                                Toast.LENGTH_LONG).show();
                    }
                });
        binding.switchModuleCensor.setChecked(modules.isCensorEnabled());
        binding.switchModuleLimits.setChecked(modules.isLimitsEnabled());
        binding.switchModuleWallet.setChecked(modules.isWalletEnabled());
        binding.buttonEditLock.setOnClickListener(view -> toggleEditSession());
        binding.buttonHelp.setOnClickListener(view ->
                startActivity(new Intent(this, HelpActivity.class)));
        binding.buttonProfiles.setOnClickListener(view ->
                startActivity(new Intent(this, ProfilesActivity.class)));
        binding.buttonDiagnostics.setOnClickListener(view ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        binding.buttonCommitment.setOnClickListener(view ->
                startActivity(new Intent(this, CommitmentActivity.class)));
        binding.switchModuleCensor.setOnCheckedChangeListener((button, checked) -> saveModules());
        binding.switchModuleLimits.setOnCheckedChangeListener((button, checked) -> saveModules());
        binding.switchModuleWallet.setOnCheckedChangeListener((button, checked) -> saveModules());
        binding.switchHardcoreMode.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingHardcore) changeHardcoreMode(checked);
        });
        binding.buttonHardcoreSystem.setOnClickListener(view -> openHardcoreSystemPage());
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.SETTINGS);
        applyEditState();
    }

    @Override protected void onResume() {
        super.onResume();
        applyEditState();
        refreshHardcoreState();
    }

    private void toggleEditSession() {
        if (ControllerPinManager.isSessionUnlocked()) {
            ControllerEditMode.enterSubMode(this);
        } else ControllerPinGate.require(this, this::applyEditState, false);
    }

    private void applyEditState() {
        if (binding == null) return;
        boolean editing = ControllerPinManager.isSessionUnlocked();
        ControllerEditMode.renderButton(this, binding.buttonEditLock);
        binding.switchModuleCensor.setEnabled(editing);
        binding.switchModuleLimits.setEnabled(editing);
        binding.switchModuleWallet.setEnabled(editing);
        binding.switchHardcoreMode.setEnabled(editing);
        binding.buttonHardcoreSystem.setEnabled(editing);
        refreshHardcoreState();
    }

    private void changeHardcoreMode(boolean enabled) {
        if (!ControllerPinManager.isDomModeActive()) {
            refreshHardcoreState();
            return;
        }
        if (enabled) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.hardcore_consent_title)
                    .setMessage(R.string.hardcore_consent_body)
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> refreshHardcoreState())
                    .setPositiveButton(R.string.hardcore_consent_enable, (dialog, which) -> {
                        hardcore.beginActivation();
                        refreshHardcoreState();
                        hardcoreActivation.launch(hardcore.activationIntent());
                    })
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.hardcore_release_title)
                    .setMessage(R.string.hardcore_release_body)
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> refreshHardcoreState())
                    .setPositiveButton(R.string.hardcore_release, (dialog, which) -> {
                        hardcore.disable();
                        refreshHardcoreState();
                    })
                    .show();
        }
    }

    private void refreshHardcoreState() {
        if (binding == null || hardcore == null) return;
        updatingHardcore = true;
        boolean active = hardcore.isEnabled();
        binding.switchHardcoreMode.setChecked(active || hardcore.isRequested());
        if (active && !new AppModeManager(this).isAccessibilityEnabled()) {
            binding.hardcoreStatus.setText(R.string.hardcore_status_accessibility);
            binding.buttonHardcoreSystem.setText(R.string.hardcore_open_accessibility);
        } else if (active) {
            binding.hardcoreStatus.setText(R.string.hardcore_status_active);
            binding.buttonHardcoreSystem.setText(R.string.hardcore_open_admin);
        } else if (hardcore.isRequested()) {
            binding.hardcoreStatus.setText(R.string.hardcore_status_pending);
            binding.buttonHardcoreSystem.setText(R.string.hardcore_open_admin);
        } else {
            binding.hardcoreStatus.setText(R.string.hardcore_status_off);
            binding.buttonHardcoreSystem.setText(R.string.hardcore_open_admin);
        }
        updatingHardcore = false;
    }

    private void openHardcoreSystemPage() {
        if (hardcore.isEnabled() && !new AppModeManager(this).isAccessibilityEnabled()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            startActivity(hardcore.adminSettingsIntent());
        }
    }

    private void saveModules() {
        if (!ControllerPinManager.isSessionUnlocked()) return;
        boolean censor = binding.switchModuleCensor.isChecked();
        modules.save(censor, binding.switchModuleLimits.isChecked(),
                binding.switchModuleWallet.isChecked());
        if (!censor) {
            startService(ScreenCaptureService.stopIntent(this));
            new AppModeManager(this).setArmed(false);
        }
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.SETTINGS);
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
