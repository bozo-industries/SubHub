package com.subhub.app.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.subhub.app.appmode.AppModeManager;
import com.subhub.app.appmode.AppModePolicy;
import com.subhub.app.appmode.AppTimerManager;
import com.subhub.app.detection.Detection;
import com.subhub.app.detection.BBox;
import com.subhub.app.detection.DetectionEngine;
import com.subhub.app.detection.DetectorConfig;
import com.subhub.app.detection.ObjectTracker;
import com.subhub.app.detection.TrackedObject;
import com.subhub.app.detection.text.AccessibilityTextSmutDetector;
import com.subhub.app.detection.text.DetectionFusion;
import com.subhub.app.detection.text.TextDetectionCoordinateMapper;
import com.subhub.app.detection.text.TextSmutConfig;
import com.subhub.app.diagnostics.DiagnosticsRepository;
import com.subhub.app.overlay.OverlayController;
import com.subhub.app.popup.PopupStormManager;
import com.subhub.app.penance.CensorTapTracker;
import com.subhub.app.penance.DwellInfractionTracker;
import com.subhub.app.penance.PenanceChargeNotifier;
import com.subhub.app.penance.PenanceInfraction;
import com.subhub.app.penance.PenanceManager;
import com.subhub.app.settings.CensorAppearance;
import com.subhub.app.settings.SettingsRepository;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.security.HardcoreModeManager;
import com.subhub.app.security.HardcoreSettingsGuard;
import com.subhub.app.stats.StatsRepository;
import com.subhub.app.stats.AchievementManager;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** User-enabled screenshot capture mode backed by Android's accessibility consent screen. */
public final class ScreenshotAccessibilityService extends AccessibilityService {
    private static final String TAG = "ScreenshotA11y";
    private static final String DIAGNOSTICS_MODE = "Accessibility screenshot";
    private static volatile boolean running;
    private static volatile boolean recognitionActive;
    private static final long MIN_TEXT_REFRESH_MS = 300L;
    private static final long SETTLED_SCROLL_REFRESH_MS = 140L;
    private static final long MOTION_SETTLE_MS = 130L;

    private final AtomicBoolean processing = new AtomicBoolean();
    private final AtomicBoolean inferenceDraining = new AtomicBoolean();
    private final AtomicBoolean settledInferenceNeeded = new AtomicBoolean();
    private final AtomicReference<InferenceFrame> pendingInference = new AtomicReference<>();
    private final AtomicLong cumulativeScrollX = new AtomicLong();
    private final AtomicLong cumulativeScrollY = new AtomicLong();
    private final AtomicLong pendingTrackerOffsetX = new AtomicLong();
    private final AtomicLong pendingTrackerOffsetY = new AtomicLong();
    private final AtomicLong motionGeneration = new AtomicLong();
    private final AtomicBoolean firstFrameReported = new AtomicBoolean();
    private final AtomicBoolean initializing = new AtomicBoolean();
    private final AtomicBoolean hardcoreGuardRefreshQueued = new AtomicBoolean();
    private final AtomicBoolean textRefreshRequested = new AtomicBoolean(true);
    private final AtomicBoolean textRefreshRunning = new AtomicBoolean();
    private final CaptureEpoch captureEpoch = new CaptureEpoch();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> reloadSettings();
    private ScheduledExecutorService worker;
    private ScheduledExecutorService inferenceWorker;
    private ScheduledExecutorService textWorker;
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
    private volatile List<Detection> cachedTextDetections = Collections.emptyList();
    private volatile long cachedTextScrollX;
    private volatile long cachedTextScrollY;
    private volatile int cachedTextWidth = 1;
    private volatile int cachedTextHeight = 1;
    private volatile int latestCaptureWidth = 1;
    private volatile int latestCaptureHeight = 1;
    private volatile long lastTextRefreshMillis;
    private volatile long lastMotionUptime;
    private volatile long lastInferenceUptime;
    private volatile boolean overlayNeedsSourceFrame;
    private volatile String foregroundPackage = "";
    private volatile String guardForegroundPackage = "";
    private AppTimerManager timers;
    private PenanceManager penance;
    private final DwellInfractionTracker dwellTracker = new DwellInfractionTracker();
    private final CensorTapTracker tapTracker = new CensorTapTracker();
    private long lastMatchedTapMillis;
    private long foregroundSinceMillis;
    private String lastBlockedPackage = "";
    private long lastBlockedAtMillis;
    private HardcoreSettingsGuard hardcoreSettingsGuard;
    private ScrollFrameMotionEstimator motionEstimator;
    private final Runnable settledHardcoreGuardRefresh = () -> {
        hardcoreGuardRefreshQueued.set(false);
        refreshHardcoreSettingsGuard();
    };
    private final Runnable settledTextRefresh = this::requestTextRefresh;
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
        motionEstimator = new ScrollFrameMotionEstimator();
        settings.preferences().registerOnSharedPreferenceChangeListener(listener);
        running = true;

