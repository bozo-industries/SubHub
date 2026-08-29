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
        assertEquals(334L, ScreenshotAccessibilityService.capturePollDelayMs(low));
        assertEquals(334L, ScreenshotAccessibilityService.capturePollDelayMs(medium));
        assertEquals(334L, ScreenshotAccessibilityService.capturePollDelayMs(high));
        assertEquals(334L, ScreenshotAccessibilityService.capturePollDelayMs(ultra));
    }

    @Test public void settledCaptureHonorsMotionAndPlatformGates() {
        assertEquals(130L, ScreenshotAccessibilityService.settledCaptureDelayMs(
                1_000L, 1_000L, 500L));
        assertEquals(234L, ScreenshotAccessibilityService.settledCaptureDelayMs(
                1_000L, 700L, 900L));
        assertEquals(0L, ScreenshotAccessibilityService.settledCaptureDelayMs(
                1_000L, 800L, 600L));
    }

    @Test public void onlyUltraKeepsInferenceAliveDuringMotion() {
        DetectorConfig high = DetectorConfig.builder().inferenceThreads(3).build();
        DetectorConfig ultra = DetectorConfig.builder().inferenceThreads(4).build();

        assertFalse(ScreenshotAccessibilityService.usesContinuousMotionInference(high));
        assertTrue(ScreenshotAccessibilityService.usesContinuousMotionInference(ultra));
    }

    @Test public void semanticTextAndOcrAreGatedByPresetCost() {
        DetectorConfig medium = DetectorConfig.builder().inferenceThreads(2).build();
        DetectorConfig high = DetectorConfig.builder().inferenceThreads(3).build();
        DetectorConfig ultra = DetectorConfig.builder().inferenceThreads(4).build();

        assertFalse(ScreenshotAccessibilityService.usesSemanticTextModel(medium));
        assertTrue(ScreenshotAccessibilityService.usesSemanticTextModel(high));
        assertTrue(ScreenshotAccessibilityService.usesSemanticTextModel(ultra));
        assertFalse(ScreenshotAccessibilityService.usesScreenshotOcr(medium));
        assertFalse(ScreenshotAccessibilityService.usesScreenshotOcr(high));
        assertTrue(ScreenshotAccessibilityService.usesScreenshotOcr(ultra));
    }

    @Test public void ocrWaitsForMotionAndUsesShortConfirmationBursts() {
        assertEquals(0L, ScreenshotAccessibilityService.ocrDelayMs(
                10_000L, 0L, 0L, false));
        assertEquals(3_000L, ScreenshotAccessibilityService.ocrDelayMs(
                10_000L, 10_000L, 0L, false));
        assertEquals(650L, ScreenshotAccessibilityService.ocrDelayMs(
                10_000L, 10_000L, 0L, true));
        assertEquals(600L, ScreenshotAccessibilityService.ocrDelayMs(
                10_000L, 0L, 10_000L, false));
    }

    @Test public void accessibilityTextOwnsNativeCaptionsBeforeOcr() {
        DetectorConfig ultra = DetectorConfig.builder().inferenceThreads(4).build();

        assertFalse(ScreenshotAccessibilityService.ocrEligibleForViewport(
                ultra, true, false, false));
        assertTrue(ScreenshotAccessibilityService.ocrEligibleForViewport(
                ultra, true, true, false));
        assertFalse(ScreenshotAccessibilityService.ocrEligibleForViewport(
                ultra, true, true, true));
        assertFalse(ScreenshotAccessibilityService.sameOcrViewport(4L, 5L, false));
        assertFalse(ScreenshotAccessibilityService.sameOcrViewport(5L, 5L, true));
        assertTrue(ScreenshotAccessibilityService.sameOcrViewport(5L, 5L, false));
    }

    @Test public void ocrSnapshotsExpireInsteadOfFlashingLongAfterTheirViewport() {
        assertTrue(ScreenshotAccessibilityService.isOcrSnapshotFresh(5_000L, 1_000L));
        assertFalse(ScreenshotAccessibilityService.isOcrSnapshotFresh(6_001L, 1_000L));
        assertFalse(ScreenshotAccessibilityService.isOcrSnapshotFresh(500L, 1_000L));
    }
}
