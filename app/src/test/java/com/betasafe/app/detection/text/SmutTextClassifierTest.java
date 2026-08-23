package com.betasafe.app.detection.text;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SmutTextClassifierTest {
    private final SmutTextClassifier classifier = new SmutTextClassifier();

    @Test public void catchesExplicitPostsFromReportedFeed() {
        TextSmutConfig config = balanced();
        assertTrue(classifier.classify(
                "I want Daddy to whip my clit and force my pussy open", config).isMatched());
        assertTrue(classifier.classify(
                "Can you stop posting? I can't cum to all of them", config).isMatched());
        assertTrue(classifier.classify(
                "You have to suck the strap; it is not negotiable", config).isMatched());
    }

    @Test public void catchesSimpleSmutAndCommonObfuscation() {
        assertTrue(classifier.classify("new smut chapter", balanced()).isMatched());
        assertTrue(classifier.classify("watch p0rn now", balanced()).isMatched());
    }

    @Test public void leavesOrdinaryFeedAndHealthContextsAlone() {
        TextSmutConfig config = balanced();
        assertFalse(classifier.classify("field-programmable gate array", config).isMatched());
        assertFalse(classifier.classify("Sam Altman. OpenAI.", config).isMatched());
        assertFalse(classifier.classify("breast cancer clinical support hotline", config).isMatched());
        assertFalse(classifier.classify("sexual health and consent guide", config).isMatched());
        assertFalse(classifier.classify("Daddy picked the kids up from school", config).isMatched());
    }

    @Test public void sensitivityAndCategorySelectionAreRespected() {
        TextSmutConfig strict = new TextSmutConfig(
                true, TextSmutConfig.SENSITIVITY_STRICT,
                TextSmutConfig.DEFAULT_CATEGORIES);
        TextSmutConfig broad = new TextSmutConfig(
                true, TextSmutConfig.SENSITIVITY_BROAD,
                TextSmutConfig.DEFAULT_CATEGORIES);
        assertFalse(classifier.classify("a BDSM discussion", strict).isMatched());
        assertTrue(classifier.classify("a BDSM discussion", broad).isMatched());

        TextSmutConfig disabled = new TextSmutConfig(
                false, TextSmutConfig.SENSITIVITY_BROAD,
                TextSmutConfig.DEFAULT_CATEGORIES);
        assertFalse(classifier.classify("explicit porn and smut", disabled).isMatched());
    }

    private static TextSmutConfig balanced() {
        return new TextSmutConfig(
                true, TextSmutConfig.SENSITIVITY_BALANCED,
                TextSmutConfig.DEFAULT_CATEGORIES);
    }
}