        worker = Executors.newSingleThreadScheduledExecutor();
        inferenceWorker = Executors.newSingleThreadScheduledExecutor();
        textWorker = Executors.newSingleThreadScheduledExecutor();
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
                    capturePollDelayMs(config),
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
            latestCaptureWidth = wrapped.getWidth();
            latestCaptureHeight = wrapped.getHeight();
            ScrollFrameMotionEstimator.Motion motion = motionEstimator == null
                    ? ScrollFrameMotionEstimator.Motion.NONE : motionEstimator.update(wrapped);
            if (motion.moved()) {
                applyFrameMotion(motion.dx, motion.dy);
                return;
            }
            long nowUptime = SystemClock.uptimeMillis();
            if (nowUptime - lastMotionUptime < MOTION_SETTLE_MS) return;
            boolean priorityFrame = settledInferenceNeeded.get();
            if (!priorityFrame
                    && nowUptime - lastInferenceUptime < captureDelayMs(detectorConfig)) return;
            lastInferenceUptime = nowUptime;
            long inferenceMotionGeneration = motionGeneration.get();
            if (!isCurrentCapture(requestedEpoch)) return;
            DetectorConfig currentConfig = detectorConfig;
            int inferenceResolution = currentConfig == null
                    ? 320 : currentConfig.getInferenceResolution();
            InferenceBitmapPreparer.Prepared prepared = InferenceBitmapPreparer.prepare(
                    wrapped, inferenceResolution, overlayNeedsSourceFrame);
            if (prepared == null) return;
            frame = prepared.bitmap;
            settledInferenceNeeded.compareAndSet(true, false);
            enqueueInference(new InferenceFrame(frame, requestedEpoch, requestedScrollX,
                    requestedScrollY, inferenceMotionGeneration,
                    prepared.sourceWidth, prepared.sourceHeight,
                    prepared.retainedSourceFrame));
            frame = null;
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

    private void enqueueInference(InferenceFrame candidate) {
        if (!running || inferenceWorker == null || inferenceWorker.isShutdown()) {
            candidate.recycle();
            return;
        }
        InferenceFrame replaced = pendingInference.getAndSet(candidate);
        if (replaced != null) replaced.recycle();
        if (inferenceWorker != null && inferenceDraining.compareAndSet(false, true)) {
            inferenceWorker.execute(this::drainInferenceQueue);
        }
    }

    /** Runs warmed ONNX work independently so screenshot motion sampling never waits on ML. */
    private void drainInferenceQueue() {
        try {
            while (running) {
                InferenceFrame candidate = pendingInference.getAndSet(null);
                if (candidate == null) return;
                try {
                    if (!isCurrentCapture(candidate.epoch)
                            || candidate.motionGeneration != motionGeneration.get()) continue;
                    runInference(candidate);
                } catch (Exception error) {
                    DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
                    Log.w(TAG, "Could not process detector frame", error);
                } finally {
                    candidate.recycle();
                }
            }
        } finally {
            inferenceDraining.set(false);
            if (pendingInference.get() != null && inferenceWorker != null
                    && !inferenceWorker.isShutdown()
                    && inferenceDraining.compareAndSet(false, true)) {
                inferenceWorker.execute(this::drainInferenceQueue);
            }
        }
    }

