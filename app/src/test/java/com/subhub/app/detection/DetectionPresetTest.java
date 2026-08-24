package com.subhub.app.detection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class DetectionPresetTest {
    @Test
    public void recoveredPresetValuesAreApplied() {
        DetectorConfig low = DetectionPreset.LOW.applyTo(DetectorConfig.builder()).build();
        DetectorConfig ultra = DetectionPreset.ULTRA.applyTo(DetectorConfig.builder()).build();

        assertEquals(0.38f, low.getConfidenceThreshold(), 0.0001f);
        assertEquals(150, low.getDetectionIntervalMs());
        assertEquals(0.35f, low.getCaptureScale(), 0.0001f);
        assertEquals(320, low.getInferenceResolution());
        assertEquals(0.18f, ultra.getConfidenceThreshold(), 0.0001f);
        assertEquals(512, ultra.getInferenceResolution());
        assertEquals(0.75f, ultra.getCaptureScale(), 0.0001f);
        assertEquals(1, low.getInferenceThreads());
        assertEquals(4, ultra.getInferenceThreads());
        assertTrue(DetectionPreset.LOW.getCustomImageDimension()
                < DetectionPreset.ULTRA.getCustomImageDimension());
        assertTrue(DetectionPreset.LOW.getCustomImageCount()
                < DetectionPreset.ULTRA.getCustomImageCount());
    }

    @Test
    public void preferenceParsingIsCaseInsensitiveAndSafe() {
        assertSame(DetectionPreset.HIGH, DetectionPreset.fromPreference("High"));
        assertSame(DetectionPreset.ULTRA, DetectionPreset.fromPreference("ultra"));
        assertSame(DetectionPreset.MEDIUM, DetectionPreset.fromPreference("unknown"));
        assertEquals("low", DetectionPreset.LOW.preferenceValue());
    }
}
