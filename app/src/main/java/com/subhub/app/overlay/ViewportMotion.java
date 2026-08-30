package com.subhub.app.overlay;

/**
 * Display-rate reconstruction of sparse Accessibility viewport measurements.
 *
 * <p>Accessibility scroll deltas describe motion that has already happened, but arrive roughly
 * every 100-120 ms. Rendering every delta as a new offset therefore produces a staircase. This
 * class treats each delta as a measurement of a continuous trajectory: every trustworthy sample
 * is applied immediately, and a bounded Hermite segment carries the observed velocity toward the
 * next sample.
 * If no next sample arrives, the lead returns to the last authoritative position.</p>
 */
final class ViewportMotion {
    private static final long MAX_SAMPLE_GAP_MS = 250L;
    private static final long DEFAULT_SAMPLE_GAP_MS = 114L;
    private static final long MIN_TRAJECTORY_MS = 72L;
    private static final long MAX_TRAJECTORY_MS = 180L;
    private static final long MIN_RETURN_MS = 64L;
    private static final long MAX_RETURN_MS = 150L;
    private static final long MIN_FALLBACK_MS = 8L;
    private static final long MAX_FALLBACK_MS = 16L;
    private static final long PRESENTATION_DEFAULT_INTERVAL_MS = 16L;
    private static final long PRESENTATION_MIN_HORIZON_MS = 12L;
    private static final long PRESENTATION_MAX_HORIZON_MS = 32L;
    private static final long PRESENTATION_MAX_GAP_MS = 56L;
    private static final long PRESENTATION_SETTLE_MS = 16L;
    private static final long FIRST_SAMPLE_HORIZON_MS = 24L;
    private static final long REVERSAL_HORIZON_MS = 16L;
    private static final long CONTINUING_HORIZON_MS = 36L;
    private static final float MAX_PREDICTION_SAMPLE_FRACTION = 0.36f;
    private static final float MAX_PREDICTION_VIEWPORT_FRACTION = 0.12f;
    private static final float MAX_CORRECTION_JUMP_PX = 56f;
    private static final float MAX_REVERSAL_JUMP_PX = 96f;
    private static final float SAME_DIRECTION_CORRECTION_FRACTION = 0.18f;
    private static final float REVERSAL_CORRECTION_FRACTION = 0.50f;
    private static final float MIN_PHASE_ERROR_PX = 96f;
    private static final float MAX_PHASE_ERROR_VIEWPORT_FRACTION = 0.08f;
    private static final float MIN_PREDICTABLE_DELTA_PX = 4f;
    private static final float MIN_POLLED_LEAD_PX = 96f;
    private static final float MAX_POLLED_LEAD_VIEWPORT_FRACTION = 0.45f;
    private static final int MIN_PHASE_LOCK_SAMPLES = 2;

    private final Axis x = new Axis();
    private final Axis y = new Axis();

    void reset(float x, float y, long nowMillis) {
        this.x.reset(x, nowMillis);
        this.y.reset(y, nowMillis);
    }

    /** Changes the detector coordinate origin without forgetting the live motion stream. */
    void rebase(float x, float y, long nowMillis) {
        this.x.rebase(x, nowMillis);
        this.y.rebase(y, nowMillis);
    }

