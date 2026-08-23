package com.betasafe.app.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.betasafe.app.appmode.AppModeManager;
import com.betasafe.app.appmode.AppModePolicy;
import com.betasafe.app.appmode.AppTimerManager;
import com.betasafe.app.detection.Detection;
import com.betasafe.app.detection.DetectionEngine;
import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.detection.ObjectTracker;
import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.detection.text.AccessibilityTextSmutDetector;
import com.betasafe.app.detection.text.DetectionFusion;
import com.betasafe.app.detection.text.TextSmutConfig;
import com.betasafe.app.diagnostics.DiagnosticsRepository;
import com.betasafe.app.overlay.OverlayController;
import com.betasafe.app.popup.PopupStormManager;
import com.betasafe.app.penance.CensorTapTracker;
import com.betasafe.app.penance.DwellInfractionTracker;
import com.betasafe.app.penance.PenanceInfraction;
import com.betasafe.app.penance.PenanceManager;
import com.betasafe.app.settings.CensorAppearance;
import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.settings.FeatureModuleManager;
import com.betasafe.app.security.HardcoreSettingsGuard;
import com.betasafe.app.stats.StatsRepository;
import com.betasafe.app.stats.AchievementManager;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** User-enabled screenshot capture mode backed by Android's accessibility consent screen. */
public final class ScreenshotAccessibilityService extends AccessibilityService {
    private static final String TAG = "ScreenshotA11y";
    private static final String DIAGNOSTICS_MODE = "Accessibility screenshot";
    private static volatile boolean running;
    private static volatile boolean recognitionActive;

