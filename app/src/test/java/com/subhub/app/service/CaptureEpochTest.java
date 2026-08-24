package com.subhub.app.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.subhub.app.detection.DetectionPreset;
import com.subhub.app.detection.DetectorConfig;

import org.junit.Test;

public final class CaptureEpochTest {
    @Test public void foregroundTransitionRejectsAnInFlightFrame() {
        CaptureEpoch epoch = new CaptureEpoch();
        long inFlight = epoch.token();
        assertTrue(epoch.accepts(inFlight, true, true));

        epoch.invalidate();

        assertFalse(epoch.accepts(inFlight, true, true));
        assertTrue(epoch.accepts(epoch.token(), true, true));
    }

    @Test public void inactiveCaptureNeverAcceptsAResult() {
        CaptureEpoch epoch = new CaptureEpoch();
        assertFalse(epoch.accepts(epoch.token(), false, true));
        assertFalse(epoch.accepts(epoch.token(), true, false));
    }

    @Test public void captureCadencePreservesForegroundResponsivenessAcrossPresets() {
        DetectorConfig low = DetectionPreset.LOW.applyTo(DetectorConfig.builder()).build();
        DetectorConfig medium = DetectionPreset.MEDIUM.applyTo(DetectorConfig.builder()).build();
        DetectorConfig high = DetectionPreset.HIGH.applyTo(DetectorConfig.builder()).build();
        DetectorConfig ultra = DetectionPreset.ULTRA.applyTo(DetectorConfig.builder()).build();

        assertEquals(450L, ScreenshotAccessibilityService.captureDelayMs(low));
        assertEquals(300L, ScreenshotAccessibilityService.captureDelayMs(medium));
        assertEquals(240L, ScreenshotAccessibilityService.captureDelayMs(high));
        assertEquals(180L, ScreenshotAccessibilityService.captureDelayMs(ultra));
        assertEquals(350L, ScreenshotAccessibilityService.capturePollDelayMs(low));
        assertEquals(350L, ScreenshotAccessibilityService.capturePollDelayMs(medium));
        assertEquals(350L, ScreenshotAccessibilityService.capturePollDelayMs(high));
        assertEquals(350L, ScreenshotAccessibilityService.capturePollDelayMs(ultra));
    }

    @Test public void settledCaptureHonorsMotionAndPlatformGates() {
        assertEquals(130L, ScreenshotAccessibilityService.settledCaptureDelayMs(
                1_000L, 1_000L, 500L));
        assertEquals(250L, ScreenshotAccessibilityService.settledCaptureDelayMs(
                1_000L, 700L, 900L));
        assertEquals(0L, ScreenshotAccessibilityService.settledCaptureDelayMs(
                1_000L, 800L, 600L));
    }
}
