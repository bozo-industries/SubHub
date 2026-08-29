package com.subhub.app.detection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.FloatBuffer;
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

    @Test
    public void contiguousNativeOutputMatchesNestedArrayDecoder() {
        int candidates = 2;
        float[][] nested = outputWithCandidates(candidates);
        nested[0][0] = 160f;
        nested[1][0] = 120f;
        nested[2][0] = 80f;
        nested[3][0] = 100f;
        nested[7][0] = 0.80f;
        FloatBuffer contiguous = FloatBuffer.allocate(
                DetectionPostProcessor.OUTPUT_FEATURES * candidates);
        for (float[] feature : nested) contiguous.put(feature);
        contiguous.flip();

        DetectionPostProcessor processor = new DetectionPostProcessor();
        List<Detection> expected = processor.decode(
                nested, 640, 360, 320, DetectorConfig.builder().build());
        List<Detection> actual = processor.decode(
                contiguous,
                DetectionPostProcessor.OUTPUT_FEATURES,
                candidates,
                640,
                360,
                320,
                DetectorConfig.builder().build());

        assertEquals(expected.size(), actual.size());
        assertEquals(expected.get(0).getCategory(), actual.get(0).getCategory());
        assertEquals(expected.get(0).getBox(), actual.get(0).getBox());
    }

    private static float[][] outputWithCandidates(int count) {
        float[][] output = new float[DetectionPostProcessor.OUTPUT_FEATURES][count];
        return output;
    }
}