    void addDelta(float dx, float dy, long nowMillis) {
        addDelta(dx, dy, nowMillis, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    void addDelta(
            float dx,
            float dy,
            long nowMillis,
            int viewportWidth,
            int viewportHeight,
            boolean authoritative) {
        x.addDelta(dx, nowMillis, viewportWidth, authoritative);
        y.addDelta(dy, nowMillis, viewportHeight, authoritative);
    }

    /** Adds a fast, presentation-only motion sample without changing authoritative coordinates. */
    void addPresentationDelta(
            float dx,
            float dy,
            long nowMillis,
            int viewportWidth,
            int viewportHeight) {
        x.addPresentationDelta(dx, nowMillis, viewportWidth);
        y.addPresentationDelta(dy, nowMillis, viewportHeight);
    }

    /** Brakes presentation prediction at a measured zero-motion sample. */
    void settlePresentation(long nowMillis) {
        x.settlePresentation(nowMillis);
        y.settlePresentation(nowMillis);
    }

    Position position(long nowMillis) {
        return new Position(x.position(nowMillis), y.position(nowMillis));
    }

    boolean isAnimating(long nowMillis) {
        return x.isAnimating(nowMillis) || y.isAnimating(nowMillis);
    }

    Position predictionAmplitude() {
        return new Position(x.predictionAmplitude(), y.predictionAmplitude());
    }

    long predictionPeakMillis() {
        return Math.max(x.trajectoryDuration, y.trajectoryDuration);
    }

    private static final class Axis {
        private float exact;
        private float segmentStart;
        private float segmentTarget;
        private float startVelocity;
        private long anchorTime;
        private long trajectoryDuration;
        private long returnDuration;
        private long lastEventTime;
        private float lastDelta;
        private boolean authoritativeTrajectory;
        private long lastPresentationSampleTime;
        private float pollMeasured;
        private float pollVelocity;
        private int consecutivePollSamples;
        private boolean pollMoving;

        void addPresentationDelta(float delta, long nowMillis, int viewportSize) {
            if (Math.abs(delta) < 0.5f) return;
            long gap = lastPresentationSampleTime <= 0L
                    ? Long.MAX_VALUE : nowMillis - lastPresentationSampleTime;
            boolean continuing = gap > 0L && gap <= PRESENTATION_MAX_GAP_MS;
            long interval = continuing ? gap : PRESENTATION_DEFAULT_INTERVAL_MS;
            if (continuing) {
                consecutivePollSamples++;
                pollMeasured += delta;
            } else {
                consecutivePollSamples = 1;
                // A new anchor baseline begins at the last coordinate-authoritative position,
                // not at a speculative event trajectory that may already be ahead of it.
                pollMeasured = exact + delta;
            }
            float maximumLead = Math.max(MIN_POLLED_LEAD_PX,
                    Math.max(1, viewportSize) * MAX_POLLED_LEAD_VIEWPORT_FRACTION);
            pollMeasured = clamp(pollMeasured, exact - maximumLead, exact + maximumLead);
            pollVelocity = delta / Math.max(1f, interval);

            // The page has already reached pollMeasured. Correct the estimator at mutation time;
            // the correction is first visible on the next vsync, so there is no extra 32 ms glide
            // behind a measurement. Subsequent frames continue the observed velocity until the
            // next 16 ms sample instead of standing still between samples.
            // This is a measured compositor phase, not a prediction. Showing a bounded fraction
            // of it recreates the very mid-transit lag the poller exists to remove. The robust
            // multi-anchor median owns presentation immediately; interpolation is only used to
            // continue its measured velocity until the next display-rate sample.
            segmentStart = pollMeasured;
            trajectoryDuration = Math.max(PRESENTATION_MIN_HORIZON_MS,
                    Math.min(PRESENTATION_MAX_HORIZON_MS, interval));
            segmentTarget = clamp(
                    pollMeasured + pollVelocity * trajectoryDuration,
                    exact - maximumLead, exact + maximumLead);
            startVelocity = pollVelocity;
            anchorTime = nowMillis;
            returnDuration = 0L;
            authoritativeTrajectory = false;
            lastPresentationSampleTime = nowMillis;
            pollMoving = true;
        }

        void settlePresentation(long nowMillis) {
            if (lastPresentationSampleTime <= 0L) return;
            float displayed = position(nowMillis);
            segmentStart = displayed;
            segmentTarget = pollMeasured;
            startVelocity = 0f;
            anchorTime = nowMillis;
            trajectoryDuration = Math.abs(segmentTarget - segmentStart) < 0.5f
                    ? 0L : PRESENTATION_SETTLE_MS;
            returnDuration = 0L;
            authoritativeTrajectory = false;
            lastPresentationSampleTime = nowMillis;
            pollVelocity = 0f;
            pollMoving = false;
        }

        void reset(float value, long nowMillis) {
            exact = value;
            segmentStart = value;
            segmentTarget = value;
            startVelocity = 0f;
            anchorTime = nowMillis;
            trajectoryDuration = 0L;
            returnDuration = 0L;
            lastEventTime = 0L;
            lastDelta = 0f;
            authoritativeTrajectory = false;
            lastPresentationSampleTime = 0L;
            pollMeasured = value;
            pollVelocity = 0f;
            consecutivePollSamples = 0;
            pollMoving = false;
        }

        void rebase(float value, long nowMillis) {
            float velocityBefore = velocity(nowMillis);
            boolean pollingLive = lastPresentationSampleTime > 0L
                    && nowMillis - lastPresentationSampleTime <= PRESENTATION_MAX_GAP_MS;
            exact = value;
            segmentStart = value;
            pollMeasured = value;
            if (pollingLive && pollMoving) {
                startVelocity = velocityBefore != 0f ? velocityBefore : pollVelocity;
                trajectoryDuration = PRESENTATION_DEFAULT_INTERVAL_MS;
                segmentTarget = value + startVelocity * trajectoryDuration;
            } else {
                segmentTarget = value;
                startVelocity = 0f;
                trajectoryDuration = 0L;
            }
            anchorTime = nowMillis;
            returnDuration = 0L;
            authoritativeTrajectory = false;
            // Keep lastEventTime, lastDelta, and lastPresentationSampleTime. Detector publication
            // is a coordinate rebase, not the end of the user's scroll gesture.
        }

        void addDelta(
                float delta,
                long nowMillis,
                int viewportSize,
                boolean authoritative) {
            // Accessibility callbacks can arrive after their event-source timestamp. Replay an
            // ordered sample at that source time so the next vsync observes the elapsed portion
            // of its trajectory. Never rewrite history when a producer delivers out of order.
            long sampleMillis = lastEventTime > 0L
                    ? Math.max(lastEventTime, nowMillis) : nowMillis;
            float velocityBefore = velocity(sampleMillis);
            float displayedBefore = position(sampleMillis);
            exact += delta;
            long gap = lastEventTime <= 0L
                    ? Long.MAX_VALUE : sampleMillis - lastEventTime;

            if (!authoritative || Math.abs(delta) < 0.5f) {
                segmentStart = displayedBefore;
                segmentTarget = exact;
                startVelocity = 0f;
                anchorTime = sampleMillis;
                trajectoryDuration = Math.max(MIN_FALLBACK_MS,
                        Math.min(MAX_FALLBACK_MS,
                                gap == Long.MAX_VALUE ? MAX_FALLBACK_MS
                                        : Math.round(gap * 0.15f)));
                returnDuration = 0L;
                authoritativeTrajectory = false;
                lastEventTime = sampleMillis;
                lastDelta = delta;
                return;
            }

            boolean livePolling = consecutivePollSamples >= MIN_PHASE_LOCK_SAMPLES
                    && lastPresentationSampleTime > 0L
                    && nowMillis - lastPresentationSampleTime <= PRESENTATION_MAX_GAP_MS;
            if (livePolling) {
                // The anchor ensemble has already presented this interval. Accessibility remains
                // coordinate authority for trackers/captures, but visually applying the same
                // displacement again creates the observed overshoot. Preserve the poll trajectory
                // while moving; once it has braked, converge any final measurement error in one
                // display frame.
                if (!pollMoving) {
                    segmentStart = displayedBefore;
                    segmentTarget = exact;
                    startVelocity = 0f;
                    anchorTime = sampleMillis;
                    trajectoryDuration = Math.abs(segmentTarget - segmentStart) < 0.5f
                            ? 0L : PRESENTATION_SETTLE_MS;
                    returnDuration = 0L;
                    authoritativeTrajectory = false;
                    pollMeasured = exact;
                }
                lastEventTime = sampleMillis;
                lastDelta = delta;
                return;
            }

            boolean firstSample = lastEventTime <= 0L || gap > MAX_SAMPLE_GAP_MS;
            boolean reversal = !firstSample && lastDelta != 0f
                    && Math.signum(lastDelta) != Math.signum(delta);
            // Accessibility is coordinate authority, but a sparse event can arrive after the
            // compositor has already moved several display frames. Correct the existing display
            // trajectory without turning the entire late measurement into a single-frame jump.
            float measurementError = exact - displayedBefore;
            float correctionFraction = reversal
                    ? REVERSAL_CORRECTION_FRACTION
                    : SAME_DIRECTION_CORRECTION_FRACTION;
            float correctionLimit = reversal
                    ? MAX_REVERSAL_JUMP_PX : MAX_CORRECTION_JUMP_PX;
            segmentStart = firstSample
                    ? exact
                    : displayedBefore + clamp(measurementError * correctionFraction,
                            -correctionLimit, correctionLimit);
            float maximumPhaseError = Math.max(MIN_PHASE_ERROR_PX,
                    Math.max(1, viewportSize) * MAX_PHASE_ERROR_VIEWPORT_FRACTION);
            segmentStart = clamp(segmentStart,
                    exact - maximumPhaseError, exact + maximumPhaseError);

            long observedGap = firstSample ? DEFAULT_SAMPLE_GAP_MS
                    : Math.max(1L, Math.min(MAX_SAMPLE_GAP_MS, gap));
            float predictedDelta = predictedNextDelta(
                    delta, firstSample, reversal, observedGap, viewportSize);
            segmentTarget = exact + predictedDelta;
            trajectoryDuration = Math.max(MIN_TRAJECTORY_MS,
                    Math.min(MAX_TRAJECTORY_MS, Math.round(observedGap * 1.28f)));
            returnDuration = Math.max(MIN_RETURN_MS,
                    Math.min(MAX_RETURN_MS, Math.round(observedGap * 0.90f)));

            float nominalVelocity = predictedDelta / Math.max(1L, trajectoryDuration);
            if (firstSample) {
                startVelocity = delta / Math.max(1f, observedGap) * 0.72f;
            } else if (reversal) {
                startVelocity = 0f;
            } else {
                float maximumVelocity = Math.max(Math.abs(nominalVelocity) * 1.8f, 0.25f);
                startVelocity = clamp(velocityBefore, -maximumVelocity, maximumVelocity);
                if (nominalVelocity != 0f
                        && Math.signum(startVelocity) != Math.signum(nominalVelocity)) {
                    startVelocity = 0f;
                }
            }
            anchorTime = sampleMillis;
            authoritativeTrajectory = true;
            lastEventTime = sampleMillis;
            lastDelta = delta;
            // This event, rather than the tentative single poll sample, owned presentation.
            // Re-anchor the poll estimator so later samples cannot continue from the pre-event
            // coordinate space and pull the overlay backward.
            pollMeasured = segmentStart;
            pollVelocity = 0f;
            consecutivePollSamples = 0;
            lastPresentationSampleTime = 0L;
            pollMoving = false;
        }

        float position(long nowMillis) {
            if (trajectoryDuration <= 0L) return segmentTarget;
            long age = Math.max(0L, nowMillis - anchorTime);
            if (age <= trajectoryDuration) {
                float progress = age / (float) trajectoryDuration;
                float endTangent = !authoritativeTrajectory && pollMoving
                        ? startVelocity * trajectoryDuration : 0f;
                float value = hermite(segmentStart, segmentTarget,
                        startVelocity * trajectoryDuration, endTangent, progress);
                return clampBetween(value, segmentStart, segmentTarget);
            }
            if (!authoritativeTrajectory || returnDuration <= 0L) return segmentTarget;
            long returnAge = age - trajectoryDuration;
            if (returnAge >= returnDuration) return exact;
            float progress = returnAge / (float) returnDuration;
            float eased = smootherStep(progress);
            return segmentTarget + (exact - segmentTarget) * eased;
        }

        float velocity(long nowMillis) {
            if (trajectoryDuration <= 0L) return 0f;
            long age = Math.max(0L, nowMillis - anchorTime);
            if (age < trajectoryDuration) {
                float progress = age / (float) trajectoryDuration;
                float endTangent = !authoritativeTrajectory && pollMoving
                        ? startVelocity * trajectoryDuration : 0f;
                float tangent = hermiteDerivative(segmentStart, segmentTarget,
                        startVelocity * trajectoryDuration, endTangent, progress);
                return tangent / trajectoryDuration;
            }
            if (!authoritativeTrajectory || returnDuration <= 0L
                    || age >= trajectoryDuration + returnDuration) return 0f;
            float progress = (age - trajectoryDuration) / (float) returnDuration;
            float derivative = smootherStepDerivative(progress);
            return (exact - segmentTarget) * derivative / returnDuration;
        }

        boolean isAnimating(long nowMillis) {
            if (trajectoryDuration <= 0L) return false;
            long total = trajectoryDuration
                    + (authoritativeTrajectory ? returnDuration : 0L);
            return nowMillis - anchorTime < total;
        }

        float predictionAmplitude() {
            return segmentTarget - exact;
        }

        private static float predictedNextDelta(
                float delta,
                boolean firstSample,
                boolean reversal,
                long observedGap,
                int viewportSize) {
            float magnitude = Math.abs(delta);
            if (magnitude < MIN_PREDICTABLE_DELTA_PX) return 0f;
            long horizon = firstSample ? FIRST_SAMPLE_HORIZON_MS
                    : reversal ? REVERSAL_HORIZON_MS : CONTINUING_HORIZON_MS;
            float predictedMagnitude = magnitude * horizon / Math.max(1f, observedGap);
            predictedMagnitude = Math.min(predictedMagnitude,
                    magnitude * MAX_PREDICTION_SAMPLE_FRACTION);
            float maximum = Math.max(24f,
                    Math.max(1, viewportSize) * MAX_PREDICTION_VIEWPORT_FRACTION);
            return Math.signum(delta) * Math.min(predictedMagnitude, maximum);
        }
    }

    private static float hermite(
            float start,
            float end,
            float startTangent,
            float endTangent,
            float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return (2f * t3 - 3f * t2 + 1f) * start
                + (t3 - 2f * t2 + t) * startTangent
                + (-2f * t3 + 3f * t2) * end
                + (t3 - t2) * endTangent;
    }

    private static float hermiteDerivative(
            float start,
            float end,
            float startTangent,
            float endTangent,
            float t) {
        float t2 = t * t;
        return (6f * t2 - 6f * t) * start
                + (3f * t2 - 4f * t + 1f) * startTangent
                + (-6f * t2 + 6f * t) * end
                + (3f * t2 - 2f * t) * endTangent;
    }

    private static float smootherStep(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static float smootherStepDerivative(float value) {
        float t = clamp(value, 0f, 1f);
        return 30f * t * t * (t * (t - 2f) + 1f);
    }

    private static float clampBetween(float value, float first, float second) {
        return clamp(value, Math.min(first, second), Math.max(first, second));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Position {
        final float x;
        final float y;

        Position(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
