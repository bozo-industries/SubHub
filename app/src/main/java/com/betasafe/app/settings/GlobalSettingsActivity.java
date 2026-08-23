package com.betasafe.app.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.databinding.ActivityGlobalSettingsBinding;
import com.betasafe.app.appmode.AppModeManager;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.diagnostics.DiagnosticsActivity;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.profiles.ProfilesActivity;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.security.ControllerPinGate;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.service.ScreenCaptureService;
import com.betasafe.app.util.SubHubNavigation;

/** Always-available home for app-wide feature, safety, backup, and support settings. */
public final class GlobalSettingsActivity extends AppCompatActivity {
    private ActivityGlobalSettingsBinding binding;
    private FeatureModuleManager modules;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGlobalSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        modules = new FeatureModuleManager(this);
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
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.SETTINGS);
        applyEditState();
    }

    @Override protected void onResume() {
        super.onResume();
        applyEditState();
    }

    private void toggleEditSession() {
        if (ControllerPinManager.isSessionUnlocked()) {
            ControllerPinManager.lockNow();
            applyEditState();
        } else ControllerPinGate.require(this, this::applyEditState, false);
    }

    private void applyEditState() {
        if (binding == null) return;
        boolean editing = ControllerPinManager.isSessionUnlocked();
        ControllerEditMode.renderButton(this, binding.buttonEditLock);
        binding.switchModuleCensor.setEnabled(editing);
        binding.switchModuleLimits.setEnabled(editing);
        binding.switchModuleWallet.setEnabled(editing);
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
