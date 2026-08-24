package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScrollFrameMotionEstimatorTest {
    private static final int WIDTH = 48;
    private static final int HEIGHT = 80;

    @Test public void findsGlobalVerticalFeedMotion() {
        int[] previous = patternedFrame();
        int[] current = translated(previous, 0, -7);

        ScrollFrameMotionEstimator.SampleMotion motion =
                ScrollFrameMotionEstimator.estimate(previous, current, WIDTH, HEIGHT);

        assertTrue(motion.moved);
        assertEquals(0, motion.dx);
        assertEquals(-7, motion.dy);
    }

    @Test public void stationaryFrameDoesNotMoveCensors() {
        int[] frame = patternedFrame();
        ScrollFrameMotionEstimator.SampleMotion motion =
                ScrollFrameMotionEstimator.estimate(frame, frame.clone(), WIDTH, HEIGHT);
        assertFalse(motion.moved);
    }

    @Test public void localAnimationDoesNotPretendTheWholeFeedScrolled() {
        int[] previous = patternedFrame();
        int[] current = previous.clone();
        for (int y = 28; y < 44; y++) {
            for (int x = 14; x < 30; x++) current[y * WIDTH + x] = (x * y * 17) & 0xff;
        }
        ScrollFrameMotionEstimator.SampleMotion motion =
                ScrollFrameMotionEstimator.estimate(previous, current, WIDTH, HEIGHT);
        assertFalse(motion.moved);
    }

    private static int[] patternedFrame() {
        int[] values = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int stripe = ((y / 7) % 2) * 70;
                int irregular = (x * 37 + y * 17 + x * y * 3 + (x ^ y) * 11) & 0x7f;
                values[y * WIDTH + x] = Math.min(255, 20 + stripe + irregular);
            }
        }
        return values;
    }

    private static int[] translated(int[] source, int dx, int dy) {
        int[] target = new int[source.length];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int targetX = x + dx;
                int targetY = y + dy;
                if (targetX >= 0 && targetX < WIDTH && targetY >= 0 && targetY < HEIGHT) {
                    target[targetY * WIDTH + targetX] = source[y * WIDTH + x];
                }
            }
        }
        return target;
    }
}
