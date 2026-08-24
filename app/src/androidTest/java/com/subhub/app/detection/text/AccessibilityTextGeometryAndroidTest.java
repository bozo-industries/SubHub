package com.subhub.app.detection.text;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AccessibilityTextGeometryAndroidTest {
    @Test public void matchedCharactersExpandToOnlyTheirRenderedLine() {
        RectF[] characters = new RectF[12];
        for (int index = 0; index < 6; index++) {
            characters[index] = new RectF(50 + index * 20, 100, 68 + index * 20, 128);
        }
        for (int index = 6; index < 12; index++) {
            characters[index] = new RectF(50 + (index - 6) * 20,
                    140, 68 + (index - 6) * 20, 168);
        }

        Rect result = AccessibilityTextGeometry.lineBounds(
                characters, 8, 11, 500, 900);

        assertEquals(new Rect(48, 138, 170, 170), result);
    }
}
