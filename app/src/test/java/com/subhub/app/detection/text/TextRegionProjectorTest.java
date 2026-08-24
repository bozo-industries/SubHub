package com.subhub.app.detection.text;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.subhub.app.detection.BBox;

import org.junit.Test;

public final class TextRegionProjectorTest {
    private final SmutTextClassifier classifier = new SmutTextClassifier();

    @Test public void matchOnSecondLineProjectsToSecondLineInsteadOfWholeNode() {
        String text = "ordinary first line that is safe touch yourself like a needy pet";
        BBox box = project(text, 70, 700, 1010, 810);

        assertNotNull(box);
        assertTrue("Expected lower line, got " + box, box.getY() >= 748);
        assertTrue("Expected a line-height box, got " + box, box.getHeight() <= 65);
        assertTrue(box.getBottom() <= 812);
    }

    @Test public void coarsePostDescriptionIsReducedToTextHeight() {
        String text = "Queen Kim profile I love putting a chastity cage on a dog too much "
                + "walking away with the key and leaving him locked up for 15 days";
        BBox box = project(text, 70, 850, 1010, 1700);

        assertNotNull(box);
        assertTrue("Coarse parent must not become a giant censor: " + box,
                box.getHeight() <= 210);
        assertTrue(box.getY() >= 850);
        assertTrue(box.getBottom() < 1300);
    }

    @Test public void multiSignalDsBlockCanCoverItsMatchedLines() {
        String text = "Good girl\nDaddy will spoil you\nYou're behaving well\nYou earned a reward";
        BBox box = project(text, 80, 300, 980, 520);

        assertNotNull(box);
        assertTrue(box.getHeight() >= 100);
        assertTrue(box.getHeight() <= 225);
    }

    private BBox project(String text, int left, int top, int right, int bottom) {
        SmutTextClassifier.Match match = classifier.classify(text, new TextSmutConfig(
                true, TextSmutConfig.SENSITIVITY_BALANCED,
                TextSmutConfig.DEFAULT_CATEGORIES));
        assertTrue(match.isMatched());
        return TextRegionProjector.project(
                text, match, left, top, right, bottom, 1080, 2400);
    }
}
