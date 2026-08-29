package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ViewportMotionTest {
    @Test
    public void repeatedDirectionSamplesInterpolateMonotonicallyToExactState() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -20f, 16L);
        assertEquals(0f, motion.position(16L).y, 0.001f);

        motion.addDelta(0f, -20f, 32L);
        float eventPosition = motion.position(32L).y;
        float betweenEvents = motion.position(36L).y;
        assertTrue(eventPosition < 0f);
        assertTrue(betweenEvents < eventPosition);
        assertTrue(betweenEvents >= -40f);
        assertTrue(motion.isAnimating(36L));

        assertEquals(-40f, motion.position(40L).y, 0.001f);
        assertFalse(motion.isAnimating(40L));

        float stopped = motion.position(200L).y;
        assertTrue(stopped <= betweenEvents);
        assertEquals(-40f, stopped, 0.001f);
        assertFalse(motion.isAnimating(200L));
    }

    @Test
    public void nextEventContinuesFromTheCurrentlyDisplayedPosition() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -100f, 100L);
        motion.addDelta(0f, -100f, 200L);
        float displayed = motion.position(250L).y;

        motion.addDelta(0f, -100f, 250L);
        assertEquals(displayed, motion.position(250L).y, 0.001f);
        assertEquals(-300f, motion.position(400L).y, 0.001f);
    }

    @Test
    public void firstLargeDeltaSmoothsToExactWithoutOvershoot() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -500f, 16L);

        assertEquals(0f, motion.position(16L).y, 0.001f);
        float partial = motion.position(24L).y;
        assertTrue(partial < 0f && partial > -500f);
        assertEquals(-500f, motion.position(32L).y, 0.001f);
    }
}
