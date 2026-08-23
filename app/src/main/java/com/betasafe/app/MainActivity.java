package com.betasafe.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.betasafe.app.databinding.ActivityMainBinding;
import com.betasafe.app.browser.BrowserActivity;
import com.betasafe.app.appmode.AppModeActivity;
import com.betasafe.app.capture.ExportActivity;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.commitment.CommitmentManager;
import com.betasafe.app.penance.PenanceActivity;
import com.betasafe.app.penance.PenanceManager;
import com.betasafe.app.penance.PenanceSnapshot;
import com.betasafe.app.service.ScreenCaptureService;
import com.betasafe.app.service.ScreenshotAccessibilityService;
import com.betasafe.app.appmode.AppModeManager;
import com.betasafe.app.appmode.AppTimerManager;
import com.betasafe.app.diagnostics.DiagnosticsRepository;
import com.betasafe.app.settings.SettingsActivity;
import com.betasafe.app.stats.StatsRepository;
import com.betasafe.app.stats.StatsSnapshot;
import com.betasafe.app.stats.StatsActivity;
import com.betasafe.app.stats.AchievementManager;
import com.betasafe.app.stats.MilestoneManager;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.settings.CaptureMethod;
import com.betasafe.app.settings.CensorAppearance;
import com.betasafe.app.settings.FeatureModuleManager;
import com.betasafe.app.settings.GlobalSettingsActivity;
import com.betasafe.app.appmode.ResumeNotificationManager;
import com.betasafe.app.security.ControllerPinGate;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.detection.text.TextSmutConfig;
import com.betasafe.app.util.AppShortcuts;
import com.betasafe.app.util.SubHubNavigation;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.snackbar.Snackbar;

