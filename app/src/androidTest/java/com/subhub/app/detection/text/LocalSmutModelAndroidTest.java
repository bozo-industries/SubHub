package com.subhub.app.detection.text;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LocalSmutModelAndroidTest {
    private static final TextSmutConfig BALANCED = new TextSmutConfig(
            true, TextSmutConfig.SENSITIVITY_BALANCED, TextSmutConfig.DEFAULT_CATEGORIES);

    @Test public void semanticFallbackCatchesContextThatRulesMiss() {
        Context context = ApplicationProvider.getApplicationContext();
        SmutTextClassifier classifier = new SmutTextClassifier(context);
        String contextual = "I want to feel your body pressed against mine tonight";

        assertFalse(classifier.classify(contextual, BALANCED, false).isMatched());
        assertTrue(classifier.classify(contextual, BALANCED, true).isMatched());
        assertTrue(classifier.classify(
                "She whispered that she wanted him inside her", BALANCED, true).isMatched());
        assertFalse(classifier.classify(
                "That beach sunset looks absolutely beautiful", BALANCED, true).isMatched());
        assertFalse(classifier.classify(
                "Breast cancer screening and sexual health clinic", BALANCED, true).isMatched());
    }

    @Test public void warmedClassifierStaysInteractive() {
        Context context = ApplicationProvider.getApplicationContext();
        SmutTextClassifier classifier = new SmutTextClassifier(context);
        classifier.classify("come over tonight I want you in my bed", BALANCED, true);
        long started = android.os.SystemClock.elapsedRealtimeNanos();
        for (int index = 0; index < 100; index++) {
            classifier.classify("I want to feel your body pressed against mine tonight " + index,
                    BALANCED, true);
        }
        long elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L;
        assertTrue("100 local classifications took " + elapsedMs + " ms", elapsedMs < 500L);
    }
}