    private final AtomicBoolean processing = new AtomicBoolean();
    private final AtomicBoolean scrollRefreshPending = new AtomicBoolean();
    private final AtomicLong cumulativeScrollX = new AtomicLong();
    private final AtomicLong cumulativeScrollY = new AtomicLong();
    private final AtomicBoolean firstFrameReported = new AtomicBoolean();
    private final AtomicBoolean initializing = new AtomicBoolean();
    private final CaptureEpoch captureEpoch = new CaptureEpoch();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> reloadSettings();
    private ScheduledExecutorService worker;
    private volatile ScheduledFuture<?> captureSchedule;
    private SettingsRepository settings;
    private StatsRepository stats;
    private DetectionEngine detector;
    private ObjectTracker tracker;
    private final AccessibilityTextSmutDetector accessibilityText =
            new AccessibilityTextSmutDetector();
    private OverlayController overlay;
    private volatile DetectorConfig detectorConfig;
    private volatile TextSmutConfig textSmutConfig;
    private volatile boolean overlayNeedsSourceFrame;
    private volatile String foregroundPackage = "";
    private AppTimerManager timers;
    private PenanceManager penance;
    private final DwellInfractionTracker dwellTracker = new DwellInfractionTracker();
    private final CensorTapTracker tapTracker = new CensorTapTracker();
    private long lastMatchedTapMillis;
    private long foregroundSinceMillis;
    private String lastBlockedPackage = "";
    private long lastBlockedAtMillis;
    private HardcoreSettingsGuard hardcoreSettingsGuard;
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            accountForegroundUsage(now);
            enforceForegroundLimit(now);
            reevaluateRecognition();
            refreshHardcoreSettingsGuard();
            main.postDelayed(this, 1_000L);
        }
    };

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
        timers = new AppTimerManager(this);
        penance = new PenanceManager(this);
        hardcoreSettingsGuard = new HardcoreSettingsGuard(this);
        settings.preferences().registerOnSharedPreferenceChangeListener(listener);
        running = true;

        worker = Executors.newSingleThreadScheduledExecutor();
        worker.execute(this::initializePipeline);
        main.post(this::reevaluateRecognition);
        main.post(timerTick);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void initializePipeline() {
        if (!running || !initializing.compareAndSet(false, true)) return;
        try {
            DetectorConfig config = settings.loadDetectorConfig();
            detectorConfig = config;
            textSmutConfig = settings.loadTextSmutConfig();
            if (detector == null) {
                detector = new DetectionEngine(this, config);
                detector.initialize();
            } else {
                detector.setConfig(config);
            }
            if (tracker == null) tracker = new ObjectTracker(config);
            else tracker.setConfig(config);
            tracker.clear();
            if (!recognitionActive) {
                Log.i(TAG, "Detector prewarmed; capture remains asleep");
                return;
            }
            DiagnosticsRepository.begin(DIAGNOSTICS_MODE, config.getInferenceResolution());
            DiagnosticsRepository.ready(DIAGNOSTICS_MODE, detector.getActiveProvider(),
                    detector.getActiveModel(), config.getInferenceResolution());
            ScheduledFuture<?> existing = captureSchedule;
            if (existing != null) existing.cancel(false);
            captureSchedule = worker.scheduleWithFixedDelay(
                    this::requestScreenshot,
                    0,
                    Math.max(350, config.getDetectionIntervalMs()),
                    TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            if (detector != null) detector.close();
            detector = null;
            tracker = null;
            if (recognitionActive) DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
            Log.e(TAG, "Could not initialize accessibility capture", error);
            main.post(this::deactivateRecognition);
        } finally {
            initializing.set(false);
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void requestScreenshot() {
        if (!running || !recognitionActive || !processing.compareAndSet(false, true)) return;
        scrollRefreshPending.set(false);
        long requestedEpoch = captureEpoch.token();
        long requestedScrollX = cumulativeScrollX.get();
        long requestedScrollY = cumulativeScrollY.get();
        TakeScreenshotCallback callback = new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult result) {
                process(result, requestedEpoch, requestedScrollX, requestedScrollY);
            }

            @Override
            public void onFailure(int errorCode) {
                DiagnosticsRepository.failCode(
                        DIAGNOSTICS_MODE, "Screenshot error", errorCode);
                Log.w(TAG, "Accessibility screenshot failed with code " + errorCode);
                finishScreenshotRequest();
            }
        };
        // Android 14+ can capture the foreground app window directly. Unlike a display capture,
        // this excludes SubHub's own accessibility overlay, so a censor stays continuously visible
        // without becoming part of the next detector input.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                int windowId = root.getWindowId();
                root.recycle();
                takeScreenshotOfWindow(windowId, worker, callback);
                return;
            }
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, callback);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void process(
            ScreenshotResult result,
            long requestedEpoch,
            long requestedScrollX,
            long requestedScrollY) {
        Bitmap wrapped = null;
        Bitmap frame = null;
        HardwareBuffer buffer = result.getHardwareBuffer();
        try {
            if (!isCurrentCapture(requestedEpoch)) return;
            wrapped = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (wrapped == null) return;
            frame = wrapped.copy(Bitmap.Config.ARGB_8888, false);
            List<Detection> visualDetections = detector.detect(frame);
            TextSmutConfig currentTextConfig = textSmutConfig;
            List<Detection> accessibilityDetections = Collections.emptyList();
            if (currentTextConfig != null && currentTextConfig.isEnabled()) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    try {
                        accessibilityDetections = accessibilityText.detect(
                                root, currentTextConfig, frame.getWidth(), frame.getHeight());
                    } finally {
                        root.recycle();
                    }
                }
            }
            List<Detection> detections = DetectionFusion.merge(
                    visualDetections, accessibilityDetections);
            if (!isCurrentCapture(requestedEpoch)) return;
            List<TrackedObject> tracks = tracker.update(detections);
            DetectorConfig currentConfig = detectorConfig;
            int recordedBlocks = stats.recordTracks(tracks, currentConfig == null
                    ? null : currentConfig.getEnabledCategories());
            long now = System.currentTimeMillis();
            if (recordedBlocks > 0) {
                penance.recordInfraction(PenanceInfraction.NEW_DETECTION, recordedBlocks, now);
                new AchievementManager(this).checkAchievements(stats.load());
            }
            if (firstFrameReported.compareAndSet(false, true)) {
                Log.i(TAG, "First accessibility frame processed in "
                        + detector.getLastInferenceMs() + " ms at "
                        + frame.getWidth() + "x" + frame.getHeight());
            }
            int width = frame.getWidth();
            int height = frame.getHeight();
            Bitmap overlayFrame = overlayNeedsSourceFrame ? frame : null;
            if (overlayFrame != null) frame = null;
            int dwellInfractions = dwellTracker.update(
                    tracks, now, penance.getDwellSeconds() * 1_000L);
            if (dwellInfractions > 0) {
                penance.recordInfraction(
                        PenanceInfraction.CENSORED_DWELL, dwellInfractions, now);
            }
            tapTracker.update(tracks, width, height, now);
            PopupStormManager.get().updateTrackedObjects(tracks, width, height);
            DiagnosticsRepository.Snapshot diagnostics = DiagnosticsRepository.recordFrame(
                    DIAGNOSTICS_MODE, detector.getLastInferenceMs(), tracks.size(), width, height);
            String diagnosticText = diagnosticsOverlayText(diagnostics);
            main.post(() -> {
                if (isCurrentCapture(requestedEpoch) && overlay != null) {
                    int motionX = clampScrollMotion(-(cumulativeScrollX.get() - requestedScrollX),
                            width);
                    int motionY = clampScrollMotion(-(cumulativeScrollY.get() - requestedScrollY),
                            height);
                    overlay.setDiagnostics(diagnosticText);
                    overlay.update(tracks, width, height, overlayFrame, motionX, motionY);
                }
                else if (overlayFrame != null) overlayFrame.recycle();
            });
        } catch (Exception error) {
            DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
            Log.w(TAG, "Could not process accessibility screenshot", error);
        } finally {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            if (wrapped != null && !wrapped.isRecycled()) wrapped.recycle();
            buffer.close();
            finishScreenshotRequest();
        }
    }

    private static int clampScrollMotion(long value, int frameExtent) {
        long limit = Math.max(1, frameExtent) * 2L;
        return (int) Math.max(-limit, Math.min(limit, value));
    }

    private void finishScreenshotRequest() {
        processing.set(false);
        if (scrollRefreshPending.get() && running && recognitionActive && worker != null) {
            worker.execute(this::requestScreenshot);
        }
    }

    private boolean isCurrentCapture(long requestedEpoch) {
        return captureEpoch.accepts(requestedEpoch, running, recognitionActive);
    }

    private void reloadSettings() {
        if (settings == null) return;
        main.post(() -> {
            if (overlay != null) {
                CensorAppearance appearance = settings.loadAppearance();
                overlayNeedsSourceFrame = appearance.requiresSourceFrame();
                overlay.setAppearance(appearance);
                overlay.setDiagnostics(diagnosticsOverlayText());
            }
        });
        DetectorConfig config = settings.loadDetectorConfig();
        detectorConfig = config;
        textSmutConfig = settings.loadTextSmutConfig();
        if (detector != null) detector.setConfig(config);
        if (tracker != null) tracker.setConfig(config);
        PopupStormManager.get().reloadSettings(this);
        main.post(this::reevaluateRecognition);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        String packageName = event.getPackageName() == null
                ? "" : event.getPackageName().toString();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            main.post(this::refreshHardcoreSettingsGuard);
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (recognitionActive && packageName.equals(foregroundPackage)) {
                dwellTracker.onScroll();
                int deltaX = event.getScrollDeltaX();
                int deltaY = event.getScrollDeltaY();
                if (deltaX != 0 || deltaY != 0) {
                    cumulativeScrollX.addAndGet(deltaX);
                    cumulativeScrollY.addAndGet(deltaY);
                    if (overlay != null) overlay.offsetContent(-deltaX, -deltaY);
                    scrollRefreshPending.set(true);
                    if (worker != null && !processing.get()) worker.execute(this::requestScreenshot);
                }
            }
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            recordCensoredTap(event, packageName);
            return;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        AppModeManager mode = new AppModeManager(this);
        if (!AppModePolicy.shouldAcceptForegroundEvent(packageName, className, getPackageName(),
                mode.inputMethodPackage())) return;
        if (packageName.equals(foregroundPackage)) return;
        long now = System.currentTimeMillis();
        accountForegroundUsage(now);
        captureEpoch.invalidate();
        foregroundPackage = packageName;
        foregroundSinceMillis = now;
        if (mode.getSelectedPackages().contains(packageName)) {
            penance.recordInfraction(PenanceInfraction.WATCHED_APP_OPEN, 1, now);
        }
        dwellTracker.clear();
        tapTracker.clear();
        resetScrollCompensation();
        if (recognitionActive && worker != null) {
            worker.execute(() -> {
                if (tracker != null) tracker.clear();
            });
        }
        if (overlay != null) overlay.clear();
        PopupStormManager.get().updateDetections(Collections.emptyList());
        if (enforceForegroundLimit(System.currentTimeMillis())) return;
        reevaluateRecognition();
    }

    private void recordCensoredTap(AccessibilityEvent event, String packageName) {
        if (!recognitionActive || !packageName.equals(foregroundPackage) || penance == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMatchedTapMillis < 500L) return;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;
        Rect bounds = new Rect();
        try {
            source.getBoundsInScreen(bounds);
        } finally {
            source.recycle();
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (tapTracker.matchesClick(bounds.left, bounds.top, bounds.right, bounds.bottom,
                metrics.widthPixels, metrics.heightPixels, now)) {
            lastMatchedTapMillis = now;
            penance.recordInfraction(PenanceInfraction.CENSORED_TAP, 1, now);
        }
    }

    private void accountForegroundUsage(long nowMillis) {
        long started = foregroundSinceMillis;
        foregroundSinceMillis = nowMillis;
        if (timers == null || started <= 0L || nowMillis <= started
                || !new FeatureModuleManager(this).isLimitsEnabled()) return;
        AppModeManager mode = new AppModeManager(this);
        timers.recordUsage(foregroundPackage, nowMillis - started,
                mode.getTimerPackages(), nowMillis);
    }

    /** Returns true when the current foreground app was dismissed for a spent budget. */
    private boolean enforceForegroundLimit(long nowMillis) {
        if (timers == null || foregroundPackage.isEmpty()
                || !new FeatureModuleManager(this).isLimitsEnabled()) return false;
        AppModeManager mode = new AppModeManager(this);
        Set<String> selected = mode.getTimerPackages();
        AppTimerManager.LimitStatus status = timers.limitStatus(
                foregroundPackage, selected, nowMillis);
        if (status == AppTimerManager.LimitStatus.NONE) return false;

        String blockedPackage = foregroundPackage;
        deactivateRecognition();
        boolean repeated = blockedPackage.equals(lastBlockedPackage)
                && nowMillis - lastBlockedAtMillis < 3_000L;
        lastBlockedPackage = blockedPackage;
        lastBlockedAtMillis = nowMillis;
        int message = status == AppTimerManager.LimitStatus.PER_APP
                ? com.betasafe.app.R.string.app_timer_blocked_app
                : com.betasafe.app.R.string.app_timer_blocked_total;
        if (!repeated) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.i(TAG, "Daily app limit enforced for " + blockedPackage + " (" + status + ")");
        if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            foregroundPackage = "";
            foregroundSinceMillis = 0L;
        }
        return true;
    }

    private void reevaluateRecognition() {
        if (!running || settings == null) return;
        boolean shouldRun = new AppModeManager(this).shouldRecognize(foregroundPackage);
        if (shouldRun && !recognitionActive) activateRecognition();
        else if (!shouldRun && recognitionActive) deactivateRecognition();
    }

    private void refreshHardcoreSettingsGuard() {
        if (hardcoreSettingsGuard == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            hardcoreSettingsGuard.refresh(foregroundPackage, root);
        } finally {
            if (root != null) root.recycle();
        }
    }

    private void activateRecognition() {
        if (recognitionActive || !running || worker == null) return;
        captureEpoch.invalidate();
        recognitionActive = true;
        Log.i(TAG, "Recognition activated for foreground package " + foregroundPackage);
        firstFrameReported.set(false);
        resetScrollCompensation();
        overlay = new OverlayController(
                this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        CensorAppearance appearance = settings.loadAppearance();
        overlayNeedsSourceFrame = appearance.requiresSourceFrame();
        overlay.setAppearance(appearance);
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
        captureEpoch.invalidate();
        Log.i(TAG, "Recognition suspended for foreground package " + foregroundPackage);
        ScheduledFuture<?> schedule = captureSchedule;
        captureSchedule = null;
        if (schedule != null) schedule.cancel(false);
        DiagnosticsRepository.stop(DIAGNOSTICS_MODE);
        if (stats != null) stats.endSession();
        if (overlay != null) overlay.close();
        overlay = null;
        PopupStormManager.get().stop();
        dwellTracker.clear();
        tapTracker.clear();
        resetScrollCompensation();
    }

    private void resetScrollCompensation() {
        cumulativeScrollX.set(0L);
        cumulativeScrollY.set(0L);
        scrollRefreshPending.set(false);
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
        accountForegroundUsage(System.currentTimeMillis());
        running = false;
        main.removeCallbacks(timerTick);
        deactivateRecognition();
        if (settings != null) {
            settings.preferences().unregisterOnSharedPreferenceChangeListener(listener);
        }
        if (worker != null) worker.shutdownNow();
        if (detector != null) detector.close();
        if (hardcoreSettingsGuard != null) hardcoreSettingsGuard.clear();
        hardcoreSettingsGuard = null;
        dwellTracker.clear();
        tapTracker.clear();
        recognitionActive = false;
        super.onDestroy();
    }
}
