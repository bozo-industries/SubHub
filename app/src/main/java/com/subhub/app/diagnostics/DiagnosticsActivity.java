package com.subhub.app.diagnostics;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.subhub.app.BuildConfig;
import com.subhub.app.R;
import com.subhub.app.databinding.ActivityDiagnosticsBinding;
import com.subhub.app.detection.DetectorConfig;
import com.subhub.app.pack.PackManager;
import com.subhub.app.security.ControllerEditMode;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.service.ScreenCaptureService;
import com.subhub.app.service.ScreenshotAccessibilityService;
import com.subhub.app.settings.SettingsRepository;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local runtime health plus explicit, user-shared Censor Lab bundles; no socket or secret fields. */
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
    private final ExecutorService labIo = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "censor-lab-export");
        thread.setDaemon(true);
        return thread;
    });
    private boolean labBusy;
    private final ActivityResultLauncher<String[]> recordingPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), recording -> {
                if (recording != null) exportAndShare(recording);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiagnosticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settings = new SettingsRepository(this);
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonRefresh.setOnClickListener(view -> render());
        binding.buttonCensorLabStart.setOnClickListener(view -> startLabSession());
        binding.buttonCensorLabStop.setOnClickListener(view -> stopLabSession());
        binding.buttonCensorLabAttach.setOnClickListener(view -> {
            if (CensorLabRecorder.isActive()) return;
            if (CensorLabRecorder.latest(this) == null) {
                Toast.makeText(this, R.string.diagnostics_lab_no_session,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            recordingPicker.launch(new String[]{"video/*"});
        });
        binding.buttonCensorLabShareTrace.setOnClickListener(view -> exportAndShare(null));
        binding.switchDiagnosticsOverlay.setChecked(settings.preferences().getBoolean(
                DiagnosticsRepository.PREF_OVERLAY, false));
        binding.switchDiagnosticsOverlay.setOnCheckedChangeListener((button, checked) ->
                settings.preferences().edit().putBoolean(
                        DiagnosticsRepository.PREF_OVERLAY, checked).apply());
        if (ControllerPinManager.isDomModeActive()) {
            editMode = ControllerEditMode.bind(this, binding.buttonEditLock, editing ->
                    binding.switchDiagnosticsOverlay.setEnabled(editing));
        } else {
            binding.buttonEditLock.setVisibility(android.view.View.GONE);
            binding.switchDiagnosticsOverlay.setEnabled(false);
        }
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
        labIo.shutdown();
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
                "Frames: %d processed / %d stale dropped\nRegions: %d total / %d latest\nInference: %d ms latest / %d ms average / %d ms peak\nStages: %d ms preprocess / %d ms runtime / %d ms postprocess\nFrame age before UI: %d ms\nEnd-to-end overlay publish: %d ms\nText: %d Accessibility candidates / %d stable / %d OCR stable\nOCR: %d runs / %d ms latest / %d ms average / %d ms peak / %d stale dropped\nLatest frame: %d × %d\nLast sanitized failure: %s",
                live.getFrames(), live.getDroppedFrames(),
                live.getTotalDetections(), live.getLastDetections(),
                live.getLastInferenceMs(), live.getAverageInferenceMs(), live.getPeakInferenceMs(),
                live.getLastPreprocessMs(), live.getLastRuntimeMs(),
                live.getLastPostprocessMs(), live.getLastFrameAgeMs(),
                live.getLastPublishDelayMs(),
                live.getLastAccessibilityTextCandidates(),
                live.getLastAccessibilityTextStable(), live.getLastOcrTextStable(),
                live.getOcrRuns(), live.getLastOcrMs(), live.getAverageOcrMs(),
                live.getPeakOcrMs(), live.getStaleOcrResults(),
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
                "SubHub %s (%d)\nAndroid %d\n%s %s\nDiagnostics transport: local bundle via Android share sheet",
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT,
                Build.MANUFACTURER, Build.MODEL));
        renderLabState();
    }

    private void startLabSession() {
        if (labBusy || CensorLabRecorder.isActive()) return;
        try {
            CensorLabRecorder.SessionState session = CensorLabRecorder.start(this);
            showSyncMarker(true, session.id);
            Toast.makeText(this, R.string.diagnostics_lab_started, Toast.LENGTH_LONG).show();
            renderLabState();
        } catch (Exception error) {
            showLabFailure(error);
        }
    }

    private void stopLabSession() {
        if (labBusy || !CensorLabRecorder.isActive()) return;
        String id = CensorLabRecorder.activeSessionId();
        labBusy = true;
        showSyncMarker(false, id);
        Toast.makeText(this, R.string.diagnostics_lab_stopping, Toast.LENGTH_SHORT).show();
        renderLabState();
        labIo.execute(() -> {
            SystemClock.sleep(1_050L);
            try {
                CensorLabRecorder.stop(getApplicationContext());
                runOnUiThread(() -> {
                    if (binding == null) return;
                    labBusy = false;
                    renderLabState();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    labBusy = false;
                    showLabFailure(error);
                    renderLabState();
                });
            }
        });
    }

    private void exportAndShare(Uri recording) {
        if (labBusy || CensorLabRecorder.isActive()) return;
        CensorLabRecorder.CompletedSession session = CensorLabRecorder.latest(this);
        if (session == null) {
            Toast.makeText(this, R.string.diagnostics_lab_no_session,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        labBusy = true;
        renderLabState();
        Toast.makeText(this, R.string.diagnostics_lab_exporting, Toast.LENGTH_SHORT).show();
        labIo.execute(() -> {
            try {
                File bundle = CensorLabBundleExporter.export(
                        getApplicationContext(), session, recording);
                Uri uri = FileProvider.getUriForFile(getApplicationContext(),
                        getPackageName() + ".updates", bundle);
                runOnUiThread(() -> {
                    if (binding == null) return;
                    labBusy = false;
                    renderLabState();
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("application/zip")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send,
                            getString(R.string.diagnostics_lab_share_chooser)));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    labBusy = false;
                    showLabFailure(error);
                    renderLabState();
                });
            }
        });
    }

    private void showSyncMarker(boolean starting, String id) {
        String safeId = id == null ? "unknown" : id;
        binding.censorLabSyncMarker.setText(getString(starting
                ? R.string.diagnostics_lab_sync_start : R.string.diagnostics_lab_sync_stop,
                safeId));
        binding.censorLabSyncMarker.setVisibility(View.VISIBLE);
        binding.diagnosticsScroll.smoothScrollTo(0, 0);
        binding.censorLabSyncMarker.postOnAnimation(() ->
                CensorLabRecorder.mark(starting ? "SYNC_START_UI_VISIBLE" : "SYNC_STOP_UI_VISIBLE"));
        binding.censorLabSyncMarker.postDelayed(() -> {
            if (binding != null) binding.censorLabSyncMarker.setVisibility(View.GONE);
        }, 1_250L);
    }

    private void renderLabState() {
        if (binding == null) return;
        CensorLabRecorder.SessionState state = CensorLabRecorder.state(this);
        if (state.active) {
            binding.censorLabStatus.setText(getString(R.string.diagnostics_lab_active,
                    state.id, state.eventCount, state.droppedEvents));
        } else if (state.completed != null) {
            String stopped = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date(state.completed.stoppedWallMillis));
            binding.censorLabStatus.setText(getString(R.string.diagnostics_lab_complete,
                    state.id, state.eventCount, state.droppedEvents, stopped));
        } else {
            binding.censorLabStatus.setText(R.string.diagnostics_lab_idle);
        }
        binding.buttonCensorLabStart.setEnabled(!labBusy && !state.active);
        binding.buttonCensorLabStop.setEnabled(!labBusy && state.active);
        boolean canShare = !labBusy && !state.active && state.completed != null;
        binding.buttonCensorLabAttach.setEnabled(canShare);
        binding.buttonCensorLabShareTrace.setEnabled(canShare);
    }

    private void showLabFailure(Exception error) {
        String detail = error == null || error.getMessage() == null
                ? "Unknown failure" : error.getMessage();
        Toast.makeText(this, getString(R.string.diagnostics_lab_failed, detail),
                Toast.LENGTH_LONG).show();
    }

    private static String yesNo(boolean value) { return value ? "Granted" : "Not granted"; }
    private static String running(boolean value) { return value ? "Running" : "Stopped"; }

    private static String duration(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
