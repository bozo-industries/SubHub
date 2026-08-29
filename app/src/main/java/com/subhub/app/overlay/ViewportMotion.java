package com.subhub.app.overlay;

/** Monotonic, bounded interpolation fed by stabilized Accessibility scroll deltas. */
final class ViewportMotion {
    private static final long MAX_SAMPLE_GAP_MS = 250L;
    // Accessibility reports where the viewport already moved. Keep just enough interpolation to
    // avoid a torn display frame, but never add multiple frames of visible censor lag.
    private static final long MIN_INTERPOLATION_MS = 8L;
    private static final long MAX_INTERPOLATION_MS = 16L;
    private static final long MIN_PHASE_CORRECTION_MS = 24L;
    private static final long MAX_PHASE_CORRECTION_MS = 40L;
    private static final long MIN_PREDICTION_PEAK_MS = 64L;
    private static final long MAX_PREDICTION_PEAK_MS = 180L;
    private static final float SAME_DIRECTION_LEAD = 0.78f;
    private static final float REVERSAL_LEAD = 0f;
    private static final float MAX_PREDICTION_VIEWPORT_FRACTION = 0.12f;

    private final Axis x = new Axis();
    private final Axis y = new Axis();

    void reset(float x, float y, long nowMillis) {
        this.x.reset(x, nowMillis);
        this.y.reset(y, nowMillis);
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
            boolean allowPrediction) {
        x.addDelta(dx, nowMillis, viewportWidth, allowPrediction);
        y.addDelta(dy, nowMillis, viewportHeight, allowPrediction);
    }

    Position position(long nowMillis) {
        return new Position(x.position(nowMillis), y.position(nowMillis));
    }

    boolean isAnimating(long nowMillis) {
        return x.isAnimating(nowMillis) || y.isAnimating(nowMillis);
    }

    Position predictionAmplitude() {
        return new Position(x.predictionAmplitude, y.predictionAmplitude);
    }

    long predictionPeakMillis() {
        return Math.max(x.predictionPeakDuration, y.predictionPeakDuration);
    }

    private static final class Axis {
        private float exact;
        private float interpolationStart;
        private long anchorTime;
        private long interpolationDuration;
        private boolean phaseCorrection;
        private long lastEventTime;
        private float lastDelta;
        private float predictionAmplitude;
        private long predictionPeakDuration;

        void reset(float value, long nowMillis) {
            exact = value;
            interpolationStart = value;
            anchorTime = nowMillis;
            interpolationDuration = 0L;
            phaseCorrection = false;
            lastEventTime = 0L;
            lastDelta = 0f;
            predictionAmplitude = 0f;
            predictionPeakDuration = 0L;
        }

        void addDelta(
                float delta,
                long nowMillis,
                int viewportSize,
                boolean allowPrediction) {
            float previouslyDisplayed = position(nowMillis);
            long previousPredictionAge = Math.max(0L, nowMillis - anchorTime);
            boolean correctingPrediction = predictionPeakDuration > 0L
                    && previousPredictionAge < predictionPeakDuration * 2L;
            exact += delta;
            long gap = lastEventTime <= 0L ? Long.MAX_VALUE : nowMillis - lastEventTime;
            if (Math.abs(delta) < 0.5f) {
                interpolationStart = exact;
                interpolationDuration = 0L;
                phaseCorrection = false;
            } else {
                interpolationStart = previouslyDisplayed;
                phaseCorrection = correctingPrediction;
                if (correctingPrediction && gap > 0L && gap <= MAX_SAMPLE_GAP_MS) {
                    interpolationDuration = Math.max(MIN_PHASE_CORRECTION_MS,
                            Math.min(MAX_PHASE_CORRECTION_MS, Math.round(gap * 0.40f)));
                } else {
                    interpolationDuration = lastEventTime <= 0L || gap > MAX_SAMPLE_GAP_MS
                            ? MAX_INTERPOLATION_MS
                            : Math.max(MIN_INTERPOLATION_MS,
                                    Math.min(MAX_INTERPOLATION_MS,
                                            Math.round(gap * 0.15f)));
                }
            }
            configurePrediction(delta, gap, viewportSize, allowPrediction);
            anchorTime = nowMillis;
            lastEventTime = nowMillis;
            lastDelta = delta;
        }

        float position(long nowMillis) {
            float interpolated = exact;
            if (interpolationDuration > 0L) {
                float progress = Math.min(1f,
                        Math.max(0f, (nowMillis - anchorTime)
                                / (float) interpolationDuration));
                // A predicted trajectory is already close to the new sample; correct its
                // residual linearly so the first post-event frame cannot absorb most of the
                // error. First observations still use a fast cubic catch-up.
                float inverse = 1f - progress;
                float eased = phaseCorrection
                        ? progress : 1f - inverse * inverse * inverse;
                interpolated = interpolationStart + (exact - interpolationStart) * eased;
            }
            if (predictionPeakDuration <= 0L) return interpolated;
            long age = Math.max(0L, nowMillis - anchorTime);
            float wave;
            if (age <= predictionPeakDuration) {
                // Accessibility deltas describe movement that already happened over the previous
                // sparse event gap. Continue that measured velocity linearly toward the expected
                // next sample instead of leaving the censor stationary for most of the interval.
                wave = age / (float) predictionPeakDuration;
            } else {
                float returnProgress = Math.min(1f,
                        (age - predictionPeakDuration) / (float) predictionPeakDuration);
                float remaining = 1f - returnProgress;
                // If no next event arrives, converge smoothly to authoritative position. The
                // amplitude is viewport-bounded so even an abrupt fling end cannot run away.
                wave = remaining * remaining;
            }
            return interpolated + predictionAmplitude * wave;
        }

        boolean isAnimating(long nowMillis) {
            long age = nowMillis - anchorTime;
            return interpolationDuration > 0L && age < interpolationDuration
                    || predictionPeakDuration > 0L
                    && age < predictionPeakDuration * 2L;
        }

        private void configurePrediction(
                float delta,
                long gap,
                int viewportSize,
                boolean allowPrediction) {
            predictionAmplitude = 0f;
            predictionPeakDuration = 0L;
            if (!allowPrediction || Math.abs(delta) < 0.5f || gap == Long.MAX_VALUE
                    || gap <= 0L || gap > MAX_SAMPLE_GAP_MS) return;
            float magnitude = Math.abs(delta);
            int direction = delta > 0f ? 1 : -1;
            int previousDirection = lastDelta > 0f ? 1 : lastDelta < 0f ? -1 : 0;
            boolean sameDirection = previousDirection == direction;
            float expectedMagnitude;
            if (sameDirection) {
                // Linear deceleration estimate: a shrinking final delta produces little or no
                // lead, while steady motion fills a bounded fraction of the next event gap.
                expectedMagnitude = Math.max(0f,
                        Math.min(magnitude, magnitude * 2f - Math.abs(lastDelta)));
            } else {
                expectedMagnitude = magnitude * 0.5f;
            }
            float leadFraction = sameDirection ? SAME_DIRECTION_LEAD : REVERSAL_LEAD;
            float maximum = Math.max(12f,
                    Math.max(1, viewportSize) * MAX_PREDICTION_VIEWPORT_FRACTION);
            predictionAmplitude = clamp(direction * expectedMagnitude * leadFraction,
                    -maximum, maximum);
            if (Math.abs(predictionAmplitude) < 0.5f) return;
            predictionPeakDuration = Math.max(MIN_PREDICTION_PEAK_MS,
                    Math.min(MAX_PREDICTION_PEAK_MS, gap));
        }
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
