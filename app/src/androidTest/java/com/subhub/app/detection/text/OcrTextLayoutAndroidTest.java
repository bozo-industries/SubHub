package com.subhub.app.detection.text;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class OcrTextLayoutAndroidTest {
    @Test public void wrappedPhraseProducesOneExactBarPerRenderedLine() {
        SmutTextClassifier classifier = new SmutTextClassifier();
        List<Detection> result = OcrTextLayout.classify(List.of(
                        new OcrTextLayout.Line("open your", new Rect(80, 300, 340, 344)),
                        new OcrTextLayout.Line("legs for me", new Rect(80, 352, 380, 396))),
                balanced(), new TextSmutDetectionFactory(classifier), 1080, 2400);

        assertEquals(2, result.size());
        assertEquals(new BBox(80, 300, 260, 44), result.get(0).getBox());
        assertEquals(new BBox(80, 352, 300, 44), result.get(1).getBox());
    }

    @Test public void unrelatedAdjacentLinesDoNotBecomeOneOversizedRegion() {
        SmutTextClassifier classifier = new SmutTextClassifier();
        List<Detection> result = OcrTextLayout.classify(List.of(
                        new OcrTextLayout.Line("send nudes", new Rect(80, 300, 340, 344)),
                        new OcrTextLayout.Line("ordinary footer", new Rect(80, 352, 380, 396))),
                balanced(), new TextSmutDetectionFactory(classifier), 1080, 2400);

        assertEquals(1, result.size());
        assertEquals(new BBox(80, 300, 260, 44), result.get(0).getBox());
    }

    private static TextSmutConfig balanced() {
        return new TextSmutConfig(true, TextSmutConfig.SENSITIVITY_BALANCED,
                TextSmutConfig.DEFAULT_CATEGORIES);
    }
}
