package com.betasafe.app.settings;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.CompoundButtonCompat;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityGlobalSettingsBinding;
import com.betasafe.app.appmode.AppModeManager;
import com.betasafe.app.appmode.AppModePolicy;
import com.betasafe.app.appmode.ResumeNotificationManager;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.diagnostics.DiagnosticsActivity;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.penance.PenanceManager;
import com.betasafe.app.penance.HardcoreAutoPayManager;
import com.betasafe.app.penance.PayPalCredentialStore;
import com.betasafe.app.penance.PayPalEnvironment;
import com.betasafe.app.profiles.ProfilesActivity;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.security.ControllerPinGate;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.security.HardcoreModeManager;
import com.betasafe.app.service.ScreenCaptureService;
import com.betasafe.app.service.ScreenshotAccessibilityService;
import com.betasafe.app.util.SubHubNavigation;

import java.text.Collator;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Always-available home for app-wide feature, safety, backup, and support settings. */
public final class GlobalSettingsActivity extends AppCompatActivity {
    private ActivityGlobalSettingsBinding binding;
    private FeatureModuleManager modules;
    private HardcoreModeManager hardcore;
    private AppModeManager appMode;
    private PayPalCredentialStore paypalCredentials;
    private HardcoreAutoPayManager autoPay;
    private ActivityResultLauncher<Intent> hardcoreActivation;
    private boolean updatingHardcore;
    private boolean updatingPaypalEnvironment;
    private boolean updatingAutoPay;
    private boolean editingUnlocked;
    private final Set<String> censorPackages = new LinkedHashSet<>();
    private final Set<String> timerPackages = new LinkedHashSet<>();
    private final ExecutorService appLoader = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGlobalSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        modules = new FeatureModuleManager(this);
        hardcore = new HardcoreModeManager(this);
        appMode = new AppModeManager(this);
        paypalCredentials = new PayPalCredentialStore(this);
        autoPay = new HardcoreAutoPayManager(this);
        censorPackages.addAll(appMode.getSelectedPackages());
        timerPackages.addAll(appMode.getTimerPackages());
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
        binding.armed.setChecked(appMode.isArmed());
        binding.modeGroup.check(appMode.getMode() == AppModePolicy.Mode.SELECTED_APPS
                ? R.id.mode_selected : R.id.mode_always);
        binding.paypalLink.setText(new PenanceManager(this).getPayPalLink());
        PayPalCredentialStore.Credentials credentials = paypalCredentials.load();
        binding.paypalClientId.setText(credentials.clientId());
        updatingPaypalEnvironment = true;
        binding.paypalEnvironment.check(credentials.environment() == PayPalEnvironment.LIVE
                ? R.id.paypal_environment_live : R.id.paypal_environment_sandbox);
        updatingPaypalEnvironment = false;
        binding.buttonEditLock.setOnClickListener(view -> toggleEditSession());
        binding.buttonHelp.setOnClickListener(view ->
                startActivity(new Intent(this, HelpActivity.class)));
        binding.buttonProfiles.setOnClickListener(view ->
                startActivity(new Intent(this, ProfilesActivity.class)));
        binding.buttonDiagnostics.setOnClickListener(view ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        binding.buttonCommitment.setVisibility(View.GONE);
        binding.switchModuleCensor.setOnCheckedChangeListener((button, checked) -> saveModules());
        binding.switchModuleLimits.setOnCheckedChangeListener((button, checked) -> saveModules());
        binding.switchModuleWallet.setOnCheckedChangeListener((button, checked) -> saveModules());
        binding.switchHardcoreMode.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingHardcore) changeHardcoreMode(checked);
        });
        binding.buttonHardcoreSystem.setOnClickListener(view -> openHardcoreSystemPage());
        binding.buttonAccessibilitySettings.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        binding.buttonSaveRecognition.setOnClickListener(view -> saveRecognition());
        binding.buttonSaveApps.setOnClickListener(view -> saveAppAssignments());
        binding.buttonSavePaypal.setOnClickListener(view -> savePayPalLink());
        binding.buttonSavePaypalSandbox.setOnClickListener(view -> savePayPalSandbox());
        binding.buttonClearPaypalSandbox.setOnClickListener(view -> clearPayPalSandbox());
        binding.paypalEnvironment.setOnCheckedChangeListener((group, checkedId) -> {
            if (!updatingPaypalEnvironment) changePayPalEnvironment(checkedId);
        });
        binding.paypalAutoPayEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingAutoPay) changeAutoPay(checked);
        });
        binding.buttonToggleApps.setOnClickListener(view -> {
            boolean show = binding.appListContent.getVisibility() != View.VISIBLE;
            binding.appListContent.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.buttonToggleApps.setText(show
                    ? R.string.app_selection_collapse : R.string.app_selection_expand);
        });
        binding.appListContent.setVisibility(View.GONE);
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.SETTINGS);
        loadApps();
        applyEditState();
    }

    @Override protected void onResume() {
        super.onResume();
        applyEditState();
        refreshHardcoreState();
        refreshAccessState();
    }

    private void toggleEditSession() {
        if (ControllerPinManager.isSessionUnlocked()) {
            ControllerEditMode.enterSubMode(this);
        } else ControllerPinGate.require(this, this::applyEditState, false);
    }

    private void applyEditState() {
        if (binding == null) return;
        editingUnlocked = ControllerPinManager.isSessionUnlocked();
        ControllerEditMode.renderButton(this, binding.buttonEditLock);
        binding.switchModuleCensor.setEnabled(editingUnlocked);
        binding.switchModuleLimits.setEnabled(editingUnlocked);
        binding.switchModuleWallet.setEnabled(editingUnlocked);
        binding.switchHardcoreMode.setEnabled(editingUnlocked);
        binding.buttonHardcoreSystem.setEnabled(editingUnlocked);
        binding.buttonAccessibilitySettings.setEnabled(editingUnlocked);
        binding.armed.setEnabled(editingUnlocked);
        binding.modeAlways.setEnabled(editingUnlocked);
        binding.modeSelected.setEnabled(editingUnlocked);
        binding.buttonSaveRecognition.setEnabled(editingUnlocked);
        binding.buttonSaveApps.setEnabled(editingUnlocked);
        binding.paypalLink.setEnabled(editingUnlocked);
        binding.buttonSavePaypal.setEnabled(editingUnlocked);
        binding.paypalClientId.setEnabled(editingUnlocked);
        binding.paypalClientSecret.setEnabled(editingUnlocked);
        binding.buttonSavePaypalSandbox.setEnabled(editingUnlocked);
        binding.buttonClearPaypalSandbox.setEnabled(editingUnlocked);
        binding.paypalEnvironmentSandbox.setEnabled(editingUnlocked);
        binding.paypalEnvironmentLive.setEnabled(editingUnlocked);
        binding.paypalAutoPayEnabled.setEnabled(editingUnlocked);
        if (hardcore != null && hardcore.isEnabled()) {
            binding.armed.setChecked(true);
            binding.armed.setEnabled(false);
        }
        for (int index = 0; index < binding.appList.getChildCount(); index++) {
            setEnabledRecursive(binding.appList.getChildAt(index), editingUnlocked);
        }
        refreshHardcoreState();
        refreshAccessState();
        refreshPayPalSandboxState();
    }

    private void saveRecognition() {
        if (!editingUnlocked) return;
        AppModePolicy.Mode mode = binding.modeSelected.isChecked()
                ? AppModePolicy.Mode.SELECTED_APPS : AppModePolicy.Mode.ALWAYS;
        if (binding.armed.isChecked() && mode == AppModePolicy.Mode.SELECTED_APPS
                && censorPackages.isEmpty()) {
            Toast.makeText(this, R.string.app_mode_select_one, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean armed = hardcore.isEnabled() || binding.armed.isChecked();
        appMode.save(armed, mode, censorPackages);
        if (armed) ResumeNotificationManager.show(this);
        else ResumeNotificationManager.cancel(this);
        Toast.makeText(this, R.string.app_mode_saved, Toast.LENGTH_SHORT).show();
        if (armed && !appMode.isAccessibilityEnabled()) {
            Toast.makeText(this, R.string.app_mode_enable_prompt, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        refreshAccessState();
    }

    private void saveAppAssignments() {
        if (!editingUnlocked) return;
        appMode.saveAppSelections(censorPackages, timerPackages);
        Toast.makeText(this, R.string.app_assignments_saved, Toast.LENGTH_SHORT).show();
        renderSelectedCount();
    }

    private void savePayPalLink() {
        if (!editingUnlocked) return;
        String link = binding.paypalLink.getText() == null
                ? "" : binding.paypalLink.getText().toString().trim();
        if (!link.isEmpty() && !validPayPalLink(link)) {
            Toast.makeText(this, R.string.paypal_settings_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        new PenanceManager(this).savePayPalLink(link);
        Toast.makeText(this, R.string.paypal_settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void savePayPalSandbox() {
        if (!editingUnlocked) return;
        String clientId = binding.paypalClientId.getText() == null
                ? "" : binding.paypalClientId.getText().toString().trim();
        String secret = binding.paypalClientSecret.getText() == null
                ? "" : binding.paypalClientSecret.getText().toString().trim();
        PayPalCredentialStore.Credentials existing = paypalCredentials.load();
        PayPalEnvironment selected = selectedPayPalEnvironment();
        if (secret.isEmpty() && selected == existing.environment()
                && clientId.equals(existing.clientId())) secret = existing.secret();
        if (clientId.isEmpty() || secret.isEmpty()) {
            Toast.makeText(this, R.string.paypal_sandbox_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        String oldBoundary = existing.boundaryId();
        if (!paypalCredentials.save(selected, clientId, secret)) {
            Toast.makeText(this, R.string.paypal_sandbox_store_failed, Toast.LENGTH_LONG).show();
            return;
        }
        binding.paypalClientSecret.setText("");
        if (!oldBoundary.equals(paypalCredentials.load().boundaryId())) {
            cancelActivePayPalCheckout();
        }
        Toast.makeText(this, R.string.paypal_sandbox_saved, Toast.LENGTH_SHORT).show();
        refreshPayPalSandboxState();
    }

    private void clearPayPalSandbox() {
        if (!editingUnlocked) return;
        paypalCredentials.clear();
        cancelActivePayPalCheckout();
        binding.paypalClientId.setText("");
        binding.paypalClientSecret.setText("");
        Toast.makeText(this, R.string.paypal_sandbox_cleared, Toast.LENGTH_SHORT).show();
        refreshPayPalSandboxState();
    }

    private void refreshPayPalSandboxState() {
        if (binding == null || paypalCredentials == null) return;
        PayPalEnvironment environment = paypalCredentials.selectedEnvironment();
        binding.paypalSandboxStatus.setText(getString(
                paypalCredentials.hasCredentials()
                        ? R.string.paypal_environment_status_ready
                        : R.string.paypal_environment_status_off,
                environment == PayPalEnvironment.LIVE ? "LIVE" : "SANDBOX"));
        PayPalCredentialStore.VaultState vaultState = paypalCredentials.vaultState();
        PayPalCredentialStore.VaultStatus vault = vaultState.status();
        int vaultStatus;
        switch (vault) {
            case READY: vaultStatus = R.string.paypal_vault_status_ready; break;
            case PENDING: vaultStatus = R.string.paypal_vault_status_pending; break;
            case UNAVAILABLE: vaultStatus = R.string.paypal_vault_status_unavailable; break;
            case REQUESTED: vaultStatus = R.string.paypal_vault_status_requested; break;
            default: vaultStatus = R.string.paypal_vault_status_off;
        }
        binding.paypalVaultStatus.setText(vaultState.isReady()
                && !vaultState.maskedPayer().isEmpty()
                ? getString(R.string.paypal_vault_status_linked, vaultState.maskedPayer())
                : getString(vaultStatus));
        updatingAutoPay = true;
        binding.paypalAutoPayEnabled.setChecked(autoPay.isEnabled());
        updatingAutoPay = false;
    }

    private PayPalEnvironment selectedPayPalEnvironment() {
        return binding.paypalEnvironment.getCheckedRadioButtonId()
                == R.id.paypal_environment_live
                ? PayPalEnvironment.LIVE : PayPalEnvironment.SANDBOX;
    }

    private void changePayPalEnvironment(int checkedId) {
        if (!editingUnlocked) {
            refreshPayPalEnvironmentSelection();
            return;
        }
        PayPalEnvironment selected = checkedId == R.id.paypal_environment_live
                ? PayPalEnvironment.LIVE : PayPalEnvironment.SANDBOX;
        if (selected == paypalCredentials.selectedEnvironment()) return;
        paypalCredentials.selectEnvironment(selected);
        cancelActivePayPalCheckout();
        binding.paypalClientId.setText("");
        binding.paypalClientSecret.setText("");
        Toast.makeText(this, R.string.paypal_environment_changed, Toast.LENGTH_SHORT).show();
        refreshPayPalSandboxState();
    }

    private void refreshPayPalEnvironmentSelection() {
        updatingPaypalEnvironment = true;
        binding.paypalEnvironment.check(
                paypalCredentials.selectedEnvironment() == PayPalEnvironment.LIVE
                        ? R.id.paypal_environment_live : R.id.paypal_environment_sandbox);
        updatingPaypalEnvironment = false;
    }

    private void changeAutoPay(boolean enabled) {
        if (!editingUnlocked) {
            refreshPayPalSandboxState();
            return;
        }
        if (!enabled) {
            autoPay.disable();
            refreshPayPalSandboxState();
            return;
        }
        if (!paypalCredentials.vaultState().isReady()) {
            updatingAutoPay = true;
            binding.paypalAutoPayEnabled.setChecked(false);
            updatingAutoPay = false;
            Toast.makeText(this, R.string.paypal_auto_pay_link_first,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.paypal_auto_pay_allow_title)
                .setMessage(R.string.paypal_auto_pay_allow_body)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    updatingAutoPay = true;
                    binding.paypalAutoPayEnabled.setChecked(false);
                    updatingAutoPay = false;
                })
                .setOnCancelListener(dialog -> {
                    updatingAutoPay = true;
                    binding.paypalAutoPayEnabled.setChecked(false);
                    updatingAutoPay = false;
                })
                .setPositiveButton(R.string.paypal_auto_pay_allow, (dialog, which) -> {
                    if (!autoPay.enable()) {
                        Toast.makeText(this, R.string.paypal_auto_pay_link_first,
                                Toast.LENGTH_SHORT).show();
                    }
                    refreshPayPalSandboxState();
                })
                .show();
    }

    private void cancelActivePayPalCheckout() {
        PenanceManager penance = new PenanceManager(this);
        String settlementId = penance.getActiveSettlementId();
        if (!settlementId.isEmpty()) penance.cancelSettlement(settlementId);
    }

    private static boolean validPayPalLink(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return host != null && "https".equalsIgnoreCase(uri.getScheme())
                    && ("paypal.me".equalsIgnoreCase(host)
                    || "paypal.com".equalsIgnoreCase(host)
                    || host.toLowerCase(Locale.ROOT).endsWith(".paypal.com"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void refreshAccessState() {
        if (binding == null || appMode == null) return;
        int status;
        if (!appMode.isAccessibilityEnabled()) status = R.string.app_mode_status_permission_off;
        else if (ScreenshotAccessibilityService.isRecognitionActive()) {
            status = R.string.app_mode_status_recognizing;
        } else if (ScreenshotAccessibilityService.isRunning()) {
            status = R.string.app_mode_status_waiting;
        } else status = R.string.app_mode_status_reconnecting;
        binding.serviceStatus.setText(status);
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

    private void loadApps() {
        appLoader.execute(() -> {
            PackageManager packages = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            @SuppressWarnings("deprecation")
            List<ResolveInfo> resolved = packages.queryIntentActivities(
                    launcher, PackageManager.MATCH_ALL);
            Map<String, AppEntry> unique = new LinkedHashMap<>();
            for (ResolveInfo info : resolved) {
                if (info.activityInfo == null
                        || getPackageName().equals(info.activityInfo.packageName)) continue;
                String packageName = info.activityInfo.packageName;
                if (unique.containsKey(packageName)) continue;
                CharSequence label = info.loadLabel(packages);
                Drawable icon;
                try { icon = info.loadIcon(packages); }
                catch (RuntimeException ignored) { icon = packages.getDefaultActivityIcon(); }
                unique.put(packageName, new AppEntry(
                        label == null ? packageName : label.toString(), packageName, icon));
            }
            List<AppEntry> entries = new ArrayList<>(unique.values());
            Collator collator = Collator.getInstance(Locale.getDefault());
            entries.sort((left, right) -> collator.compare(left.label, right.label));
            runOnUiThread(() -> renderApps(entries));
        });
    }

    private void renderApps(List<AppEntry> entries) {
        if (binding == null) return;
        binding.loadingApps.setVisibility(View.GONE);
        binding.appList.removeAllViews();
        Set<String> installed = new LinkedHashSet<>();
        int columns = Math.max(2, getResources().getInteger(R.integer.app_picker_columns));
        for (int index = 0; index < entries.size(); index++) {
            AppEntry entry = entries.get(index);
            installed.add(entry.packageName);
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(6), dp(7), dp(6), dp(6));
            tile.setContentDescription(entry.label + ", " + entry.packageName);

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(entry.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

            TextView label = new TextView(this);
            label.setText(entry.label);
            label.setTextColor(getColor(R.color.text_primary));
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

            LinearLayout choices = new LinearLayout(this);
            choices.setOrientation(LinearLayout.VERTICAL);
            CheckBox censor = assignmentCheck(R.string.app_selection_censor,
                    censorPackages.contains(entry.packageName));
            CheckBox limit = assignmentCheck(R.string.app_selection_limit,
                    timerPackages.contains(entry.packageName));
            choices.addView(censor);
            choices.addView(limit);
            tile.addView(choices, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            Runnable updateTile = () -> tile.setBackgroundResource(
                    censor.isChecked() || limit.isChecked()
                            ? R.drawable.bg_app_picker_tile_selected
                            : R.drawable.bg_app_picker_tile);
            censor.setOnCheckedChangeListener((button, checked) -> {
                if (checked) censorPackages.add(entry.packageName);
                else censorPackages.remove(entry.packageName);
                updateTile.run();
                renderSelectedCount();
            });
            limit.setOnCheckedChangeListener((button, checked) -> {
                if (checked) timerPackages.add(entry.packageName);
                else timerPackages.remove(entry.packageName);
                updateTile.run();
                renderSelectedCount();
            });
            updateTile.run();
            GridLayout.LayoutParams tileParams = new GridLayout.LayoutParams(
                    GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f));
            tileParams.width = 0;
            tileParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
            tileParams.setMargins(dp(3), dp(3), dp(3), dp(3));
            binding.appList.addView(tile, tileParams);
        }
        censorPackages.retainAll(installed);
        timerPackages.retainAll(installed);
        renderSelectedCount();
        applyEditState();
    }

    private CheckBox assignmentCheck(int label, boolean checked) {
        CheckBox check = new CheckBox(this);
        check.setText(label);
        check.setTextColor(getColor(R.color.text_secondary));
        check.setTextSize(10f);
        check.setChecked(checked);
        check.setEnabled(editingUnlocked);
        check.setMinHeight(dp(32));
        check.setPadding(0, 0, 0, 0);
        CompoundButtonCompat.setButtonTintList(check,
                ColorStateList.valueOf(getColor(R.color.accent)));
        return check;
    }

    private void renderSelectedCount() {
        if (binding != null) binding.selectedCount.setText(getString(
                R.string.app_selection_count, censorPackages.size(), timerPackages.size()));
    }

    private static void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setEnabledRecursive(group.getChildAt(index), enabled);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        appLoader.shutdownNow();
        binding = null;
        super.onDestroy();
    }

    private static final class AppEntry {
        private final String label;
        private final String packageName;
        private final Drawable icon;

        private AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
