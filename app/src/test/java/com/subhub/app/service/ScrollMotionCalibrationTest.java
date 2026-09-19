package com.subhub.app.service;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ScrollMotionCalibrationTest {
    private static AutomaticScrollLearningObserver.Scope scope(ScrollLearningKey.Axis axis) {
        return new AutomaticScrollLearningObserver.Scope("com.example.app", 1, 1, 10,
                7, 1000, 2000, 420, 0, 60000, axis, ScrollLearningKey.Evidence.EXPLICIT);
    }
    private static ScrollCalibrationLearner.Profile profile(double scale, ScrollLearningKey.Axis axis) {
        ScrollLearningKey key = new ScrollLearningKey("com.example.app", 1, "a".repeat(64),
                1000, 2000, 420, 0, 60000, axis, ScrollLearningKey.Evidence.EXPLICIT);
        return new ScrollCalibrationLearner.Profile(key, scale, 100, 8, 4, 0, 12, 6, 3);
    }

    @Test public void activationAndRevocationChangeOnlyFutureIncrements() {
        ScrollMotionCalibration calibration = new ScrollMotionCalibration();
        long camera = 100000;
        ScrollMotionCalibration.Motion first = calibration.apply(scope(ScrollLearningKey.Axis.Y),
                profile(2, ScrollLearningKey.Axis.Y), 0, -100);
        assertTrue(first.scaleChanged); assertTrue(first.calibrated);
        camera -= first.dy;
        assertEquals(100200, camera);
        ScrollMotionCalibration.Motion next = calibration.apply(scope(ScrollLearningKey.Axis.Y),
                profile(2, ScrollLearningKey.Axis.Y), 0, -100);
        assertFalse(next.scaleChanged); camera -= next.dy;
        ScrollMotionCalibration.Motion revoked = calibration.apply(scope(ScrollLearningKey.Axis.Y), null, 0, -100);
        assertTrue(revoked.scaleChanged); assertFalse(revoked.calibrated); camera -= revoked.dy;
        assertEquals(100500, camera);
    }

    @Test public void fractionalRoundingDoesNotLoseDistanceInEitherAxisOrDirection() {
        for (ScrollLearningKey.Axis axis : ScrollLearningKey.Axis.values()) {
            for (int sign : new int[]{-1, 1}) {
                ScrollMotionCalibration calibration = new ScrollMotionCalibration();
                int total = 0;
                for (int index = 0; index < 100; index++) {
                    ScrollMotionCalibration.Motion value = calibration.apply(scope(axis), profile(.125, axis),
                            axis == ScrollLearningKey.Axis.X ? sign : 0,
                            axis == ScrollLearningKey.Axis.Y ? sign : 0);
                    total += axis == ScrollLearningKey.Axis.X ? value.dx : value.dy;
                }
                assertEquals(sign * 12.5, total, .5);
            }
        }
    }

    @Test public void diagonalAndWrongAxisNeverUseSingleAxisMapping() {
        ScrollMotionCalibration calibration = new ScrollMotionCalibration();
        assertFalse(calibration.apply(scope(ScrollLearningKey.Axis.Y), profile(2, ScrollLearningKey.Axis.Y),
                2, 5).calibrated);
        assertFalse(calibration.apply(scope(ScrollLearningKey.Axis.X), profile(2, ScrollLearningKey.Axis.Y),
                5, 0).calibrated);
    }

    @Test public void exceptionalScaledDeltaFallsBackInsteadOfBeingSilentlyClamped() {
        ScrollMotionCalibration calibration = new ScrollMotionCalibration();
        ScrollMotionCalibration.Motion value = calibration.apply(scope(ScrollLearningKey.Axis.Y),
                profile(8, ScrollLearningKey.Axis.Y), 0, 1000);
        assertFalse(value.calibrated); assertFalse(value.scaleChanged); assertEquals(1000, value.dy);
        calibration.apply(scope(ScrollLearningKey.Axis.Y), profile(8, ScrollLearningKey.Axis.Y), 0, 100);
        value = calibration.apply(scope(ScrollLearningKey.Axis.Y), profile(8, ScrollLearningKey.Axis.Y), 0, 1000);
        assertFalse(value.calibrated); assertTrue(value.scaleChanged);
    }

    @Test public void malformedAndUnvalidatedProfilesFailClosed() {
        for (double scale : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, .1, 9}) {
            assertFalse(new ScrollMotionCalibration().apply(scope(ScrollLearningKey.Axis.Y),
                    profile(scale, ScrollLearningKey.Axis.Y), 0, 100).calibrated);
        }
        ScrollCalibrationLearner.Profile good = profile(2, ScrollLearningKey.Axis.Y);
        ScrollCalibrationLearner.Profile unvalidated = new ScrollCalibrationLearner.Profile(good.key,
                2, 100, 0, 0, 0, 12, 5, 2);
        assertFalse(new ScrollMotionCalibration().apply(scope(ScrollLearningKey.Axis.Y), unvalidated, 0, 100).calibrated);
    }

    @Test public void resetDiscardsFractionalDebtAndCalibration() {
        ScrollMotionCalibration calibration = new ScrollMotionCalibration();
        calibration.apply(scope(ScrollLearningKey.Axis.Y), profile(.125, ScrollLearningKey.Axis.Y), 0, 3);
        calibration.reset();
        assertEquals(0, calibration.apply(scope(ScrollLearningKey.Axis.Y),
                profile(.125, ScrollLearningKey.Axis.Y), 0, 3).dy);
    }

    @Test public void rejectedMappingDoesNotOscillateBetweenCoordinateSystems() {
        ScrollMotionCalibration calibration = new ScrollMotionCalibration();
        ScrollCalibrationLearner.Profile profile = profile(8, ScrollLearningKey.Axis.Y);
        calibration.apply(scope(ScrollLearningKey.Axis.Y), profile, 0, 1000);
        ScrollMotionCalibration.Motion later = calibration.apply(scope(ScrollLearningKey.Axis.Y), profile, 0, 100);
        assertFalse(later.calibrated); assertFalse(later.scaleChanged); assertEquals(100, later.dy);
    }
}
