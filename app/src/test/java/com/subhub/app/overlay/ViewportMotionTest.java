package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ViewportMotionTest {
    @Test
    public void sparseScrollEventsProduceIntermediateDisplayPositionsThenSettleExactly() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -20f, 16L);
        assertEquals(-20f, motion.position(16L).y, 0.001f);

        motion.addDelta(0f, -20f, 32L);
        float eventPosition = motion.position(32L).y;
        float betweenEvents = motion.position(40L).y;
        assertTrue(eventPosition < -30f);
        assertTrue(betweenEvents < eventPosition);
        assertTrue(motion.isAnimating(40L));

        assertEquals(-40f, motion.position(200L).y, 0.001f);
        assertFalse(motion.isAnimating(200L));
    }

    @Test
    public void firstLargeDeltaAppliesImmediatelyWithoutInventingVelocity() {
        ViewportMotion motion = new ViewportMotion();
        motion.reset(0f, 0f, 0L);
        motion.addDelta(0f, -500f, 16L);

        assertEquals(-500f, motion.position(16L).y, 0.001f);
        assertEquals(-500f, motion.position(32L).y, 0.001f);
    }
}
