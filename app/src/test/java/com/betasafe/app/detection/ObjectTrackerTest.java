package com.betasafe.app.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class ObjectTrackerTest {
    @Test
    public void nearbyDetectionsRetainTrackIdentity() {
        DetectorConfig config = DetectorConfig.builder().trackingSmoothing(0.5f).build();
        ObjectTracker tracker = new ObjectTracker(config);
        Detection first = detection(new BBox(10, 10, 50, 50));
        List<TrackedObject> initial = tracker.update(Collections.singletonList(first), 1_000_000_000L);
        Detection moved = detection(new BBox(20, 10, 50, 50));
        List<TrackedObject> next = tracker.update(Collections.singletonList(moved), 1_050_000_000L);

        assertEquals(initial.get(0).getId(), next.get(0).getId());
        assertEquals(initial.get(0).getId(), moved.getTrackId());
        assertEquals(new BBox(15, 10, 50, 50), next.get(0).getBox());
    }

    @Test
    public void staleTrackExpiresAfterAgeAndMissingFrameThresholds() {
        DetectorConfig config = DetectorConfig.builder()
                .trackMaxAgeSeconds(0.2f)
                .minRemoveFrames(2)
                .build();
        ObjectTracker tracker = new ObjectTracker(config);
        tracker.update(Collections.singletonList(detection(new BBox(10, 10, 50, 50))), 1_000_000_000L);
        tracker.update(Collections.emptyList(), 1_100_000_000L);
        List<TrackedObject> result = tracker.update(Collections.emptyList(), 1_300_000_000L);

        assertTrue(result.isEmpty());
    }

    private static Detection detection(BBox box) {
        return new Detection("FEMALE_BREAST_EXPOSED", "breasts", 0.9f, box, true, true);
    }
}
