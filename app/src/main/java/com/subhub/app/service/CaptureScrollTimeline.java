package com.subhub.app.service;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Resolves the viewport phase at the hardware screenshot timestamp. */
final class CaptureScrollTimeline {
    private static final int MAX_SAMPLES = 96;

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    synchronized void record(
            long uptimeMillis,
            long scrollX,
            long scrollY,
            long motionGeneration) {
        samples.addLast(new Sample(uptimeMillis, scrollX, scrollY, motionGeneration));
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
    }

    synchronized Phase resolve(
            long screenshotUptimeMillis,
            long requestedAtUptimeMillis,
            long requestedScrollX,
            long requestedScrollY,
            long requestedGeneration) {
        Iterator<Sample> iterator = samples.descendingIterator();
        while (iterator.hasNext()) {
            Sample sample = iterator.next();
            if (sample.uptimeMillis > screenshotUptimeMillis) continue;
            if (sample.uptimeMillis < requestedAtUptimeMillis) break;
            return new Phase(sample.scrollX, sample.scrollY, sample.motionGeneration,
                    screenshotUptimeMillis, true);
        }
        return new Phase(requestedScrollX, requestedScrollY, requestedGeneration,
                screenshotUptimeMillis, false);
    }

    synchronized void clear() {
        samples.clear();
    }

    private static final class Sample {
        private final long uptimeMillis;
        private final long scrollX;
        private final long scrollY;
        private final long motionGeneration;

        private Sample(
                long uptimeMillis,
                long scrollX,
                long scrollY,
                long motionGeneration) {
            this.uptimeMillis = uptimeMillis;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.motionGeneration = motionGeneration;
        }
    }

    static final class Phase {
        final long scrollX;
        final long scrollY;
        final long motionGeneration;
        final long screenshotUptimeMillis;
        final boolean resolvedFromMotion;

        private Phase(
                long scrollX,
                long scrollY,
                long motionGeneration,
                long screenshotUptimeMillis,
                boolean resolvedFromMotion) {
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.motionGeneration = motionGeneration;
            this.screenshotUptimeMillis = screenshotUptimeMillis;
            this.resolvedFromMotion = resolvedFromMotion;
        }
    }
}
