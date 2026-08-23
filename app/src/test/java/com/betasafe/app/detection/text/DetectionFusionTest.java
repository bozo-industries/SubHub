package com.betasafe.app.detection.text;

import static org.junit.Assert.assertEquals;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DetectionFusionTest {
    @Test public void overlappingAccessibilityAndVisualTextBecomeOneRegion() {
        Detection accessibility = text(new BBox(100, 200, 300, 80));
        Detection visualText = text(new BBox(105, 205, 290, 70));
        List<Detection> merged = DetectionFusion.merge(
                Collections.emptyList(),
                Collections.singletonList(accessibility),
                Collections.singletonList(visualText));
        assertEquals(1, merged.size());
        assertEquals(new BBox(100, 200, 300, 80), merged.get(0).getBox());
    }

    @Test public void adjacentExplicitLinesBecomeOneStablePostRegion() {
        Detection first = text(new BBox(80, 300, 500, 48));
        Detection second = text(new BBox(90, 354, 480, 48));
        List<Detection> merged = DetectionFusion.merge(
                Collections.emptyList(), Arrays.asList(first, second));
        assertEquals(1, merged.size());
        assertEquals(new BBox(80, 300, 500, 102), merged.get(0).getBox());
    }

    @Test public void distantPostsRemainSeparate() {
        List<Detection> merged = DetectionFusion.merge(
                Collections.emptyList(),
                Arrays.asList(
                        text(new BBox(80, 300, 500, 48)),
                        text(new BBox(80, 700, 500, 48))));
        assertEquals(2, merged.size());
    }

    private static Detection text(BBox box) {
        return new Detection("TEXT_SMUT_EXPLICIT", "text_smut", 0.9f, box, true, false);
    }
}
