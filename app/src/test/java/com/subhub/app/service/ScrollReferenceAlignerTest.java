package com.subhub.app.service;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ScrollReferenceAlignerTest {
    private static final ScrollLearningKey KEY = ScrollCalibrationLearnerTest.key();
    private static List<ViewportAnchorGeometry.Bounds> anchors(int dy) {
        return List.of(new ViewportAnchorGeometry.Bounds(10, 1000 + dy, 110, 1100 + dy),
                new ViewportAnchorGeometry.Bounds(250, 1500 + dy, 350, 1600 + dy),
                new ViewportAnchorGeometry.Bounds(500, 2000 + dy, 600, 2100 + dy));
    }
    private static ViewportAnchorGeometry geometry() {
        ViewportAnchorGeometry geometry = new ViewportAnchorGeometry();
        geometry.reset(anchors(0), 0, 0, 7, 0);
        return geometry;
    }
    private static ScrollReferenceAligner aligner() {
        ScrollReferenceAligner aligner = new ScrollReferenceAligner();
        aligner.begin(KEY, 7);
        return aligner;
    }
    private static boolean add(ScrollReferenceAligner aligner, ViewportAnchorGeometry geometry,
            long time, int dy, int readMs) {
        long start = time - readMs / 2, end = start + readMs;
        return aligner.addGeometry(KEY, 7, geometry.estimate(anchors(dy), 7, start, end, end, 32), end);
    }
    private static void straight(ScrollReferenceAligner aligner, ViewportAnchorGeometry geometry,
            int end, int step, int readMs) {
        for (int time = 1000; time <= end; time += step) {
            assertTrue(add(aligner, geometry, time, -2 * (time - 1000), readMs));
        }
    }

    @Test public void alignsIndependentGeometryToEventSourceTimeNotDeliveryTime() {
        ScrollReferenceAligner aligner = aligner();
        straight(aligner, geometry(), 1448, 16, 0);
        ScrollReferenceAligner.Alignment result = aligner.align(KEY, 7, 1, 1056, 1176, 1400, -120, 1448);
        assertEquals(ScrollReferenceAligner.Status.ALIGNED, result.status);
        assertEquals(-240, result.sample.measuredDelta, .001);
        assertEquals(8, result.sample.uncertaintyPixels, .001);
        assertEquals(1056, result.sample.referenceStart);
        assertEquals(1176, result.sample.referenceEnd);
        ScrollCalibrationLearner learner = ScrollCalibrationLearnerTest.learner();
        assertEquals(ScrollCalibrationLearner.Result.TRAINING, learner.observe(result.sample, 1448));
        assertEquals(ScrollReferenceAligner.Status.REPEATED_INTERVAL,
                aligner.align(KEY, 7, 1, 1056, 1176, 1400, -120, 1448).status);
    }

    @Test public void completeIndependentPipelineCanValidateAScaleWithoutOverlayPredictions() {
        ScrollReferenceAligner aligner = aligner();
        ViewportAnchorGeometry geometry = geometry();
        ScrollCalibrationLearner learner = ScrollCalibrationLearnerTest.learner();
        long nextReference = 1000;
        for (int index = 0; index < 18; index++) {
            long start = 1056 + index * 120L, end = start + 120;
            while (nextReference <= end + 40) {
                assertTrue(add(aligner, geometry, nextReference, (int) (-2 * (nextReference - 1000)), 0));
                nextReference += 16;
            }
            long gesture = index / 6 + 1;
            ScrollReferenceAligner.Alignment result = aligner.align(KEY, 7, gesture,
                    start, end, end + 20, -120, nextReference - 16);
            assertEquals("interval " + index, ScrollReferenceAligner.Status.ALIGNED, result.status);
            learner.observe(result.sample, nextReference - 16);
        }
        assertNotNull(learner.profile());
        assertEquals(2, learner.profile().pixelsPerEventPixel, .001);
        assertEquals(120, learner.profile().eventIntervalMs, .001);
        assertTrue(aligner.evictedReferences() > 0);
        assertEquals(96, aligner.retainedReferences());
    }

    @Test public void missingFutureMeasurementsWaitInsteadOfExtrapolating() {
        ScrollReferenceAligner aligner = aligner();
        ViewportAnchorGeometry geometry = geometry();
        straight(aligner, geometry, 1160, 16, 0);
        ScrollReferenceAligner.Alignment result = aligner.align(KEY, 7, 1, 1056, 1176, 1196, -120, 1196);
        assertEquals(ScrollReferenceAligner.Status.WAITING, result.status);
        assertNull(result.sample);
        for (int time = 1176; time <= 1208; time += 16) add(aligner, geometry, time, -2 * (time - 1000), 0);
        assertEquals(ScrollReferenceAligner.Status.ALIGNED,
                aligner.align(KEY, 7, 1, 1056, 1176, 1196, -120, 1208).status);
    }

    @Test public void sparseReferenceCannotManufacturePreciseTrainingPairs() {
        ScrollReferenceAligner aligner = aligner();
        straight(aligner, geometry(), 1256, 64, 0);
        assertEquals(ScrollReferenceAligner.Status.SPARSE_REFERENCE,
                aligner.align(KEY, 7, 1, 1072, 1192, 1212, -120, 1256).status);
    }

    @Test public void reversalsAndBurstyProviderUpdatesRejectInterpolation() {
        ScrollReferenceAligner aligner = aligner();
        ViewportAnchorGeometry geometry = geometry();
        for (int time = 1000; time <= 1240; time += 16) {
            int dy = time < 1080 ? 0 : -400;
            assertTrue(add(aligner, geometry, time, dy, 0));
        }
        assertEquals(ScrollReferenceAligner.Status.UNSTABLE_REFERENCE,
                aligner.align(KEY, 7, 1, 1072, 1192, 1212, -120, 1240).status);
    }

    @Test public void timingUncertaintyIsNotDiscardedToForceCalibration() {
        ScrollReferenceAligner aligner = aligner();
        straight(aligner, geometry(), 1208, 16, 8);
        assertEquals(ScrollReferenceAligner.Status.UNCERTAIN,
                aligner.align(KEY, 7, 1, 1056, 1176, 1196, -120, 1212).status);
    }

    @Test public void newBaselineDoesNotBridgeUnrelatedOriginsAndOldBaselineCannotReturn() {
        ScrollReferenceAligner aligner = aligner();
        ViewportAnchorGeometry geometry = geometry();
        straight(aligner, geometry, 1208, 16, 0);
        ViewportAnchorGeometry.Result delayedOld = geometry.estimate(anchors(-900), 7, 1240, 1240, 1240, 32);
        geometry.reset(anchors(0), 0, 5000, 7, 1210);
        add(aligner, geometry, 1224, -20, 0);
        assertEquals(1, aligner.retainedReferences());
        assertFalse(aligner.addGeometry(KEY, 7, delayedOld, 1240));
        assertEquals(ScrollReferenceAligner.Status.EXPIRED,
                aligner.align(KEY, 7, 1, 1104, 1224, 1244, -120, 1244).status);
    }

    @Test public void invalidScopeStaleOrNonmonotonicReadsCannotEnterHistory() {
        ScrollReferenceAligner aligner = aligner();
        ViewportAnchorGeometry geometry = geometry();
        ViewportAnchorGeometry.Result reference = geometry.estimate(anchors(-100), 7, 1100, 1100, 1100, 32);
        assertFalse(aligner.addGeometry(KEY, 8, reference, 1100));
        assertFalse(aligner.addGeometry(KEY, 7, reference, 1200));
        assertTrue(aligner.addGeometry(KEY, 7, reference, 1100));
        assertFalse(aligner.addGeometry(KEY, 7, reference, 1100));
        assertEquals(3, aligner.rejectedReferences());
        assertEquals(1, aligner.acceptedReferences());
        assertEquals(ScrollReferenceAligner.Status.WRONG_SCOPE,
                aligner.align(KEY, 8, 1, 1100, 1220, 1240, -120, 1240).status);
    }

    @Test public void expiredIntervalsAndTimeTravelAreNotTrainingEvidence() {
        ScrollReferenceAligner aligner = aligner();
        straight(aligner, geometry(), 1208, 16, 0);
        assertEquals(ScrollReferenceAligner.Status.INVALID_EVENT,
                aligner.align(KEY, 7, 1, 1056, 1176, 1196, -120, 1196).status);
        assertEquals(ScrollReferenceAligner.Status.EXPIRED,
                aligner.align(KEY, 7, 1, 1056, 1176, 1196, -120, 1700).status);
    }
}
