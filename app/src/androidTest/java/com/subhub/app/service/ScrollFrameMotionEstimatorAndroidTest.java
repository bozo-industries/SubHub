package com.subhub.app.service;

import static org.junit.Assert.assertNotNull;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ScrollFrameMotionEstimatorAndroidTest {
    @Test public void samplesAccessibilityStyleHardwareBitmapWithoutFullReadback() {
        Bitmap software = Bitmap.createBitmap(216, 384, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < software.getHeight(); y++) {
            for (int x = 0; x < software.getWidth(); x++) {
                software.setPixel(x, y, Color.rgb(
                        (x * 17 + y * 7) & 0xff,
                        (x * 5 + y * 19) & 0xff,
                        (x * 23 + y * 3) & 0xff));
            }
        }
        Bitmap hardware = software.copy(Bitmap.Config.HARDWARE, false);
        ScrollFrameMotionEstimator estimator = new ScrollFrameMotionEstimator();
        try {
            assertNotNull(hardware);
            assertNotNull(estimator.update(hardware));
        } finally {
            estimator.close();
            if (hardware != null && !hardware.isRecycled()) hardware.recycle();
            software.recycle();
        }
    }
}
