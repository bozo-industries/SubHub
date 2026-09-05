package com.subhub.app.service;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Timing must preserve dimensions, source ownership and software output. */
@RunWith(AndroidJUnit4.class)
public final class InferencePreparationTimingAndroidTest {
    @Test public void softwarePreparationDoesNotClaimHardwareReadback() {
        verify(false);
    }

    @Test public void hardwarePreparationReportsNonnegativeStageTimes() {
        verify(true);
    }

    private void verify(boolean hardware) {
        Bitmap software = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888);
        Bitmap source = hardware ? software.copy(Bitmap.Config.HARDWARE, false) : software;
        assertNotNull(source);
        InferenceBitmapPreparer.Prepared result = null;
        try {
            result = InferenceBitmapPreparer.prepare(source, 160, false);
            assertNotNull(result);
            assertEquals(80, result.bitmap.getWidth());
            assertEquals(160, result.bitmap.getHeight());
            assertEquals(Bitmap.Config.ARGB_8888, result.bitmap.getConfig());
            assertFalse(source.isRecycled());
            assertTrue(result.scaleNanos >= 0L);
            assertTrue(result.readbackNanos >= 0L);
            if (!hardware) assertFalse(result.hardwareReadback);
        } finally {
            if (result != null) result.bitmap.recycle();
            if (source != software) source.recycle();
            software.recycle();
        }
    }
}
