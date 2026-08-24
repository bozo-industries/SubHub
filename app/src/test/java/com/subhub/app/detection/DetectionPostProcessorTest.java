package com.subhub.app.detection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public final class DetectionPostProcessorTest {
    @Test
    public void decodesNchwFeatureOutputIntoOriginalFrameCoordinates() {
        float[][] output = outputWithCandidates(1);
        output[0][0] = 160f;
        output[1][0] = 120f;
        output[2][0] = 80f;
        output[3][0] = 100f;
        output[4 + 3][0] = 0.80f; // FEMALE_BREAST_EXPOSED

        List<Detection> detections = new DetectionPostProcessor().decode(
                output, 640, 360, 320, DetectorConfig.builder().build());

        assertEquals(1, detections.size());
        assertEquals("breasts", detections.get(0).getCategory());
        assertEquals(new BBox(234, 132, 172, 216), detections.get(0).getBox());
    }

    @Test
    public void sameCategoryNmsKeepsHigherConfidenceCandidate() {
        float[][] output = outputWithCandidates(2);
        for (int candidate = 0; candidate < 2; candidate++) {
            output[0][candidate] = 100f + candidate;
            output[1][candidate] = 100f + candidate;
            output[2][candidate] = 50f;
            output[3][candidate] = 50f;
            output[4 + 4][candidate] = candidate == 0 ? 0.90f : 0.70f;
        }

        List<Detection> detections = new DetectionPostProcessor().decode(
                output, 320, 320, 320, DetectorConfig.builder().build());

        assertEquals(1, detections.size());
        assertEquals(0.90f, detections.get(0).getConfidence(), 0.0001f);
    }

    private static float[][] outputWithCandidates(int count) {
        float[][] output = new float[DetectionPostProcessor.OUTPUT_FEATURES][count];
        return output;
    }
}
