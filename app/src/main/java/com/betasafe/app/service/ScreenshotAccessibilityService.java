package com.betasafe.app.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;

import com.betasafe.app.detection.Detection;
import com.betasafe.app.detection.DetectionEngine;
import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.detection.ObjectTracker;
import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.diagnostics.DiagnosticsRepository;
import com.betasafe.app.overlay.OverlayController;
import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.stats.StatsRepository;
import com.betasafe.app.stats.AchievementManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-enabled screenshot capture mode backed by Android's accessibility consent screen. */
public final class ScreenshotAccessibilityService extends AccessibilityService {
    private static final String TAG = "ScreenshotA11y";
    private static final String DIAGNOSTICS_MODE = "Accessibility screenshot";
    private static volatile boolean running;

    private final AtomicBoolean processing = new AtomicBoolean();
    private final AtomicBoolean firstFrameReported = new AtomicBoolean();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> reloadSettings();
    private ScheduledExecutorService worker;
    private SettingsRepository settings;
    private StatsRepository stats;
    private DetectionEngine detector;
    private ObjectTracker tracker;
    private OverlayController overlay;
    private volatile DetectorConfig detectorConfig;

    public static boolean isRunning() { return running; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Accessibility screenshot capture requires Android 11 or newer");
            disableSelf();
            return;
        }
        settings = new SettingsRepository(this);
        stats = new StatsRepository(this);
        settings.preferences().registerOnSharedPreferenceChangeListener(listener);
        overlay = new OverlayController(
                this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        overlay.setAppearance(settings.loadAppearance());
        overlay.show();
        stats.startSession();
        running = true;

        worker = Executors.newSingleThreadScheduledExecutor();
        worker.execute(this::initializePipeline);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void initializePipeline() {
        try {
            DetectorConfig config = settings.loadDetectorConfig();
            detectorConfig = config;
            DiagnosticsRepository.begin(DIAGNOSTICS_MODE, config.getInferenceResolution());
            detector = new DetectionEngine(this, config);
            detector.initialize();
            DiagnosticsRepository.ready(DIAGNOSTICS_MODE, detector.getActiveProvider(),
                    detector.getActiveModel(), config.getInferenceResolution());
            tracker = new ObjectTracker(config);
            worker.scheduleWithFixedDelay(
                    this::requestScreenshot,
                    0,
                    Math.max(500, config.getDetectionIntervalMs()),
                    TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
            Log.e(TAG, "Could not initialize accessibility capture", error);
            disableSelf();
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void requestScreenshot() {
        if (!running || !processing.compareAndSet(false, true)) return;
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult result) {
                process(result);
            }

            @Override
            public void onFailure(int errorCode) {
                DiagnosticsRepository.failCode(
                        DIAGNOSTICS_MODE, "Screenshot error", errorCode);
                Log.w(TAG, "Accessibility screenshot failed with code " + errorCode);
                processing.set(false);
            }
        });
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void process(ScreenshotResult result) {
        Bitmap wrapped = null;
        Bitmap frame = null;
        HardwareBuffer buffer = result.getHardwareBuffer();
        try {
            wrapped = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (wrapped == null) return;
            frame = wrapped.copy(Bitmap.Config.ARGB_8888, false);
            List<Detection> detections = detector.detect(frame);
            List<TrackedObject> tracks = tracker.update(detections);
            DetectorConfig currentConfig = detectorConfig;
            boolean recordedBlocks = stats.onTracks(tracks, currentConfig == null
                    ? null : currentConfig.getEnabledCategories());
            if (recordedBlocks) {
                new AchievementManager(this).checkAchievements(stats.load());
            }
            if (firstFrameReported.compareAndSet(false, true)) {
                Log.i(TAG, "First accessibility frame processed in "
                        + detector.getLastInferenceMs() + " ms at "
                        + frame.getWidth() + "x" + frame.getHeight());
            }
            Bitmap overlayFrame = frame.copy(Bitmap.Config.ARGB_8888, false);
            int width = frame.getWidth();
            int height = frame.getHeight();
            DiagnosticsRepository.Snapshot diagnostics = DiagnosticsRepository.recordFrame(
                    DIAGNOSTICS_MODE, detector.getLastInferenceMs(), tracks.size(), width, height);
            String diagnosticText = diagnosticsOverlayText(diagnostics);
            main.post(() -> {
                if (overlay != null) {
                    overlay.setDiagnostics(diagnosticText);
                    overlay.update(tracks, width, height, overlayFrame);
                }
                else overlayFrame.recycle();
            });
        } catch (Exception error) {
            DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
            Log.w(TAG, "Could not process accessibility screenshot", error);
        } finally {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            if (wrapped != null && !wrapped.isRecycled()) wrapped.recycle();
            buffer.close();
            processing.set(false);
        }
    }

    private void reloadSettings() {
        if (settings == null) return;
        main.post(() -> {
            if (overlay != null) {
                overlay.setAppearance(settings.loadAppearance());
                overlay.setDiagnostics(diagnosticsOverlayText());
            }
        });
        DetectorConfig config = settings.loadDetectorConfig();
        detectorConfig = config;
        if (detector != null) detector.setConfig(config);
        if (tracker != null) tracker.setConfig(config);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || tracker == null) return;
        tracker.clear();
        main.post(() -> {
            if (overlay != null) overlay.update(Collections.emptyList(), 1, 1, null);
        });
    }

    @Override
    public void onInterrupt() {
        // Android may temporarily interrupt feedback; the scheduled capture loop remains owned here.
    }

    private String diagnosticsOverlayText() {
        return diagnosticsOverlayText(DiagnosticsRepository.snapshot());
    }

    private String diagnosticsOverlayText(DiagnosticsRepository.Snapshot snapshot) {
        return settings != null && settings.preferences().getBoolean(
                DiagnosticsRepository.PREF_OVERLAY, false)
                ? DiagnosticsRepository.overlayText(snapshot) : "";
    }

    @Override
    public void onDestroy() {
        running = false;
        DiagnosticsRepository.stop(DIAGNOSTICS_MODE);
        if (settings != null) {
            settings.preferences().unregisterOnSharedPreferenceChangeListener(listener);
        }
        if (worker != null) worker.shutdownNow();
        if (stats != null) stats.endSession();
        if (detector != null) detector.close();
        if (overlay != null) overlay.close();
        super.onDestroy();
    }
}
