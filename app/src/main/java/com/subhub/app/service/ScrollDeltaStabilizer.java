package com.subhub.app.service;

/**
 * Rejects isolated direction reversals and implausibly large jumps in Accessibility scroll data.
 *
 * <p>Some RecyclerView producers emit correction records whose sign is opposite to the visible
 * gesture. Applying each record literally makes an otherwise fast overlay bounce. Initial motion
 * is accepted immediately; a genuine reversal is accepted after two consecutive meaningful
 * samples and reconciles the initially held displacement. Strictly alternating rapid input enters
 * a pass-through burst after four samples, so deliberate up/down jitter cannot accumulate a large
 * one-direction error. A gesture gap clears direction so the first sample of a deliberate new
 * gesture is applied immediately instead of being withheld and folded into an oversized second
 * sample.</p>
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
        return new Result(rawDx, rawDy, dx, dy,
                x.wasRapidReversalOutput() || y.wasRapidReversalOutput());
    }

    synchronized void reset() {
        x.reset();
        y.reset();
        lastSampleUptime = 0L;
    }

    private static final class Axis {
        private int direction;
        private int oppositeCount;
        private int pendingOpposite;
        private int lastRawDirection;
        private int alternatingTransitions;
        private int alternatingDebt;
        private boolean rapidReversalMode;
        private int rapidSameDirectionSamples;
        private boolean rapidReversalOutput;

        int filter(int raw, int limit) {
            rapidReversalOutput = false;
            int clamped = clamp(raw, -limit, limit);
            int magnitude = Math.abs(clamped);
            if (magnitude < MEANINGFUL_DELTA_PX) {
                return direction != 0 && (clamped == 0
                        || Integer.signum(clamped) == direction) ? clamped : 0;
            }
            int sign = Integer.signum(clamped);
            boolean rawReversed = lastRawDirection != 0 && sign != lastRawDirection;
            if (rawReversed) {
                alternatingTransitions++;
                rapidSameDirectionSamples = 1;
            } else {
                alternatingTransitions = 0;
                alternatingDebt = 0;
                rapidSameDirectionSamples++;
            }
            lastRawDirection = sign;

            if (rapidReversalMode) {
                rapidReversalOutput = true;
                direction = sign;
                oppositeCount = 0;
                pendingOpposite = 0;
                if (!rawReversed && rapidSameDirectionSamples >= 3) {
                    rapidReversalMode = false;
                    alternatingTransitions = 0;
                    alternatingDebt = 0;
                }
                return clamped;
            }

            if (alternatingTransitions >= 3) {
                // The previous samples proved that the held opposite records were deliberate
                // motion, not one producer correction. Reconcile that debt once, then follow the
                // burst literally. ViewportMotion spreads this correction across display frames.
                int reconciled = clamp(clamped + alternatingDebt, -limit, limit);
                rapidReversalMode = true;
                rapidReversalOutput = true;
                direction = sign;
                oppositeCount = 0;
                pendingOpposite = 0;
                alternatingDebt = 0;
                return reconciled;
            }

            if (direction == 0) {
                direction = sign;
                oppositeCount = 0;
                pendingOpposite = 0;
                return clamped;
            }
            if (direction != 0 && sign == direction) {
                oppositeCount = 0;
                pendingOpposite = 0;
                return clamped;
            }
            oppositeCount++;
            if (oppositeCount < 2) {
                pendingOpposite = clamped;
                alternatingDebt += clamped;
                return 0;
            }

            direction = sign;
            oppositeCount = 0;
            int confirmed = clamp(pendingOpposite + clamped, -limit, limit);
            pendingOpposite = 0;
            return confirmed;
        }

        boolean wasRapidReversalOutput() {
            return rapidReversalOutput;
        }

        void startSession() {
            direction = 0;
            oppositeCount = 0;
            pendingOpposite = 0;
            lastRawDirection = 0;
            alternatingTransitions = 0;
            alternatingDebt = 0;
            rapidReversalMode = false;
            rapidSameDirectionSamples = 0;
            rapidReversalOutput = false;
        }

        void reset() {
            startSession();
        }
    }

    static final class Result {
        final int rawDx;
        final int rawDy;
        final int dx;
        final int dy;
        final boolean rapidReversal;

        Result(int rawDx, int rawDy, int dx, int dy, boolean rapidReversal) {
            this.rawDx = rawDx;
            this.rawDy = rawDy;
            this.dx = dx;
            this.dy = dy;
            this.rapidReversal = rapidReversal;
        }

        boolean moved() { return dx != 0 || dy != 0; }
        boolean changed() { return rawDx != dx || rawDy != dy; }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
