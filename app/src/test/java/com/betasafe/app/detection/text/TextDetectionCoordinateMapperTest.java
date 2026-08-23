package com.betasafe.app.detection.text;

import static org.junit.Assert.assertEquals;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class TextDetectionCoordinateMapperTest {
    @Test public void windowCaptureScaleRoundTripsToOriginalScreenPosition() {
        Detection screenDetection = new Detection(
                "TEXT_SMUT_FETISH", "text_smut", 0.9f,
                new BBox(80, 1200, 920, 64), true, false);

        List<Detection> mapped = TextDetectionCoordinateMapper.screenToCapture(
                Collections.singletonList(screenDetection), 1080, 2400, 1080, 2268);

        BBox capture = mapped.get(0).getBox();
        assertEquals(1134, capture.getY());
        assertEquals(60, capture.getHeight());
        assertEquals(1200, Math.round(capture.getY() * (2400f / 2268f)));
        assertEquals(1263, Math.round(capture.getBottom() * (2400f / 2268f)));
    }

    @Test public void fullDisplayCapturePreservesTextBounds() {
        BBox original = new BBox(80, 900, 920, 64);
        Detection detection = new Detection(
                "TEXT_SMUT_EXPLICIT", "text_smut", 0.9f, original, true, false);

        List<Detection> mapped = TextDetectionCoordinateMapper.screenToCapture(
                Collections.singletonList(detection), 1080, 2400, 1080, 2400);

        assertEquals(original, mapped.get(0).getBox());
    }
}