    private void runInference(InferenceFrame candidate) throws Exception {
            Bitmap frame = candidate.frame;
            long requestedEpoch = candidate.epoch;
            long requestedScrollX = candidate.scrollX;
            long requestedScrollY = candidate.scrollY;
            long inferenceMotionGeneration = candidate.motionGeneration;
            int width = candidate.sourceWidth;
            int height = candidate.sourceHeight;
            List<Detection> visualDetections = detector.detect(frame, width, height);
            TextSmutConfig currentTextConfig = textSmutConfig;
            List<Detection> accessibilityDetections = Collections.emptyList();
            if (currentTextConfig != null && currentTextConfig.isEnabled()) {
                accessibilityDetections = cachedTextForFrame(
                        width, height, requestedScrollX, requestedScrollY);
                requestTextRefresh();
            }
            List<Detection> detections = DetectionFusion.merge(
                    visualDetections, accessibilityDetections);
            if (!isCurrentCapture(requestedEpoch)
                    || inferenceMotionGeneration != motionGeneration.get()) return;
            applyPendingTrackerMotion(width, height);
            List<TrackedObject> tracks = tracker.update(detections);
            DetectorConfig currentConfig = detectorConfig;
            int recordedBlocks = stats.recordTracks(tracks, currentConfig == null
                    ? null : currentConfig.getEnabledCategories());
            long now = System.currentTimeMillis();
            if (recordedBlocks > 0) {
                int charged = penance.recordInfraction(
                        PenanceInfraction.NEW_DETECTION, recordedBlocks, now);
                PenanceChargeNotifier.show(this, penance,
                        PenanceInfraction.NEW_DETECTION, charged, now);
                new AchievementManager(this).checkAchievements(stats.load());
            }
            if (firstFrameReported.compareAndSet(false, true)) {
                Log.i(TAG, "First accessibility frame processed in "
                        + detector.getLastInferenceMs() + " ms at "
                        + width + "x" + height);
            }
            Bitmap overlayFrame = candidate.retainedSourceFrame
                    ? candidate.detachFrame() : null;
            int dwellInfractions = dwellTracker.update(
                    tracks, now, penance.getDwellSeconds() * 1_000L, false);
            if (dwellInfractions > 0) {
                int charged = penance.recordInfraction(
                        PenanceInfraction.CENSORED_DWELL, dwellInfractions, now);
                PenanceChargeNotifier.show(this, penance,
                        PenanceInfraction.CENSORED_DWELL, charged, now);
            }
            tapTracker.update(tracks, width, height, now);
            PopupStormManager.get().updateTrackedObjects(tracks, width, height);
            DiagnosticsRepository.Snapshot diagnostics = DiagnosticsRepository.recordFrame(
                    DIAGNOSTICS_MODE, detector.getLastInferenceMs(), tracks.size(), width, height);
            String diagnosticText = diagnosticsOverlayText(diagnostics);
            main.post(() -> {
                if (isCurrentCapture(requestedEpoch) && overlay != null
                        && inferenceMotionGeneration == motionGeneration.get()) {
                    overlay.setDiagnostics(diagnosticText);
                    overlay.update(tracks, width, height, overlayFrame);
                }
                else if (overlayFrame != null) overlayFrame.recycle();
            });
    }

    static long captureDelayMs(DetectorConfig config) {
        int threads = config == null ? 2 : config.getInferenceThreads();
        long floor;
        if (threads <= 1) floor = 450L;
        else if (threads == 2) floor = 300L;
        else if (threads == 3) floor = 240L;
        else floor = 180L;
        return Math.max(floor, config == null ? 0L : config.getDetectionIntervalMs());
    }

