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

import com.betasafe.app.appmode.AppModeManager;
import com.betasafe.app.appmode.AppModePolicy;
import com.betasafe.app.detection.Detection;
import com.betasafe.app.detection.DetectionEngine;
import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.detection.ObjectTracker;
import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.diagnostics.DiagnosticsRepository;
import com.betasafe.app.overlay.OverlayController;
import com.betasafe.app.popup.PopupStormManager;
import com.betasafe.app.penance.PenanceManager;
import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.stats.StatsRepository;
import com.betasafe.app.stats.AchievementManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-enabled screenshot capture mode backed by Android's accessibility consent screen. */
public final class ScreenshotAccessibilityService extends AccessibilityService {
    private static final String TAG = "ScreenshotA11y";
    private static final String DIAGNOSTICS_MODE = "Accessibility screenshot";
    private static volatile boolean running;
    private static volatile boolean recognitionActive;

    private final AtomicBoolean processing = new AtomicBoolean();
    private final AtomicBoolean firstFrameReported = new AtomicBoolean();
    private final AtomicBoolean initializing = new AtomicBoolean();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> reloadSettings();
    private ScheduledExecutorService worker;
    private volatile ScheduledFuture<?> captureSchedule;
    private SettingsRepository settings;
    private StatsRepository stats;
    private DetectionEngine detector;
    private ObjectTracker tracker;
    private OverlayController overlay;
    private volatile DetectorConfig detectorConfig;
    private volatile String foregroundPackage = "";

    public static boolean isRunning() { return running; }
    public static boolean isRecognitionActive() { return recognitionActive; }

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
        running = true;

        worker = Executors.newSingleThreadScheduledExecutor();
        main.post(this::reevaluateRecognition);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void initializePipeline() {
        if (!recognitionActive || !initializing.compareAndSet(false, true)) return;
        try {
            DetectorConfig config = settings.loadDetectorConfig();
            detectorConfig = config;
            DiagnosticsRepository.begin(DIAGNOSTICS_MODE, config.getInferenceResolution());
            if (detector == null) {
                detector = new DetectionEngine(this, config);
                detector.initialize();
            } else {
                detector.setConfig(config);
            }
            DiagnosticsRepository.ready(DIAGNOSTICS_MODE, detector.getActiveProvider(),
                    detector.getActiveModel(), config.getInferenceResolution());
            if (tracker == null) tracker = new ObjectTracker(config);
            else tracker.setConfig(config);
            tracker.clear();
            if (!recognitionActive) return;
            ScheduledFuture<?> existing = captureSchedule;
            if (existing != null) existing.cancel(false);
            captureSchedule = worker.scheduleWithFixedDelay(
                    this::requestScreenshot,
                    0,
                    Math.max(500, config.getDetectionIntervalMs()),
                    TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            if (detector != null) detector.close();
            detector = null;
            tracker = null;
            DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
            Log.e(TAG, "Could not initialize accessibility capture", error);
            main.post(this::deactivateRecognition);
        } finally {
            initializing.set(false);
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void requestScreenshot() {
        if (!running || !recognitionActive || !processing.compareAndSet(false, true)) return;
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
            if (!recognitionActive) return;
            wrapped = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (wrapped == null) return;
            frame = wrapped.copy(Bitmap.Config.ARGB_8888, false);
            List<Detection> detections = detector.detect(frame);
            List<TrackedObject> tracks = tracker.update(detections);
            DetectorConfig currentConfig = detectorConfig;
            int recordedBlocks = stats.recordTracks(tracks, currentConfig == null
                    ? null : currentConfig.getEnabledCategories());
            if (recordedBlocks > 0) {
                new PenanceManager(this).recordStrikes(recordedBlocks, System.currentTimeMillis());
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
            PopupStormManager.get().updateTrackedObjects(tracks, width, height);
            DiagnosticsRepository.Snapshot diagnostics = DiagnosticsRepository.recordFrame(
                    DIAGNOSTICS_MODE, detector.getLastInferenceMs(), tracks.size(), width, height);
            String diagnosticText = diagnosticsOverlayText(diagnostics);
            main.post(() -> {
                if (recognitionActive && overlay != null) {
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
        PopupStormManager.get().reloadSettings(this);
        main.post(this::reevaluateRecognition);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        String packageName = event.getPackageName() == null
                ? "" : event.getPackageName().toString();
        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        AppModeManager mode = new AppModeManager(this);
        if (!AppModePolicy.shouldAcceptForegroundEvent(packageName, className, getPackageName(),
                mode.inputMethodPackage())) return;
        foregroundPackage = packageName;
        if (recognitionActive && worker != null) {
            worker.execute(() -> {
                if (tracker != null) tracker.clear();
            });
        }
        main.post(() -> {
            if (overlay != null) overlay.update(Collections.emptyList(), 1, 1, null);
            reevaluateRecognition();
        });
    }

    private void reevaluateRecognition() {
        if (!running || settings == null) return;
        boolean shouldRun = new AppModeManager(this).shouldRecognize(foregroundPackage);
        if (shouldRun && !recognitionActive) activateRecognition();
        else if (!shouldRun && recognitionActive) deactivateRecognition();
    }

    private void activateRecognition() {
        if (recognitionActive || !running || worker == null) return;
        recognitionActive = true;
        Log.i(TAG, "Recognition activated for foreground package " + foregroundPackage);
        firstFrameReported.set(false);
        overlay = new OverlayController(
                this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        overlay.setAppearance(settings.loadAppearance());
        overlay.setDiagnostics(diagnosticsOverlayText());
        overlay.show();
        PopupStormManager.get().start(this);
        stats.startSession();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            worker.execute(this::initializePipeline);
        }
    }

    private void deactivateRecognition() {
        if (!recognitionActive && overlay == null) return;
        recognitionActive = false;
        Log.i(TAG, "Recognition suspended for foreground package " + foregroundPackage);
        ScheduledFuture<?> schedule = captureSchedule;
        captureSchedule = null;
        if (schedule != null) schedule.cancel(false);
        DiagnosticsRepository.stop(DIAGNOSTICS_MODE);
        if (stats != null) stats.endSession();
        if (overlay != null) overlay.close();
        overlay = null;
        PopupStormManager.get().stop();
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
        deactivateRecognition();
        if (settings != null) {
            settings.preferences().unregisterOnSharedPreferenceChangeListener(listener);
        }
        if (worker != null) worker.shutdownNow();
        if (detector != null) detector.close();
        recognitionActive = false;
        super.onDestroy();
    }
}
