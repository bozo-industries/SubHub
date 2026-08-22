package com.betasafe.app.detection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BBoxTest {
    @Test
    public void intersectionOverUnionUsesUnionArea() {
        BBox first = new BBox(0, 0, 10, 10);
        BBox second = new BBox(5, 5, 10, 10);

        assertEquals(25f / 175f, first.intersectionOverUnion(second), 0.0001f);
    }

    @Test
    public void paddingClampsToFrameBounds() {
        assertEquals(new BBox(0, 0, 15, 15), new BBox(0, 0, 10, 10).padded(0.5f, 20, 20));
    }
}
