package com.subhub.app.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.subhub.app.BuildConfig;
import com.subhub.app.appmode.AppModeManager;
import com.subhub.app.appmode.AppModePolicy;
import com.subhub.app.appmode.AppTimerManager;
import com.subhub.app.appmode.AppTimerRuntimePolicy;
import com.subhub.app.detection.Detection;
import com.subhub.app.detection.BBox;
import com.subhub.app.detection.DetectionEngine;
import com.subhub.app.detection.DetectorConfig;
import com.subhub.app.detection.FastVisualGate;
import com.subhub.app.detection.ObjectTracker;
import com.subhub.app.detection.TrackedObject;
import com.subhub.app.detection.VisualDetectionStabilizer;
import com.subhub.app.detection.VisualTrackArbitrator;
import com.subhub.app.detection.VisualIdentityReconciler;
import com.subhub.app.detection.text.AccessibilityTextSmutDetector;
import com.subhub.app.detection.text.DetectionFusion;
import com.subhub.app.detection.text.OcrTextSmutDetector;
import com.subhub.app.detection.text.SmutTextClassifier;
import com.subhub.app.detection.text.TextDetectionCoordinateMapper;
import com.subhub.app.detection.text.TextSmutConfig;
import com.subhub.app.detection.text.TextDetectionStabilizer;
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
import com.subhub.app.subliminal.SubliminalOverlayController;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** User-enabled screenshot capture mode backed by Android's accessibility consent screen. */
public final class ScreenshotAccessibilityService extends AccessibilityService {
    private static final String TAG = "ScreenshotA11y";
    private static final String DIAGNOSTICS_MODE = "Accessibility screenshot";
    private static volatile boolean running;
    private static volatile boolean recognitionActive;
    private static final long MIN_TEXT_REFRESH_MS = 120L;
    private static final long TEXT_CANDIDATE_CONFIRM_MS = 48L;
    private static final long CONTENT_TEXT_REFRESH_MS = 80L;
    private static final long CONTENT_TEXT_MAX_DEBOUNCE_MS = 500L;
    private static final long ACCESSIBILITY_TEXT_STALE_TTL_MS = 1_000L;
    // Short pauses inside a fling regularly exceed the visual 130 ms settle gate. Text traversal
    // is much more expensive than moving the existing overlay, so wait through those micro-pauses
    // instead of launching work that the next Accessibility scroll event immediately invalidates.
    private static final long SETTLED_SCROLL_REFRESH_MS = 240L;
    private static final long POST_SCROLL_TEXT_RECONCILE_MS = 900L;
    private static final long MOTION_SETTLE_MS = 130L;
    // AOSP enforces a strict >333 ms per-window request interval. Schedule at the first safe
    // millisecond instead of leaving an extra 16 ms idle on every capture.
    private static final long ACCESSIBILITY_SCREENSHOT_INTERVAL_MS = 334L;
    private static final long OCR_INTERVAL_MS = 3_000L;
    private static final long OCR_CONFIRM_INTERVAL_MS = 650L;
    private static final long OCR_MOTION_SETTLE_MS = 600L;
    private static final long OCR_VISUAL_IDLE_RETRY_MS = 24L;
    private static final long OCR_VISUAL_IDLE_TIMEOUT_MS = 600L;
    private static final long OCR_RESULT_TTL_MS = 5_000L;
    private static final int OCR_MAX_DIMENSION = 1_024;
    private static final int FAST_INFERENCE_RESOLUTION = 320;
    private static final long QUALITY_REFRESH_INTERVAL_MS = 1_000L;
    private static final long QUALITY_SLOW_REFRESH_INTERVAL_MS = 2_500L;
    private static final long QUALITY_SLOW_RUNTIME_MS = 180L;
    // The screenshot API already enforces a 334 ms cadence, so an additional 850 ms quality gate
    // delayed confirmed coverage into a visibly separate two-second render. Start refinement on
    // the first platform-safe settled capture; generation fences still discard resumed motion.
    private static final long QUALITY_MOTION_SETTLE_MS = MOTION_SETTLE_MS;
    private static final long QUALITY_RESULT_TTL_MS = 2_500L;
    private static final long STREAMING_QUALITY_RESULT_TTL_MS = 1_000L;
    private static final long QUALITY_CONFIRMATION_INTERVAL_MS = 250L;

    private final AtomicBoolean processing = new AtomicBoolean();
    private final AtomicBoolean inferenceDraining = new AtomicBoolean();
    private final AtomicBoolean settledInferenceNeeded = new AtomicBoolean();
    private final AtomicBoolean qualityConfirmationRequested = new AtomicBoolean();
    private final AtomicBoolean qualityConfirmationBurstUsed = new AtomicBoolean();
    private final AtomicLong qualityBatchCommittedGeneration =
            new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong qualityBatchClosedGeneration =
            new AtomicLong(Long.MIN_VALUE);
    private final AtomicReference<InferenceFrame> pendingInference = new AtomicReference<>();
    private final AtomicReference<QualityInferenceFrame> pendingQualityInference =
            new AtomicReference<>();
    private final AtomicBoolean qualityInferenceDraining = new AtomicBoolean();
    private final AtomicLong droppedInferenceFrames = new AtomicLong();
    private final AtomicLong droppedQualityInferenceFrames = new AtomicLong();
    private final AtomicLong staleQualityInferenceFrames = new AtomicLong();
    private final AtomicLong cumulativeScrollX = new AtomicLong();
    private final AtomicLong cumulativeScrollY = new AtomicLong();
    private final AtomicLong pendingTrackerOffsetX = new AtomicLong();
    private final AtomicLong pendingTrackerOffsetY = new AtomicLong();
    private final AtomicLong motionGeneration = new AtomicLong();
    private final AtomicLong textSceneGeneration = new AtomicLong();
    private final AtomicLong textContentGeneration = new AtomicLong();
    private final Object scrollStateLock = new Object();
    private final AtomicBoolean firstFrameReported = new AtomicBoolean();
    private final AtomicBoolean initializing = new AtomicBoolean();
    private final AtomicBoolean hardcoreGuardRefreshQueued = new AtomicBoolean();
    private final AtomicBoolean textRefreshRequested = new AtomicBoolean(true);
    private final AtomicBoolean textRefreshRunning = new AtomicBoolean();
    private final AtomicBoolean contentTextRefreshScheduled = new AtomicBoolean();
    private final AtomicInteger textContentEvents = new AtomicInteger();
    private final AtomicInteger textContentChangeTypes = new AtomicInteger();
    private final AtomicInteger textContentStaleRetries = new AtomicInteger();
    private final AtomicInteger accessibilityCandidateScans = new AtomicInteger();
    private final AtomicReference<AccessibilityTextSmutDetector.ScanResult>
            pendingTextConfirmation = new AtomicReference<>();
    private final AtomicBoolean ocrRunning = new AtomicBoolean();
    private final AtomicBoolean ocrConfirmationRequested = new AtomicBoolean();
    private final AtomicReference<Bitmap> activeOcrBitmap = new AtomicReference<>();
    private final CaptureEpoch captureEpoch = new CaptureEpoch();
    private final AccessibilityScrollMotionResolver scrollMotionResolver =
            new AccessibilityScrollMotionResolver();
    private final ScrollDeltaStabilizer scrollDeltaStabilizer = new ScrollDeltaStabilizer();
    private final CaptureScrollTimeline captureScrollTimeline = new CaptureScrollTimeline();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> reloadSettings();
    private ScheduledExecutorService worker;
    private ScheduledExecutorService inferenceWorker;
    private ScheduledExecutorService qualityInferenceWorker;
    private ScheduledExecutorService textWorker;
    private ScheduledExecutorService ocrWorker;
    private volatile ScheduledFuture<?> captureSchedule;
    private volatile ScheduledFuture<?> priorityCaptureSchedule;
    private SettingsRepository settings;
    private StatsRepository stats;
    private DetectionEngine detector;
    private DetectionEngine fastDetector;
    private ObjectTracker tracker;
    private SmutTextClassifier smutTextClassifier;
    private AccessibilityTextSmutDetector accessibilityText;
    private OcrTextSmutDetector screenshotText;
    private final TextDetectionStabilizer accessibilityTextStabilizer =
            new TextDetectionStabilizer();
    private final TextDetectionStabilizer ocrTextStabilizer = new TextDetectionStabilizer();
    private final VisualDetectionStabilizer qualityVisualStabilizer =
            new VisualDetectionStabilizer();
    private OverlayController overlay;
    private volatile DetectorConfig detectorConfig;
    private volatile TextSmutConfig textSmutConfig;
    private volatile TextDetectionSnapshot cachedAccessibilityText = TextDetectionSnapshot.EMPTY;
    private volatile TextDetectionSnapshot cachedOcrText = TextDetectionSnapshot.EMPTY;
    private volatile VisualDetectionSnapshot cachedQualityVisual =
            VisualDetectionSnapshot.EMPTY;
    private volatile boolean accessibilityTextCandidatesPresent;
    private volatile int latestCaptureWidth = 1;
    private volatile int latestCaptureHeight = 1;
    private volatile long lastTextRefreshMillis;
    private volatile long lastTextContentChangeUptime;
    private volatile long textContentBurstStartedUptime;
    private volatile long accessibilityTextInvalidatedAtUptime;
    private volatile long lastOcrCompletionUptime;
    private volatile long lastMotionUptime;
    private volatile long lastInferenceUptime;
    private volatile long lastQualityInferenceUptime;
    private volatile long lastScreenshotRequestUptime;
    private volatile long lastScrollDiagnosticUptime;
    private volatile long scrollTraceId;
    private volatile long scrollTraceStartedUptime;
    private volatile long lastScrollTraceEventUptime;
    private String lastPublishedTextFingerprint = "";
    private long skippedUnchangedTextPublishes;
    private final Map<Integer, BBox> lastPublishedVisualBoxes = new HashMap<>();
    private volatile boolean overlayNeedsSourceFrame;
    private volatile String foregroundPackage = "";
    private volatile String guardForegroundPackage = "";
    private AppTimerManager timers;
    private PenanceManager penance;
    private final DwellInfractionTracker dwellTracker = new DwellInfractionTracker();
    private final CensorTapTracker tapTracker = CensorTapTracker.shared();
    private long lastMatchedTapMillis;
    private long foregroundSinceMillis;
    private String lastBlockedPackage = "";
    private long lastBlockedAtMillis;
    private HardcoreSettingsGuard hardcoreSettingsGuard;
    private ScrollFrameMotionEstimator motionEstimator;
    private SubliminalOverlayController subliminalOverlay;
    private final Runnable settledHardcoreGuardRefresh = () -> {
        hardcoreGuardRefreshQueued.set(false);
        refreshHardcoreSettingsGuard();
    };
    private final Runnable settledTextRefresh = this::requestTextRefresh;
    private final Runnable contentTextRefresh = () -> {
        contentTextRefreshScheduled.set(false);
        textContentBurstStartedUptime = 0L;
        requestTextRefresh();
    };
    private final Runnable staleAccessibilityTextExpiry = () ->
            publishTextLane(captureEpoch.token(), "accessibility-expiry");
    private final Runnable settledScrollTrace = () -> {
        long now = SystemClock.uptimeMillis();
        long idleMs = now - lastScrollTraceEventUptime;
        if (lastScrollTraceEventUptime > 0L && idleMs >= MOTION_SETTLE_MS) {
            Log.i(TAG, "SCROLL_IDLE id=" + scrollTraceId + " afterLastEventMs=" + idleMs
                    + " durationMs=" + (now - scrollTraceStartedUptime));
        }
    };
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try {
                long now = System.currentTimeMillis();
                boolean foregroundChanged = syncForegroundFromActiveRoot(now);
                if (!foregroundChanged) accountForegroundUsage(now);
                enforceForegroundLimit(now);
                reevaluateRecognition();
                reevaluateSubliminals();
                refreshHardcoreSettingsGuard();
            } catch (RuntimeException error) {
                DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
                Log.e(TAG, "Accessibility service tick failed", error);
            } finally {
                if (running) main.postDelayed(this, 1_000L);
            }
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
        smutTextClassifier = new SmutTextClassifier(this);
        accessibilityText = new AccessibilityTextSmutDetector(smutTextClassifier);
        screenshotText = new OcrTextSmutDetector(smutTextClassifier);
        hardcoreSettingsGuard = new HardcoreSettingsGuard(this);
        subliminalOverlay = new SubliminalOverlayController(this);
        motionEstimator = new ScrollFrameMotionEstimator();
        settings.preferences().registerOnSharedPreferenceChangeListener(listener);
        running = true;
        configureAccessibilityCadence(settings.loadDetectorConfig());

