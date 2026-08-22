package com.betasafe.app.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;
import com.betasafe.app.settings.CensorAppearance;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class CensorRendererTest {
    @Test
    public void solidCensorChangesOnlyThePaddedDetectionRegion() {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap source = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.WHITE);
        Bitmap target = source.copy(Bitmap.Config.ARGB_8888, true);
        Detection detection = new Detection(
                "FEMALE_BREAST_EXPOSED", "breasts", .95f,
                new BBox(40, 40, 40, 40), true, true);
        CensorAppearance appearance = new CensorAppearance(
                CensorAppearance.Type.BOX, 100, false, false, Color.MAGENTA);

        try (CensorRenderer renderer = new CensorRenderer(context)) {
            renderer.draw(target, source, Collections.singletonList(detection), appearance);
            assertNotEquals(Color.WHITE, target.getPixel(60, 60));
            assertEquals(Color.WHITE, target.getPixel(2, 2));
        } finally {
            source.recycle();
            target.recycle();
        }
    }
}
