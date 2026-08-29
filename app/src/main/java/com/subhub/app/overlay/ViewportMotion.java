package com.subhub.app.overlay;

/** Monotonic, bounded interpolation fed by stabilized Accessibility scroll deltas. */
final class ViewportMotion {
    private static final long MAX_SAMPLE_GAP_MS = 250L;
    // Accessibility reports where the viewport already moved. Keep just enough interpolation to
    // avoid a torn display frame, but never add multiple frames of visible censor lag.
    private static final long MIN_INTERPOLATION_MS = 8L;
    private static final long MAX_INTERPOLATION_MS = 16L;
    private static final long MIN_PREDICTION_WINDOW_MS = 64L;
    private static final long MAX_PREDICTION_WINDOW_MS = 240L;
    private static final float SAME_DIRECTION_LEAD = 0.32f;
    private static final float REVERSAL_LEAD = 0.16f;

    private final Axis x = new Axis();
    private final Axis y = new Axis();

    void reset(float x, float y, long nowMillis) {
        this.x.reset(x, nowMillis);
        this.y.reset(y, nowMillis);
    }

    void addDelta(float dx, float dy, long nowMillis) {
        x.addDelta(dx, nowMillis);
        y.addDelta(dy, nowMillis);
    }

    Position position(long nowMillis) {
        return new Position(x.position(nowMillis), y.position(nowMillis));
    }

    boolean isAnimating(long nowMillis) {
        return x.isAnimating(nowMillis) || y.isAnimating(nowMillis);
    }

    private static final class Axis {
        private float exact;
        private float interpolationStart;
        private long anchorTime;
        private long interpolationDuration;
        private long lastEventTime;
        private float lastDelta;
        private float predictionAmplitude;
        private long predictionDuration;

        void reset(float value, long nowMillis) {
            exact = value;
            interpolationStart = value;
            anchorTime = nowMillis;
            interpolationDuration = 0L;
            lastEventTime = 0L;
            lastDelta = 0f;
            predictionAmplitude = 0f;
            predictionDuration = 0L;
        }

        void addDelta(float delta, long nowMillis) {
            float previouslyDisplayed = position(nowMillis);
            exact += delta;
            long gap = lastEventTime <= 0L ? Long.MAX_VALUE : nowMillis - lastEventTime;
            if (Math.abs(delta) < 0.5f) {
                interpolationStart = exact;
                interpolationDuration = 0L;
            } else {
                interpolationStart = previouslyDisplayed;
                interpolationDuration = lastEventTime <= 0L || gap > MAX_SAMPLE_GAP_MS
                        ? MAX_INTERPOLATION_MS
                        : Math.max(MIN_INTERPOLATION_MS,
                                Math.min(MAX_INTERPOLATION_MS, Math.round(gap * 0.15f)));
            }
            configurePrediction(delta, gap);
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
                // Cubic ease-out reaches exact state quickly without crossing it.
                float inverse = 1f - progress;
                float eased = 1f - inverse * inverse * inverse;
                interpolated = interpolationStart + (exact - interpolationStart) * eased;
            }
            if (predictionDuration <= 0L) return interpolated;
            float predictionProgress = Math.min(1f,
                    Math.max(0f, (nowMillis - anchorTime) / (float) predictionDuration));
            // A finite sin² pulse reaches its lead at the expected next event, then returns to the
            // authoritative offset if no event arrives. This fills sparse-event gaps without
            // leaving permanent overshoot after scrolling stops.
            float wave = (float) Math.sin(Math.PI * predictionProgress);
            return interpolated + predictionAmplitude * wave * wave;
        }

        boolean isAnimating(long nowMillis) {
            long age = nowMillis - anchorTime;
            return interpolationDuration > 0L && age < interpolationDuration
                    || predictionDuration > 0L && age < predictionDuration;
        }

        private void configurePrediction(float delta, long gap) {
            predictionAmplitude = 0f;
            predictionDuration = 0L;
            if (Math.abs(delta) < 0.5f || gap == Long.MAX_VALUE
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
            predictionAmplitude = direction * expectedMagnitude * leadFraction;
            predictionDuration = Math.max(MIN_PREDICTION_WINDOW_MS,
                    Math.min(MAX_PREDICTION_WINDOW_MS, gap * 2L));
        }
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
