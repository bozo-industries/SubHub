package com.subhub.app.detection;

import static org.junit.Assert.*;
import java.nio.FloatBuffer;
import org.junit.Test;

public final class SharedModelImageTest {
    @Test public void snapshotDetachesReusableBufferAndExcludesPadding() {
        int[] reused = {0xff112233, 0xff000000, 0xff445566, 0xff000000};
        SharedModelImage image = SharedModelImage.copyContent(reused, 2, 1, 2);
        java.util.Arrays.fill(reused, 0);
        int[] actual = new int[2];
        image.copyTopLeft(actual, 1, 2);
        assertArrayEquals(new int[]{0xff112233, 0xff445566}, actual);
    }

    @Test public void topLeftCopyPreservesPixelsAndBlackPadding() {
        SharedModelImage image = SharedModelImage.takeOwnership(new int[]{0xff112233, 0xff445566}, 1, 2);
        int[] target = new int[6];
        image.copyTopLeft(target, 3, 2);
        assertArrayEquals(new int[]{0xff112233, 0xff000000, 0xff000000,
                0xff445566, 0xff000000, 0xff000000}, target);
    }

    @Test public void personAdapterUsesRgbAndCenteredNormalizedBlackPadding() {
        SharedModelImage image = SharedModelImage.takeOwnership(new int[]{0xffff0000, 0xffff0000}, 1, 2);
        FloatBuffer tensor = FloatBuffer.allocate(3 * 416 * 416);
        image.writePersonTensor(tensor, new PersonBoxDecoder.Letterbox(100, 200));
        assertEquals(-103.53f / 57.375f, tensor.get(0), 1e-6f);
        assertEquals((255 - 103.53f) / 57.375f, tensor.get(104), 1e-6f);
        assertEquals(-116.28f / 57.12f, tensor.get(416 * 416 + 104), 1e-6f);
        assertEquals(-123.675f / 58.395f, tensor.get(2 * 416 * 416 + 104), 1e-6f);
        assertEquals(0, tensor.position());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesWrongDimensions() {
        SharedModelImage.takeOwnership(new int[3], 2, 2);
    }
}
