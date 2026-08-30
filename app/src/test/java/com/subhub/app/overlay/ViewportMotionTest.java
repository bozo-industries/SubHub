package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ViewportMotionTest {
    @Test
    public void repeatedAuthoritativeSamplesAreExactThenAdvanceBetweenEvents() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -20f, 16L);
        assertEquals(-20f, motion.position(16L).y, 0.001f);

        motion.addDelta(0f, -20f, 32L);
        float eventPosition = motion.position(32L).y;
        float betweenEvents = motion.position(36L).y;
        assertEquals(-40f, eventPosition, 0.001f);
        assertTrue(betweenEvents < eventPosition);
        assertTrue(motion.isAnimating(36L));

        assertTrue(motion.position(40L).y < -40f);
        assertTrue(motion.position(96L).y < -40f);
        assertTrue(motion.isAnimating(96L));

        float stopped = motion.position(200L).y;
        assertEquals(-40f, stopped, 0.001f);
        assertFalse(motion.isAnimating(200L));
    }

    @Test
    public void nextAuthoritativeEventPreservesBoundedPresentationResidual() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -100f, 100L);
        motion.addDelta(0f, -100f, 200L);
        float displayed = motion.position(250L).y;

        motion.addDelta(0f, -100f, 250L);
        assertTrue(displayed < -200f);
        assertEquals(-100f, motion.position(250L).y - displayed, 0.001f);
        assertTrue(motion.position(250L).y >= -364f);
        assertEquals(-300f, motion.position(450L).y, 0.001f);
    }

    @Test
    public void firstAuthoritativeLargeDeltaIsExactImmediately() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -500f, 16L);

        assertEquals(-500f, motion.position(16L).y, 0.001f);
        assertEquals(-500f, motion.position(24L).y, 0.001f);
        assertEquals(-500f, motion.position(32L).y, 0.001f);
    }

    @Test
    public void nonAuthoritativeFallbackStillUsesShortCatchUp() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -500f, 16L, 1_344, 2_992, false);

        assertEquals(0f, motion.position(16L).y, 0.001f);
        float partial = motion.position(24L).y;
        assertTrue(partial < 0f && partial > -500f);
        assertEquals(-500f, motion.position(32L).y, 0.001f);
    }

    @Test
    public void steadySparseEventsAdvanceBetweenSamplesAndReturnToExactIfScrollingStops() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -100f, 10L);
        motion.addDelta(0f, -100f, 110L);

        assertEquals(-200f, motion.position(110L).y, 0.001f);
        float betweenEvents = motion.position(210L).y;
        assertEquals(-264f, betweenEvents, 0.001f);
        assertTrue(motion.isAnimating(210L));
        assertEquals(-200f, motion.position(310L).y, 0.001f);
        assertFalse(motion.isAnimating(310L));
    }

    @Test
    public void predictionIsBoundedForViewportSizedFlingDeltas() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -3_000f, 10L, 1_344, 2_992, true);
        motion.addDelta(0f, -3_000f, 110L, 1_344, 2_992, true);

        float exact = -6_000f;
        float predicted = motion.position(210L).y;
        assertEquals(exact - 64f, predicted, 0.001f);
        assertEquals(exact, motion.position(310L).y, 0.001f);
    }

    @Test
    public void presentationResidualNeverExceedsSixtyFourPixels() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -3_000f, 10L, 1_344, 2_992, true);
        motion.addDelta(0f, -3_000f, 110L, 1_344, 2_992, true);

        for (long now = 110L; now <= 310L; now += 4L) {
            float exact = -6_000f;
            assertTrue(Math.abs(motion.position(now).y - exact) <= 64.001f);
        }
    }

    @Test
    public void reversalKeepsEventTranslationContinuousAndSettlesWithoutOvershoot() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -100f, 10L);
        motion.addDelta(0f, -100f, 110L);
        float beforeReverse = motion.position(160L).y;

        motion.addDelta(0f, 80f, 160L);

        assertEquals(80f, motion.position(160L).y - beforeReverse, 0.001f);
        for (long now = 160L; now <= 360L; now += 4L) {
            assertTrue(motion.position(now).y <= -120f + 0.001f);
        }
        assertEquals(-120f, motion.position(360L).y, 0.001f);
    }

    @Test
    public void sparsePhaseLockedStreamHasNoLargeCorrectionFrame() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -100f, 10L, 1_344, 2_992, true);
        motion.addDelta(0f, -100f, 110L, 1_344, 2_992, true);
        motion.addDelta(0f, -100f, 210L, 1_344, 2_992, true);

        float previous = motion.position(210L).y;
        float largestStep = 0f;
        for (long now = 218L; now <= 310L; now += 8L) {
            float current = motion.position(now).y;
            largestStep = Math.max(largestStep, Math.abs(current - previous));
            previous = current;
        }
        motion.addDelta(0f, -100f, 310L, 1_344, 2_992, true);
        float afterEvent = motion.position(310L).y;
        assertEquals(-100f, afterEvent - previous, 0.001f);
        previous = afterEvent;
        for (long now = 318L; now <= 410L; now += 8L) {
            float current = motion.position(now).y;
            largestStep = Math.max(largestStep, Math.abs(current - previous));
            previous = current;
        }

        assertTrue("Largest phase-locked step was " + largestStep, largestStep <= 32f);
    }

    @Test
    public void deceleratingFinalSampleDoesNotPredictAnOvershoot() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -100f, 10L);
        motion.addDelta(0f, -40f, 110L);

        assertEquals(-140f, motion.position(210L).y, 0.001f);
    }
}
