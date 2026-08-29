package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.subhub.app.detection.BBox;

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
}
