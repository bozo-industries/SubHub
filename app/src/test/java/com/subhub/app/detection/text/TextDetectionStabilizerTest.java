package com.subhub.app.detection.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;

import org.junit.Test;

import java.util.Collections;

public final class TextDetectionStabilizerTest {
    @Test public void oneScanFalsePositiveNeverPublishes() {
        TextDetectionStabilizer stabilizer = new TextDetectionStabilizer();
        assertTrue(stabilizer.update(Collections.singletonList(text("line", 10))).isEmpty());
        assertTrue(stabilizer.update(Collections.emptyList()).isEmpty());
    }

    @Test public void repeatedAnchorPublishesAndBridgesOneMiss() {
        TextDetectionStabilizer stabilizer = new TextDetectionStabilizer();
        stabilizer.update(Collections.singletonList(text("line", 10)));
        assertEquals(1, stabilizer.update(
                Collections.singletonList(text("line", 60))).size());
        assertEquals(1, stabilizer.update(Collections.emptyList()).size());
        assertTrue(stabilizer.update(Collections.emptyList()).isEmpty());
    }

    @Test public void postScrollReconciliationDoesNotRetainMissingConfirmedLine() {
        TextDetectionStabilizer stabilizer = new TextDetectionStabilizer();
        stabilizer.update(Collections.singletonList(text("line", 10)), false);
        assertEquals(1, stabilizer.update(
                Collections.singletonList(text("line", 60)), false).size());

        assertTrue(stabilizer.update(Collections.emptyList(), false).isEmpty());
    }

    private static Detection text(String anchor, int top) {
        return new Detection("TEXT_SMUT_ACCESSIBILITY_EXPLICIT", "text_smut", 0.9f,
                new BBox(20, top, 300, 40), true, false,
                Detection.ObservationSource.ACCESSIBILITY,
                Detection.GeometryQuality.EXACT, anchor);
    }
}
