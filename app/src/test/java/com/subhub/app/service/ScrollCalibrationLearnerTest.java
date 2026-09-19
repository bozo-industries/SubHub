package com.subhub.app.service;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ScrollCalibrationLearnerTest {
    static ScrollLearningKey key() {
        return key("com.example.reader", 1, "0".repeat(64), 1344, 2992, 480, 0, 120000,
                ScrollLearningKey.Axis.Y, ScrollLearningKey.Evidence.EXPLICIT);
    }
    static ScrollLearningKey key(String app, long version, String surface, int width, int height,
            int density, int rotation, int refresh, ScrollLearningKey.Axis axis,
            ScrollLearningKey.Evidence evidence) {
        return new ScrollLearningKey(app, version, surface, width, height, density, rotation, refresh, axis, evidence);
    }
    static ScrollCalibrationLearner.Sample sample(int index, long gesture, double ratio) {
        long start = 1000 + index * 100L, end = start + 100;
        return new ScrollCalibrationLearner.Sample(key(), 7, gesture, start, end, start, end,
                end + 20, -100, -100 * ratio, 1, 3, 8, true);
    }
    static ScrollCalibrationLearner learner() {
        ScrollCalibrationLearner result = new ScrollCalibrationLearner();
        result.begin(true, key(), 7);
        return result;
    }
    static ScrollCalibrationLearner.Result feed(ScrollCalibrationLearner learner, int index,
            long gesture, double ratio) {
        ScrollCalibrationLearner.Sample sample = sample(index, gesture, ratio);
        return learner.observe(sample, sample.receivedAt);
    }
    static void train(ScrollCalibrationLearner learner) {
        for (int index = 0; index < 12; index++) feed(learner, index, index < 6 ? 1 : 2, 2);
    }
    static ScrollCalibrationLearner ready() {
        ScrollCalibrationLearner learner = learner();
        train(learner);
        for (int index = 12; index < 18; index++) feed(learner, index, 3, 2);
        return learner;
    }

    @Test public void independentHeldOutGestureIsRequiredBeforePublishingProfile() {
        ScrollCalibrationLearner learner = learner();
        train(learner);
        assertEquals(ScrollCalibrationLearner.State.VALIDATING, learner.state());
        assertNull(learner.profile());
        for (int index = 12; index < 18; index++) feed(learner, index, 2, 2);
        assertNull("same training gesture is not holdout", learner.profile());
        for (int index = 18; index < 24; index++) feed(learner, index, 3, 2);
        ScrollCalibrationLearner.Profile profile = learner.profile();
        assertNotNull(profile);
        assertEquals(2, profile.pixelsPerEventPixel, .001);
        assertEquals(100, profile.eventIntervalMs, .001);
        assertEquals(20, profile.deliveryLagMs, .001);
        assertEquals(0, profile.deliveryJitterMs, .001);
        assertEquals(0, profile.validationMeanErrorPx, .001);
        assertEquals(12, profile.trainingSamples);
        assertEquals(6, profile.validationSamples);
        assertEquals(3, profile.gestures);
    }

    @Test public void passingTrainingDoesNotHideHoldoutFailure() {
        ScrollCalibrationLearner learner = learner();
        train(learner);
        assertEquals(ScrollCalibrationLearner.Result.VALIDATION_FAILED, feed(learner, 12, 3, 3));
        assertEquals(ScrollCalibrationLearner.State.INSUFFICIENT, learner.state());
        assertNull(learner.profile());
    }

    @Test public void aSingleTrainingOutlierDoesNotDriveTheMapping() {
        ScrollCalibrationLearner learner = learner();
        for (int index = 0; index < 12; index++) feed(learner, index, index < 6 ? 1 : 2, index == 5 ? 4 : 2);
        for (int index = 12; index < 18; index++) feed(learner, index, 3, 2);
        assertEquals(2, learner.profile().pixelsPerEventPixel, .001);
    }

    @Test public void noProfileFromOneGestureOrInconsistentEvidence() {
        for (boolean inconsistent : new boolean[]{false, true}) {
            ScrollCalibrationLearner learner = learner();
            for (int index = 0; index < 32; index++) {
                feed(learner, index, inconsistent ? index / 6 + 1 : 1,
                        inconsistent && index % 2 == 0 ? 4 : 2);
            }
            assertEquals(ScrollCalibrationLearner.State.INSUFFICIENT, learner.state());
            assertNull(learner.profile());
        }
    }

    @Test public void disarmingRevokesProfileAndAcceptsNoMoreData() {
        ScrollCalibrationLearner learner = ready();
        assertNotNull(learner.profile());
        learner.disable();
        assertEquals(ScrollCalibrationLearner.Result.REJECTED, feed(learner, 18, 4, 2));
        assertNull(learner.profile());
        assertEquals(0, learner.acceptedSamples());
    }

    @Test public void scopeChangeAndDisplayChangeCannotReuseCalibration() {
        ScrollCalibrationLearner learner = ready();
        learner.begin(true, key(), 8);
        assertNull(learner.profile());
        assertEquals(ScrollCalibrationLearner.Result.REJECTED, feed(learner, 18, 4, 2));
        learner.begin(true, key("com.example.reader", 2, "0".repeat(64), 1344, 2992, 480, 0,
                120000, ScrollLearningKey.Axis.Y, ScrollLearningKey.Evidence.EXPLICIT), 7);
        assertEquals(ScrollCalibrationLearner.Result.REJECTED, feed(learner, 18, 4, 2));
    }

    @Test public void repeatedMonitorDisagreementRevokesRatherThanSilentlyRetunes() {
        for (double wrong : new double[]{4, 10, -2}) {
            ScrollCalibrationLearner learner = ready();
            feed(learner, 18, 4, wrong);
            feed(learner, 19, 4, wrong);
            assertNotNull(learner.profile());
            assertEquals(ScrollCalibrationLearner.Result.INVALIDATED, feed(learner, 20, 4, wrong));
            assertNull(learner.profile());
            assertEquals(ScrollCalibrationLearner.State.LEARNING, learner.state());
            assertEquals("cannot replay the invalidating interval as new evidence",
                    ScrollCalibrationLearner.Result.REJECTED, feed(learner, 20, 4, 2));
        }
    }

    @Test public void oneDisagreementDoesNotDestroyAConsistentProfile() {
        ScrollCalibrationLearner learner = ready();
        feed(learner, 18, 4, 4);
        feed(learner, 19, 4, 2);
        feed(learner, 20, 4, 4);
        feed(learner, 21, 4, 4);
        assertNotNull(learner.profile());
    }

    @Test public void staleDuplicateMisalignedOrSelfPredictedSamplesAreRejected() {
        for (int fault = 0; fault < 9; fault++) {
            ScrollCalibrationLearner learner = learner();
            ScrollCalibrationLearner.Sample sample = new ScrollCalibrationLearner.Sample(key(),
                    fault == 0 ? 8 : 7, 1, 1000, 1100, fault == 1 ? 990 : 1000, 1100, 1120,
                    fault == 2 ? Double.NaN : -100, fault == 3 ? 0 : -200,
                    fault == 4 ? 50 : 1, fault == 5 ? 2 : 3, fault == 6 ? 20 : 8, fault != 7);
            assertEquals("fault " + fault, ScrollCalibrationLearner.Result.REJECTED,
                    learner.observe(sample, fault == 8 ? 1200 : 1120));
            assertEquals(0, learner.acceptedSamples());
        }
        ScrollCalibrationLearner learner = learner();
        feed(learner, 0, 1, 2);
        assertEquals(ScrollCalibrationLearner.Result.REJECTED, feed(learner, 0, 1, 2));
        assertEquals(1, learner.acceptedSamples());
    }
}
