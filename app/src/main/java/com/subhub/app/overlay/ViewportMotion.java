package com.subhub.app.overlay;

/**
 * Display-rate viewport transform fed by sparse Accessibility scroll deltas.
 *
 * The authoritative offset remains event based. A short velocity coast fills the gaps between
 * events, then converges to that authoritative value so a stopped scroll cannot drift forever.
 */
final class ViewportMotion {
    private static final float VELOCITY_SMOOTHING = 0.72f;
    private static final float MAX_VELOCITY_PER_MS = 4f;
    private static final float EVENT_CORRECTION_RETAINED = 0.30f;
    private static final long COAST_MILLIS = 32L;
    private static final long SETTLE_MILLIS = 96L;

    private float authoritativeX;
    private float authoritativeY;
    private float correctionX;
    private float correctionY;
    private float velocityX;
    private float velocityY;
    private long updatedAtMillis;
    private boolean hasPreviousDelta;

    void reset(float x, float y, long nowMillis) {
        authoritativeX = x;
        authoritativeY = y;
        correctionX = 0f;
        correctionY = 0f;
        velocityX = 0f;
        velocityY = 0f;
        updatedAtMillis = nowMillis;
        hasPreviousDelta = false;
    }

    void addDelta(float dx, float dy, long nowMillis) {
        Position before = position(nowMillis);
        long elapsed = Math.max(1L, nowMillis - updatedAtMillis);
        authoritativeX += dx;
        authoritativeY += dy;
        if (hasPreviousDelta && elapsed <= 250L) {
            float measuredX = clamp(dx / elapsed, -MAX_VELOCITY_PER_MS, MAX_VELOCITY_PER_MS);
            float measuredY = clamp(dy / elapsed, -MAX_VELOCITY_PER_MS, MAX_VELOCITY_PER_MS);
            velocityX = velocityX * (1f - VELOCITY_SMOOTHING)
                    + measuredX * VELOCITY_SMOOTHING;
            velocityY = velocityY * (1f - VELOCITY_SMOOTHING)
                    + measuredY * VELOCITY_SMOOTHING;
            correctionX = (before.x - authoritativeX) * EVENT_CORRECTION_RETAINED;
            correctionY = (before.y - authoritativeY) * EVENT_CORRECTION_RETAINED;
        } else {
            velocityX = 0f;
            velocityY = 0f;
            correctionX = 0f;
            correctionY = 0f;
        }
        hasPreviousDelta = true;
        updatedAtMillis = nowMillis;
    }

    Position position(long nowMillis) {
        long age = Math.max(0L, nowMillis - updatedAtMillis);
        float correctionDecay = 1f - Math.min(1f, age / (float) SETTLE_MILLIS);
        float coast;
        if (age <= COAST_MILLIS) {
            coast = age;
        } else {
            float settle = Math.min(1f, (age - COAST_MILLIS) / (float) SETTLE_MILLIS);
            coast = COAST_MILLIS * (1f - settle);
        }
        return new Position(
                authoritativeX + correctionX * correctionDecay + velocityX * coast,
                authoritativeY + correctionY * correctionDecay + velocityY * coast);
    }

    boolean isAnimating(long nowMillis) {
        if (nowMillis - updatedAtMillis >= COAST_MILLIS + SETTLE_MILLIS) return false;
        return Math.abs(correctionX) >= 0.1f || Math.abs(correctionY) >= 0.1f
                || Math.abs(velocityX) >= 0.005f || Math.abs(velocityY) >= 0.005f;
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
