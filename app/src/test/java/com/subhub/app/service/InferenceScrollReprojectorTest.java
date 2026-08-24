package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;

import org.junit.Test;

import java.util.List;

public final class InferenceScrollReprojectorTest {
    @Test public void scrollDownMovesOldScreenshotDetectionUpToLivePosition() {
        Detection detection = detection(new BBox(200, 800, 300, 400));

        List<Detection> shifted = InferenceScrollReprojector.toCurrentViewport(
                List.of(detection), 1080, 2400, 1080, 2400,
                100L, 400L, 100L, 580L);

        assertEquals(new BBox(200, 620, 300, 400), shifted.get(0).getBox());
    }

    @Test public void projectionScalesScreenMotionIntoCaptureCoordinates() {
        Detection detection = detection(new BBox(100, 200, 80, 120));

        List<Detection> shifted = InferenceScrollReprojector.toCurrentViewport(
                List.of(detection), 540, 1200, 1080, 2400,
                0L, 0L, 0L, 200L);

        assertEquals(new BBox(100, 100, 80, 120), shifted.get(0).getBox());
    }

    @Test public void offscreenResultsAreDroppedAndVisibleEdgesAreClipped() {
        List<Detection> shifted = InferenceScrollReprojector.toCurrentViewport(
                List.of(
                        detection(new BBox(10, 10, 50, 50)),
                        detection(new BBox(100, 180, 100, 80))),
                300, 240, 300, 240,
                0L, 0L, 0L, 80L);

        assertEquals(1, shifted.size());
        assertEquals(new BBox(100, 100, 100, 80), shifted.get(0).getBox());
    }

    @Test public void unchangedScrollReusesDetectionList() {
        List<Detection> detections = List.of(detection(new BBox(10, 10, 50, 50)));
        assertSame(detections, InferenceScrollReprojector.toCurrentViewport(
                detections, 300, 240, 300, 240,
                10L, 20L, 10L, 20L));
    }

    private static Detection detection(BBox box) {
        return new Detection("EXPOSED_TEST", "EXPOSED", 1f, box, true, true);
    }
}
