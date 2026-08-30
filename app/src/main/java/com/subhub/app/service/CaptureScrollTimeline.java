package com.subhub.app.service;

import java.util.ArrayDeque;

/** Resolves event-sourced viewport state at the hardware screenshot timestamp. */
final class CaptureScrollTimeline {
    private static final int MAX_SAMPLES = 192;

    private final ArrayDeque<Motion> motions = new ArrayDeque<>();
    private long lastEffectiveUptimeMillis;

    synchronized void record(
            long effectiveUptimeMillis,
            long receivedUptimeMillis,
            int contentDx,
            int contentDy,
            long motionGeneration) {
        long effective = Math.max(0L, effectiveUptimeMillis);
        long received = Math.max(effective, receivedUptimeMillis);
        boolean outOfOrder = lastEffectiveUptimeMillis > 0L
                && effective < lastEffectiveUptimeMillis;
        motions.addLast(new Motion(effective, received, contentDx, contentDy,
                motionGeneration, outOfOrder));
        lastEffectiveUptimeMillis = Math.max(lastEffectiveUptimeMillis, effective);
        while (motions.size() > MAX_SAMPLES) motions.removeFirst();
    }

    synchronized Phase resolve(
            long screenshotUptimeMillis,
            long requestedAtUptimeMillis,
            long requestedScrollX,
            long requestedScrollY,
            long requestedGeneration) {
        long scrollX = requestedScrollX;
        long scrollY = requestedScrollY;
        long resolvedGeneration = requestedGeneration;
        long maximumDeliveryDelayMs = 0L;
        boolean resolved = false;
        boolean uncertain = false;
        for (Motion motion : motions) {
            // Generation says whether the request-time snapshot already incorporated this delta.
            // Effective time says whether the pixels had incorporated it when Android captured
            // the hardware buffer. This remains correct even when delivery crosses the request.
            if (motion.motionGeneration <= requestedGeneration
                    || motion.effectiveUptimeMillis > screenshotUptimeMillis) continue;
            scrollX += motion.contentDx;
            scrollY += motion.contentDy;
            resolvedGeneration = Math.max(resolvedGeneration, motion.motionGeneration);
            maximumDeliveryDelayMs = Math.max(maximumDeliveryDelayMs,
                    motion.receivedUptimeMillis - motion.effectiveUptimeMillis);
            uncertain |= motion.outOfOrder;
            resolved = true;
        }
        return new Phase(scrollX, scrollY, resolvedGeneration,
                screenshotUptimeMillis, resolved, uncertain, maximumDeliveryDelayMs,
                requestedAtUptimeMillis);
    }

    synchronized void clear() {
        motions.clear();
        lastEffectiveUptimeMillis = 0L;
    }

    private static final class Motion {
        private final long effectiveUptimeMillis;
        private final long receivedUptimeMillis;
        private final int contentDx;
        private final int contentDy;
        private final long motionGeneration;
        private final boolean outOfOrder;

        private Motion(
                long effectiveUptimeMillis,
                long receivedUptimeMillis,
                int contentDx,
                int contentDy,
                long motionGeneration,
                boolean outOfOrder) {
            this.effectiveUptimeMillis = effectiveUptimeMillis;
            this.receivedUptimeMillis = receivedUptimeMillis;
            this.contentDx = contentDx;
            this.contentDy = contentDy;
            this.motionGeneration = motionGeneration;
            this.outOfOrder = outOfOrder;
        }
    }

    static final class Phase {
        final long scrollX;
        final long scrollY;
        final long motionGeneration;
        final long screenshotUptimeMillis;
        final boolean resolvedFromMotion;
        final boolean phaseUncertain;
        final long maximumDeliveryDelayMs;
        final long requestedAtUptimeMillis;

        private Phase(
                long scrollX,
                long scrollY,
                long motionGeneration,
                long screenshotUptimeMillis,
                boolean resolvedFromMotion,
                boolean phaseUncertain,
                long maximumDeliveryDelayMs,
                long requestedAtUptimeMillis) {
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.motionGeneration = motionGeneration;
            this.screenshotUptimeMillis = screenshotUptimeMillis;
            this.resolvedFromMotion = resolvedFromMotion;
            this.phaseUncertain = phaseUncertain;
            this.maximumDeliveryDelayMs = Math.max(0L, maximumDeliveryDelayMs);
            this.requestedAtUptimeMillis = Math.max(0L, requestedAtUptimeMillis);
        }
    }
}