/** Main source UI and explicit permission flow for starting on-device protection. */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private TextView editLockButton;
    private MediaProjectionManager projectionManager;
    private ActivityResultLauncher<Intent> projectionPermission;
    private ActivityResultLauncher<Intent> overlayPermission;
    private ActivityResultLauncher<String> notificationPermission;
    private long selectedPactDurationMs;
    private final Handler uiTimer = new Handler(Looper.getMainLooper());
    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            renderRuntimeState();
            uiTimer.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (ControllerPinManager.isDomModeActive()
                && SubHubNavigation.redirectIfDisabled(this, SubHubNavigation.Screen.CENSOR)) return;
        editLockButton = findViewById(R.id.button_edit_lock);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        projectionPermission = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent service = ScreenCaptureService.startIntent(
                                this, result.getResultCode(), result.getData());
                        ContextCompat.startForegroundService(this, service);
                        startSelectedPact();
                        updateProtectionButton(true);
                    } else {
                        showStatus(R.string.capture_cancelled);
                    }
                });
        overlayPermission = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                ignored -> {
                    if (Settings.canDrawOverlays(this)) requestProjection();
                    else showStatus(R.string.overlay_permission_needed);
                });
        notificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                ignored -> continueStartFlow());

        binding.buttonProtection.setOnClickListener(this::toggleProtection);
        editLockButton.setOnClickListener(view -> toggleEditSession());
        binding.buttonAccessibilityCapture.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.CENSOR);
        binding.buttonCensorSettings.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        binding.buttonBrowser.setOnClickListener(view ->
                startActivity(new Intent(this, BrowserActivity.class)));
        binding.buttonExport.setOnClickListener(view ->
                startActivity(new Intent(this, ExportActivity.class)));
        binding.buttonHelp.setOnClickListener(view ->
                startActivity(new Intent(this, HelpActivity.class)));
        binding.buttonStatistics.setOnClickListener(view ->
                startActivity(new Intent(this, StatsActivity.class)));
        binding.onboardingHelp.setOnClickListener(view -> {
            markOnboardingSeen();
            startActivity(new Intent(this, HelpActivity.class));
        });
        binding.onboardingDismiss.setOnClickListener(view -> markOnboardingSeen());
        binding.buttonCommitmentView.setVisibility(View.GONE);
        binding.commitmentTimer1h.setOnClickListener(view ->
                selectPactDuration(60L * 60L * 1000L));
        binding.commitmentTimer24h.setOnClickListener(view ->
                selectPactDuration(24L * 60L * 60L * 1000L));
        binding.commitmentTimer7d.setOnClickListener(view ->
                selectPactDuration(7L * 24L * 60L * 60L * 1000L));
        binding.commitmentTimer30d.setOnClickListener(view ->
                selectPactDuration(30L * 24L * 60L * 60L * 1000L));
        binding.buttonPenanceView.setOnClickListener(view ->
                startActivity(new Intent(this, PenanceActivity.class)));
        binding.subWalletPay.setOnClickListener(view ->
                startActivity(new Intent(this, PenanceActivity.class)));
        boolean seen = getSharedPreferences(SettingsRepository.PREFERENCES_NAME, MODE_PRIVATE)
                .getBoolean("has_seen_onboarding", false);
        binding.onboardingCard.setVisibility(seen ? View.GONE : View.VISIBLE);
        AppShortcuts.install(this);
        ControllerPinGate.ensureConfigured(this, () -> handleShortcutIntent(getIntent()));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShortcutIntent(intent);
    }

    private void handleShortcutIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        intent.setAction(Intent.ACTION_MAIN);
        if (AppShortcuts.ACTION_OPEN_BROWSER.equals(action)) {
            if (!ControllerPinManager.isDomModeActive()) return;
            AppShortcuts.reportUsed(this, "open_browser");
            startActivity(new Intent(this, BrowserActivity.class));
        } else if (AppShortcuts.ACTION_START_PROTECTION.equals(action)) {
            AppShortcuts.reportUsed(this, "start_protection");
            binding.getRoot().post(() -> toggleProtection(binding.buttonProtection));
        }
    }

    private void markOnboardingSeen() {
        getSharedPreferences(SettingsRepository.PREFERENCES_NAME, MODE_PRIVATE)
                .edit().putBoolean("has_seen_onboarding", true).apply();
        binding.onboardingCard.setVisibility(View.GONE);
    }

    private void toggleProtection(View view) {
        if (!new FeatureModuleManager(this).isCensorEnabled()) {
            startActivity(new Intent(this, GlobalSettingsActivity.class));
            return;
        }
        AppModeManager appMode = new AppModeManager(this);
        boolean stopping = ScreenCaptureService.isRunning() || appMode.isArmed()
                || ScreenshotAccessibilityService.isRecognitionActive();
        if (stopping && !CommitmentManager.mayStopProtection(this)) {
            showStatus(R.string.commitment_stop_requires_dom);
            return;
        }
        if (ScreenCaptureService.isRunning()) {
            startService(ScreenCaptureService.stopIntent(this));
            updateProtectionButton(false);
            return;
        }
        if (appMode.isArmed() || ScreenshotAccessibilityService.isRecognitionActive()) {
            appMode.setArmed(false);
            ResumeNotificationManager.cancel(this);
            updateProtectionButton(false);
            return;
        }
        if (new SettingsRepository(this).loadCaptureMethod() == CaptureMethod.APP_MODE) {
            appMode.setArmed(true);
            startSelectedPact();
            ResumeNotificationManager.show(this);
            if (!appMode.isAccessibilityEnabled()) {
                showStatus(R.string.capture_method_enable_accessibility);
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            updateProtectionButton(false);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            continueStartFlow();
        }
    }

    private void continueStartFlow() {
        if (!Settings.canDrawOverlays(this)) {
            showStatus(R.string.overlay_permission_needed);
            overlayPermission.launch(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        requestProjection();
    }

    private void requestProjection() {
        projectionPermission.launch(projectionManager.createScreenCaptureIntent());
    }

    private void showStatus(int message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private void selectPactDuration(long durationMillis) {
        if (ControllerPinManager.isDomModeActive() || CommitmentManager.isActive(this)) return;
        selectedPactDurationMs = selectedPactDurationMs == durationMillis ? 0L : durationMillis;
        renderPactSelection();
    }

    private void startSelectedPact() {
        if (selectedPactDurationMs <= 0L || CommitmentManager.isActive(this)) return;
        CommitmentManager.start(this, selectedPactDurationMs);
        selectedPactDurationMs = 0L;
        renderCommitmentState();
    }

    private void renderPactSelection() {
        pactButton(binding.commitmentTimer1h, 60L * 60L * 1000L);
        pactButton(binding.commitmentTimer24h, 24L * 60L * 60L * 1000L);
        pactButton(binding.commitmentTimer7d, 7L * 24L * 60L * 60L * 1000L);
        pactButton(binding.commitmentTimer30d, 30L * 24L * 60L * 60L * 1000L);
    }

    private void pactButton(TextView button, long duration) {
        boolean selected = selectedPactDurationMs == duration;
        button.setBackgroundResource(selected
                ? R.drawable.bg_primary_button : R.drawable.bg_outline_button);
        button.setTextColor(getColor(selected ? R.color.text_primary : R.color.accent));
    }

    private void renderCommitmentState() {
        if (binding == null) return;
        boolean active = CommitmentManager.isActive(this);
        boolean domMode = ControllerPinManager.isDomModeActive();
        binding.commitmentCard.setVisibility(active || !domMode ? View.VISIBLE : View.GONE);
        binding.commitmentStartPanel.setVisibility(!domMode && !active ? View.VISIBLE : View.GONE);
        binding.commitmentActivePanel.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) binding.commitmentStatus.setText(getString(
                R.string.commitment_active_remaining,
                CommitmentActivity.formatDuration(CommitmentManager.remainingMillis(this))));
        renderPactSelection();
    }

    private void updateProtectionButton(boolean running) {
        boolean appModeRunning = new AppModeManager(this).isArmed()
                || ScreenshotAccessibilityService.isRecognitionActive();
        binding.buttonProtection.setEnabled(true);
        binding.buttonProtection.setText(running || appModeRunning
                ? R.string.stop_protection : R.string.start_protection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            uiTimer.removeCallbacks(uiTick);
            uiTimer.post(uiTick);
            renderCommitmentState();
            PenanceSnapshot penance = new PenanceManager(this).snapshot(System.currentTimeMillis());
            binding.penanceCard.setVisibility(View.GONE);
            binding.penanceStatus.setText(penance.isEnabled()
                    ? getString(R.string.penance_home_status,
                            PenanceManager.formatMoney(penance.getDueCents()),
                            PenanceManager.formatMoney(penance.getMercyCents()),
                            PenanceManager.formatMoney(penance.getPaidCents()))
                    : getString(R.string.penance_home_inactive));
            showProgressUnlocks(new StatsRepository(this).load());
            renderEditState();
            renderSubDashboard();
        }
    }

    private void toggleEditSession() {
        if (ControllerPinManager.isDomModeActive()) {
            ControllerPinManager.enterSubMode();
            renderEditState();
        } else ControllerPinGate.require(this, this::renderEditState, false);
    }

    private void renderEditState() {
        if (binding == null) return;
        boolean domMode = ControllerPinManager.isDomModeActive();
        ControllerEditMode.renderButton(this, editLockButton);
        TextView headerSubtitle = findViewById(R.id.header_subtitle);
        if (headerSubtitle != null) headerSubtitle.setText(domMode
                ? R.string.header_subtitle_dom : R.string.header_subtitle_sub);
        binding.domContent.setVisibility(domMode ? View.VISIBLE : View.GONE);
        binding.subDashboard.setVisibility(domMode ? View.GONE : View.VISIBLE);
        int bottom = dp(domMode ? 116 : 26);
        binding.pageContent.setPaddingRelative(binding.pageContent.getPaddingStart(),
                binding.pageContent.getPaddingTop(), binding.pageContent.getPaddingEnd(), bottom);
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.CENSOR);
        renderSubDashboard();
        renderCommitmentState();
        updateProtectionButton(ScreenCaptureService.isRunning());
    }

    private void renderSubDashboard() {
        if (binding == null || ControllerPinManager.isDomModeActive()) return;
        FeatureModuleManager modules = new FeatureModuleManager(this);
        boolean censorEnabled = modules.isCensorEnabled();
        boolean limitsEnabled = modules.isLimitsEnabled();
        boolean walletEnabled = modules.isWalletEnabled();
        binding.subCensorCard.setVisibility(censorEnabled ? View.VISIBLE : View.GONE);
        binding.subLimitsCard.setVisibility(View.GONE);
        binding.subWalletCard.setVisibility(View.GONE);
        binding.subModulesEmpty.setVisibility(!censorEnabled ? View.VISIBLE : View.GONE);
        binding.buttonProtection.setVisibility(censorEnabled ? View.VISIBLE : View.GONE);
        binding.protectionStatusRow.setVisibility(censorEnabled ? View.VISIBLE : View.GONE);
        binding.runtimeStatus.setVisibility(censorEnabled ? View.VISIBLE : View.GONE);
        binding.modeHint.setVisibility(censorEnabled ? View.VISIBLE : View.GONE);

        long now = System.currentTimeMillis();
        if (censorEnabled) {
            boolean active = ScreenCaptureService.isRunning()
                    || ScreenshotAccessibilityService.isRecognitionActive();
            boolean armed = new AppModeManager(this).isEffectivelyArmed(now);
            binding.subCensorVoice.setText(active ? R.string.sub_censor_active
                    : armed ? R.string.sub_censor_armed : R.string.sub_censor_idle);
            SettingsRepository settings = new SettingsRepository(this);
            CensorAppearance appearance = settings.loadAppearance();
            TextSmutConfig textSmut = settings.loadTextSmutConfig();
            String detector = getString(textSmut.isEnabled()
                    ? R.string.sub_censor_detection_images_text
                    : R.string.sub_censor_detection_images);
            String capture = getString(settings.loadCaptureMethod() == CaptureMethod.APP_MODE
                    ? R.string.sub_capture_app_watch : R.string.sub_capture_screen_session);
            binding.subCensorSummary.setText(getString(R.string.sub_censor_summary,
                    friendlyEffectName(appearance.getType()), detector + " · " + capture));
            StatsSnapshot stats = new StatsRepository(this).load();
            binding.subCensorStats.setText(getString(R.string.sub_censor_stats,
                    stats.getCurrentSessionBlocks(),
                    StatsSnapshot.formatClock(stats.getCurrentSessionSeconds())));
        }

        if (limitsEnabled) {
            AppModeManager appMode = new AppModeManager(this);
            AppTimerManager.Settings timer = new AppTimerManager(this).loadSettings();
            String limits;
            if (timer.perAppEnabled && timer.totalEnabled) {
                limits = getString(R.string.sub_limits_both,
                        timer.perAppMinutes, timer.totalMinutes);
            } else if (timer.perAppEnabled) {
                limits = getString(R.string.sub_limits_per_app, timer.perAppMinutes);
            } else if (timer.totalEnabled) {
                limits = getString(R.string.sub_limits_combined, timer.totalMinutes);
            } else {
                limits = getString(R.string.sub_limits_none);
            }
            binding.subLimitsSummary.setText(timer.anyEnabled()
                    ? getString(R.string.sub_limits_selected,
                            appMode.getTimerPackages().size(), limits)
                    : limits);
            binding.subLimitsDetail.setText(appMode.isEffectivelyArmed(now)
                    ? R.string.sub_limits_armed : R.string.sub_limits_sleeping);
        }

        if (walletEnabled) {
            PenanceSnapshot wallet = new PenanceManager(this).snapshot(now);
            binding.subWalletVoice.setText(wallet.isEnabled()
                    ? R.string.sub_wallet_active : R.string.sub_wallet_inactive);
            binding.subWalletSummary.setText(getString(R.string.sub_wallet_summary,
                    PenanceManager.formatMoney(wallet.getDueCents()),
                    PenanceManager.formatMoney(wallet.getMercyCents()),
                    PenanceManager.formatMoney(wallet.getPaidCents())));
            boolean checkout = wallet.getCheckoutCents() > 0;
            binding.subWalletPay.setVisibility(wallet.getDueCents() > 0 || checkout
                    ? View.VISIBLE : View.GONE);
            binding.subWalletPay.setText(checkout
                    ? R.string.sub_wallet_resume : R.string.sub_wallet_pay);
        }
    }

    private String friendlyEffectName(CensorAppearance.Type type) {
        String raw = type == null ? "BOX" : type.name();
        String spaced = raw.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onPause() {
        uiTimer.removeCallbacks(uiTick);
        super.onPause();
    }

    private void renderRuntimeState() {
        if (binding == null) return;
        boolean manual = ScreenCaptureService.isRunning();
        boolean automatic = ScreenshotAccessibilityService.isRecognitionActive();
        boolean armed = new AppModeManager(this).isArmed();
        boolean waiting = ScreenshotAccessibilityService.isRunning()
                && new AppModeManager(this).isEffectivelyArmed(System.currentTimeMillis())
                && !automatic;
        updateProtectionButton(manual);

        CaptureMethod method = new SettingsRepository(this).loadCaptureMethod();
        binding.buttonAccessibilityCapture.setText(method == CaptureMethod.APP_MODE
                ? R.string.capture_method_app_mode_button
                : R.string.capture_method_recording_button);

        int status = (manual || automatic) ? R.string.status_active
                : (waiting || armed) ? R.string.status_app_mode_waiting : R.string.status_inactive;
        binding.protectionStatus.setText(status);
        binding.protectionStatus.setTextColor(getColor(
                manual || automatic || waiting ? R.color.accent : R.color.text_muted));
        binding.protectionStatusDot.setAlpha(manual || automatic ? 1f : waiting ? 0.55f : 0.25f);

        DiagnosticsRepository.Snapshot runtime = DiagnosticsRepository.snapshot();
        if (runtime.isRunning() && runtime.isReady()) {
            binding.runtimeStatus.setText(getString(R.string.runtime_ready,
                    runtime.getProvider(), runtime.getModel()));
        } else if (runtime.isRunning() && !"None".equals(runtime.getLastFailure())) {
            binding.runtimeStatus.setText(getString(
                    R.string.runtime_failed, runtime.getLastFailure()));
        } else if (runtime.isRunning()) {
            binding.runtimeStatus.setText(R.string.runtime_initializing);
        } else {
            binding.runtimeStatus.setText(R.string.runtime_idle);
        }

        StatsSnapshot stats = new StatsRepository(this).load();
        binding.statsBlocks.setText(String.valueOf(stats.getCurrentSessionBlocks()));
        binding.statsTime.setText(StatsSnapshot.formatClock(stats.getCurrentSessionSeconds()));
        binding.statsSessions.setText(String.valueOf(stats.getSessions()));
        renderCommitmentState();
        renderSubDashboard();
    }

    private void showProgressUnlocks(StatsSnapshot stats) {
        AchievementManager achievements = new AchievementManager(this);
        achievements.checkAchievements(stats);
        java.util.List<AchievementManager.Achievement> unlocked =
                achievements.takePendingNotifications();
        if (!unlocked.isEmpty()) {
            AchievementManager.Achievement first = unlocked.get(0);
            String message = getString(first.getDescription());
            if (unlocked.size() > 1) message += "\n\n" + getString(
                    R.string.achievement_more_unlocked, unlocked.size() - 1);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.achievement_unlocked_title)
                            + " " + getString(first.getName()))
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        MilestoneManager.Result milestone = MilestoneManager.takeUnseen(this, stats.getTotalBlocks());
        if (milestone != null) {
            Snackbar.make(binding.getRoot(), milestone.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
