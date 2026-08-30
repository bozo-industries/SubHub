package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public final class CensorLabelLayoutTest {
    @Test public void narrowBoxChoosesAvailableShortPhraseBeforeEllipsizing() {
        String selected = CensorLabelLayout.selectPhrase(
                Arrays.asList("TRIBUTE FIRST", "PAY TO PEEK", "EARN IT"),
                0, 7f, String::length);

        assertEquals("EARN IT", selected);
    }

    @Test public void wideBoxKeepsStableIdPhraseChoice() {
        String selected = CensorLabelLayout.selectPhrase(
                Arrays.asList("TRIBUTE FIRST", "PAY TO PEEK", "EARN IT"),
                1, 20f, String::length);

        assertEquals("PAY TO PEEK", selected);
    }

    @Test public void customPhraseUsesEllipsisOnlyAsLastResort() {
        assertEquals("CUS…", CensorLabelLayout.ellipsize(
                "CUSTOM PHRASE", 4f, String::length));
    }
}
