package com.betasafe.app.diagnostics;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.betasafe.app.BuildConfig;
import com.betasafe.app.databinding.ActivityDiagnosticsBinding;
import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.pack.PackManager;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.service.ScreenCaptureService;
import com.betasafe.app.service.ScreenshotAccessibilityService;
import com.betasafe.app.settings.SettingsRepository;

import java.util.Locale;

/** Local-only runtime health view; intentionally has no socket, export, or secret fields. */
public final class DiagnosticsActivity extends AppCompatActivity {
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            refreshHandler.postDelayed(this, 500);
        }
    };
    private ActivityDiagnosticsBinding binding;
    private SettingsRepository settings;
    private ControllerEditMode editMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiagnosticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settings = new SettingsRepository(this);
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonRefresh.setOnClickListener(view -> render());
        binding.switchDiagnosticsOverlay.setChecked(settings.preferences().getBoolean(
                DiagnosticsRepository.PREF_OVERLAY, false));
        binding.switchDiagnosticsOverlay.setOnCheckedChangeListener((button, checked) ->
                settings.preferences().edit().putBoolean(
                        DiagnosticsRepository.PREF_OVERLAY, checked).apply());
        editMode = ControllerEditMode.bind(this, binding.buttonEditLock, editing ->
                binding.switchDiagnosticsOverlay.setEnabled(editing));
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (editMode != null) editMode.refresh();
        refreshHandler.removeCallbacks(refresh);
        refreshHandler.post(refresh);
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private void render() {
        if (binding == null) return;
        DiagnosticsRepository.Snapshot live = DiagnosticsRepository.snapshot();
        String state = live.isRunning() ? (live.isReady() ? "RUNNING" : "INITIALIZING") : "IDLE";
        binding.runtimeStatus.setText(String.format(Locale.ROOT,
                "State: %s\nCapture: %s\nProvider: %s\nModel: %s\nModel input: %d × %d\nUptime: %s",
                state, live.getMode(), live.getProvider(), live.getModel(), live.getResolution(),
                live.getResolution(), duration(live.getUptimeMs())));
        binding.performanceStatus.setText(String.format(Locale.ROOT,
                "Frames: %d\nRegions: %d total / %d latest\nInference: %d ms latest / %d ms average / %d ms peak\nLatest frame: %d × %d\nLast sanitized failure: %s",
                live.getFrames(), live.getTotalDetections(), live.getLastDetections(),
                live.getLastInferenceMs(), live.getAverageInferenceMs(), live.getPeakInferenceMs(),
                live.getFrameWidth(), live.getFrameHeight(), live.getLastFailure()));

        DetectorConfig config = settings.loadDetectorConfig();
        binding.configurationStatus.setText(String.format(Locale.ROOT,
                "Preset: %s\nRequested model: %s\nInput: %d × %d\nCapture scale: %.0f%%\nInterval: %d ms\nConfidence: %.0f%%\nEnabled detector categories: %d\nConfiguration pack: %s",
                settings.loadDetectionPreset().preferenceValue(), config.getModelFilename(),
                config.getInferenceResolution(), config.getInferenceResolution(),
                config.getCaptureScale() * 100f, config.getDetectionIntervalMs(),
                config.getConfidenceThreshold() * 100f, config.getEnabledCategories().size(),
                new PackManager(this).activePackId() == null ? "None" : "Active"));

        boolean notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batteryExempt = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        binding.permissionsStatus.setText(String.format(Locale.ROOT,
                "Overlay permission: %s\nNotifications: %s\nMediaProjection capture: %s\nAccessibility capture: %s\nBattery optimization exemption: %s",
                yesNo(Settings.canDrawOverlays(this)), yesNo(notifications),
                running(ScreenCaptureService.isRunning()),
                running(ScreenshotAccessibilityService.isRunning()), yesNo(batteryExempt)));

        binding.buildStatus.setText(String.format(Locale.ROOT,
                "SubHub %s (%d)\nAndroid %d\n%s %s\nDiagnostics transport: in-app memory only",
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT,
                Build.MANUFACTURER, Build.MODEL));
    }

    private static String yesNo(boolean value) { return value ? "Granted" : "Not granted"; }
    private static String running(boolean value) { return value ? "Running" : "Stopped"; }

    private static String duration(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
