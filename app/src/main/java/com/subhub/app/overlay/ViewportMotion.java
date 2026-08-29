package com.subhub.app.overlay;

/**
 * Exact viewport transform fed by Accessibility scroll deltas.
 *
 * API 34+ finger samples own between-event motion. This fallback never extrapolates beyond an
 * authoritative callback, so a stopped fling cannot reverse direction or retain prediction drift.
 */
final class ViewportMotion {
    private float authoritativeX;
    private float authoritativeY;

    void reset(float x, float y, long nowMillis) {
        authoritativeX = x;
        authoritativeY = y;
    }

    void addDelta(float dx, float dy, long nowMillis) {
        authoritativeX += dx;
        authoritativeY += dy;
    }

    Position position(long nowMillis) {
        return new Position(authoritativeX, authoritativeY);
    }

    boolean isAnimating(long nowMillis) {
        return false;
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
