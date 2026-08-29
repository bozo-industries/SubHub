package com.subhub.app.service;

/**
 * Rejects isolated direction reversals and implausibly large jumps in Accessibility scroll data.
 *
 * <p>Some RecyclerView producers emit correction records whose sign is opposite to the visible
 * gesture. Applying each record literally makes an otherwise fast overlay bounce. Initial motion
 * is accepted immediately; a genuine reversal is accepted after two consecutive meaningful
 * samples without replaying the held sample as one oversized jump. Direction persists across
 * gesture gaps so repeated scrolling in the same direction remains immediate.</p>
 */
final class ScrollDeltaStabilizer {
    private static final long SESSION_GAP_MS = 250L;
    private static final int MEANINGFUL_DELTA_PX = 8;

    private final Axis x = new Axis();
    private final Axis y = new Axis();
    private long lastSampleUptime;

    synchronized Result filter(
            int rawDx,
            int rawDy,
            long nowUptime,
            int viewportWidth,
            int viewportHeight) {
        if (lastSampleUptime <= 0L || nowUptime - lastSampleUptime > SESSION_GAP_MS) {
            x.startSession();
            y.startSession();
        }
        lastSampleUptime = nowUptime;
        int dx = x.filter(rawDx, Math.max(32, Math.max(1, viewportWidth) / 2));
        int dy = y.filter(rawDy, Math.max(32, Math.max(1, viewportHeight) / 2));
        return new Result(rawDx, rawDy, dx, dy);
    }

    synchronized void reset() {
        x.reset();
        y.reset();
        lastSampleUptime = 0L;
    }

    private static final class Axis {
        private int direction;
        private int oppositeCount;

        int filter(int raw, int limit) {
            int clamped = clamp(raw, -limit, limit);
            int magnitude = Math.abs(clamped);
            if (magnitude < MEANINGFUL_DELTA_PX) {
                return direction != 0 && (clamped == 0
                        || Integer.signum(clamped) == direction) ? clamped : 0;
            }
            int sign = Integer.signum(clamped);
            if (direction == 0) {
                direction = sign;
                oppositeCount = 0;
                return clamped;
            }
            if (direction != 0 && sign == direction) {
                oppositeCount = 0;
                return clamped;
            }
            oppositeCount++;
            if (oppositeCount < 2) return 0;

            direction = sign;
            oppositeCount = 0;
            // The first opposite sample was held only as evidence. Replaying it now would turn
            // two normally spaced scroll records into one visible double-sized jump.
            return clamped;
        }

        void startSession() {
            oppositeCount = 0;
        }

        void reset() {
            direction = 0;
            oppositeCount = 0;
        }
    }

    static final class Result {
        final int rawDx;
        final int rawDy;
        final int dx;
        final int dy;

        Result(int rawDx, int rawDy, int dx, int dy) {
            this.rawDx = rawDx;
            this.rawDy = rawDy;
            this.dx = dx;
            this.dy = dy;
        }

        boolean moved() { return dx != 0 || dy != 0; }
        boolean changed() { return rawDx != dx || rawDy != dy; }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
