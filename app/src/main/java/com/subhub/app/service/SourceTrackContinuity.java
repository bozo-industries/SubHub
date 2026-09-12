package com.subhub.app.service;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;
import com.subhub.app.detection.TrackedObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Source-to-source association aid. Does not estimate pixel time or the current display camera. */
final class SourceTrackContinuity {
    private SpatialRegionCache.Frame previous;
    private long previousSourceX, previousSourceY;

    synchronized void clear() { previous = null; }

    /** Commit only after tracker.update succeeds; an unknown update breaks the image chain. */
    synchronized void record(SpatialRegionCache.Frame frame, long sourceX, long sourceY) {
        previous = frame != null && frame.result.pose != null ? frame : null;
        previousSourceX = sourceX;
        previousSourceY = sourceY;
    }

    /** Pure proposal: caller invalidates the old basis before mutation, then records successful update. */
    synchronized Map<Integer, Integer> corrections(SpatialRegionCache.Frame frame,
            long sourceX, long sourceY, long oldTrackerY, long nextTrackerY,
            int dx, int dy, int width, int height, List<TrackedObject> tracks,
            List<Detection> alignedDetections, float minimumConfidence) {
        if (frame == null || !frame.sameMapAs(previous) || frame.id <= previous.id
                || frame.receipt < previous.receipt || sourceX != previousSourceX || dx != 0
                || width != frame.scope.sourceWidth || height != frame.scope.sourceHeight) {
            return Collections.emptyMap();
        }
        double scale = height / (double) frame.viewportHeight;
        double correction = ((double) sourceY - previousSourceY) * scale
                - (frame.result.pose.sourceY - previous.result.pose.sourceY);
        if (!Double.isFinite(correction) || Math.abs(correction) > height / 3d) return Collections.emptyMap();
        int extra = (int) Math.round(correction);
        if (extra == 0 || (long) dy + extra < Integer.MIN_VALUE || (long) dy + extra > Integer.MAX_VALUE) {
            return Collections.emptyMap();
        }
        double previousTop = previous.top + ((double) previousSourceY - oldTrackerY) * scale;
        double previousBottom = previous.bottom + ((double) previousSourceY - oldTrackerY) * scale;
        double currentTop = frame.top + ((double) sourceY - nextTrackerY) * scale;
        double currentBottom = frame.bottom + ((double) sourceY - nextTrackerY) * scale;
        Map<Integer, Integer> candidates = new HashMap<>();
        Map<Integer, Integer> uses = new HashMap<>();
        for (TrackedObject track : tracks) {
            if (!track.isActive() || track.getFramesMissing() != 0
                    || !visual(track.getObservationSource(), track.getCategory(), track.getAnchorKey())
                    || !inside(track.getRawBox(), previousTop, previousBottom, width)) continue;
            BBox base = shifted(track.getRawBox(), dx, dy);
            BBox moved = shifted(track.getRawBox(), dx, dy + extra);
            if (base == null || moved == null || !inside(moved, currentTop, currentBottom, width)) continue;
            int chosen = -1;
            float bestBase = 0;
            for (int index = 0; index < alignedDetections.size(); index++) {
                Detection detection = alignedDetections.get(index);
                if (!visual(detection.getSource(), detection.getCategory(), detection.getAnchorKey())
                        || !track.getClassName().equals(detection.getClassName())
                        || detection.getConfidence() < minimumConfidence) continue;
                bestBase = Math.max(bestBase, base.intersectionOverUnion(detection.getBox()));
                if (inside(detection.getBox(), currentTop, currentBottom, width)
                        && moved.intersectionOverUnion(detection.getBox()) >= .5f) {
                    if (chosen >= 0) { chosen = -2; break; }
                    chosen = index;
                }
            }
            // Keep a stationary/fixed object at its better-supported event position. The .5 IoU
            // admission is stricter than ordinary tracking; no identity or confidence gate is relaxed.
            if (chosen >= 0 && moved.intersectionOverUnion(alignedDetections.get(chosen).getBox())
                    >= bestBase + .2f) {
                candidates.put(track.getId(), chosen);
                uses.put(chosen, uses.getOrDefault(chosen, 0) + 1);
            }
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<Integer, Integer> candidate : candidates.entrySet()) {
            if (uses.get(candidate.getValue()) != 1) continue;
            Detection target = alignedDetections.get(candidate.getValue());
            boolean occupied = false;
            for (TrackedObject track : tracks) {
                if (track.getId() == candidate.getKey() || !track.isActive()) continue;
                BBox base = shifted(track.getRawBox(), dx, dy);
                if (base != null && base.intersectionOverUnion(target.getBox()) >= .5f) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) result.put(candidate.getKey(), extra);
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean visual(Detection.ObservationSource source, String category, String anchor) {
        return source == Detection.ObservationSource.VISUAL && category != null
                && !category.startsWith("text_") && anchor == null;
    }

    private static boolean inside(BBox box, double top, double bottom, int width) {
        // A partly cropped body object can still have a valid match. Require its center in both
        // registered crops AND a matching live box; crop membership alone never moves a track.
        return box.getArea() > 0 && box.getCenterX() >= 0 && box.getCenterX() < width
                && box.getCenterY() >= top && box.getCenterY() < bottom;
    }

    private static BBox shifted(BBox box, int dx, int dy) {
        long x = (long) box.getX() + dx, y = (long) box.getY() + dy;
        if (x < Integer.MIN_VALUE || x + box.getWidth() > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE || y + box.getHeight() > Integer.MAX_VALUE) return null;
        return new BBox((int) x, (int) y, box.getWidth(), box.getHeight());
    }
}
