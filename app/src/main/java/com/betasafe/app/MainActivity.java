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
import com.betasafe.app.diagnostics.DiagnosticsRepository;
import com.betasafe.app.settings.SettingsActivity;
import com.betasafe.app.stats.StatsRepository;
import com.betasafe.app.stats.StatsSnapshot;
import com.betasafe.app.stats.StatsActivity;
import com.betasafe.app.stats.AchievementManager;
import com.betasafe.app.stats.MilestoneManager;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.util.AppShortcuts;
import com.betasafe.app.util.ParityNavigation;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.snackbar.Snackbar;

/** Main source UI and explicit permission flow for starting on-device protection. */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private MediaProjectionManager projectionManager;
    private ActivityResultLauncher<Intent> projectionPermission;
    private ActivityResultLauncher<Intent> overlayPermission;
    private ActivityResultLauncher<String> notificationPermission;
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
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        projectionPermission = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent service = ScreenCaptureService.startIntent(
                                this, result.getResultCode(), result.getData());
                        ContextCompat.startForegroundService(this, service);
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
        binding.buttonAccessibilityCapture.setOnClickListener(view ->
                startActivity(new Intent(this, AppModeActivity.class)));
        ParityNavigation.bind(this, binding.getRoot(), ParityNavigation.Screen.HOME);
        binding.buttonStatistics.setOnClickListener(view ->
                startActivity(new Intent(this, StatsActivity.class)));
        binding.onboardingHelp.setOnClickListener(view -> {
            markOnboardingSeen();
            startActivity(new Intent(this, HelpActivity.class));
        });
        binding.onboardingDismiss.setOnClickListener(view -> markOnboardingSeen());
        binding.buttonCommitmentView.setOnClickListener(view ->
                startActivity(new Intent(this, CommitmentActivity.class)));
        binding.buttonPenanceView.setOnClickListener(view ->
                startActivity(new Intent(this, PenanceActivity.class)));
        boolean seen = getSharedPreferences(SettingsRepository.PREFERENCES_NAME, MODE_PRIVATE)
                .getBoolean("has_seen_onboarding", false);
        binding.onboardingCard.setVisibility(seen ? View.GONE : View.VISIBLE);
        AppShortcuts.install(this);
        handleShortcutIntent(getIntent());
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
        if (ScreenCaptureService.isRunning()) {
            startService(ScreenCaptureService.stopIntent(this));
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

    private void updateProtectionButton(boolean running) {
        binding.buttonProtection.setEnabled(!ScreenshotAccessibilityService.isRecognitionActive());
        binding.buttonProtection.setText(running ? R.string.stop_protection
                : ScreenshotAccessibilityService.isRecognitionActive()
                ? R.string.app_mode_active : R.string.start_protection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            uiTimer.removeCallbacks(uiTick);
            uiTimer.post(uiTick);
            boolean commitmentActive = CommitmentManager.isActive(this);
            binding.commitmentCard.setVisibility(commitmentActive ? View.VISIBLE : View.GONE);
            if (commitmentActive) {
                binding.commitmentStatus.setText(getString(R.string.commitment_active_remaining,
                        CommitmentActivity.formatDuration(
                                CommitmentManager.remainingMillis(this))));
            }
            PenanceSnapshot penance = new PenanceManager(this).snapshot(System.currentTimeMillis());
            binding.penanceStatus.setText(penance.isEnabled()
                    ? getString(R.string.penance_home_status,
                            PenanceManager.formatMoney(penance.getDueCents()),
                            PenanceManager.formatMoney(penance.getMercyCents()),
                            PenanceManager.formatMoney(penance.getPaidCents()))
                    : getString(R.string.penance_home_inactive));
            showProgressUnlocks(new StatsRepository(this).load());
        }
    }

    @Override protected void onPause() {
        uiTimer.removeCallbacks(uiTick);
        super.onPause();
    }

    private void renderRuntimeState() {
        if (binding == null) return;
        boolean manual = ScreenCaptureService.isRunning();
        boolean automatic = ScreenshotAccessibilityService.isRecognitionActive();
        boolean waiting = ScreenshotAccessibilityService.isRunning()
                && new AppModeManager(this).isArmed() && !automatic;
        updateProtectionButton(manual);

        int status = (manual || automatic) ? R.string.status_active
                : waiting ? R.string.status_app_mode_waiting : R.string.status_inactive;
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