    static long capturePollDelayMs(DetectorConfig config) {
        int threads = config == null ? 2 : config.getInferenceThreads();
        if (threads <= 1) return 150L;
        if (threads == 2) return 90L;
        if (threads == 3) return 60L;
        return 40L;
    }

    private void applyFrameMotion(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        lastMotionUptime = SystemClock.uptimeMillis();
        motionGeneration.incrementAndGet();
        settledInferenceNeeded.set(true);
        discardPendingInference();
        // cumulativeScroll stores content-scroll direction; dx/dy are screen movement.
        cumulativeScrollX.addAndGet(-dx);
        cumulativeScrollY.addAndGet(-dy);
        pendingTrackerOffsetX.addAndGet(dx);
        pendingTrackerOffsetY.addAndGet(dy);
        dwellTracker.onScroll();
        textRefreshRequested.set(true);
        main.removeCallbacks(settledTextRefresh);
        main.postDelayed(settledTextRefresh, SETTLED_SCROLL_REFRESH_MS);
        main.post(() -> {
            if (recognitionActive && overlay != null) overlay.offsetContent(dx, dy);
        });
    }

    private void applyPendingTrackerMotion(int width, int height) {
        int dx = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE,
                pendingTrackerOffsetX.getAndSet(0L)));
        int dy = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE,
                pendingTrackerOffsetY.getAndSet(0L)));
        if (tracker != null) tracker.offsetActiveTracks(dx, dy, width, height);
    }

    private void discardPendingInference() {
        InferenceFrame pending = pendingInference.getAndSet(null);
        if (pending != null) pending.recycle();
    }

    private Rect screenBounds() {
        WindowManager manager = getSystemService(WindowManager.class);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = manager.getMaximumWindowMetrics().getBounds();
            if (!bounds.isEmpty()) return new Rect(0, 0, bounds.width(), bounds.height());
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return new Rect(0, 0, Math.max(1, metrics.widthPixels), Math.max(1, metrics.heightPixels));
    }

    private void finishScreenshotRequest() {
        processing.set(false);
    }

    private void requestTextRefresh() {
        TextSmutConfig config = textSmutConfig;
        if (!running || !recognitionActive || textWorker == null || config == null
                || !config.isEnabled() || !textRefreshRequested.get()
                || !textRefreshRunning.compareAndSet(false, true)) return;
        long wait = MIN_TEXT_REFRESH_MS
                - (System.currentTimeMillis() - lastTextRefreshMillis);
        if (wait > 0L) {
            textRefreshRunning.set(false);
            main.removeCallbacks(settledTextRefresh);
            main.postDelayed(settledTextRefresh, wait);
            return;
        }
        textRefreshRequested.set(false);
        long epoch = captureEpoch.token();
        int captureWidth = latestCaptureWidth;
        int captureHeight = latestCaptureHeight;
        long scrollX = cumulativeScrollX.get();
        long scrollY = cumulativeScrollY.get();
        textWorker.execute(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            try {
                if (root == null || !isCurrentCapture(epoch)) return;
                Rect screen = screenBounds();
                List<Detection> screenDetections = accessibilityText.detect(
                        root, config, screen.width(), screen.height());
                List<Detection> mapped = TextDetectionCoordinateMapper.screenToCapture(
                        screenDetections, screen.width(), screen.height(),
                        captureWidth, captureHeight);
                if (!isCurrentCapture(epoch)) return;
                cachedTextDetections = mapped;
                cachedTextWidth = captureWidth;
                cachedTextHeight = captureHeight;
                cachedTextScrollX = scrollX;
                cachedTextScrollY = scrollY;
                lastTextRefreshMillis = System.currentTimeMillis();
            } finally {
                if (root != null) root.recycle();
                textRefreshRunning.set(false);
                if (textRefreshRequested.get()) main.post(settledTextRefresh);
            }
        });
    }

    private List<Detection> cachedTextForFrame(
            int width, int height, long requestedScrollX, long requestedScrollY) {
        List<Detection> source = cachedTextDetections;
        if (source.isEmpty()) return source;
        float scaleX = width / (float) Math.max(1, cachedTextWidth);
        float scaleY = height / (float) Math.max(1, cachedTextHeight);
        Rect screen = screenBounds();
        float scrollScaleX = width / (float) Math.max(1, screen.width());
        float scrollScaleY = height / (float) Math.max(1, screen.height());
        int offsetX = Math.round(-(requestedScrollX - cachedTextScrollX) * scrollScaleX);
        int offsetY = Math.round(-(requestedScrollY - cachedTextScrollY) * scrollScaleY);
        List<Detection> shifted = new ArrayList<>(source.size());
        for (Detection detection : source) {
            BBox box = detection.getBox();
            int left = Math.round(box.getX() * scaleX) + offsetX;
            int top = Math.round(box.getY() * scaleY) + offsetY;
            int right = Math.round(box.getRight() * scaleX) + offsetX;
            int bottom = Math.round(box.getBottom() * scaleY) + offsetY;
            if (right <= 0 || bottom <= 0 || left >= width || top >= height) continue;
            left = Math.max(0, left);
            top = Math.max(0, top);
            right = Math.min(width, right);
            bottom = Math.min(height, bottom);
            shifted.add(new Detection(detection.getClassName(), detection.getCategory(),
                    detection.getConfidence(),
                    new BBox(left, top, Math.max(1, right - left), Math.max(1, bottom - top)),
                    detection.isNsfw(), detection.isExposed()));
        }
        return shifted;
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
        textRefreshRequested.set(true);
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
        if (!packageName.isEmpty()) guardForegroundPackage = packageName;
        boolean settingsEvent = HardcoreSettingsGuard.isSettingsPackage(packageName);
        boolean windowTransition = event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        if (settingsEvent && (windowTransition
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED)) {
            queueHardcoreSettingsGuardRefresh(windowTransition ? 0L : 32L);
        } else if (windowTransition && hardcoreSettingsGuard != null) {
            // Adding the accessibility badge itself emits a window event from our package. A
            // live-root refresh distinguishes that feedback from actually leaving Settings and
            // prevents the guard from clearing/re-adding (the visible flash users reported).
            queueHardcoreSettingsGuardRefresh(0L);
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (recognitionActive && packageName.equals(foregroundPackage)) {
                dwellTracker.onScroll();
                // Some apps only emit this event after touch-up. Use it to reject an in-flight
                // detector frame; thumbnail motion estimation supplies the actual displacement.
                lastMotionUptime = SystemClock.uptimeMillis();
                motionGeneration.incrementAndGet();
                settledInferenceNeeded.set(true);
                discardPendingInference();
                textRefreshRequested.set(true);
                main.removeCallbacks(settledTextRefresh);
                main.postDelayed(settledTextRefresh, SETTLED_SCROLL_REFRESH_MS);
            }
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && recognitionActive && packageName.equals(foregroundPackage)) {
            textRefreshRequested.set(true);
            main.removeCallbacks(settledTextRefresh);
            main.postDelayed(settledTextRefresh, 100L);
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
            int charged = penance.recordInfraction(
                    PenanceInfraction.WATCHED_APP_OPEN, 1, now);
            PenanceChargeNotifier.show(this, penance,
                    PenanceInfraction.WATCHED_APP_OPEN, charged, now);
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
            int charged = penance.recordInfraction(PenanceInfraction.CENSORED_TAP, 1, now);
            PenanceChargeNotifier.show(this, penance,
                    PenanceInfraction.CENSORED_TAP, charged, now);
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
                ? com.subhub.app.R.string.app_timer_blocked_app
                : com.subhub.app.R.string.app_timer_blocked_total;
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
        // Android Settings is handled by the lightweight Hardcore guard. Running the detector
        // there competes for the same accessibility overlay channel and wastes capture/ML work.
        boolean shouldRun = !HardcoreSettingsGuard.isSettingsPackage(foregroundPackage)
                && new AppModeManager(this).shouldRecognize(foregroundPackage);
        if (shouldRun && !recognitionActive) activateRecognition();
        else if (!shouldRun && recognitionActive) deactivateRecognition();
    }

    private void refreshHardcoreSettingsGuard() {
        if (hardcoreSettingsGuard == null) return;
        boolean hardcore = new HardcoreModeManager(this).isEnabled();
        boolean domMode = ControllerPinManager.isDomModeActive();
        if (!hardcore || domMode) {
            hardcoreSettingsGuard.clear();
            return;
        }
        String expectedPackage = guardForegroundPackage;
        // Window-state events can be coalesced while Settings restores a Compose page. In
        // Hardcore/Sub mode, confirm against the live root once per guard tick instead of
        // trusting a stale package cache and silently leaving destructive controls uncovered.
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            String activePackage = root != null && root.getPackageName() != null
                    ? root.getPackageName().toString() : expectedPackage;
            hardcoreSettingsGuard.refresh(activePackage, root);
        } finally {
            if (root != null) root.recycle();
        }
    }

    private void queueHardcoreSettingsGuardRefresh(long delayMillis) {
        if (!hardcoreGuardRefreshQueued.compareAndSet(false, true)) return;
        main.postDelayed(settledHardcoreGuardRefresh, Math.max(0L, delayMillis));
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
        discardPendingInference();
        DiagnosticsRepository.stop(DIAGNOSTICS_MODE);
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
        cachedTextDetections = Collections.emptyList();
        cachedTextScrollX = 0L;
        cachedTextScrollY = 0L;
        pendingTrackerOffsetX.set(0L);
        pendingTrackerOffsetY.set(0L);
        settledInferenceNeeded.set(false);
        discardPendingInference();
        motionGeneration.incrementAndGet();
        lastMotionUptime = 0L;
        lastInferenceUptime = 0L;
        if (motionEstimator != null) motionEstimator.reset();
        textRefreshRequested.set(true);
        main.removeCallbacks(settledTextRefresh);
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

    private static final class InferenceFrame {
        private Bitmap frame;
        private final long epoch;
        private final long scrollX;
        private final long scrollY;
        private final long motionGeneration;
        private final int sourceWidth;
        private final int sourceHeight;
        private final boolean retainedSourceFrame;

        private InferenceFrame(
                Bitmap frame,
                long epoch,
                long scrollX,
                long scrollY,
                long motionGeneration,
                int sourceWidth,
                int sourceHeight,
                boolean retainedSourceFrame) {
            this.frame = frame;
            this.epoch = epoch;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.motionGeneration = motionGeneration;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.retainedSourceFrame = retainedSourceFrame;
        }

        private Bitmap detachFrame() {
            Bitmap detached = frame;
            frame = null;
            return detached;
        }

        private void recycle() {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            frame = null;
        }
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
        discardPendingInference();
        if (inferenceWorker != null) inferenceWorker.shutdownNow();
        if (textWorker != null) textWorker.shutdownNow();
        if (detector != null) detector.close();
        if (motionEstimator != null) motionEstimator.close();
        motionEstimator = null;
        if (hardcoreSettingsGuard != null) hardcoreSettingsGuard.clear();
        hardcoreSettingsGuard = null;
        main.removeCallbacks(settledHardcoreGuardRefresh);
        hardcoreGuardRefreshQueued.set(false);
        main.removeCallbacks(settledTextRefresh);
        dwellTracker.clear();
        tapTracker.clear();
        recognitionActive = false;
        super.onDestroy();
    }
}
