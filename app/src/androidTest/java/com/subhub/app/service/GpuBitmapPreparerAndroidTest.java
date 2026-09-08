package com.subhub.app.service;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.filters.SdkSuppress;
import org.junit.Test;
import static org.junit.Assert.*;

public final class GpuBitmapPreparerAndroidTest {
    @SdkSuppress(minSdkVersion = 29)
    @Test public void alternatingShapesAndAlphaNeverReturnPreviousPixelsOrOwnTheSource() {
        for (int i = 0; i < 12; i++) {
            int width = i % 2 == 0 ? 300 : 800;
            int height = i % 2 == 0 ? 800 : 300;
            int color = i % 2 == 0 ? Color.RED : 0x800000ff;
            Bitmap software = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            software.eraseColor(color);
            Bitmap hardware = software.copy(Bitmap.Config.HARDWARE, false);
            assertNotNull(hardware);
            try {
                InferenceBitmapPreparer.Prepared value = GpuBitmapPreparer.prepare(hardware, 320, true);
                assertNotNull(value);
                try {
                    int[] size = InferenceBitmapPreparer.targetDimensions(width, height, 320);
                    assertEquals(size[0], value.bitmap.getWidth());
                    assertEquals(size[1], value.bitmap.getHeight());
                    assertEquals(color, value.bitmap.getPixel(size[0] / 2, size[1] / 2));
                    assertTrue(value.retainedSourceFrame);
                    assertFalse(hardware.isRecycled());
                    assertEquals(Bitmap.Config.ARGB_8888, value.bitmap.getConfig());
                } finally { value.bitmap.recycle(); }
            } finally { hardware.recycle(); software.recycle(); }
        }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test public void unsupportedSourcesReturnFallbackWithoutTakingOwnership() {
        assertNull(GpuBitmapPreparer.prepare(null, 320, false));
        Bitmap source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        assertNull(GpuBitmapPreparer.prepare(source, 320, false));
        assertFalse(source.isRecycled());
        source.recycle();
        assertNull(GpuBitmapPreparer.prepare(source, 320, false));
    }
}
