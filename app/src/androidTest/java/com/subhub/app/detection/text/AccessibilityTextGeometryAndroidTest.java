package com.subhub.app.detection.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;

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

    @Test public void exactRenderedLineIsNotProjectedAgain() {
        String text = "ordinary first line that is safe touch yourself like a needy pet";
        SmutTextClassifier classifier = new SmutTextClassifier();
        SmutTextClassifier.Match match = classifier.classify(text, new TextSmutConfig(
                true, TextSmutConfig.SENSITIVITY_BALANCED,
                TextSmutConfig.DEFAULT_CATEGORIES));

        Detection result = new TextSmutDetectionFactory(classifier).createExact(
                new Rect(64, 420, 512, 458), match, 1080, 2400,
                "TEXT_SMUT_ACCESSIBILITY_");

        assertNotNull(result);
        assertEquals(new BBox(64, 420, 448, 38), result.getBox());
    }
}
