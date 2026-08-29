package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;

import org.junit.Test;

public final class RenderTrackSnapshotTest {
    @Test
    public void predictionAdvancesByDisplayTimeAndStopsAtTheConfiguredHorizon() {
        RenderTrackSnapshot snapshot = new RenderTrackSnapshot(
                1, "body", new BBox(100, 200, 80, 60), 0.4f, -0.2f);

        assertEquals(new BBox(120, 190, 80, 60), snapshot.predict(50f, 180f));
        assertEquals(new BBox(172, 164, 80, 60), snapshot.predict(500f, 180f));
        assertTrue(snapshot.isMoving());
    }

    @Test
    public void aStationarySnapshotDoesNotRequestDisplayRateFrames() {
        RenderTrackSnapshot snapshot = new RenderTrackSnapshot(
                1, "text_smut", new BBox(10, 20, 30, 40), 0f, 0f);

        assertEquals(snapshot.box(), snapshot.predict(100f, 180f));
        assertFalse(snapshot.isMoving());
    }

    @Test
    public void textGeometryNeverDriftsFromClassifierVelocityNoise() {
        RenderTrackSnapshot snapshot = new RenderTrackSnapshot(
                1, "text_smut", new BBox(10, 20, 300, 40), 0.8f, -0.5f);

        assertEquals(snapshot.box(), snapshot.predict(100f, 180f));
        assertFalse(snapshot.isMoving());
    }

    @Test
    public void directTextLaneKeepsAStableIdAcrossGeometryChanges() {
        Detection first = textDetection(new BBox(10, 20, 300, 40));
        Detection moved = textDetection(new BBox(10, 120, 300, 40));

        RenderTrackSnapshot firstSnapshot = RenderTrackSnapshot.fromTextDetection(first);
        RenderTrackSnapshot movedSnapshot = RenderTrackSnapshot.fromTextDetection(moved);

        assertEquals(firstSnapshot.id(), movedSnapshot.id());
        assertTrue(firstSnapshot.id() < 0);
    }

    private static Detection textDetection(BBox box) {
        return new Detection("TEXT_SMUT_ACCESSIBILITY_EXPLICIT", "text_smut",
                0.9f, box, true, true).withObservation(
                Detection.ObservationSource.ACCESSIBILITY,
                Detection.GeometryQuality.EXACT, "a11y:id:caption");
    }
}