        worker = newScheduledWorker("SubHub-capture", Process.THREAD_PRIORITY_DISPLAY);
        inferenceWorker = newScheduledWorker(
                "SubHub-fast-inference", Process.THREAD_PRIORITY_DISPLAY);
        qualityInferenceWorker = newScheduledWorker(
                "SubHub-quality-inference", Process.THREAD_PRIORITY_DEFAULT);
        textWorker = newScheduledWorker("SubHub-text", Process.THREAD_PRIORITY_BACKGROUND);
        ocrWorker = newScheduledWorker("SubHub-ocr", Process.THREAD_PRIORITY_BACKGROUND);
        worker.execute(this::initializePipeline);
        main.post(this::reevaluateRecognition);
        main.post(this::reevaluateSubliminals);
        main.post(timerTick);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void initializePipeline() {
        if (!running || !initializing.compareAndSet(false, true)) return;
        try {
            DetectorConfig config = settings.loadDetectorConfig();
            DetectorConfig fastConfig = fastDetectorConfig(config);
            detectorConfig = config;
            textSmutConfig = settings.loadTextSmutConfig();
            warmTextModels(config);
            if (detector == null) {
                detector = new DetectionEngine(this, config, false);
                detector.initialize();
            } else {
                detector.setConfig(config);
            }
            if (fastDetector == null) {
                fastDetector = new DetectionEngine(this, fastConfig, true);
                fastDetector.initialize();
            } else {
                fastDetector.setConfig(fastConfig);
            }
            DetectorConfig trackerConfig = accessibilityTrackerConfig(config);
            if (tracker == null) tracker = new ObjectTracker(trackerConfig);
            else tracker.setConfig(trackerConfig);
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
            if (fastDetector != null) fastDetector.close();
            fastDetector = null;
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
        if (!running || !recognitionActive) return;
        long requestUptime = SystemClock.uptimeMillis();
        if (requestUptime - lastScreenshotRequestUptime
                < ACCESSIBILITY_SCREENSHOT_INTERVAL_MS) return;
        if (!processing.compareAndSet(false, true)) return;
        lastScreenshotRequestUptime = requestUptime;
        long requestedEpoch = captureEpoch.token();
        long requestedScrollX = cumulativeScrollX.get();
        long requestedScrollY = cumulativeScrollY.get();
        long requestedGeneration = motionGeneration.get();
        ForegroundWindowResolver.Candidate liveWindow = resolveLiveApplicationWindow();
        int activeWindowId = liveWindow == null ? -1 : liveWindow.windowId;
        String livePackage = liveWindow == null ? "" : liveWindow.packageName;
        AppModeManager mode = new AppModeManager(this);
        if (AppModePolicy.shouldAcceptLiveForegroundPackage(
                livePackage, mode.inputMethodPackage())
                && !livePackage.equals(foregroundPackage)) {
            String confirmedPackage = livePackage;
            main.post(() -> acceptForegroundPackage(
                    confirmedPackage, System.currentTimeMillis()));
            finishScreenshotRequest();
            return;
        }
        TakeScreenshotCallback callback = new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult result) {
                process(result, requestedEpoch, requestedScrollX, requestedScrollY,
                        requestedGeneration, requestUptime);
            }

            @Override
            public void onFailure(int errorCode) {
                if (errorCode != ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    DiagnosticsRepository.failCode(
                            DIAGNOSTICS_MODE, "Screenshot error", errorCode);
                    Log.w(TAG, "Accessibility screenshot failed with code " + errorCode);
                }
                finishScreenshotRequest();
            }
        };
        // Android 14+ can capture the foreground app window directly. Unlike a display capture,
        // this excludes SubHub's own accessibility overlay, so a censor stays continuously visible
        // without becoming part of the next detector input.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && activeWindowId >= 0) {
            takeScreenshotOfWindow(activeWindowId, worker, callback);
            return;
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, callback);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void process(
            ScreenshotResult result,
            long requestedEpoch,
            long requestedScrollX,
            long requestedScrollY,
            long requestedGeneration,
            long requestedAtUptimeMillis) {
        Bitmap wrapped = null;
        Bitmap frame = null;
        HardwareBuffer buffer = result.getHardwareBuffer();
        try {
            if (!isCurrentCapture(requestedEpoch)) return;
            wrapped = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (wrapped == null) return;
            CaptureScrollTimeline.Phase capturePhase = captureScrollTimeline.resolve(
                    result.getTimestamp(), requestedAtUptimeMillis,
                    requestedScrollX, requestedScrollY, requestedGeneration);
            long sourceScrollX = capturePhase.scrollX;
            long sourceScrollY = capturePhase.scrollY;
            latestCaptureWidth = wrapped.getWidth();
            latestCaptureHeight = wrapped.getHeight();
            DetectorConfig currentConfig = detectorConfig;
            boolean continuousMotionInference = usesContinuousMotionInference(currentConfig);
            long sampledGeneration = capturePhase.motionGeneration;
            long motionSampledAt = SystemClock.uptimeMillis();
            Log.i(TAG, "CAPTURE_PHASE requestToCaptureMs="
                    + Math.max(0L, capturePhase.screenshotUptimeMillis
                    - requestedAtUptimeMillis)
                    + " callbackDelayMs=" + Math.max(0L,
                    motionSampledAt - capturePhase.screenshotUptimeMillis)
                    + " requestScroll=" + requestedScrollX + ',' + requestedScrollY
                    + " captureScroll=" + sourceScrollX + ',' + sourceScrollY
                    + " requestGeneration=" + requestedGeneration
                    + " captureGeneration=" + sampledGeneration
                    + " timelineResolved=" + capturePhase.resolvedFromMotion);
            boolean estimateFrameMotion = shouldEstimateFrameMotion(
                    currentConfig, motionSampledAt, lastScrollTraceEventUptime);
            ScrollFrameMotionEstimator.Motion motion = motionEstimator == null
                    || !estimateFrameMotion
                    ? ScrollFrameMotionEstimator.Motion.NONE : motionEstimator.update(wrapped);
            if (!estimateFrameMotion && motionEstimator != null) motionEstimator.reset();
            if (motion.moved()) {
                boolean applied = applyFrameMotion(motion.dx, motion.dy, sampledGeneration);
                Log.i(TAG, "FRAME_MOTION dx=" + motion.dx + " dy=" + motion.dy
                        + " applied=" + applied
                        + " afterAccessibilityMs=" + (lastScrollTraceEventUptime <= 0L
                                ? 0L : motionSampledAt - lastScrollTraceEventUptime));
                if (!continuousMotionInference) return;
                // Screenshot-estimated motion happened before this frame was captured, so the
                // frame itself already represents the newly updated scroll position.
                if (applied) {
                    sourceScrollX = cumulativeScrollX.get();
                    sourceScrollY = cumulativeScrollY.get();
                    sampledGeneration = motionGeneration.get();
                }
            }
            if (!continuousMotionInference
                    && sampledGeneration != motionGeneration.get()) return;
            long nowUptime = SystemClock.uptimeMillis();
            if (!continuousMotionInference
                    && nowUptime - lastMotionUptime < MOTION_SETTLE_MS) return;
            boolean priorityFrame = settledInferenceNeeded.get();
            if (!priorityFrame
                    && nowUptime - lastInferenceUptime < captureDelayMs(detectorConfig)) return;
            lastInferenceUptime = nowUptime;
            boolean motionSettled = lastMotionUptime <= 0L
                    || nowUptime - lastMotionUptime >= MOTION_SETTLE_MS;
            boolean qualityConfirmation = qualityConfirmationRequested.get();
            boolean qualityRefine = shouldRunQualityRefinement(
                    nowUptime, lastMotionUptime, lastQualityInferenceUptime,
                    firstFrameReported.get(),
                    cachedQualityVisual == VisualDetectionSnapshot.EMPTY,
                    detector == null ? 0L : detector.getLastRuntimeMs(),
                    pendingInference.get() != null,
                    qualityConfirmation);
            long inferenceMotionGeneration = sampledGeneration;
            if (!isCurrentCapture(requestedEpoch)) return;
            int inferenceResolution = currentConfig == null
                    ? 320 : currentConfig.getInferenceResolution();
            boolean streamingQuality = usesStreamingQualityPipeline(currentConfig)
                    && detector != null && fastDetector != null && detector != fastDetector;
            int fastFrameResolution = streamingQuality && !overlayNeedsSourceFrame
                    ? FAST_INFERENCE_RESOLUTION : inferenceResolution;
            InferenceBitmapPreparer.Prepared prepared = InferenceBitmapPreparer.prepare(
                    wrapped, fastFrameResolution,
                    overlayNeedsSourceFrame);
            if (prepared == null) return;
            frame = prepared.bitmap;
            // Priority means "publish the first settled fast frame", not "immediately saturate
            // the CPU with quality and text refinement at the same time".
            if (priorityFrame && motionSettled) {
                settledInferenceNeeded.compareAndSet(true, false);
            }
            enqueueInference(new InferenceFrame(frame, requestedEpoch, sourceScrollX,
                    sourceScrollY, inferenceMotionGeneration,
                    prepared.sourceWidth, prepared.sourceHeight,
                    prepared.retainedSourceFrame, continuousMotionInference,
                    qualityRefine && !streamingQuality,
                    qualityRefine && qualityConfirmation && !streamingQuality,
                    requestedAtUptimeMillis));
            frame = null;
            maybeRequestOcr(wrapped, requestedEpoch, sourceScrollX, sourceScrollY,
                    inferenceMotionGeneration, currentConfig);
            // Transfer the immutable hardware screenshot to the quality lane instead of making
            // its 512 px software copy on the latency-critical capture callback. One retained
            // source is allowed; replacement and motion both close it immediately.
            if (streamingQuality && qualityRefine
                    && pendingQualityInference.get() == null
                    && !qualityInferenceDraining.get()) {
                enqueueQualityInference(new QualityInferenceFrame(
                        wrapped, buffer, requestedEpoch, sourceScrollX, sourceScrollY,
                        inferenceMotionGeneration, wrapped.getWidth(), wrapped.getHeight(),
                        inferenceResolution, capturePhase.screenshotUptimeMillis));
                wrapped = null;
                buffer = null;
            }
        } catch (Exception error) {
            DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
            Log.w(TAG, "Could not process accessibility screenshot", error);
        } finally {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            if (wrapped != null && !wrapped.isRecycled()) wrapped.recycle();
            if (buffer != null) buffer.close();
            finishScreenshotRequest();
        }
    }

    private void enqueueInference(InferenceFrame candidate) {
        if (!running || inferenceWorker == null || inferenceWorker.isShutdown()) {
            candidate.recycle();
            return;
        }
        InferenceFrame replaced = pendingInference.getAndSet(candidate);
        if (replaced != null) {
            droppedInferenceFrames.incrementAndGet();
            replaced.recycle();
        }
        if (inferenceWorker != null && inferenceDraining.compareAndSet(false, true)) {
            inferenceWorker.execute(this::drainInferenceQueue);
        }
    }

    private void enqueueQualityInference(QualityInferenceFrame candidate) {
        if (!running || qualityInferenceWorker == null || qualityInferenceWorker.isShutdown()) {
            candidate.recycle();
            return;
        }
        QualityInferenceFrame replaced = pendingQualityInference.getAndSet(candidate);
        if (replaced != null) {
            droppedQualityInferenceFrames.incrementAndGet();
            replaced.recycle();
        }
        scheduleQualityInferenceIfFastIdle();
    }

    /** Quality may fill otherwise idle accelerator time, but never gets ahead of fast work. */
    private void scheduleQualityInferenceIfFastIdle() {
        if (!running || qualityInferenceWorker == null || qualityInferenceWorker.isShutdown()
                || pendingQualityInference.get() == null
                || inferenceDraining.get() || pendingInference.get() != null) return;
        if (qualityInferenceDraining.compareAndSet(false, true)) {
            qualityInferenceWorker.execute(this::drainQualityInferenceQueue);
        }
    }

    /**
     * Uses NNAPI opportunistically after the CPU fast lane drains. Results never receive direct
     * render authority and are abandoned instead of reprojected when the viewport phase changes.
     */
    private void drainQualityInferenceQueue() {
        try {
            while (running) {
                if (inferenceDraining.get() || pendingInference.get() != null) return;
                QualityInferenceFrame candidate = pendingQualityInference.getAndSet(null);
                if (candidate == null) return;
                try {
                    if (isCurrentCapture(candidate.epoch)) runStreamingQualityInference(candidate);
                } catch (Exception error) {
                    DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
                    Log.w(TAG, "Could not process streaming quality frame", error);
                } finally {
                    candidate.recycle();
                }
            }
        } finally {
            qualityInferenceDraining.set(false);
            scheduleQualityInferenceIfFastIdle();
        }
    }

    private void runStreamingQualityInference(QualityInferenceFrame candidate) throws Exception {
        DetectionEngine quality = detector;
        long currentGeneration = motionGeneration.get();
        if (quality == null || !isCurrentCapture(candidate.epoch)) return;
        if (candidate.motionGeneration != currentGeneration
                || (lastMotionUptime > 0L
                && SystemClock.uptimeMillis() - lastMotionUptime < QUALITY_MOTION_SETTLE_MS)) {
            logStreamingQualityDrop("motion-generation-before", candidate, currentGeneration);
            return;
        }
        long prepareStarted = SystemClock.elapsedRealtimeNanos();
        InferenceBitmapPreparer.Prepared prepared = InferenceBitmapPreparer.prepare(
                candidate.sourceFrame, candidate.inferenceResolution, false);
        long bitmapPrepareMs = Math.max(0L, Math.round(
                (SystemClock.elapsedRealtimeNanos() - prepareStarted) / 1_000_000d));
        if (prepared == null) return;
        List<Detection> detected;
        try {
            detected = quality.detect(
                    prepared.bitmap, candidate.sourceWidth, candidate.sourceHeight);
        } finally {
            if (!prepared.bitmap.isRecycled()) prepared.bitmap.recycle();
        }
        int rawVisualCount = detected.size();
        if (!isCurrentCapture(candidate.epoch)) return;

        currentGeneration = motionGeneration.get();
        if (candidate.motionGeneration != currentGeneration) {
            logStreamingQualityDrop("motion-generation-after", candidate, currentGeneration);
            return;
        }
        ScrollPosition current = currentScrollPosition();
        long deltaX = current.scrollX - candidate.scrollX;
        long deltaY = current.scrollY - candidate.scrollY;
        Rect viewport = screenBounds();
        if (Math.abs(deltaX) > viewport.width() || Math.abs(deltaY) > viewport.height()) {
            Log.i(TAG, "QUALITY_STREAM_DROP reason=viewport-disjoint sourceGeneration="
                    + candidate.motionGeneration + " currentGeneration=" + currentGeneration
                    + " scrollDelta=" + deltaX + ',' + deltaY);
            return;
        }
        List<Detection> aligned = shiftDetectionSource(
                detected, candidate.sourceWidth, candidate.sourceHeight,
                candidate.scrollX, candidate.scrollY,
                candidate.sourceWidth, candidate.sourceHeight,
                current.scrollX, current.scrollY);
        VisualDetectionStabilizer.UpdateResult stabilized =
                qualityVisualStabilizer.updateWithMetrics(aligned, detectorConfig);
        List<Detection> coverage = markQualityCoverage(stabilized.stableDetections());
        long cachedAt = SystemClock.uptimeMillis();
        synchronized (scrollStateLock) {
            currentGeneration = motionGeneration.get();
            if (candidate.motionGeneration != currentGeneration) {
                qualityVisualStabilizer.clear();
                logStreamingQualityDrop(
                        "motion-generation-commit", candidate, currentGeneration);
                return;
            }
            cachedQualityVisual = new VisualDetectionSnapshot(
                    coverage, candidate.sourceWidth, candidate.sourceHeight,
                    current.scrollX, current.scrollY, cachedAt, currentGeneration);
        }
        lastQualityInferenceUptime = cachedAt;
        Log.i(TAG, "QUALITY_STREAM_CACHE scrollId=" + scrollTraceId
                + " captureAgeMs=" + (cachedAt - candidate.capturedAtUptimeMillis)
                + " bitmapPrepareMs=" + bitmapPrepareMs
                + " inferenceMs=" + quality.getLastInferenceMs()
                + " preprocessMs=" + quality.getLastPreprocessMs()
                + " runtimeMs=" + quality.getLastRuntimeMs()
                + " postprocessMs=" + quality.getLastPostprocessMs()
                + " afterMotionMs=" + (lastMotionUptime <= 0L
                        ? 0L : cachedAt - lastMotionUptime)
                + " rawVisual=" + rawVisualCount
                + " stableVisual=" + coverage.size()
                + " pendingVisual=" + stabilized.pendingCandidates()
                + " sourceGeneration=" + candidate.motionGeneration
                + " cacheGeneration=" + currentGeneration
                + " reproject=" + (-deltaX) + ',' + (-deltaY)
                + " dropped=" + droppedQualityInferenceFrames.get()
                + " staleDropped=" + staleQualityInferenceFrames.get());
    }

    private void logStreamingQualityDrop(
            String reason, QualityInferenceFrame candidate, long currentGeneration) {
        long dropped = staleQualityInferenceFrames.incrementAndGet();
        Log.i(TAG, "QUALITY_STREAM_DROP reason=" + reason
                + " sourceGeneration=" + candidate.motionGeneration
                + " currentGeneration=" + currentGeneration
                + " staleDropped=" + dropped);
    }

    /** Runs warmed ONNX work independently so screenshot motion sampling never waits on ML. */
    private void drainInferenceQueue() {
        try {
            while (running) {
                InferenceFrame candidate = pendingInference.getAndSet(null);
                if (candidate == null) return;
                try {
                    if (!isCurrentCapture(candidate.epoch)
                            || (!candidate.continuousMotionInference
                            && candidate.motionGeneration != motionGeneration.get())) continue;
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
            } else {
                scheduleQualityInferenceIfFastIdle();
            }
        }
    }

    private void runInference(InferenceFrame candidate) throws Exception {
        DetectionEngine realtime = fastDetector == null ? detector : fastDetector;
        if (realtime == null) return;
        boolean hasSeparateQuality = detector != null && detector != realtime;
        boolean qualityPlanned = candidate.qualityRefine && hasSeparateQuality;
        runInferencePass(candidate, realtime, true, !qualityPlanned);
        if (!qualityPlanned) return;
        if (!isCurrentCapture(candidate.epoch)
                || candidate.motionGeneration != motionGeneration.get()
                || SystemClock.uptimeMillis() - lastMotionUptime < MOTION_SETTLE_MS
                || textRefreshRunning.get()) {
            publishRetainedSourceFrame(candidate, "quality-ineligible");
            return;
        }
        if (pendingInference.get() != null) {
            Log.i(TAG, "QUALITY_SKIP reason=fast-pending lastRuntimeMs="
                    + detector.getLastRuntimeMs());
            publishRetainedSourceFrame(candidate, "fast-pending");
            return;
        }
        if (candidate.qualityConfirmation) {
            qualityConfirmationRequested.compareAndSet(true, false);
            Log.i(TAG, "QUALITY_CONFIRMATION_RUN generation=" + candidate.motionGeneration
                    + " afterQualityMs=" + Math.max(0L,
                    SystemClock.uptimeMillis() - lastQualityInferenceUptime));
        }
        lastQualityInferenceUptime = SystemClock.uptimeMillis();
        runInferencePass(candidate, detector, false, true);
    }

    /** Publishes the fast frame when an optional quality pass yields to newer real-time work. */
    private void publishRetainedSourceFrame(InferenceFrame candidate, String reason) {
        if (!candidate.retainedSourceFrame) return;
        Bitmap sourceFrame = candidate.detachFrame();
        if (sourceFrame == null) return;
        List<TrackedObject> currentTracks = visualRenderTracks(
                tracker.activeTracks()).tracks();
        ScrollPosition current = currentScrollPosition();
        InferenceScrollReprojector.ScreenMotion sourceMotion =
                InferenceScrollReprojector.screenMotion(
                        candidate.scrollX, candidate.scrollY,
                        current.scrollX, current.scrollY);
        main.post(() -> {
            if (isCurrentCapture(candidate.epoch) && overlay != null) {
                overlay.update(currentTracks, candidate.sourceWidth, candidate.sourceHeight,
                        sourceFrame, 0, 0, sourceMotion.dx, sourceMotion.dy);
                Log.i(TAG, "SOURCE_FRAME_PUBLISH reason=" + reason);
            } else {
                sourceFrame.recycle();
            }
        });
    }

    private void runInferencePass(
            InferenceFrame candidate,
            DetectionEngine engine,
            boolean fastPass,
            boolean finalPass) throws Exception {
        if (!fastPass && pendingInference.get() != null) {
            publishRetainedSourceFrame(candidate, "fast-arrived-before-quality");
            return;
        }
        Bitmap frame = candidate.frame;
        long requestedEpoch = candidate.epoch;
        long requestedScrollX = candidate.scrollX;
        long requestedScrollY = candidate.scrollY;
        long inferenceMotionGeneration = candidate.motionGeneration;
        int width = candidate.sourceWidth;
        int height = candidate.sourceHeight;
        List<Detection> visualDetections = engine.detect(frame, width, height);
        int rawVisualCount = visualDetections.size();
        if (!fastPass && pendingInference.get() != null) {
            publishRetainedSourceFrame(candidate, "fast-arrived-during-quality");
            return;
        }
        if (!fastPass && (!isCurrentCapture(requestedEpoch)
                || inferenceMotionGeneration != motionGeneration.get()
                || SystemClock.uptimeMillis() - lastMotionUptime < MOTION_SETTLE_MS)) {
            return;
        }
        int cachedQualityCount;
        int qualityOnlyCount = 0;
        int identityRealtimeLinked = 0;
        int identityQualityLinked = 0;
        int identityFused = 0;
        int identityCarriedQuality = 0;
        int identityUnlinkedQuality = 0;
        if (fastPass) {
            visualDetections = FastVisualGate.filter(visualDetections, detectorConfig);
            List<Detection> cachedQuality = cachedQualityForFrame(
                    width, height, requestedScrollX, requestedScrollY);
            cachedQualityCount = cachedQuality.size();
            VisualIdentityReconciler.Result identity = VisualIdentityReconciler.reconcile(
                    visualDetections, cachedQuality, tracker.activeTracks());
            visualDetections = identity.detections();
            identityRealtimeLinked = identity.realtimeLinked();
            identityQualityLinked = identity.qualityLinked();
            identityFused = identity.fused();
            identityCarriedQuality = identity.carriedQuality();
            identityUnlinkedQuality = identity.unlinkedQuality();
            for (Detection detection : visualDetections) {
                if (detection != null && detection.getSource()
                        == Detection.ObservationSource.QUALITY_VISUAL) {
                    qualityOnlyCount++;
                }
            }
        } else {
            VisualDetectionStabilizer.UpdateResult stabilizedQuality =
                    qualityVisualStabilizer.updateWithMetrics(
                            visualDetections, detectorConfig);
            visualDetections = stabilizedQuality.stableDetections();
            if (!isCurrentCapture(requestedEpoch)
                    || inferenceMotionGeneration != motionGeneration.get()) return;
            List<Detection> qualityCoverage = markQualityCoverage(visualDetections);
            VisualIdentityReconciler.Result identity = VisualIdentityReconciler.reconcile(
                    Collections.emptyList(), qualityCoverage, tracker.activeTracks());
            qualityCoverage = identity.detections();
            int reconciledCoverageCount = qualityCoverage.size();
            boolean batchAlreadyCommitted = qualityBatchCommittedGeneration.get()
                    == inferenceMotionGeneration;
            boolean batchAlreadyClosed = qualityBatchClosedGeneration.get()
                    == inferenceMotionGeneration;
            qualityCoverage = transactionalQualityCoverage(
                    qualityCoverage, stabilizedQuality.pendingCandidates(),
                    batchAlreadyCommitted || batchAlreadyClosed);
            int deferredUnlinked = reconciledCoverageCount - qualityCoverage.size();
            int supplementedTracks = tracker.supplementConfirmedQualityCoverage(
                    qualityCoverage, System.nanoTime());
            boolean completeQualityScene = stabilizedQuality.pendingCandidates() <= 0;
            int retiredQualityTracks = completeQualityScene
                    ? tracker.finishQualityCoverageHandoff(qualityCoverage) : 0;
            if (supplementedTracks > 0) {
                qualityBatchCommittedGeneration.set(inferenceMotionGeneration);
                qualityBatchClosedGeneration.set(inferenceMotionGeneration);
                qualityConfirmationRequested.set(false);
            } else if (candidate.qualityConfirmation || completeQualityScene) {
                // A generation receives one bounded confirmation opportunity. Later periodic
                // refreshes may strengthen existing identities, but cannot spawn a delayed second
                // visible wave into an already settled scene.
                qualityBatchClosedGeneration.set(inferenceMotionGeneration);
                qualityConfirmationRequested.set(false);
            }
            boolean batchClosed = qualityBatchClosedGeneration.get()
                    == inferenceMotionGeneration;
            boolean confirmationRequested = false;
            if (!batchClosed && stabilizedQuality.pendingCandidates() > 0
                    && qualityConfirmationBurstUsed.compareAndSet(false, true)) {
                qualityConfirmationRequested.set(true);
                confirmationRequested = true;
            } else if (stabilizedQuality.pendingCandidates() <= 0 || batchClosed) {
                qualityConfirmationRequested.set(false);
            }
            String qualityBatchState = qualityBatchCommittedGeneration.get()
                    == inferenceMotionGeneration ? "COMMITTED"
                    : batchClosed ? "CLOSED" : confirmationRequested
                            || qualityConfirmationRequested.get()
                            ? "CONFIRMATION_ARMED" : "OPEN";
            cachedQualityVisual = new VisualDetectionSnapshot(
                    qualityCoverage, width, height, requestedScrollX, requestedScrollY,
                    SystemClock.uptimeMillis(), inferenceMotionGeneration);
            cachedQualityCount = visualDetections.size();
            long cachedAt = SystemClock.uptimeMillis();
            long inferenceMs = engine.getLastInferenceMs();
            long preprocessMs = engine.getLastPreprocessMs();
            long runtimeMs = engine.getLastRuntimeMs();
            long postprocessMs = engine.getLastPostprocessMs();
            Log.i(TAG, "QUALITY_CACHE scrollId=" + scrollTraceId
                    + " captureAgeMs=" + (cachedAt - candidate.capturedAtUptimeMillis)
                    + " inferenceMs=" + inferenceMs
                    + " preprocessMs=" + preprocessMs
                    + " runtimeMs=" + runtimeMs
                    + " postprocessMs=" + postprocessMs
                    + " afterMotionMs=" + (lastMotionUptime <= 0L
                            ? 0L : cachedAt - lastMotionUptime)
                    + " rawVisual=" + rawVisualCount
                    + " stableVisual=" + cachedQualityCount
                    + " identityLinked=" + identity.qualityLinked()
                    + " identityUnlinked=" + identity.unlinkedQuality()
                    + " pendingVisual=" + stabilizedQuality.pendingCandidates()
                    + " deferredUnlinked=" + deferredUnlinked
                    + " supplementedTracks=" + supplementedTracks
                    + " retiredQualityTracks=" + retiredQualityTracks
                    + " confirmationRequested=" + confirmationRequested
                    + " qualityBatchState=" + qualityBatchState
                    + " cacheGeneration=" + inferenceMotionGeneration);
            // Quality is a background geometry cache. Publishing it as a second authority made
            // a still box alternate between fast and quality rectangles every refresh. The next
            // fast frame consumes this cache once through the ordinary tracker update.
            Bitmap qualityOverlayFrame = candidate.retainedSourceFrame
                    ? candidate.detachFrame() : null;
            if (qualityOverlayFrame != null || supplementedTracks > 0
                    || retiredQualityTracks > 0) {
                List<TrackedObject> activeTracks = tracker.activeTracks();
                VisualTrackArbitrator.Result renderArbitration =
                        visualRenderTracks(activeTracks);
                List<TrackedObject> currentTracks = renderArbitration.tracks();
                if (supplementedTracks > 0) {
                    recordSupplementedTrackState(activeTracks, width, height);
                }
                ScrollPosition current = currentScrollPosition();
                InferenceScrollReprojector.ScreenMotion sourceMotion =
                        InferenceScrollReprojector.screenMotion(
                                requestedScrollX, requestedScrollY,
                                current.scrollX, current.scrollY);
                main.post(() -> {
                    if (isCurrentCapture(requestedEpoch) && overlay != null
                            && inferenceMotionGeneration == motionGeneration.get()) {
                        if (qualityOverlayFrame != null) {
                            overlay.update(currentTracks, width, height, qualityOverlayFrame,
                                    0, 0, sourceMotion.dx, sourceMotion.dy);
                        } else {
                            overlay.updateTracksOnly(currentTracks, width, height, 0, 0);
                        }
                        if (supplementedTracks > 0 || retiredQualityTracks > 0) {
                            VisualGeometryDelta geometry = recordVisualGeometry(currentTracks);
                            String handoffEvent = supplementedTracks > 0
                                    ? "QUALITY_SUPPLEMENT_PUBLISH" : "QUALITY_RETIRE_PUBLISH";
                            Log.i(TAG, handoffEvent + " scrollId=" + scrollTraceId
                                    + " added=" + supplementedTracks
                                    + " retired=" + retiredQualityTracks
                                    + " afterMotionMs=" + (lastMotionUptime <= 0L ? 0L
                                    : SystemClock.uptimeMillis() - lastMotionUptime)
                                    + " geometryMatched=" + geometry.matched
                                    + " geometryChanged=" + geometry.changed
                                    + " maxCenterDeltaPx=" + geometry.maxCenterDeltaPx
                                    + " maxSizeDeltaPx=" + geometry.maxSizeDeltaPx
                                    + " duplicatesSuppressed=" + renderArbitration.suppressed()
                                    + " renderTracks=" + currentTracks.size());
                        }
                    } else {
                        if (qualityOverlayFrame != null) qualityOverlayFrame.recycle();
                    }
                });
            }
            return;
        }
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
                || (!candidate.continuousMotionInference
                && inferenceMotionGeneration != motionGeneration.get())) return;
        ScrollAlignment alignment = consumeTrackerMotion(width, height);
        if (!candidate.continuousMotionInference
                && inferenceMotionGeneration != motionGeneration.get()) return;
        if (candidate.continuousMotionInference) {
            Rect viewport = screenBounds();
            detections = InferenceScrollReprojector.toCurrentViewport(
                    detections, width, height, viewport.width(), viewport.height(),
                    requestedScrollX, requestedScrollY,
                    alignment.scrollX, alignment.scrollY);
        }
        List<TrackedObject> tracks = tracker.update(detections);
        VisualTrackArbitrator.Result renderArbitration = visualRenderTracks(tracks);
        List<TrackedObject> renderTracks = renderArbitration.tracks();
        int qualityOnlyTrackCount = 0;
        for (TrackedObject track : tracks) {
            if (track != null && track.isQualityOnly()) qualityOnlyTrackCount++;
        }
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
        long inferenceMs = engine.getLastInferenceMs();
        long preprocessMs = engine.getLastPreprocessMs();
        long runtimeMs = engine.getLastRuntimeMs();
        long postprocessMs = engine.getLastPostprocessMs();
        if (firstFrameReported.compareAndSet(false, true)) {
            Log.i(TAG, "First accessibility fast frame processed in "
                    + inferenceMs + " ms at " + width + "x" + height);
        }
        Bitmap overlayFrame = finalPass && candidate.retainedSourceFrame
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
                DIAGNOSTICS_MODE, inferenceMs, preprocessMs, runtimeMs, postprocessMs,
                SystemClock.uptimeMillis() - candidate.capturedAtUptimeMillis,
                droppedInferenceFrames.get(), tracks.size(), width, height);
        String diagnosticText = diagnosticsOverlayText(diagnostics);
        int publishedQualityOnlyCount = qualityOnlyCount;
        int publishedIdentityRealtimeLinked = identityRealtimeLinked;
        int publishedIdentityQualityLinked = identityQualityLinked;
        int publishedIdentityFused = identityFused;
        int publishedIdentityCarriedQuality = identityCarriedQuality;
        int publishedIdentityUnlinkedQuality = identityUnlinkedQuality;
        int publishedQualityOnlyTrackCount = qualityOnlyTrackCount;
        InferenceScrollReprojector.ScreenMotion sourceFrameMotion =
                InferenceScrollReprojector.screenMotion(
                        requestedScrollX, requestedScrollY,
                        alignment.scrollX, alignment.scrollY);
        main.post(() -> {
            if (isCurrentCapture(requestedEpoch) && overlay != null
                    && (candidate.continuousMotionInference
                    || inferenceMotionGeneration == motionGeneration.get())) {
                ScrollPosition current = currentScrollPosition();
                InferenceScrollReprojector.ScreenMotion liveMotion =
                        InferenceScrollReprojector.screenMotion(
                                alignment.scrollX, alignment.scrollY,
                                current.scrollX, current.scrollY);
                overlay.setDiagnostics(diagnosticText);
                overlay.update(renderTracks, width, height, overlayFrame,
                        liveMotion.dx, liveMotion.dy,
                        sourceFrameMotion.dx, sourceFrameMotion.dy);
                VisualGeometryDelta geometry = recordVisualGeometry(renderTracks);
                long publishedAt = SystemClock.uptimeMillis();
                long publishDelay = publishedAt - candidate.capturedAtUptimeMillis;
                DiagnosticsRepository.recordPublishDelay(DIAGNOSTICS_MODE, publishDelay);
                Log.i(TAG, "OVERLAY_PUBLISH pass=" + (fastPass ? "fast" : "quality")
                        + " scrollId=" + scrollTraceId
                        + " captureAgeMs=" + publishDelay
                        + " inferenceMs=" + inferenceMs
                        + " preprocessMs=" + preprocessMs
                        + " runtimeMs=" + runtimeMs
                        + " postprocessMs=" + postprocessMs
                        + " afterMotionMs=" + (lastMotionUptime <= 0L
                                ? 0L : publishedAt - lastMotionUptime)
                        + " sourceScroll=" + requestedScrollX + ',' + requestedScrollY
                        + " alignedScroll=" + alignment.scrollX + ',' + alignment.scrollY
                        + " currentScroll=" + current.scrollX + ',' + current.scrollY
                        + " sourceReproject=" + sourceFrameMotion.dx + ','
                        + sourceFrameMotion.dy
                        + " liveReproject=" + liveMotion.dx + ',' + liveMotion.dy
                        + " captureGeneration=" + inferenceMotionGeneration
                        + " currentGeneration=" + motionGeneration.get()
                        + " tracks=" + tracks.size()
                        + " rawVisual=" + rawVisualCount
                        + " cachedQuality=" + cachedQualityCount
                        + " qualityOnly=" + publishedQualityOnlyCount
                        + " identityRealtimeLinked=" + publishedIdentityRealtimeLinked
                        + " identityQualityLinked=" + publishedIdentityQualityLinked
                        + " identityFused=" + publishedIdentityFused
                        + " identityCarriedQuality=" + publishedIdentityCarriedQuality
                        + " identityUnlinkedQuality=" + publishedIdentityUnlinkedQuality
                        + " geometryMatched=" + geometry.matched
                        + " geometryChanged=" + geometry.changed
                        + " maxCenterDeltaPx=" + geometry.maxCenterDeltaPx
                        + " maxSizeDeltaPx=" + geometry.maxSizeDeltaPx
                        + " dropped=" + droppedInferenceFrames.get()
                        + " duplicatesSuppressed=" + renderArbitration.suppressed()
                        + " qualityOnlyTracks=" + publishedQualityOnlyTrackCount
                        + " renderTracks=" + renderTracks.size()
                        + " qualityActive=" + qualityInferenceDraining.get());
            } else if (overlayFrame != null) {
                overlayFrame.recycle();
            }
        });
    }

    /** Mirrors fast-publish bookkeeping for confirmed tracks first exposed by quality inference. */
    private void recordSupplementedTrackState(
            List<TrackedObject> tracks,
            int width,
            int height) {
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

    static DetectorConfig fastDetectorConfig(DetectorConfig quality) {
        DetectorConfig source = quality == null ? DetectorConfig.builder().build() : quality;
        return source.toBuilder()
                .inferenceResolution(Math.min(
                        FAST_INFERENCE_RESOLUTION, source.getInferenceResolution()))
                .detectionIntervalMs(0L)
                .build();
    }

    static DetectorConfig accessibilityTrackerConfig(DetectorConfig configured) {
        DetectorConfig source = configured == null
                ? DetectorConfig.builder().build() : configured;
        return source.toBuilder()
                .motionPrediction(false)
                .velocitySmoothing(0f)
                .maxExtrapolationMs(0f)
                .build();
    }

    static boolean shouldRunQualityRefinement(
            long nowUptime,
            long lastMotionUptime,
            long lastQualityUptime,
            boolean firstFrameReported,
            boolean qualityCacheEmpty) {
        return shouldRunQualityRefinement(nowUptime, lastMotionUptime, lastQualityUptime,
                firstFrameReported, qualityCacheEmpty, 0L, false, false);
    }

    static boolean shouldRunQualityRefinement(
            long nowUptime,
            long lastMotionUptime,
            long lastQualityUptime,
            boolean firstFrameReported,
            boolean qualityCacheEmpty,
            long lastQualityRuntimeMs,
            boolean fastFramePending) {
        return shouldRunQualityRefinement(nowUptime, lastMotionUptime, lastQualityUptime,
                firstFrameReported, qualityCacheEmpty, lastQualityRuntimeMs,
                fastFramePending, false);
    }

    static boolean shouldRunQualityRefinement(
            long nowUptime,
            long lastMotionUptime,
            long lastQualityUptime,
            boolean firstFrameReported,
            boolean qualityCacheEmpty,
            long lastQualityRuntimeMs,
            boolean fastFramePending,
            boolean confirmationRequested) {
        if (fastFramePending) return false;
        if (lastMotionUptime > 0L
                && nowUptime - lastMotionUptime < QUALITY_MOTION_SETTLE_MS) return false;
        if (confirmationRequested) {
            return nowUptime - lastQualityUptime >= QUALITY_CONFIRMATION_INTERVAL_MS;
        }
        if (!firstFrameReported || qualityCacheEmpty) return true;
        long interval = lastQualityRuntimeMs >= QUALITY_SLOW_RUNTIME_MS
                ? QUALITY_SLOW_REFRESH_INTERVAL_MS : QUALITY_REFRESH_INTERVAL_MS;
        return nowUptime - lastQualityUptime >= interval;
    }

    static List<Detection> transactionalQualityCoverage(
            List<Detection> qualityCoverage,
            int pendingCandidates,
            boolean batchClosed) {
        if (qualityCoverage == null || qualityCoverage.isEmpty()) {
            return Collections.emptyList();
        }
        // VisualDetectionStabilizer has already confirmed every item in qualityCoverage. Pending
        // candidates are separate observations and must not hold unrelated stable regions hostage.
        if (!batchClosed) return qualityCoverage;
        List<Detection> linkedCoverage = new ArrayList<>();
        for (Detection detection : qualityCoverage) {
            if (detection != null && detection.getTrackId() >= 0) {
                linkedCoverage.add(detection);
            }
        }
        return linkedCoverage;
    }

    static boolean usesContinuousMotionInference(DetectorConfig config) {
        return config != null && config.getInferenceThreads() >= 4;
    }

    static boolean usesStreamingQualityPipeline(DetectorConfig config) {
        return usesContinuousMotionInference(config)
                && config.getInferenceResolution() > FAST_INFERENCE_RESOLUTION;
    }

    private static ScheduledExecutorService newScheduledWorker(
            String name, int androidPriority) {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(() -> {
                Process.setThreadPriority(androidPriority);
                runnable.run();
            }, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    static boolean shouldEstimateFrameMotion(
            DetectorConfig config,
            long nowUptime,
            long lastAccessibilityScrollUptime) {
        // Ultra continuously classifies each accepted Accessibility frame. A second global-motion
        // estimator can only double-apply movement already represented by scroll events and the
        // next detector frame. Retain the estimator solely as a conservative fallback for slower
        // presets/apps that expose no usable Accessibility scroll events.
        if (usesContinuousMotionInference(config)) return false;
        return lastAccessibilityScrollUptime <= 0L
                || nowUptime - lastAccessibilityScrollUptime >= 750L;
    }

    static boolean usesSemanticTextModel(DetectorConfig config) {
        return config != null && config.getInferenceThreads() >= 3;
    }

    static boolean usesScreenshotOcr(DetectorConfig config) {
        return config != null && config.getInferenceThreads() >= 4;
    }

    static long capturePollDelayMs(DetectorConfig config) {
        // Android rejects tighter Accessibility screenshots with
        // ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT. Scroll events own the real-time path.
        return ACCESSIBILITY_SCREENSHOT_INTERVAL_MS;
    }

    private boolean applyFrameMotion(int dx, int dy, long expectedGeneration) {
        if (dx == 0 && dy == 0) return false;
        synchronized (scrollStateLock) {
            if (!motionGeneration.compareAndSet(expectedGeneration, expectedGeneration + 1L)) {
                return false;
            }
        }
        applyScrollMotion(dx, dy, false, false);
        return true;
    }

    private void applyEventMotion(int dx, int dy, boolean allowPrediction) {
        if (dx == 0 && dy == 0) return;
        synchronized (scrollStateLock) {
            motionGeneration.incrementAndGet();
        }
        // Establish the next screenshot as a fresh visual baseline. Otherwise the estimator sees
        // the same movement Android just reported and translates every censor a second time.
        if (motionEstimator != null) motionEstimator.reset();
        applyScrollMotion(dx, dy, true, allowPrediction);
    }

    private void traceScrollEvent(
            long nowUptime,
            long eventAgeMs,
            int rawDx,
            int rawDy,
            int dx,
            int dy,
            String source,
            String evidence,
            int adjustedPixels,
            boolean amplified) {
        long gap = lastScrollTraceEventUptime <= 0L
                ? Long.MAX_VALUE : nowUptime - lastScrollTraceEventUptime;
        if (gap > 250L) {
            scrollTraceId++;
            scrollTraceStartedUptime = nowUptime;
            Log.i(TAG, "SCROLL_START id=" + scrollTraceId + " source=" + source);
        }
        lastScrollTraceEventUptime = nowUptime;
        Log.i(TAG, "SCROLL_EVENT id=" + scrollTraceId + " source=" + source
                + " gapMs=" + (gap == Long.MAX_VALUE ? 0L : gap)
                + " eventAgeMs=" + eventAgeMs
                + " rawDx=" + rawDx + " rawDy=" + rawDy
                + " dx=" + dx + " dy=" + dy
                + " evidence=" + evidence
                + " adjustedPx=" + adjustedPixels
                + " amplified=" + amplified);
        main.removeCallbacks(settledScrollTrace);
        main.postDelayed(settledScrollTrace, MOTION_SETTLE_MS);
    }

    private void applyScrollMotion(
            int dx,
            int dy,
            boolean alreadyOnMainThread,
            boolean allowPrediction) {
        if (dx == 0 && dy == 0) return;
        qualityVisualStabilizer.clear();
        qualityConfirmationRequested.set(false);
        qualityConfirmationBurstUsed.set(false);
        discardPendingQualityInference();
        VisualDetectionSnapshot invalidatedQuality = cachedQualityVisual;
        cachedQualityVisual = VisualDetectionSnapshot.EMPTY;
        if (invalidatedQuality != VisualDetectionSnapshot.EMPTY
                && !invalidatedQuality.detections.isEmpty()) {
            Log.i(TAG, "QUALITY_CACHE_INVALIDATED reason=motion detections="
                    + invalidatedQuality.detections.size()
                    + " ageMs=" + Math.max(0L, SystemClock.uptimeMillis()
                    - invalidatedQuality.capturedAtUptimeMillis)
                    + " generation=" + motionGeneration.get());
        }
        resetTextConfirmationForMotion();
        lastMotionUptime = SystemClock.uptimeMillis();
        settledInferenceNeeded.set(true);
        // cumulativeScroll stores content-scroll direction; dx/dy are screen movement.
        synchronized (scrollStateLock) {
            cumulativeScrollX.addAndGet(-dx);
            cumulativeScrollY.addAndGet(-dy);
            pendingTrackerOffsetX.addAndGet(dx);
            pendingTrackerOffsetY.addAndGet(dy);
            captureScrollTimeline.record(lastMotionUptime,
                    cumulativeScrollX.get(), cumulativeScrollY.get(), motionGeneration.get());
        }
        offsetVisualGeometryHistory(dx, dy);
        android.util.DisplayMetrics tapMetrics = getResources().getDisplayMetrics();
        tapTracker.offsetContent(dx, dy, tapMetrics.widthPixels, tapMetrics.heightPixels,
                System.currentTimeMillis());
        if (!usesContinuousMotionInference(detectorConfig)) discardPendingInference();
        dwellTracker.onScroll();
        textRefreshRequested.set(true);
        invalidateAccessibilityTextSnapshot();
        invalidateOcrForMotion();
        main.removeCallbacks(settledTextRefresh);
        main.postDelayed(settledTextRefresh, SETTLED_SCROLL_REFRESH_MS);
        queueSettledCapture();
        Runnable moveOverlay = () -> {
            if (recognitionActive && overlay != null) {
                overlay.offsetContent(dx, dy, allowPrediction);
            }
        };
        if (alreadyOnMainThread || Looper.myLooper() == main.getLooper()) moveOverlay.run();
        else main.post(moveOverlay);
    }

    private void resetTextConfirmationForMotion() {
        accessibilityCandidateScans.set(0);
        cancelPendingTextConfirmation();
    }

    private void cancelPendingTextConfirmation() {
        AccessibilityTextSmutDetector.ScanResult pending =
                pendingTextConfirmation.getAndSet(null);
        if (pending != null) pending.close();
    }

    private synchronized void invalidateAccessibilityTextSnapshot() {
        TextDetectionSnapshot snapshot = cachedAccessibilityText;
        if (snapshot == TextDetectionSnapshot.EMPTY || snapshot.detections.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        if (accessibilityTextInvalidatedAtUptime <= snapshot.capturedAtUptimeMillis) {
            accessibilityTextInvalidatedAtUptime = now;
        }
        long delay = Math.max(0L, ACCESSIBILITY_TEXT_STALE_TTL_MS
                - (now - accessibilityTextInvalidatedAtUptime));
        main.removeCallbacks(staleAccessibilityTextExpiry);
        main.postDelayed(staleAccessibilityTextExpiry, delay);
    }

    private synchronized void cacheAccessibilityText(TextDetectionSnapshot snapshot) {
        cachedAccessibilityText = snapshot == null ? TextDetectionSnapshot.EMPTY : snapshot;
        accessibilityTextInvalidatedAtUptime = 0L;
        main.removeCallbacks(staleAccessibilityTextExpiry);
    }

    private ScrollAlignment consumeTrackerMotion(int width, int height) {
        long scrollX;
        long scrollY;
        int dx;
        int dy;
        synchronized (scrollStateLock) {
            scrollX = cumulativeScrollX.get();
            scrollY = cumulativeScrollY.get();
            dx = saturatingInt(pendingTrackerOffsetX.getAndSet(0L));
            dy = saturatingInt(pendingTrackerOffsetY.getAndSet(0L));
        }
        if (tracker != null) tracker.offsetActiveTracks(dx, dy, width, height);
        return new ScrollAlignment(scrollX, scrollY);
    }

    private ScrollPosition currentScrollPosition() {
        synchronized (scrollStateLock) {
            return new ScrollPosition(cumulativeScrollX.get(), cumulativeScrollY.get());
        }
    }

    private static int saturatingInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private void discardPendingInference() {
        InferenceFrame pending = pendingInference.getAndSet(null);
        if (pending != null) pending.recycle();
    }

    private void discardPendingQualityInference() {
        QualityInferenceFrame pending = pendingQualityInference.getAndSet(null);
        if (pending != null) pending.recycle();
    }

    /** Coalesces movement into the earliest screenshot Android's platform gate will accept. */
    private void queueSettledCapture() {
        ScheduledExecutorService captureWorker = worker;
        if (!running || !recognitionActive || captureWorker == null
                || captureWorker.isShutdown()) return;
        ScheduledFuture<?> existing = priorityCaptureSchedule;
        if (existing != null) existing.cancel(false);
        priorityCaptureSchedule = captureWorker.schedule(
                this::requestSettledCapture, MOTION_SETTLE_MS, TimeUnit.MILLISECONDS);
    }

    private void requestSettledCapture() {
        if (!running || !recognitionActive || worker == null || worker.isShutdown()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        long now = SystemClock.uptimeMillis();
        long wait = settledCaptureDelayMs(
                now, lastMotionUptime, lastScreenshotRequestUptime);
        if (wait > 0L) {
            priorityCaptureSchedule = worker.schedule(
                    this::requestSettledCapture, wait, TimeUnit.MILLISECONDS);
            return;
        }
        priorityCaptureSchedule = null;
        Log.i(TAG, "SETTLED_CAPTURE_REQUEST scrollId=" + scrollTraceId
                + " afterMotionMs=" + (lastMotionUptime <= 0L
                        ? 0L : now - lastMotionUptime)
                + " platformGapMs=" + (now - lastScreenshotRequestUptime));
        requestScreenshot();
    }

    static long settledCaptureDelayMs(
            long nowUptime, long lastMotionUptime, long lastRequestUptime) {
        long motionWait = MOTION_SETTLE_MS - (nowUptime - lastMotionUptime);
        long platformWait = ACCESSIBILITY_SCREENSHOT_INTERVAL_MS
                - (nowUptime - lastRequestUptime);
        return Math.max(0L, Math.max(motionWait, platformWait));
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
                || !config.isEnabled() || !textRefreshRequested.get()) return;
        long nowUptime = SystemClock.uptimeMillis();
        long contentWait = contentTextRefreshDelayMs(
                nowUptime, lastTextContentChangeUptime, textContentBurstStartedUptime);
        if (contentWait > 0L) {
            main.removeCallbacks(settledTextRefresh);
            main.postDelayed(settledTextRefresh, contentWait);
            return;
        }
        long wait = textScanStartDelayMs(
                nowUptime, lastMotionUptime,
                System.currentTimeMillis(), lastTextRefreshMillis);
        if (wait > 0L) {
            main.removeCallbacks(settledTextRefresh);
            main.postDelayed(settledTextRefresh, wait);
            return;
        }
        if (!textRefreshRunning.compareAndSet(false, true)) return;
        cancelPendingTextConfirmation();
        textRefreshRequested.set(false);
        long epoch = captureEpoch.token();
        int captureWidth = latestCaptureWidth;
        int captureHeight = latestCaptureHeight;
        textWorker.execute(() -> {
            long scanStartedUptime = SystemClock.uptimeMillis();
            long scanMotionGeneration = motionGeneration.get();
            long scanSceneGeneration = textSceneGeneration.get();
            long scanContentGeneration = textContentGeneration.get();
            int contentEventsAtStart = textContentEvents.getAndSet(0);
            int contentTypesAtStart = textContentChangeTypes.getAndSet(0);
            long contentChangedAtStart = lastTextContentChangeUptime;
            ScrollPosition scanScroll = currentScrollPosition();
            AccessibilityNodeInfo root = null;
            AccessibilityTextSmutDetector.ScanResult scan = null;
            try {
                root = accessibilityTextRoot();
                long rootReadyUptime = SystemClock.uptimeMillis();
                if (root == null || !isCurrentCapture(epoch)) return;
                Rect screen = screenBounds();
                scan = accessibilityText.detectWithMetrics(
                        root, config, screen.width(), screen.height(),
                        usesSemanticTextModel(detectorConfig),
                        usesScreenshotOcr(detectorConfig),
                        () -> !isCurrentCapture(epoch)
                                || config != textSmutConfig
                                || motionGeneration.get() != scanMotionGeneration
                                || textSceneGeneration.get() != scanSceneGeneration);
                long detectionCompleteUptime = SystemClock.uptimeMillis();
                List<Detection> mapped = TextDetectionCoordinateMapper.screenToCapture(
                        scan.getDetections(), screen.width(), screen.height(),
                        captureWidth, captureHeight);
                long mappedUptime = SystemClock.uptimeMillis();
                if (!isCurrentCapture(epoch) || config != textSmutConfig) return;
                long currentMotionGeneration = motionGeneration.get();
                long currentContentGeneration = textContentGeneration.get();
                ScrollPosition currentScroll = currentScrollPosition();
                if (!shouldPublishTextScan(
                        scanMotionGeneration, currentMotionGeneration,
                        scanScroll.scrollX, scanScroll.scrollY,
                        currentScroll.scrollX, currentScroll.scrollY)
                        || textSceneGeneration.get() != scanSceneGeneration) {
                    textRefreshRequested.set(true);
                    Log.i(TAG, "TEXT_SCAN discarded=stale candidates=" + mapped.size()
                            + " durationMs=" + (mappedUptime - scanStartedUptime)
                            + " rootMs=" + (rootReadyUptime - scanStartedUptime)
                            + " detectMs=" + (detectionCompleteUptime - rootReadyUptime)
                            + " mapMs=" + (mappedUptime - detectionCompleteUptime)
                            + " visited=" + scan.getVisitedNodes()
                            + " textNodes=" + scan.getTextNodes()
                            + " classified=" + scan.getClassifiedNodes()
                            + " cancelled=" + scan.isCancelled()
                            + " generation=" + scanMotionGeneration + "->"
                            + currentMotionGeneration
                            + " sceneGeneration=" + scanSceneGeneration + "->"
                            + textSceneGeneration.get()
                            + " contentEvents=" + contentEventsAtStart
                            + " contentTypes=" + contentTypesAtStart);
                    return;
                }
                long contentQuietMs = Math.max(0L,
                        mappedUptime - lastTextContentChangeUptime);
                boolean contentChangedDuringScan =
                        currentContentGeneration != scanContentGeneration;
                if (contentChangedDuringScan
                        && contentQuietMs < CONTENT_TEXT_REFRESH_MS
                        && textContentStaleRetries.getAndIncrement() < 1) {
                    textRefreshRequested.set(true);
                    Log.i(TAG, "TEXT_SCAN discarded=content-active candidates=" + mapped.size()
                            + " durationMs=" + (mappedUptime - scanStartedUptime)
                            + " contentGeneration=" + scanContentGeneration + "->"
                            + currentContentGeneration
                            + " contentQuietMs=" + contentQuietMs);
                    return;
                }
                textContentStaleRetries.set(0);
                boolean bestEffortContentSnapshot = contentChangedDuringScan;
                boolean bridgeConfirmedMiss = shouldBridgeTextMisses(
                        scanStartedUptime, lastMotionUptime,
                        Math.max(contentChangedAtStart, lastTextContentChangeUptime))
                        && !bestEffortContentSnapshot;
                TextDetectionStabilizer.UpdateResult scene =
                        accessibilityTextStabilizer.updateWithMetrics(
                        mapped, bridgeConfirmedMiss);
                int candidateScan = accessibilityCandidateScans.incrementAndGet();
                accessibilityTextCandidatesPresent = !mapped.isEmpty();
                if (accessibilityTextCandidatesPresent) clearCachedOcr();
                DiagnosticsRepository.recordAccessibilityText(
                        DIAGNOSTICS_MODE, mapped.size(),
                        scene.getStableDetections().size());
                Log.i(TAG, "TEXT_SCAN accepted candidates=" + mapped.size()
                        + " stable=" + scene.getStableDetections().size()
                        + " pending=" + scene.getPendingCandidates()
                        + " present=" + scene.getConfirmedPresent()
                        + " bridged=" + scene.getBridgedConfirmed()
                        + " bridgeMiss=" + bridgeConfirmedMiss
                        + " durationMs=" + (mappedUptime - scanStartedUptime)
                        + " rootMs=" + (rootReadyUptime - scanStartedUptime)
                        + " detectMs=" + (detectionCompleteUptime - rootReadyUptime)
                        + " mapMs=" + (mappedUptime - detectionCompleteUptime)
                        + " visited=" + scan.getVisitedNodes()
                        + " textNodes=" + scan.getTextNodes()
                        + " classified=" + scan.getClassifiedNodes()
                        + " probes=" + scan.getConfirmationProbeCount()
                        + " contentGeneration=" + scanContentGeneration + "->"
                        + currentContentGeneration
                        + " contentQuietMs=" + contentQuietMs
                        + " bestEffort=" + bestEffortContentSnapshot
                        + " contentEvents=" + contentEventsAtStart
                        + " contentTypes=" + contentTypesAtStart);
                lastTextRefreshMillis = System.currentTimeMillis();
                boolean targetedConfirmation = scene.getPendingCandidates() > 0
                        && scan.getConfirmationProbeCount() > 0
                        && !bestEffortContentSnapshot;
                if (targetedConfirmation) {
                    scheduleTextCandidateConfirmation(scan, config, epoch,
                            screen.width(), screen.height(), captureWidth, captureHeight,
                            scanMotionGeneration, scanSceneGeneration,
                            scanContentGeneration, scanScroll);
                    scan = null;
                } else if (scene.getPendingCandidates() == 0) {
                    cacheAccessibilityText(new TextDetectionSnapshot(
                            scene.getStableDetections(), captureWidth, captureHeight,
                            scanScroll.scrollX, scanScroll.scrollY));
                    publishTextLane(epoch, "accessibility",
                            scanMotionGeneration, scanSceneGeneration,
                            currentContentGeneration);
                }
                if (!targetedConfirmation && scene.getPendingCandidates() > 0
                        && (candidateScan < 2 || bestEffortContentSnapshot)) {
                    textRefreshRequested.set(true);
                    main.postDelayed(settledTextRefresh, MIN_TEXT_REFRESH_MS);
                }
            } finally {
                if (root != null) root.recycle();
                if (scan != null) scan.close();
                textRefreshRunning.set(false);
                if (textRefreshRequested.get()) {
                    long retryNowUptime = SystemClock.uptimeMillis();
                    long retryDelay = textRefreshDelayAfterMotion(
                            retryNowUptime, lastMotionUptime);
                    main.removeCallbacks(settledTextRefresh);
                    main.postDelayed(settledTextRefresh, retryDelay);
                }
            }
        });
    }

    private void scheduleTextCandidateConfirmation(
            AccessibilityTextSmutDetector.ScanResult scan,
            TextSmutConfig config,
            long epoch,
            int screenWidth,
            int screenHeight,
            int captureWidth,
            int captureHeight,
            long scanMotionGeneration,
            long scanSceneGeneration,
            long scanContentGeneration,
            ScrollPosition scanScroll) {
        ScheduledExecutorService executor = textWorker;
        if (executor == null || executor.isShutdown()) {
            scan.close();
            return;
        }
        AccessibilityTextSmutDetector.ScanResult replaced =
                pendingTextConfirmation.getAndSet(scan);
        if (replaced != null && replaced != scan) replaced.close();
        try {
            executor.schedule(() -> {
                if (!pendingTextConfirmation.compareAndSet(scan, null)) return;
                if (!textRefreshRunning.compareAndSet(false, true)) {
                    scan.close();
                    textRefreshRequested.set(true);
                    main.post(settledTextRefresh);
                    return;
                }
                long startedUptime = SystemClock.uptimeMillis();
                try {
                    ScrollPosition currentScroll = currentScrollPosition();
                    if (config != textSmutConfig || !isCurrentCapture(epoch)
                            || textSceneGeneration.get() != scanSceneGeneration
                            || textContentGeneration.get() != scanContentGeneration
                            || !shouldPublishTextScan(
                            scanMotionGeneration, motionGeneration.get(),
                            scanScroll.scrollX, scanScroll.scrollY,
                            currentScroll.scrollX, currentScroll.scrollY)) {
                        return;
                    }
                    AccessibilityTextSmutDetector.ConfirmResult confirmation =
                            accessibilityText.confirmCandidates(
                                    scan, config, screenWidth, screenHeight,
                                    usesSemanticTextModel(detectorConfig),
                                    usesScreenshotOcr(detectorConfig),
                                    () -> !isCurrentCapture(epoch)
                                            || motionGeneration.get() != scanMotionGeneration
                                            || textContentGeneration.get()
                                                    != scanContentGeneration
                                            || textSceneGeneration.get()
                                                    != scanSceneGeneration);
                    if (confirmation.isCancelled() || !isCurrentCapture(epoch)) return;
                    List<Detection> mapped = TextDetectionCoordinateMapper.screenToCapture(
                            confirmation.getDetections(), screenWidth, screenHeight,
                            captureWidth, captureHeight);
                    currentScroll = currentScrollPosition();
                    if (!shouldPublishTextScan(
                            scanMotionGeneration, motionGeneration.get(),
                            scanScroll.scrollX, scanScroll.scrollY,
                            currentScroll.scrollX, currentScroll.scrollY)
                            || textContentGeneration.get() != scanContentGeneration
                            || textSceneGeneration.get() != scanSceneGeneration) {
                        return;
                    }
                    TextDetectionStabilizer.UpdateResult scene =
                            accessibilityTextStabilizer.confirmSubset(mapped);
                    cacheAccessibilityText(new TextDetectionSnapshot(
                            scene.getStableDetections(), captureWidth, captureHeight,
                            scanScroll.scrollX, scanScroll.scrollY));
                    DiagnosticsRepository.recordAccessibilityText(
                            DIAGNOSTICS_MODE, confirmation.getConfirmedNodes(),
                            scene.getStableDetections().size());
                    Log.i(TAG, "TEXT_CONFIRM targeted confirmed="
                            + confirmation.getConfirmedNodes()
                            + " promoted=" + scene.getNewlyConfirmedCandidates()
                            + " stable=" + scene.getStableDetections().size()
                            + " pending=" + scene.getPendingCandidates()
                            + " attempted=" + confirmation.getAttemptedNodes()
                            + " refreshed=" + confirmation.getRefreshedNodes()
                            + " durationMs="
                            + (SystemClock.uptimeMillis() - startedUptime));
                    publishTextLane(epoch, "accessibility-targeted",
                            scanMotionGeneration, scanSceneGeneration,
                            scanContentGeneration);
                    lastTextRefreshMillis = System.currentTimeMillis();
                    if (scene.getPendingCandidates() > 0
                            && accessibilityCandidateScans.get() < 2) {
                        textRefreshRequested.set(true);
                    }
                } finally {
                    scan.close();
                    textRefreshRunning.set(false);
                    if (textRefreshRequested.get()) {
                        main.removeCallbacks(settledTextRefresh);
                        main.post(settledTextRefresh);
                    }
                }
            }, TEXT_CANDIDATE_CONFIRM_MS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException error) {
            pendingTextConfirmation.compareAndSet(scan, null);
            scan.close();
            Log.w(TAG, "Could not schedule targeted text confirmation", error);
        }
    }

    static boolean shouldPublishTextScan(
            long sampledMotionGeneration,
            long currentMotionGeneration,
            long sampledScrollX,
            long sampledScrollY,
            long currentScrollX,
            long currentScrollY) {
        return sampledMotionGeneration == currentMotionGeneration
                && sampledScrollX == currentScrollX
                && sampledScrollY == currentScrollY;
    }

    static boolean shouldBridgeTextMisses(
            long scanStartedUptime,
            long lastMotionUptime,
            long lastContentChangeUptime) {
        boolean motionSettled = lastMotionUptime <= 0L
                || scanStartedUptime - lastMotionUptime >= POST_SCROLL_TEXT_RECONCILE_MS;
        boolean contentSettled = lastContentChangeUptime <= 0L
                || scanStartedUptime - lastContentChangeUptime >= POST_SCROLL_TEXT_RECONCILE_MS;
        return motionSettled && contentSettled;
    }

    private AccessibilityNodeInfo accessibilityTextRoot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getRootInActiveWindow(
                    AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST);
        }
        return getRootInActiveWindow();
    }

    static long textRefreshDelayAfterMotion(long nowUptime, long lastMotionUptime) {
        if (lastMotionUptime <= 0L) return 0L;
        return Math.max(0L,
                SETTLED_SCROLL_REFRESH_MS - (nowUptime - lastMotionUptime));
    }

    static long textScanStartDelayMs(
            long nowUptime,
            long lastMotionUptime,
            long nowMillis,
            long lastRefreshMillis) {
        long motionDelay = textRefreshDelayAfterMotion(nowUptime, lastMotionUptime);
        long cadenceDelay = Math.max(0L,
                MIN_TEXT_REFRESH_MS - (nowMillis - lastRefreshMillis));
        return Math.max(motionDelay, cadenceDelay);
    }

    static long contentTextRefreshDelayMs(
            long nowUptime,
            long lastContentChangeUptime,
            long contentBurstStartedUptime) {
        if (lastContentChangeUptime <= 0L || nowUptime < lastContentChangeUptime) return 0L;
        long quietDelay = Math.max(0L,
                CONTENT_TEXT_REFRESH_MS - (nowUptime - lastContentChangeUptime));
        if (contentBurstStartedUptime <= 0L || nowUptime < contentBurstStartedUptime) {
            return quietDelay;
        }
        long maximumDelay = Math.max(0L,
                CONTENT_TEXT_MAX_DEBOUNCE_MS - (nowUptime - contentBurstStartedUptime));
        return Math.min(quietDelay, maximumDelay);
    }

    static boolean isAccessibilityTextSnapshotFresh(
            long nowUptime,
            long capturedAtUptime,
            long invalidatedAtUptime) {
        if (capturedAtUptime <= 0L || nowUptime < capturedAtUptime) return false;
        if (invalidatedAtUptime <= capturedAtUptime) return true;
        return nowUptime >= invalidatedAtUptime
                && nowUptime - invalidatedAtUptime <= ACCESSIBILITY_TEXT_STALE_TTL_MS;
    }

    private List<Detection> cachedTextForFrame(
            int width, int height, long requestedScrollX, long requestedScrollY) {
        long nowUptime = SystemClock.uptimeMillis();
        TextDetectionSnapshot accessibilitySnapshot = cachedAccessibilityText;
        List<Detection> accessibility = isAccessibilityTextSnapshotFresh(
                nowUptime, accessibilitySnapshot.capturedAtUptimeMillis,
                accessibilityTextInvalidatedAtUptime)
                ? shiftTextSource(accessibilitySnapshot,
                        width, height, requestedScrollX, requestedScrollY)
                : Collections.emptyList();
        TextDetectionSnapshot ocrSnapshot = cachedOcrText;
        List<Detection> ocr = isOcrSnapshotFresh(
                nowUptime, ocrSnapshot.capturedAtUptimeMillis)
                ? shiftTextSource(ocrSnapshot,
                        width, height, requestedScrollX, requestedScrollY)
                : Collections.emptyList();
        return DetectionFusion.merge(Collections.emptyList(), accessibility, ocr);
    }

    private List<Detection> cachedQualityForFrame(
            int width, int height, long requestedScrollX, long requestedScrollY) {
        VisualDetectionSnapshot snapshot = cachedQualityVisual;
        boolean streaming = usesStreamingQualityPipeline(detectorConfig);
        long maximumAgeMs = streaming
                ? STREAMING_QUALITY_RESULT_TTL_MS : QUALITY_RESULT_TTL_MS;
        if (snapshot == VisualDetectionSnapshot.EMPTY
                || SystemClock.uptimeMillis() - snapshot.capturedAtUptimeMillis
                        > maximumAgeMs) {
            return Collections.emptyList();
        }
        long currentGeneration = motionGeneration.get();
        if (!isQualityCacheGenerationCurrent(snapshot.motionGeneration, currentGeneration)) {
            if (cachedQualityVisual == snapshot) cachedQualityVisual = VisualDetectionSnapshot.EMPTY;
            Log.i(TAG, "QUALITY_CACHE_REJECTED reason=motion-generation cacheGeneration="
                    + snapshot.motionGeneration + " currentGeneration=" + currentGeneration
                    + " detections=" + snapshot.detections.size());
            return Collections.emptyList();
        }
        return shiftDetectionSource(snapshot.detections,
                snapshot.width, snapshot.height, snapshot.scrollX, snapshot.scrollY,
                width, height, requestedScrollX, requestedScrollY);
    }

    static boolean isQualityCacheGenerationCurrent(
            long cacheMotionGeneration,
            long currentMotionGeneration) {
        return cacheMotionGeneration == currentMotionGeneration;
    }

    private static List<Detection> markQualityCoverage(List<Detection> detections) {
        if (detections == null || detections.isEmpty()) return Collections.emptyList();
        List<Detection> marked = new ArrayList<>(detections.size());
        for (Detection detection : detections) {
            if (detection == null) continue;
            marked.add(detection.withObservation(
                    Detection.ObservationSource.QUALITY_VISUAL,
                    detection.getGeometryQuality(),
                    detection.getAnchorKey()));
        }
        return Collections.unmodifiableList(marked);
    }

    private void publishTextLane(long epoch, String source) {
        publishTextLane(epoch, source, -1L, -1L, -1L);
    }

    private void publishTextLane(
            long epoch,
            String source,
            long expectedMotionGeneration,
            long expectedSceneGeneration) {
        publishTextLane(epoch, source, expectedMotionGeneration, expectedSceneGeneration, -1L);
    }

    private void publishTextLane(
            long epoch,
            String source,
            long expectedMotionGeneration,
            long expectedSceneGeneration,
            long expectedContentGeneration) {
        main.post(() -> {
            if (!isCurrentCapture(epoch) || overlay == null) return;
            if ((expectedMotionGeneration >= 0L
                    && motionGeneration.get() != expectedMotionGeneration)
                    || (expectedSceneGeneration >= 0L
                    && textSceneGeneration.get() != expectedSceneGeneration)
                    || (expectedContentGeneration >= 0L
                    && textContentGeneration.get() != expectedContentGeneration)) {
                Log.i(TAG, "TEXT_PUBLISH skipped=stale source=" + source);
                return;
            }
            int width = Math.max(1, latestCaptureWidth);
            int height = Math.max(1, latestCaptureHeight);
            ScrollPosition current = currentScrollPosition();
            List<Detection> detections = cachedTextForFrame(
                    width, height, current.scrollX, current.scrollY);
            String fingerprint = width + "x" + height + '@'
                    + current.scrollX + ',' + current.scrollY + '|'
                    + detectionFingerprint(detections, false);
            if (fingerprint.equals(lastPublishedTextFingerprint)) {
                skippedUnchangedTextPublishes++;
                return;
            }
            lastPublishedTextFingerprint = fingerprint;
            overlay.updateText(detections, width, height, 0, 0);
            long now = SystemClock.uptimeMillis();
            Log.i(TAG, "TEXT_PUBLISH source=" + source + " regions=" + detections.size()
                    + " unchangedSkipped=" + skippedUnchangedTextPublishes
                    + " afterMotionMs=" + (lastMotionUptime <= 0L
                            ? 0L : now - lastMotionUptime));
            skippedUnchangedTextPublishes = 0L;
        });
    }

    private static String detectionFingerprint(
            List<Detection> detections,
            boolean coarseGeometry) {
        if (detections == null || detections.isEmpty()) return "empty";
        List<String> parts = new ArrayList<>(detections.size());
        for (Detection detection : detections) {
            if (detection == null) continue;
            BBox box = detection.getBox();
            String anchor = detection.getAnchorKey();
            if (coarseGeometry && anchor != null && !anchor.isEmpty()) {
                parts.add(detection.getClassName() + '@' + anchor);
            } else {
                int x = coarseGeometry ? box.getX() / 32 : box.getX();
                int y = coarseGeometry ? box.getY() / 24 : box.getY();
                int width = coarseGeometry ? box.getWidth() / 32 : box.getWidth();
                int height = coarseGeometry ? box.getHeight() / 16 : box.getHeight();
                parts.add(detection.getClassName() + '@' + (anchor == null ? "" : anchor)
                        + ':' + x + ',' + y + ',' + width + ',' + height);
            }
        }
        Collections.sort(parts);
        return String.join(";", parts);
    }

    private static VisualTrackArbitrator.Result visualRenderTracks(
            List<TrackedObject> tracks) {
        return VisualTrackArbitrator.arbitrate(tracks);
    }

    private synchronized VisualGeometryDelta recordVisualGeometry(
            List<TrackedObject> tracks) {
        int matched = 0;
        int changed = 0;
        int maxCenterDeltaPx = 0;
        int maxSizeDeltaPx = 0;
        Map<Integer, BBox> current = new HashMap<>();
        if (tracks != null) {
            for (TrackedObject track : tracks) {
                if (track == null) continue;
                BBox box = track.getBox();
                current.put(track.getId(), box);
                BBox previous = lastPublishedVisualBoxes.get(track.getId());
                if (previous == null) continue;
                matched++;
                int centerDelta = Math.max(
                        Math.abs(box.getCenterX() - previous.getCenterX()),
                        Math.abs(box.getCenterY() - previous.getCenterY()));
                int sizeDelta = Math.max(
                        Math.abs(box.getWidth() - previous.getWidth()),
                        Math.abs(box.getHeight() - previous.getHeight()));
                maxCenterDeltaPx = Math.max(maxCenterDeltaPx, centerDelta);
                maxSizeDeltaPx = Math.max(maxSizeDeltaPx, sizeDelta);
                if (centerDelta > 1 || sizeDelta > 1) changed++;
            }
        }
        lastPublishedVisualBoxes.clear();
        lastPublishedVisualBoxes.putAll(current);
        return new VisualGeometryDelta(
                matched, changed, maxCenterDeltaPx, maxSizeDeltaPx);
    }

    private synchronized void clearVisualGeometryHistory() {
        lastPublishedVisualBoxes.clear();
    }

    private synchronized void offsetVisualGeometryHistory(int dx, int dy) {
        if (dx == 0 && dy == 0 || lastPublishedVisualBoxes.isEmpty()) return;
        for (Map.Entry<Integer, BBox> entry : lastPublishedVisualBoxes.entrySet()) {
            BBox box = entry.getValue();
            entry.setValue(new BBox(
                    box.getX() + dx,
                    box.getY() + dy,
                    box.getWidth(),
                    box.getHeight()));
        }
    }

    private List<Detection> shiftTextSource(
            TextDetectionSnapshot snapshot,
            int width,
            int height,
            long requestedScrollX,
            long requestedScrollY) {
        return shiftDetectionSource(snapshot.detections,
                snapshot.width, snapshot.height, snapshot.scrollX, snapshot.scrollY,
                width, height, requestedScrollX, requestedScrollY);
    }

    private List<Detection> shiftDetectionSource(
            List<Detection> source,
            int sourceWidth,
            int sourceHeight,
            long sourceScrollX,
            long sourceScrollY,
            int width,
            int height,
            long requestedScrollX,
            long requestedScrollY) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        float scaleX = width / (float) Math.max(1, sourceWidth);
        float scaleY = height / (float) Math.max(1, sourceHeight);
        Rect screen = screenBounds();
        float scrollScaleX = width / (float) Math.max(1, screen.width());
        float scrollScaleY = height / (float) Math.max(1, screen.height());
        int offsetX = Math.round(-(requestedScrollX - sourceScrollX) * scrollScaleX);
        int offsetY = Math.round(-(requestedScrollY - sourceScrollY) * scrollScaleY);
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
            Detection shiftedDetection = new Detection(
                    detection.getClassName(), detection.getCategory(),
                    detection.getConfidence(),
                    new BBox(left, top, Math.max(1, right - left), Math.max(1, bottom - top)),
                    detection.isNsfw(), detection.isExposed(), detection.getSource(),
                    detection.getGeometryQuality(), detection.getAnchorKey());
            if (detection.getTrackId() >= 0) shiftedDetection.setTrackId(detection.getTrackId());
            shifted.add(shiftedDetection);
        }
        return shifted;
    }

    private void maybeRequestOcr(
            Bitmap source,
            long epoch,
            long scrollX,
            long scrollY,
            long requestedMotionGeneration,
            DetectorConfig config) {
        TextSmutConfig currentTextConfig = textSmutConfig;
        ScheduledExecutorService callbackExecutor = ocrWorker;
        long now = SystemClock.uptimeMillis();
        if (!ocrEligibleForViewport(config,
                    currentTextConfig != null && currentTextConfig.isEnabled(),
                    firstFrameReported.get(), accessibilityTextCandidatesPresent)
                || screenshotText == null
                || callbackExecutor == null || callbackExecutor.isShutdown()
                || ocrDelayMs(now, lastOcrCompletionUptime, lastMotionUptime,
                        ocrConfirmationRequested.get()) > 0L
                || !ocrRunning.compareAndSet(false, true)) return;
        InferenceBitmapPreparer.Prepared prepared = InferenceBitmapPreparer.prepare(
                source, OCR_MAX_DIMENSION, false);
        if (prepared == null) {
            ocrRunning.set(false);
            return;
        }
        Bitmap ocrBitmap = prepared.bitmap;
        activeOcrBitmap.set(ocrBitmap);
        callbackExecutor.execute(() -> startOcrWhenVisualIdle(
                ocrBitmap, prepared.sourceWidth, prepared.sourceHeight,
                epoch, scrollX, scrollY, requestedMotionGeneration,
                currentTextConfig, callbackExecutor, now));
    }

    private void startOcrWhenVisualIdle(
            Bitmap ocrBitmap,
            int sourceWidth,
            int sourceHeight,
            long epoch,
            long scrollX,
            long scrollY,
            long requestedMotionGeneration,
            TextSmutConfig requestedTextConfig,
            ScheduledExecutorService callbackExecutor,
            long queuedAtUptime) {
        long now = SystemClock.uptimeMillis();
        if (!isOcrRequestCurrent(epoch, requestedMotionGeneration, requestedTextConfig)) {
            abandonQueuedOcr(ocrBitmap);
            return;
        }
        if (inferenceDraining.get() || pendingInference.get() != null) {
            if (now - queuedAtUptime >= OCR_VISUAL_IDLE_TIMEOUT_MS) {
                abandonQueuedOcr(ocrBitmap);
            } else {
                callbackExecutor.schedule(() -> startOcrWhenVisualIdle(
                                ocrBitmap, sourceWidth, sourceHeight,
                                epoch, scrollX, scrollY, requestedMotionGeneration,
                                requestedTextConfig, callbackExecutor, queuedAtUptime),
                        OCR_VISUAL_IDLE_RETRY_MS, TimeUnit.MILLISECONDS);
            }
            return;
        }
        long startedAtUptime = now;
        try {
            screenshotText.detect(ocrBitmap, requestedTextConfig,
                    sourceWidth, sourceHeight, callbackExecutor,
                    new OcrTextSmutDetector.Callback() {
                        @Override public void onComplete(List<Detection> detections) {
                            long completedAt = SystemClock.uptimeMillis();
                            boolean stale = !isOcrRequestCurrent(
                                    epoch, requestedMotionGeneration, requestedTextConfig);
                            int stableCount = 0;
                            try {
                                if (stale) {
                                    clearCachedOcr();
                                    return;
                                }
                                List<Detection> stable = ocrTextStabilizer.update(detections);
                                stableCount = stable.size();
                                cachedOcrText = new TextDetectionSnapshot(
                                        stable, sourceWidth, sourceHeight,
                                        scrollX, scrollY, completedAt);
                                ocrConfirmationRequested.set(
                                        stable.size() != detections.size());
                                publishTextLane(epoch, "ocr");
                            } finally {
                                DiagnosticsRepository.recordOcr(
                                        DIAGNOSTICS_MODE,
                                        completedAt - startedAtUptime, stale, stableCount);
                                finishOcr(ocrBitmap, completedAt);
                            }
                        }

                        @Override public void onFailure(Exception error) {
                            long completedAt = SystemClock.uptimeMillis();
                            finishOcr(ocrBitmap, completedAt);
                            Log.w(TAG, "Ultra screenshot OCR failed", error);
                        }
                    });
        } catch (RuntimeException error) {
            finishOcr(ocrBitmap, SystemClock.uptimeMillis());
            Log.w(TAG, "Could not start Ultra screenshot OCR", error);
        }
    }

    private boolean isOcrRequestCurrent(
            long epoch,
            long requestedMotionGeneration,
            TextSmutConfig requestedTextConfig) {
        return isCurrentCapture(epoch)
                && sameOcrViewport(requestedMotionGeneration, motionGeneration.get(),
                        accessibilityTextCandidatesPresent)
                && usesScreenshotOcr(detectorConfig)
                && requestedTextConfig == textSmutConfig;
    }

    private void abandonQueuedOcr(Bitmap bitmap) {
        lastOcrCompletionUptime = SystemClock.uptimeMillis();
        releaseOcrBitmap(bitmap);
        ocrRunning.set(false);
    }

    private void finishOcr(Bitmap bitmap, long completedAtUptime) {
        lastOcrCompletionUptime = completedAtUptime;
        releaseOcrBitmap(bitmap);
        ocrRunning.set(false);
    }

    static long ocrDelayMs(
            long nowUptime,
            long lastCompletionUptime,
            long lastMotionUptime,
            boolean confirmationRequested) {
        long interval = confirmationRequested
                ? OCR_CONFIRM_INTERVAL_MS : OCR_INTERVAL_MS;
        long cadenceWait = lastCompletionUptime <= 0L ? 0L
                : interval - (nowUptime - lastCompletionUptime);
        long motionWait = lastMotionUptime <= 0L ? 0L
                : OCR_MOTION_SETTLE_MS - (nowUptime - lastMotionUptime);
        return Math.max(0L, Math.max(cadenceWait, motionWait));
    }

    static boolean ocrEligibleForViewport(
            DetectorConfig config,
            boolean textEnabled,
            boolean firstVisualFramePublished,
            boolean accessibilityTextCandidatesPresent) {
        return usesScreenshotOcr(config) && textEnabled && firstVisualFramePublished
                && !accessibilityTextCandidatesPresent;
    }

    static boolean sameOcrViewport(
            long requestedMotionGeneration,
            long currentMotionGeneration,
            boolean accessibilityTextCandidatesPresent) {
        return requestedMotionGeneration == currentMotionGeneration
                && !accessibilityTextCandidatesPresent;
    }

    static boolean isOcrSnapshotFresh(long nowUptime, long capturedAtUptime) {
        return capturedAtUptime > 0L && nowUptime >= capturedAtUptime
                && nowUptime - capturedAtUptime <= OCR_RESULT_TTL_MS;
    }

    private void clearCachedOcr() {
        cachedOcrText = TextDetectionSnapshot.EMPTY;
        ocrTextStabilizer.clear();
        ocrConfirmationRequested.set(false);
    }

    private void invalidateOcrForMotion() {
        clearCachedOcr();
        accessibilityTextCandidatesPresent = false;
        DiagnosticsRepository.recordAccessibilityText(DIAGNOSTICS_MODE, 0, 0);
    }

    private void releaseOcrBitmap(Bitmap bitmap) {
        if (bitmap != null && activeOcrBitmap.compareAndSet(bitmap, null)
                && !bitmap.isRecycled()) bitmap.recycle();
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
        main.post(() -> {
            if (overlay != null) {
                // Accessibility deltas already move every box at event cadence. Detector
                // velocity in this mode is geometry noise, not missing viewport motion.
                overlay.setMaxExtrapolationMs(0f);
            }
        });
        configureAccessibilityCadence(config);
        textSmutConfig = settings.loadTextSmutConfig();
        textSceneGeneration.incrementAndGet();
        accessibilityCandidateScans.set(0);
        cancelPendingTextConfirmation();
        warmTextModels(config);
        if (!usesScreenshotOcr(config)) cachedOcrText = TextDetectionSnapshot.EMPTY;
        textRefreshRequested.set(true);
        if (detector != null) detector.setConfig(config);
        if (fastDetector != null) fastDetector.setConfig(fastDetectorConfig(config));
        if (tracker != null) tracker.setConfig(accessibilityTrackerConfig(config));
        PopupStormManager.get().reloadSettings(this);
        main.post(() -> {
            if (subliminalOverlay != null) subliminalOverlay.updateSettings();
            reevaluateRecognition();
            reevaluateSubliminals();
        });
    }

    private void warmTextModels(DetectorConfig config) {
        ScheduledExecutorService accessibilityExecutor = textWorker;
        if (accessibilityExecutor != null && !accessibilityExecutor.isShutdown()
                && usesSemanticTextModel(config) && smutTextClassifier != null) {
            accessibilityExecutor.execute(smutTextClassifier::warmSemanticModel);
        }
        ScheduledExecutorService currentOcrWorker = ocrWorker;
        if (currentOcrWorker != null && !currentOcrWorker.isShutdown()
                && usesScreenshotOcr(config) && screenshotText != null) {
            screenshotText.warmUp(currentOcrWorker);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handleAccessibilityEvent(event);
        } catch (RuntimeException error) {
            DiagnosticsRepository.fail(DIAGNOSTICS_MODE, error);
            Log.e(TAG, "Accessibility event failed", error);
        }
    }

    private void handleAccessibilityEvent(AccessibilityEvent event) {
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
                int viewportWidth = latestCaptureWidth;
                int viewportHeight = latestCaptureHeight;
                if (viewportWidth <= 1 || viewportHeight <= 1) {
                    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                    viewportWidth = Math.max(1, metrics.widthPixels);
                    viewportHeight = Math.max(1, metrics.heightPixels);
                }
                AccessibilityScrollMotionResolver.Motion rawMotion = scrollMotionResolver.resolve(
                        event, viewportWidth, viewportHeight);
                long scrollNow = SystemClock.uptimeMillis();
                long sourceTime = event.getEventTime();
                if (sourceTime <= 0L || sourceTime > scrollNow) sourceTime = scrollNow;
                long eventAgeMs = Math.max(0L, scrollNow - sourceTime);
                ScrollDeltaStabilizer.Result motion = scrollDeltaStabilizer.filter(
                        rawMotion.dx, rawMotion.dy, sourceTime, viewportWidth, viewportHeight,
                        rawMotion.authoritative());
                if (BuildConfig.DEBUG && scrollNow - lastScrollDiagnosticUptime >= 250L) {
                    lastScrollDiagnosticUptime = scrollNow;
                    Log.d(TAG, "Scroll event screen motion raw=" + rawMotion.dx + ','
                            + rawMotion.dy + " filtered=" + motion.dx + ',' + motion.dy);
                }
                traceScrollEvent(scrollNow, eventAgeMs, rawMotion.dx, rawMotion.dy,
                        motion.dx, motion.dy,
                        motion.authoritative ? "accessibility-authoritative"
                                : motion.rapidReversal ? "rapid-reversal"
                                : rawMotion.moved() && !motion.moved()
                                ? "direction-suppressed" : "accessibility",
                        rawMotion.evidence.name(), motion.adjustedPixels(), motion.amplified());
                if (motion.moved()) {
                    applyEventMotion(motion.dx, motion.dy, motion.authoritative);
                } else {
                    if (rawMotion.moved() && motionEstimator != null) {
                        // Prevent screenshot phase-correlation from applying a rejected producer
                        // correction while Accessibility direction is being confirmed.
                        motionEstimator.reset();
                    }
                    dwellTracker.onScroll();
                    // Keep screenshot motion as a fallback for custom views which omit deltas.
                    lastMotionUptime = SystemClock.uptimeMillis();
                    settledInferenceNeeded.set(true);
                    textRefreshRequested.set(true);
                    main.removeCallbacks(settledTextRefresh);
                    main.postDelayed(settledTextRefresh, SETTLED_SCROLL_REFRESH_MS);
                }
            }
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && recognitionActive && packageName.equals(foregroundPackage)) {
            if (!isTextRelevantContentChange(event.getContentChangeTypes())) return;
            int contentTypes = event.getContentChangeTypes();
            long contentChangedAt = SystemClock.uptimeMillis();
            lastTextContentChangeUptime = contentChangedAt;
            textContentGeneration.incrementAndGet();
            textContentEvents.incrementAndGet();
            textContentChangeTypes.updateAndGet(previous -> previous | contentTypes);
            textRefreshRequested.set(true);
            invalidateAccessibilityTextSnapshot();
            if (textContentBurstStartedUptime <= 0L) {
                textContentBurstStartedUptime = contentChangedAt;
            }
            long refreshDelay = contentTextRefreshDelayMs(
                    contentChangedAt, lastTextContentChangeUptime,
                    textContentBurstStartedUptime);
            if (refreshDelay > 0L) {
                main.removeCallbacks(contentTextRefresh);
                contentTextRefreshScheduled.set(true);
                main.postDelayed(contentTextRefresh, refreshDelay);
            } else if (contentTextRefreshScheduled.compareAndSet(false, true)) {
                main.post(contentTextRefresh);
            }
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && !packageName.equals(foregroundPackage)
                && syncForegroundFromActiveRoot(System.currentTimeMillis())) {
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
        ForegroundWindowResolver.Candidate liveWindow = resolveLiveApplicationWindow();
        String confirmedPackage = liveWindow == null || liveWindow.packageName.isEmpty()
                ? packageName : liveWindow.packageName;
        if (confirmedPackage.equals(foregroundPackage)) {
            if (!packageName.equals(confirmedPackage)) {
                Log.i(TAG, "FOREGROUND_HOLD eventPackage=" + packageName
                        + " protectedPackage=" + confirmedPackage);
            }
            return;
        }
        acceptForegroundPackage(confirmedPackage, System.currentTimeMillis());
    }

    static boolean isTextRelevantContentChange(int changeTypes) {
        if (changeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) return true;
        int relevant = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_INVALID
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED;
        return (changeTypes & relevant) != 0;
    }

    private boolean syncForegroundFromActiveRoot(long nowMillis) {
        ForegroundWindowResolver.Candidate liveWindow = resolveLiveApplicationWindow();
        String livePackage = liveWindow == null ? "" : liveWindow.packageName;
        if (livePackage.isEmpty() || livePackage.equals(foregroundPackage)) return false;
        acceptForegroundPackage(livePackage, nowMillis);
        return true;
    }

    private ForegroundWindowResolver.Candidate resolveLiveApplicationWindow() {
        List<ForegroundWindowResolver.Candidate> candidates = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                AccessibilityNodeInfo root = window.getRoot();
                try {
                    String packageName = root != null && root.getPackageName() != null
                            ? root.getPackageName().toString() : "";
                    candidates.add(new ForegroundWindowResolver.Candidate(
                            packageName, window.getId(), window.isActive(), window.isFocused(),
                            window.getLayer()));
                } finally {
                    if (root != null) root.recycle();
                }
            }
        }
        AppModeManager mode = new AppModeManager(this);
        ForegroundWindowResolver.Candidate selected = ForegroundWindowResolver.select(
                candidates, mode.inputMethodPackage(),
                recognitionActive ? foregroundPackage : "");
        if (selected != null) return selected;

        // Some OEMs briefly omit the interactive-window list during transitions. Keep the old
        // active-root fallback for that narrow gap; the next event/tick will retry the full list.
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            String packageName = root != null && root.getPackageName() != null
                    ? root.getPackageName().toString() : "";
            if (!AppModePolicy.shouldAcceptLiveForegroundPackage(
                    packageName, mode.inputMethodPackage())) return null;
            return new ForegroundWindowResolver.Candidate(
                    packageName, root.getWindowId(), true, true, 0);
        } finally {
            if (root != null) root.recycle();
        }
    }

    private void acceptForegroundPackage(String packageName, long now) {
        if (packageName == null || packageName.isEmpty()
                || packageName.equals(foregroundPackage)) return;
        accountForegroundUsage(now);
        captureEpoch.invalidate();
        foregroundPackage = packageName;
        foregroundSinceMillis = now;
        resetTextSnapshots();
        AppModeManager mode = new AppModeManager(this);
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
        reevaluateSubliminals();
        PopupStormManager.get().updateDetections(Collections.emptyList());
        if (enforceForegroundLimit(System.currentTimeMillis())) return;
        reevaluateRecognition();
    }

    private void resetTextSnapshots() {
        textSceneGeneration.incrementAndGet();
        cancelPendingTextConfirmation();
        textContentGeneration.incrementAndGet();
        textContentEvents.set(0);
        textContentChangeTypes.set(0);
        textContentStaleRetries.set(0);
        lastTextContentChangeUptime = 0L;
        textContentBurstStartedUptime = 0L;
        accessibilityTextInvalidatedAtUptime = 0L;
        main.removeCallbacks(contentTextRefresh);
        main.removeCallbacks(staleAccessibilityTextExpiry);
        contentTextRefreshScheduled.set(false);
        cacheAccessibilityText(TextDetectionSnapshot.EMPTY);
        clearCachedOcr();
        cachedQualityVisual = VisualDetectionSnapshot.EMPTY;
        qualityVisualStabilizer.clear();
        qualityConfirmationRequested.set(false);
        qualityConfirmationBurstUsed.set(false);
        accessibilityTextStabilizer.clear();
        accessibilityCandidateScans.set(0);
        lastPublishedTextFingerprint = "";
        skippedUnchangedTextPublishes = 0L;
        clearVisualGeometryHistory();
        accessibilityTextCandidatesPresent = false;
        textRefreshRequested.set(true);
        main.post(() -> {
            if (overlay != null) overlay.updateText(
                    Collections.emptyList(), latestCaptureWidth, latestCaptureHeight, 0, 0);
        });
    }

    private void recordCensoredTap(AccessibilityEvent event, String packageName) {
        if ((!recognitionActive && !ScreenCaptureService.isRunning())
                || !packageName.equals(foregroundPackage) || penance == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMatchedTapMillis < 500L) return;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean matched = matchesTapSource(event.getSource(), metrics, now);
        for (int index = 0; !matched && index < event.getRecordCount(); index++) {
            matched = matchesTapSource(event.getRecord(index).getSource(), metrics, now);
        }
        if (matched) {
            chargeCensoredTap(now);
        }
    }

    private void chargeCensoredTap(long nowMillis) {
        if (penance == null || nowMillis - lastMatchedTapMillis < 500L) return;
        lastMatchedTapMillis = nowMillis;
        int charged = penance.recordInfraction(PenanceInfraction.CENSORED_TAP, 1, nowMillis);
        PenanceChargeNotifier.show(this, penance,
                PenanceInfraction.CENSORED_TAP, charged, nowMillis);
    }

    private boolean matchesTapSource(
            AccessibilityNodeInfo source,
            android.util.DisplayMetrics metrics,
            long nowMillis) {
        if (source == null) return false;
        AccessibilityNodeInfo node = source;
        Rect bounds = new Rect();
        try {
            // A few custom views put the event source on an empty virtual child. Only in that
            // invalid-bounds case, try its immediate parents; valid unrelated child targets are
            // never broadened to a whole post, which keeps Like/Reply taps from false charging.
            for (int depth = 0; node != null && depth < 3; depth++) {
                bounds.setEmpty();
                node.getBoundsInScreen(bounds);
                if (!bounds.isEmpty()) {
                    return tapTracker.matchesClick(
                            bounds.left, bounds.top, bounds.right, bounds.bottom,
                            metrics.widthPixels, metrics.heightPixels, nowMillis);
                }
                AccessibilityNodeInfo parent = node.getParent();
                if (node != source) node.recycle();
                node = parent;
            }
            return false;
        } finally {
            if (node != null && node != source) node.recycle();
            source.recycle();
        }
    }

    private void accountForegroundUsage(long nowMillis) {
        long started = foregroundSinceMillis;
        foregroundSinceMillis = nowMillis;
        if (timers == null || started <= 0L || nowMillis <= started
                || !appTimerRuntimeActive(nowMillis)) return;
        AppModeManager mode = new AppModeManager(this);
        timers.recordUsage(foregroundPackage, nowMillis - started,
                mode.getTimerPackages(), nowMillis);
    }

    /** Returns true when the current foreground app was dismissed for a spent budget. */
    private boolean enforceForegroundLimit(long nowMillis) {
        if (timers == null || foregroundPackage.isEmpty()
                || !appTimerRuntimeActive(nowMillis)) {
            lastBlockedPackage = "";
            lastBlockedAtMillis = 0L;
            return false;
        }
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
        if (!repeated) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            stats.recordLimitIntervention();
        }
        Log.i(TAG, "Daily app limit enforced for " + blockedPackage + " (" + status + ")");
        if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            foregroundPackage = "";
            foregroundSinceMillis = 0L;
        }
        return true;
    }

    private boolean appTimerRuntimeActive(long nowMillis) {
        boolean armed = new AppModeManager(this).isEffectivelyArmed(nowMillis);
        boolean limitsEnabled = new FeatureModuleManager(this).isLimitsEnabled();
        return AppTimerRuntimePolicy.shouldRun(armed, limitsEnabled);
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

    private void reevaluateSubliminals() {
        if (!running || subliminalOverlay == null) return;
        boolean shouldRun = !HardcoreSettingsGuard.isSettingsPackage(foregroundPackage)
                && new AppModeManager(this).shouldShowSubliminal(foregroundPackage);
        subliminalOverlay.setEligible(shouldRun);
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
        // Hardcore/Sub mode, resolve the actual application window instead of trusting
        // getRootInActiveWindow(): once our concealment overlay is attached, some Android builds
        // report that overlay as the active root while navigating from App info to Storage.
        AccessibilityNodeInfo root = resolveHardcoreGuardApplicationRoot();
        if (root == null) root = getRootInActiveWindow();
        try {
            String activePackage = root != null && root.getPackageName() != null
                    ? root.getPackageName().toString() : expectedPackage;
            hardcoreSettingsGuard.refresh(activePackage, root);
        } finally {
            if (root != null) root.recycle();
        }
    }

    private AccessibilityNodeInfo resolveHardcoreGuardApplicationRoot() {
        List<AccessibilityWindowInfo> available = getWindows();
        if (available == null || available.isEmpty()) return null;
        AccessibilityNodeInfo bestRoot = null;
        int bestScore = Integer.MIN_VALUE;
        for (AccessibilityWindowInfo window : available) {
            if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                continue;
            }
            AccessibilityNodeInfo candidate = window.getRoot();
            if (candidate == null) continue;
            String packageName = candidate.getPackageName() == null
                    ? "" : candidate.getPackageName().toString();
            int score = (window.isActive() ? 1_000 : 0)
                    + (window.isFocused() ? 500 : 0)
                    + (HardcoreSettingsGuard.isSettingsPackage(packageName) ? 100 : 0)
                    + window.getLayer();
            if (score > bestScore) {
                if (bestRoot != null) bestRoot.recycle();
                bestRoot = candidate;
                bestScore = score;
            } else {
                candidate.recycle();
            }
        }
        return bestRoot;
    }

    private void queueHardcoreSettingsGuardRefresh(long delayMillis) {
        if (!hardcoreGuardRefreshQueued.compareAndSet(false, true)) return;
        main.postDelayed(settledHardcoreGuardRefresh, Math.max(0L, delayMillis));
    }

    private void activateRecognition() {
        if (recognitionActive || !running || worker == null) return;
        captureEpoch.invalidate();
        recognitionActive = true;
        droppedInferenceFrames.set(0L);
        Log.i(TAG, "Recognition activated for foreground package " + foregroundPackage);
        firstFrameReported.set(false);
        resetScrollCompensation();
        overlay = new OverlayController(
                this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        CensorAppearance appearance = settings.loadAppearance();
        overlayNeedsSourceFrame = appearance.requiresSourceFrame();
        overlay.setAppearance(appearance);
        DetectorConfig config = settings.loadDetectorConfig();
        overlay.setMaxExtrapolationMs(0f);
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
        ScheduledFuture<?> prioritySchedule = priorityCaptureSchedule;
        priorityCaptureSchedule = null;
        if (prioritySchedule != null) prioritySchedule.cancel(false);
        discardPendingInference();
        discardPendingQualityInference();
        DiagnosticsRepository.stop(DIAGNOSTICS_MODE);
        if (overlay != null) overlay.close();
        overlay = null;
        PopupStormManager.get().stop();
        dwellTracker.clear();
        tapTracker.clear();
        resetTextSnapshots();
        resetScrollCompensation();
    }

    private void resetScrollCompensation() {
        cancelPendingTextConfirmation();
        ScheduledFuture<?> prioritySchedule = priorityCaptureSchedule;
        priorityCaptureSchedule = null;
        if (prioritySchedule != null) prioritySchedule.cancel(false);
        synchronized (scrollStateLock) {
            cumulativeScrollX.set(0L);
            cumulativeScrollY.set(0L);
            pendingTrackerOffsetX.set(0L);
            pendingTrackerOffsetY.set(0L);
        }
        cacheAccessibilityText(TextDetectionSnapshot.EMPTY);
        clearCachedOcr();
        cachedQualityVisual = VisualDetectionSnapshot.EMPTY;
        qualityVisualStabilizer.clear();
        qualityConfirmationRequested.set(false);
        qualityConfirmationBurstUsed.set(false);
        accessibilityTextStabilizer.clear();
        accessibilityCandidateScans.set(0);
        lastPublishedTextFingerprint = "";
        skippedUnchangedTextPublishes = 0L;
        clearVisualGeometryHistory();
        accessibilityTextCandidatesPresent = false;
        settledInferenceNeeded.set(false);
        discardPendingInference();
        discardPendingQualityInference();
        motionGeneration.incrementAndGet();
        lastMotionUptime = 0L;
        lastInferenceUptime = 0L;
        lastQualityInferenceUptime = 0L;
        lastScreenshotRequestUptime = 0L;
        lastOcrCompletionUptime = 0L;
        if (motionEstimator != null) motionEstimator.reset();
        scrollMotionResolver.reset();
        scrollDeltaStabilizer.reset();
        main.removeCallbacks(settledScrollTrace);
        lastScrollTraceEventUptime = 0L;
        textRefreshRequested.set(true);
        main.removeCallbacks(settledTextRefresh);
    }

    private void configureAccessibilityCadence(DetectorConfig config) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        boolean ultra = config != null && config.getInferenceThreads() >= 4;
        info.notificationTimeout = ultra ? 0L : 16L;
        if (ultra) info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
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

    private static final class QualityInferenceFrame {
        private Bitmap sourceFrame;
        private HardwareBuffer sourceBuffer;
        private final long epoch;
        private final long scrollX;
        private final long scrollY;
        private final long motionGeneration;
        private final int sourceWidth;
        private final int sourceHeight;
        private final int inferenceResolution;
        private final long capturedAtUptimeMillis;

        private QualityInferenceFrame(
                Bitmap sourceFrame,
                HardwareBuffer sourceBuffer,
                long epoch,
                long scrollX,
                long scrollY,
                long motionGeneration,
                int sourceWidth,
                int sourceHeight,
                int inferenceResolution,
                long capturedAtUptimeMillis) {
            this.sourceFrame = sourceFrame;
            this.sourceBuffer = sourceBuffer;
            this.epoch = epoch;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.motionGeneration = motionGeneration;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.inferenceResolution = inferenceResolution;
            this.capturedAtUptimeMillis = capturedAtUptimeMillis;
        }

        private void recycle() {
            if (sourceFrame != null && !sourceFrame.isRecycled()) sourceFrame.recycle();
            sourceFrame = null;
            if (sourceBuffer != null) sourceBuffer.close();
            sourceBuffer = null;
        }
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
        private final boolean continuousMotionInference;
        private final boolean qualityRefine;
        private final boolean qualityConfirmation;
        private final long capturedAtUptimeMillis;

        private InferenceFrame(
                Bitmap frame,
                long epoch,
                long scrollX,
                long scrollY,
                long motionGeneration,
                int sourceWidth,
                int sourceHeight,
                boolean retainedSourceFrame,
                boolean continuousMotionInference,
                boolean qualityRefine,
                boolean qualityConfirmation,
                long capturedAtUptimeMillis) {
            this.frame = frame;
            this.epoch = epoch;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.motionGeneration = motionGeneration;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.retainedSourceFrame = retainedSourceFrame;
            this.continuousMotionInference = continuousMotionInference;
            this.qualityRefine = qualityRefine;
            this.qualityConfirmation = qualityConfirmation;
            this.capturedAtUptimeMillis = capturedAtUptimeMillis;
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

    private static final class VisualGeometryDelta {
        private final int matched;
        private final int changed;
        private final int maxCenterDeltaPx;
        private final int maxSizeDeltaPx;

        private VisualGeometryDelta(
                int matched,
                int changed,
                int maxCenterDeltaPx,
                int maxSizeDeltaPx) {
            this.matched = matched;
            this.changed = changed;
            this.maxCenterDeltaPx = maxCenterDeltaPx;
            this.maxSizeDeltaPx = maxSizeDeltaPx;
        }
    }

    private static class ScrollPosition {
        final long scrollX;
        final long scrollY;

        ScrollPosition(long scrollX, long scrollY) {
            this.scrollX = scrollX;
            this.scrollY = scrollY;
        }
    }

    private static final class TextDetectionSnapshot {
        private static final TextDetectionSnapshot EMPTY = new TextDetectionSnapshot(
                Collections.emptyList(), 1, 1, 0L, 0L, 0L);
        private final List<Detection> detections;
        private final int width;
        private final int height;
        private final long scrollX;
        private final long scrollY;
        private final long capturedAtUptimeMillis;

        private TextDetectionSnapshot(
                List<Detection> detections,
                int width,
                int height,
                long scrollX,
                long scrollY) {
            this(detections, width, height, scrollX, scrollY,
                    SystemClock.uptimeMillis());
        }

        private TextDetectionSnapshot(
                List<Detection> detections,
                int width,
                int height,
                long scrollX,
                long scrollY,
                long capturedAtUptimeMillis) {
            this.detections = detections == null ? Collections.emptyList() : detections;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.capturedAtUptimeMillis = Math.max(0L, capturedAtUptimeMillis);
        }
    }

    private static final class VisualDetectionSnapshot {
        private static final VisualDetectionSnapshot EMPTY = new VisualDetectionSnapshot(
                Collections.emptyList(), 1, 1, 0L, 0L, 0L, -1L);
        private final List<Detection> detections;
        private final int width;
        private final int height;
        private final long scrollX;
        private final long scrollY;
        private final long capturedAtUptimeMillis;
        private final long motionGeneration;

        private VisualDetectionSnapshot(
                List<Detection> detections,
                int width,
                int height,
                long scrollX,
                long scrollY,
                long capturedAtUptimeMillis,
                long motionGeneration) {
            this.detections = detections == null || detections.isEmpty()
                    ? Collections.emptyList() : new ArrayList<>(detections);
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.capturedAtUptimeMillis = Math.max(0L, capturedAtUptimeMillis);
            this.motionGeneration = motionGeneration;
        }
    }

    private static final class ScrollAlignment extends ScrollPosition {
        ScrollAlignment(long scrollX, long scrollY) {
            super(scrollX, scrollY);
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
        discardPendingQualityInference();
        if (qualityInferenceWorker != null) qualityInferenceWorker.shutdownNow();
        if (screenshotText != null) screenshotText.close();
        screenshotText = null;
        releaseOcrBitmap(activeOcrBitmap.get());
        ocrRunning.set(false);
        if (ocrWorker != null) ocrWorker.shutdownNow();
        if (textWorker != null) textWorker.shutdownNow();
        if (detector != null) detector.close();
        if (fastDetector != null) fastDetector.close();
        fastDetector = null;
        if (motionEstimator != null) motionEstimator.close();
        motionEstimator = null;
        if (hardcoreSettingsGuard != null) hardcoreSettingsGuard.clear();
        hardcoreSettingsGuard = null;
        if (subliminalOverlay != null) subliminalOverlay.close();
        subliminalOverlay = null;
        main.removeCallbacks(settledHardcoreGuardRefresh);
        hardcoreGuardRefreshQueued.set(false);
        main.removeCallbacks(settledTextRefresh);
        main.removeCallbacks(contentTextRefresh);
        main.removeCallbacks(staleAccessibilityTextExpiry);
        contentTextRefreshScheduled.set(false);
        dwellTracker.clear();
        tapTracker.clear();
        recognitionActive = false;
        super.onDestroy();
    }
}
