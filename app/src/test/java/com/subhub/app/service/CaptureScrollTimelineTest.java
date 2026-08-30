package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CaptureScrollTimelineTest {
    @Test public void captureUsesLatestMotionAtOrBeforeHardwareTimestamp() {
        CaptureScrollTimeline timeline = new CaptureScrollTimeline();
        timeline.record(1_020L, 0L, 100L, 4L);
        timeline.record(1_040L, 0L, 240L, 5L);
        timeline.record(1_070L, 0L, 400L, 6L);

        CaptureScrollTimeline.Phase phase = timeline.resolve(
                1_055L, 1_000L, 0L, 0L, 3L);

        assertEquals(240L, phase.scrollY);
        assertEquals(5L, phase.motionGeneration);
        assertTrue(phase.resolvedFromMotion);
    }

    @Test public void motionAfterHardwareCaptureIsNotFoldedIntoDetectorSource() {
        CaptureScrollTimeline timeline = new CaptureScrollTimeline();
        timeline.record(2_020L, 0L, 100L, 8L);
        timeline.record(2_080L, 0L, 500L, 9L);

        CaptureScrollTimeline.Phase phase = timeline.resolve(
                2_050L, 2_000L, 0L, 0L, 7L);

        assertEquals(100L, phase.scrollY);
        assertEquals(8L, phase.motionGeneration);
    }

    @Test public void requestSnapshotIsBaselineWhenNoInterveningMotionExists() {
        CaptureScrollTimeline timeline = new CaptureScrollTimeline();
        timeline.record(2_900L, 10L, 20L, 2L);

        CaptureScrollTimeline.Phase phase = timeline.resolve(
                3_030L, 3_000L, 30L, 40L, 3L);

        assertEquals(30L, phase.scrollX);
        assertEquals(40L, phase.scrollY);
        assertEquals(3L, phase.motionGeneration);
        assertFalse(phase.resolvedFromMotion);
    }
}
