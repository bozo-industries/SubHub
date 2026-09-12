package com.subhub.app.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serialized diagnostic adapter. No production camera, cache, tracker or renderer mutations. */
final class VisualCameraShadow {
    private final VisualScrollReconciler camera = new VisualScrollReconciler();
    private final Map<Long, Long> producerTimes = new LinkedHashMap<>();
    private RowMotionObserver.Scope scope;
    private long horizontalTime = -1L;

    synchronized void event(RowMotionObserver.Scope observedScope, long producer, long sourceTime,
            int screenDx, int screenDy, double initialEventY) {
        if (sourceTime < 0L || !acceptScope(observedScope, initialEventY)) return;
        if (screenDx == 0 && screenDy == 0) return;
        Long previous = producerTimes.get(producer);
        long start = producer != 0L && previous != null && previous < sourceTime ? previous : 0L;
        if (producer != 0L && (previous == null || sourceTime > previous)) {
            producerTimes.put(producer, sourceTime);
            while (producerTimes.size() > 16) producerTimes.remove(producerTimes.keySet().iterator().next());
        }
        if (screenDx != 0) horizontalTime = Math.max(horizontalTime, sourceTime);
        camera.recordEvent(start, sourceTime, -screenDy);
    }

    synchronized Result observe(RowMotionObserver.Scope observedScope, RowMotionObserver.Sample sample,
            int preparedHeight, double eventFrameY) {
        if (sample == null || preparedHeight <= 0 || !acceptScope(observedScope, eventFrameY)) {
            return new Result(false, false, true, false, 0, 0, 0);
        }
        boolean horizontal = horizontalTime > sample.previousTime && horizontalTime <= sample.currentTime;
        double screenDy = sample.dy * observedScope.sourceHeight / preparedHeight;
        VisualScrollReconciler.Measurement measurement = camera.observe(
                sample.previousTime, sample.currentTime, screenDy, sample.accepted && !horizontal);
        return new Result(true, measurement.accepted, camera.hasUnresolvedEventInterval(), horizontal,
                measurement.frameY, measurement.correctionY, camera.cameraY());
    }

    private boolean acceptScope(RowMotionObserver.Scope candidate, double initialY) {
        if (candidate == null || !candidate.valid() || !Double.isFinite(initialY)) return false;
        // A worker result from the old document must not reset a newer event scope backward.
        if (scope != null && (candidate.captureEpoch < scope.captureEpoch
                || candidate.document < scope.document)) return false;
        if (scope == null || !scope.matches(candidate)) {
            scope = candidate;
            camera.reset(initialY);
            producerTimes.clear();
            horizontalTime = -1L;
        }
        return true;
    }

    static final class Result {
        final boolean scopeValid, accepted, uncertain, horizontal;
        final double frameY, correctionY, cameraY;
        Result(boolean scopeValid, boolean accepted, boolean uncertain, boolean horizontal,
                double frameY, double correctionY, double cameraY) {
            this.scopeValid = scopeValid;
            this.accepted = accepted;
            this.uncertain = uncertain;
            this.horizontal = horizontal;
            this.frameY = frameY;
            this.correctionY = correctionY;
            this.cameraY = cameraY;
        }
    }
}
