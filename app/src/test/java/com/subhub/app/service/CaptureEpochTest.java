package com.subhub.app.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.accessibility.AccessibilityEvent;

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

    @Test public void ultraNeverRunsCompetingScreenshotMotion() {
        DetectorConfig ultra = DetectorConfig.builder().inferenceThreads(4).build();

        assertFalse(ScreenshotAccessibilityService.shouldEstimateFrameMotion(
                ultra, 10_000L, 0L));
        assertFalse(ScreenshotAccessibilityService.shouldEstimateFrameMotion(
                ultra, 10_000L, 9_000L));
    }

    @Test public void slowerPresetUsesFrameMotionOnlyWithoutRecentAccessibilitySignal() {
        DetectorConfig balanced = DetectorConfig.builder().inferenceThreads(3).build();

        assertTrue(ScreenshotAccessibilityService.shouldEstimateFrameMotion(
                balanced, 10_000L, 0L));
        assertFalse(ScreenshotAccessibilityService.shouldEstimateFrameMotion(
                balanced, 10_000L, 9_500L));
        assertTrue(ScreenshotAccessibilityService.shouldEstimateFrameMotion(
                balanced, 10_000L, 9_000L));
    }

    @Test public void textScanMustMatchCurrentMotionGeneration() {
        assertTrue(ScreenshotAccessibilityService.shouldPublishTextScan(
                12L, 12L, 40L, 80L, 40L, 80L));
        assertFalse(ScreenshotAccessibilityService.shouldPublishTextScan(
                12L, 13L, 40L, 80L, 40L, 80L));
        assertFalse(ScreenshotAccessibilityService.shouldPublishTextScan(
                12L, 12L, 40L, 80L, 40L, 120L));
    }

    @Test public void postScrollTextScanDropsMissesAndRetriesAfterSettle() {
        assertFalse(ScreenshotAccessibilityService.shouldBridgeTextMisses(10_500L, 10_000L));
        assertTrue(ScreenshotAccessibilityService.shouldBridgeTextMisses(10_900L, 10_000L));
        assertEquals(190L,
                ScreenshotAccessibilityService.textRefreshDelayAfterMotion(10_050L, 10_000L));
        assertEquals(0L,
                ScreenshotAccessibilityService.textRefreshDelayAfterMotion(10_250L, 10_000L));
        assertEquals(190L, ScreenshotAccessibilityService.textScanStartDelayMs(
                10_050L, 10_000L, 20_000L, 19_000L));
        assertEquals(80L, ScreenshotAccessibilityService.textScanStartDelayMs(
                10_250L, 10_000L, 20_000L, 19_960L));
        assertEquals(0L, ScreenshotAccessibilityService.textScanStartDelayMs(
                10_250L, 10_000L, 20_000L, 19_800L));
    }

    @Test public void textRefreshIgnoresStateOnlyAccessibilityChurn() {
        assertTrue(ScreenshotAccessibilityService.isTextRelevantContentChange(
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED));
        assertTrue(ScreenshotAccessibilityService.isTextRelevantContentChange(
                AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT));
        assertTrue(ScreenshotAccessibilityService.isTextRelevantContentChange(
                AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE));
        assertFalse(ScreenshotAccessibilityService.isTextRelevantContentChange(
                AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION));
        assertFalse(ScreenshotAccessibilityService.isTextRelevantContentChange(
                AccessibilityEvent.CONTENT_CHANGE_TYPE_ENABLED));
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

    @Test public void realtimeDetectorKeepsQualitySettingsAtA320PixelInput() {
        DetectorConfig quality = DetectorConfig.builder()
                .inferenceResolution(512)
                .confidenceThreshold(0.18f)
                .inferenceThreads(4)
                .detectionIntervalMs(90L)
                .build();

        DetectorConfig fast = ScreenshotAccessibilityService.fastDetectorConfig(quality);

        assertEquals(320, fast.getInferenceResolution());
        assertEquals(0L, fast.getDetectionIntervalMs());
        assertEquals(4, fast.getInferenceThreads());
        assertEquals(0.18f, fast.getConfidenceThreshold(), 0.001f);
        assertEquals(512, quality.getInferenceResolution());
    }

    @Test public void accessibilityTrackerDisablesCompetingVelocityPrediction() {
        DetectorConfig configured = DetectionPreset.ULTRA.applyTo(
                DetectorConfig.builder()).build();

        DetectorConfig tracker = ScreenshotAccessibilityService
                .accessibilityTrackerConfig(configured);

        assertFalse(tracker.isMotionPrediction());
        assertEquals(0f, tracker.getVelocitySmoothing(), 0f);
        assertEquals(0f, tracker.getMaxExtrapolationMs(), 0f);
        assertEquals(configured.getTrackingSmoothing(),
                tracker.getTrackingSmoothing(), 0f);
    }

    @Test public void qualityRefinementWaitsOutsideTheCriticalScrollWindow() {
        assertFalse(ScreenshotAccessibilityService.shouldRunQualityRefinement(
                10_849L, 10_000L, 0L, true, true));
        assertTrue(ScreenshotAccessibilityService.shouldRunQualityRefinement(
                10_850L, 10_000L, 0L, true, true));
        assertFalse(ScreenshotAccessibilityService.shouldRunQualityRefinement(
                11_500L, 10_000L, 11_000L, true, false));
        assertTrue(ScreenshotAccessibilityService.shouldRunQualityRefinement(
                12_000L, 10_000L, 11_000L, true, false));
        assertTrue(ScreenshotAccessibilityService.shouldRunQualityRefinement(
                100L, 0L, 0L, false, true));
    }
}
