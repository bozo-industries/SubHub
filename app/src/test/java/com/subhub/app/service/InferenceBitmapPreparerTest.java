package com.subhub.app.service;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class InferenceBitmapPreparerTest {
    @Test public void fitsPortraitFrameToUltraLongEdge() {
        assertArrayEquals(new int[]{230, 512},
                InferenceBitmapPreparer.targetDimensions(1080, 2400, 512));
    }

    @Test public void neverUpscalesSmallFrame() {
        assertArrayEquals(new int[]{240, 320},
                InferenceBitmapPreparer.targetDimensions(240, 320, 512));
    }
}
