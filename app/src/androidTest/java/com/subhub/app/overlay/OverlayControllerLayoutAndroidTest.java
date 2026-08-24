package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class OverlayControllerLayoutAndroidTest {
    @Test public void censorOverlayUsesFullDisplayCoordinates() {
        WindowManager.LayoutParams params = OverlayController.createLayoutParams(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);

        assertEquals(Gravity.TOP | Gravity.START, params.gravity);
        assertTrue((params.flags & WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(0, params.getFitInsetsTypes());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                    params.layoutInDisplayCutoutMode);
        }
    }
}
