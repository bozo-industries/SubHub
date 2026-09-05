package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ViewportMotionTest {
    @Test
    public void steadySlowScrollCorrectsPhaseWithoutIncreasingJumpBudget() {
        for (int interval : new int[]{60, 100, 120}) {
            for (int frameStep : new int[]{8, 16}) {
                for (int direction : new int[]{-1, 1}) {
                    ViewportMotion motion = new ViewportMotion();
                    motion.reset(0f, 0f, 1_000L);
                    double errorSum = 0;
                    int samples = 0;
                    for (int elapsed = 1; elapsed <= 1_200; elapsed++) {
                        long now = 1_000L + elapsed;
                        if (elapsed % interval == 0) {
                            float before = motion.position(now).y;
                            motion.addDelta(0, direction * .5f * interval,
                                    now, 1_344, 2_992, true);
                            if (elapsed > interval) {
                                assertTrue("same-direction jump budget",
                                        Math.abs(motion.position(now).y - before) <= 56.001f);
                            }
                        }
                        if (elapsed > interval * 2 && elapsed % frameStep == 0) {
                            errorSum += Math.abs(motion.position(now).y
                                    - direction * .5f * elapsed);
                            samples++;
                        }
                    }
                    assertTrue("mean phase error at " + interval + "ms / " + frameStep + "ms",
                            errorSum / samples < 22.0);
                    assertEquals(direction * .5f * (1_200 / interval) * interval,
                            motion.position(2_700L).y, .001f);
                    assertFalse(motion.isAnimating(2_700L));
                }
            }
        }
    }

    @Test
    public void firstAuthoritativeSampleCoversMeasuredPositionImmediately() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);

        motion.addDelta(0f, -500f, 16L, 1_344, 2_992, true);

        assertEquals(-500f, motion.position(16L).y, 0.001f);
        assertTrue(motion.position(32L).y < -500f);
    }

    @Test
    public void steadySparseStreamRebasesEveryEventToMeasuredPosition() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        long now = 16L;
        motion.addDelta(0f, -400f, now, 1_344, 2_992, true);

        float largestDisplayStep = 0f;
        float previous = motion.position(now).y;
        for (int event = 1; event <= 7; event++) {
            long nextEvent = now + 114L;
            for (long frame = now + 8L; frame < nextEvent; frame += 8L) {
                float current = motion.position(frame).y;
                assertTrue("Trajectory reversed at " + frame, current <= previous + 0.001f);
                largestDisplayStep = Math.max(largestDisplayStep, Math.abs(current - previous));
                previous = current;
            }
            float beforeEvent = motion.position(nextEvent).y;
            motion.addDelta(0f, -400f, nextEvent, 1_344, 2_992, true);
            float afterEvent = motion.position(nextEvent).y;
            float exact = -400f * (event + 1);
            assertTrue(Math.abs(afterEvent - exact) <= 2_992f * 0.08f + 0.001f);
            assertTrue(Math.abs(afterEvent - beforeEvent) <= 2_992f * 0.08f + 0.001f);
            largestDisplayStep = Math.max(largestDisplayStep,
                    Math.abs(afterEvent - previous));
            previous = afterEvent;
            now = nextEvent;
        }

        assertTrue("Display step was " + largestDisplayStep,
                largestDisplayStep <= 2_992f * 0.08f + 0.001f);
    }

    @Test
    public void deceleratingStreamDoesNotReverseWhileEventsContinue() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        long[] times = {16L, 130L, 244L, 358L, 472L};
        float[] deltas = {-420f, -350f, -260f, -160f, -80f};
        float previous = 0f;
        for (int index = 0; index < times.length; index++) {
            float before = motion.position(times[index]).y;
            motion.addDelta(0f, deltas[index], times[index], 1_344, 2_992, true);
            float after = motion.position(times[index]).y;
            float expected = 0f;
            for (int deltaIndex = 0; deltaIndex <= index; deltaIndex++) {
                expected += deltas[deltaIndex];
            }
            assertTrue(Math.abs(after - expected) <= 2_992f * 0.08f + 0.001f);
            assertTrue(after <= previous + 0.001f);
            previous = after;
        }
    }

    @Test
    public void reversalCorrectionIsBoundedAndTurnsTowardNewDirection() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -300f, 16L, 1_344, 2_992, true);
        motion.addDelta(0f, -300f, 130L, 1_344, 2_992, true);
        float beforeReverse = motion.position(244L).y;

        motion.addDelta(0f, 240f, 244L, 1_344, 2_992, true);
        float atReverse = motion.position(244L).y;
        float afterReverse = motion.position(284L).y;

        assertTrue(Math.abs(atReverse - -360f) <= 2_992f * 0.08f + 0.001f);
        assertTrue(Math.abs(atReverse - beforeReverse) <= 96.001f);
        assertTrue(afterReverse > atReverse);
    }

    @Test
    public void predictionSettlesBackToLastMeasurementWithoutDrift() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -300f, 16L, 1_344, 2_992, true);
        motion.addDelta(0f, -300f, 130L, 1_344, 2_992, true);

        assertTrue(motion.isAnimating(200L));
        assertEquals(-600f, motion.position(500L).y, 0.001f);
        assertFalse(motion.isAnimating(500L));
    }

    @Test
    public void sharplyDeceleratingTailBrakesInOneDisplayFrame() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -240f, 16L, 1_344, 2_992, true);
        motion.addDelta(0f, -30f, 130L, 1_344, 2_992, true);

        assertEquals(0f, motion.predictionAmplitude().y, 0.001f);
        assertEquals(-270f, motion.position(146L).y, 0.001f);
        assertFalse(motion.isAnimating(147L));
    }

    @Test
    public void reversalHasNoForwardOvershootAndSettlesWithinTwoFrames() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -240f, 16L, 1_344, 2_992, true);
        motion.addDelta(0f, 30f, 130L, 1_344, 2_992, true);

        assertEquals(0f, motion.predictionAmplitude().y, 0.001f);
        float previous = motion.position(130L).y;
        for (long frame = 138L; frame <= 162L; frame += 8L) {
            float current = motion.position(frame).y;
            assertTrue(current >= previous - 0.001f);
            assertTrue(current <= -210f + 0.001f);
            previous = current;
        }
        assertEquals(-210f, motion.position(162L).y, 0.001f);
        assertFalse(motion.isAnimating(163L));
    }

    @Test
    public void zeroDistanceBrakeDoesNotAdvertiseLongAnimation() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -120f, 16L, 1_344, 2_992, true);
        motion.addDelta(0f, 0f, 130L, 1_344, 2_992, true);

        assertFalse(motion.isAnimating(147L));
    }

    @Test
    public void flingPredictionIsViewportBounded() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -5_000f, 16L, 1_344, 2_992, true);

        assertTrue(Math.abs(motion.predictionAmplitude().y) <= 2_992f * 0.18f + 0.001f);
    }

    @Test
    public void nonAuthoritativeFallbackUsesOnlyOneDisplayFrame() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -500f, 16L, 1_344, 2_992, false);

        assertEquals(0f, motion.position(16L).y, 0.001f);
        float partial = motion.position(24L).y;
        assertTrue(partial < 0f && partial > -500f);
        assertEquals(-500f, motion.position(32L).y, 0.001f);
    }

    @Test
    public void detectorCoordinateRebasePreservesLivePollPhaseAcrossEvent() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -300f, 16L, 1_344, 2_992, true);
        motion.addPresentationDelta(0f, -40f, 80L, 1_344, 2_992);
        motion.rebase(0f, 0f, 88L);
        motion.addPresentationDelta(0f, -30f, 96L, 1_344, 2_992);
        float beforeEvent = motion.position(112L).y;

        motion.addDelta(0f, -260f, 112L, 1_344, 2_992, true);

        assertEquals(beforeEvent, motion.position(112L).y, 0.001f);
        assertTrue(Math.abs(motion.predictionAmplitude().y)
                <= 2_992f * 0.45f + 0.001f);
    }

    @Test
    public void presentationSamplesApplyMeasuredPhaseAndContinueBetweenPolls() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);

        motion.addPresentationDelta(0f, -180f, 40L, 1_344, 2_992);
        assertEquals(-180f, motion.position(40L).y, 0.001f);
        assertTrue(motion.position(48L).y < -180f);
        motion.addPresentationDelta(0f, -180f, 80L, 1_344, 2_992);
        motion.addPresentationDelta(0f, -180f, 120L, 1_344, 2_992);

        assertTrue(Math.abs(motion.predictionAmplitude().y)
                <= 2_992f * 0.45f + 0.001f);
    }

    @Test
    public void phaseLockedEventDoesNotApplyPolledIntervalTwice() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -240f, 16L, 1_344, 2_992, true);
        motion.addPresentationDelta(0f, -60f, 48L, 1_344, 2_992);
        motion.addPresentationDelta(0f, -60f, 64L, 1_344, 2_992);
        float beforeEvent = motion.position(72L).y;

        motion.addDelta(0f, -120f, 72L, 1_344, 2_992, true);

        assertEquals(beforeEvent, motion.position(72L).y, 0.001f);
    }

    @Test
    public void measuredZeroBrakesPredictionImmediatelyAndCanBeReconciled() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -200f, 16L, 1_344, 2_992, true);
        motion.addPresentationDelta(0f, -80f, 48L, 1_344, 2_992);
        motion.addPresentationDelta(0f, -80f, 64L, 1_344, 2_992);
        assertTrue(motion.position(72L).y < -360f);

        motion.settlePresentation(80L);

        assertEquals(-360f, motion.position(96L).y, 0.001f);
        motion.addDelta(0f, -160f, 104L, 1_344, 2_992, true);
        assertEquals(-360f, motion.position(120L).y, 0.001f);
        assertEquals(-360f, motion.position(160L).y, 0.001f);
    }

    @Test
    public void sparseFlingCannotLeaveRenderedViewportFarBehindMeasurement() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -900f, 16L, 1_344, 2_992, true);
        motion.addDelta(0f, -2_600f, 130L, 1_344, 2_992, true);

        float phaseError = Math.abs(motion.position(130L).y - -3_500f);
        assertTrue(phaseError <= 2_992f * 0.08f + 0.001f);
    }

    @Test
    public void delayedCallbackReplaysTrajectoryFromEventSourceTime() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);

        // Called at t=130, but Android says the scroll observation became effective at t=100.
        motion.addDelta(0f, -240f, 100L, 1_344, 2_992, true);

        assertEquals(-240f, motion.position(100L).y, 0.001f);
        assertTrue("The next presentation must include elapsed motion, not restart at callback",
                motion.position(130L).y < -240f);
    }
}
