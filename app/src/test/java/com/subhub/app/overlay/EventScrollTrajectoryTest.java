package com.subhub.app.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public final class EventScrollTrajectoryTest {
    @Test public void timingChangesCannotMoveCurrentTrajectory() {
        EventScrollTrajectory value = new EventScrollTrajectory();
        value.reset(0, 0, 0);
        value.measure(100, 100, 100, 105, 2000);
        float before = value.position(130), velocity = value.velocity(130);
        value.configureTiming(60, 12, 8);
        assertEquals(before, value.position(130), 0);
        assertEquals(velocity, value.velocity(130), 0);
        value.measure(150, 50, 150, 155, 2000);
        float displayed = value.position(160);
        value.configureTiming(0, 0, 0);
        assertEquals(displayed, value.position(160), 0);
    }

    @Test public void learnedCadenceImprovesFirstFrameVelocityForFastProducer() {
        EventScrollTrajectory generic = new EventScrollTrajectory(), learned = new EventScrollTrajectory();
        generic.reset(0, 0, 0); learned.reset(0, 0, 0);
        learned.configureTiming(50, 0, 0);
        generic.measure(100, 100, 100, 100, 2000);
        learned.measure(100, 100, 100, 100, 2000);
        float truth = 110; // independently specified constant 2 px/ms at 5 ms after the event
        assertTrue(Math.abs(learned.position(105) - truth) < Math.abs(generic.position(105) - truth));
        assertEquals(100, learned.position(1000), .001);
    }

    @Test public void excessDeliveryDelayCannotRestartFullForecastLifetime() {
        EventScrollTrajectory normal = new EventScrollTrajectory(), delayed = new EventScrollTrajectory();
        normal.configureTiming(100, 10, 2); delayed.configureTiming(100, 10, 2);
        normal.reset(0, 0, 0); delayed.reset(0, 0, 0);
        normal.measure(100, 100, 100, 110, 2000);
        delayed.measure(100, 100, 100, 310, 2000);
        normal.measure(200, 100, 200, 210, 2000);
        delayed.measure(200, 100, 200, 410, 2000);
        assertTrue(delayed.predictionPeakMillis() < normal.predictionPeakMillis());
        assertTrue(Math.abs(delayed.predictionAmplitude()) <= Math.abs(normal.predictionAmplitude()));
        assertEquals(200, delayed.position(1000), .001);
    }

    @Test public void malformedSettingsRestoreExactGenericBehavior() {
        EventScrollTrajectory normal = new EventScrollTrajectory(), invalid = new EventScrollTrajectory();
        invalid.configureTiming(Float.NaN, 0, 0);
        normal.reset(0, 0, 0); invalid.reset(0, 0, 0);
        normal.measure(-100, -100, 100, 110, 2000);
        invalid.measure(-100, -100, 100, 110, 2000);
        for (long now = 110; now < 500; now += 8) assertEquals(normal.position(now), invalid.position(now), 0);
    }
}
