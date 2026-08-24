package com.subhub.app.detection;

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

    @Test
    public void classificationFlickerRetainsSpatialIdentity() {
        ObjectTracker tracker = new ObjectTracker(DetectorConfig.builder().build());
        Detection first = detection(new BBox(10, 10, 50, 50));
        int id = tracker.update(Collections.singletonList(first), 1_000_000_000L).get(0).getId();
        Detection relabeled = new Detection("FEMALE_BREAST_COVERED", "breasts_covered",
                0.88f, new BBox(12, 11, 51, 49), true, true);

        List<TrackedObject> result = tracker.update(
                Collections.singletonList(relabeled), 1_100_000_000L);

        assertEquals(id, result.get(0).getId());
        assertEquals(id, relabeled.getTrackId());
    }

    @Test
    public void nestedOverlayDetectionDoesNotCreateRecursiveTrack() {
        ObjectTracker tracker = new ObjectTracker(DetectorConfig.builder().build());
        Detection original = detection(new BBox(20, 20, 100, 100));
        tracker.update(Collections.singletonList(original), 1_000_000_000L);
        tracker.update(Collections.singletonList(
                detection(new BBox(20, 20, 100, 100))), 1_100_000_000L);
        Detection nested = detection(new BBox(45, 45, 40, 40));

        List<TrackedObject> result = tracker.update(
                java.util.Arrays.asList(detection(new BBox(20, 20, 100, 100)), nested),
                1_200_000_000L);

        assertEquals(1, result.size());
        assertEquals(result.get(0).getId(), nested.getTrackId());
    }

    private static Detection detection(BBox box) {
        return new Detection("FEMALE_BREAST_EXPOSED", "breasts", 0.9f, box, true, true);
    }
}
