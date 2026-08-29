package com.subhub.app.detection.text;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Suppresses one-scan text noise and bridges one missed scan for already confirmed lines. */
public final class TextDetectionStabilizer {
    private final Map<String, Observation> observations = new HashMap<>();
    private long generation;

    public synchronized List<Detection> update(List<Detection> detections) {
        return update(detections, true);
    }

    /**
     * Updates confirmed text regions.
     *
     * @param bridgeConfirmedMiss when true, retain a confirmed line across one missing scan;
     *                            disable during post-scroll reconciliation so departed feed text
     *                            cannot survive as a ghost for another scan interval.
     */
    public synchronized List<Detection> update(
            List<Detection> detections,
            boolean bridgeConfirmedMiss) {
        generation++;
        List<Detection> stable = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (detections != null) {
            for (Detection detection : detections) {
                if (detection == null) continue;
                String key = key(detection);
                seen.add(key);
                Observation previous = observations.get(key);
                int hits = previous != null && previous.lastSeenGeneration == generation - 1
                        ? previous.hits + 1 : 1;
                Observation current = new Observation(detection, hits, generation);
                observations.put(key, current);
                if (hits >= 2) stable.add(detection);
            }
        }

        Iterator<Map.Entry<String, Observation>> iterator = observations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Observation> entry = iterator.next();
            if (seen.contains(entry.getKey())) continue;
            Observation value = entry.getValue();
            long missed = generation - value.lastSeenGeneration;
            if (bridgeConfirmedMiss && value.hits >= 2 && missed == 1) {
                stable.add(value.detection);
            }
            if (missed > 1 || value.hits < 2) iterator.remove();
        }
        return stable;
    }

    public synchronized void clear() {
        observations.clear();
        generation = 0L;
    }

    private static String key(Detection detection) {
        String anchor = detection.getAnchorKey();
        if (anchor != null && !anchor.isEmpty()) return detection.getClassName() + '|' + anchor;
        BBox box = detection.getBox();
        return detection.getClassName() + '|' + box.getCenterX() / 48 + '|'
                + box.getCenterY() / 32 + '|' + box.getWidth() / 48 + '|'
                + box.getHeight() / 16;
    }

    private static final class Observation {
        private final Detection detection;
        private final int hits;
        private final long lastSeenGeneration;

        private Observation(Detection detection, int hits, long lastSeenGeneration) {
            this.detection = detection;
            this.hits = hits;
            this.lastSeenGeneration = lastSeenGeneration;
        }
    }
}
