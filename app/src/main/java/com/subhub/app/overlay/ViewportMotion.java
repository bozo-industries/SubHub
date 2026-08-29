package com.subhub.app.overlay;

/** Monotonic, bounded interpolation fed by stabilized Accessibility scroll deltas. */
final class ViewportMotion {
    private static final long MAX_SAMPLE_GAP_MS = 250L;
    private static final long MIN_INTERPOLATION_MS = 24L;
    private static final long MAX_INTERPOLATION_MS = 56L;

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

        void reset(float value, long nowMillis) {
            exact = value;
            interpolationStart = value;
            anchorTime = nowMillis;
            interpolationDuration = 0L;
            lastEventTime = 0L;
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
                                Math.min(MAX_INTERPOLATION_MS, Math.round(gap * 0.55f)));
            }
            anchorTime = nowMillis;
            lastEventTime = nowMillis;
        }

        float position(long nowMillis) {
            if (interpolationDuration <= 0L) return exact;
            float progress = Math.min(1f,
                    Math.max(0f, (nowMillis - anchorTime) / (float) interpolationDuration));
            // Cubic ease-out reaches exact state quickly without crossing it.
            float inverse = 1f - progress;
            float eased = 1f - inverse * inverse * inverse;
            return interpolationStart + (exact - interpolationStart) * eased;
        }

        boolean isAnimating(long nowMillis) {
            return interpolationDuration > 0L
                    && nowMillis - anchorTime < interpolationDuration;
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
