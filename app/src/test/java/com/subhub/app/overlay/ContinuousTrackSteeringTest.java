package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.subhub.app.detection.BBox;

import org.junit.Test;

import java.util.Collections;

public final class ContinuousTrackSteeringTest {
    @Test
    public void retargetStartsFromDisplayedPositionAndAdvancesEveryDisplayFrame() {
        ContinuousTrackSteering steering = new ContinuousTrackSteering();
        steering.updateTarget(1, box(100), 1000, 1000, 0L, false);
        steering.updateTarget(1, box(220), 1000, 1000, 100L, false);

        assertEquals(100, steering.position(1, 1000, 1000, 100L).getX());
        int atEightMs = steering.position(1, 1000, 1000, 108L).getX();
        int atSixteenMs = steering.position(1, 1000, 1000, 116L).getX();
        int atThirtyTwoMs = steering.position(1, 1000, 1000, 132L).getX();

        assertTrue(atEightMs > 100);
        assertTrue(atSixteenMs > atEightMs);
        assertTrue(atThirtyTwoMs > atSixteenMs);
        assertTrue(atThirtyTwoMs < 220);
        assertTrue(steering.isAnimating(132L));
    }

    @Test
    public void courseCorrectionNeverJumpsAtRetargetTime() {
        ContinuousTrackSteering steering = new ContinuousTrackSteering();
        steering.updateTarget(1, box(100), 1000, 1000, 0L, false);
        steering.updateTarget(1, box(300), 1000, 1000, 100L, false);
        int beforeCorrection = steering.position(1, 1000, 1000, 150L).getX();

        steering.updateTarget(1, box(40), 1000, 1000, 150L, false);
        assertEquals(beforeCorrection,
                steering.position(1, 1000, 1000, 150L).getX());
        int afterCorrection = steering.position(1, 1000, 1000, 166L).getX();
        assertTrue(afterCorrection < beforeCorrection);
        assertTrue(afterCorrection > 40);
    }

    @Test
    public void repeatedMotionKeepsAdvancingBetweenDetectorObservations() {
        ContinuousTrackSteering steering = new ContinuousTrackSteering();
        steering.updateTarget(1, box(0), 1000, 1000, 0L, true);
        steering.updateTarget(1, box(100), 1000, 1000, 100L, true);
        int first = steering.position(1, 1000, 1000, 150L).getX();
        int second = steering.position(1, 1000, 1000, 175L).getX();
        int third = steering.position(1, 1000, 1000, 200L).getX();

        assertTrue(first > 0);
        assertTrue(second > first);
        assertTrue(third > second);
        assertTrue(steering.isAnimating(200L));
    }

    @Test
    public void viewportOriginRebasePreservesScreenPosition() {
        ContinuousTrackSteering steering = new ContinuousTrackSteering();
        steering.updateTarget(1, box(300), 1000, 1000, 0L, false);
        int oldScreenX = steering.position(1, 1000, 1000, 0L).getX() - 200;

        steering.offsetAll(-0.2f, 0f, 10L);
        int newScreenX = steering.position(1, 1000, 1000, 10L).getX();

        assertEquals(oldScreenX, newScreenX);
    }

    @Test
    public void removedIdentitiesDoNotLeaveSteeringState() {
        ContinuousTrackSteering steering = new ContinuousTrackSteering();
        steering.updateTarget(1, box(100), 1000, 1000, 0L, false);
        steering.retain(Collections.emptySet());

        assertEquals(0, steering.size());
        assertFalse(steering.isAnimating(100L));
    }

    private static BBox box(int x) {
        return new BBox(x, 200, 100, 100);
    }
}
