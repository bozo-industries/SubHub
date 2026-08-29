package com.subhub.app.detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Gives the settled detector temporal authority without letting one low-confidence frame flash.
 */
public final class VisualDetectionStabilizer {
    private static final float MATCH_IOU = 0.20f;

    private final List<Observation> observations = new ArrayList<>();
    private long generation;
    private int nextId;

    public synchronized List<Detection> update(
            List<Detection> detections,
            DetectorConfig config) {
        generation++;
        List<Detection> stable = new ArrayList<>();
        Set<Integer> matched = new HashSet<>();
        if (detections != null) {
            for (Detection detection : detections) {
                if (detection == null || "text_smut".equals(detection.getCategory())) continue;
                Observation best = null;
                float bestIou = 0f;
                for (Observation candidate : observations) {
                    if (matched.contains(candidate.id)
                            || candidate.lastSeenGeneration != generation - 1) continue;
                    float iou = candidate.detection.getBox()
                            .intersectionOverUnion(detection.getBox());
                    if (iou >= MATCH_IOU && iou > bestIou) {
                        best = candidate;
                        bestIou = iou;
                    }
                }
                int hits = best == null ? 1 : best.hits + 1;
                boolean immediate = detection.getConfidence()
                        >= FastVisualGate.confidenceFloor(detection.getClassName(), config);
                boolean confirmed = immediate || hits >= 2 || best != null && best.confirmed;
                if (best == null) {
                    best = new Observation(nextId++, detection, hits, generation, confirmed);
                    observations.add(best);
                } else {
                    best.detection = detection;
                    best.hits = hits;
                    best.lastSeenGeneration = generation;
                    best.confirmed = confirmed;
                }
                matched.add(best.id);
                if (confirmed) stable.add(detection);
            }
        }

        Iterator<Observation> iterator = observations.iterator();
        while (iterator.hasNext()) {
            Observation observation = iterator.next();
            if (matched.contains(observation.id)) continue;
            long missed = generation - observation.lastSeenGeneration;
            if (observation.confirmed && missed == 1) stable.add(observation.detection);
            if (missed > 1 || !observation.confirmed) iterator.remove();
        }
        return stable;
    }

    public synchronized void clear() {
        observations.clear();
        generation = 0L;
        nextId = 0;
    }

    private static final class Observation {
        private final int id;
        private Detection detection;
        private int hits;
        private long lastSeenGeneration;
        private boolean confirmed;

        Observation(
                int id,
                Detection detection,
                int hits,
                long lastSeenGeneration,
                boolean confirmed) {
            this.id = id;
            this.detection = detection;
            this.hits = hits;
            this.lastSeenGeneration = lastSeenGeneration;
            this.confirmed = confirmed;
        }
    }
}
