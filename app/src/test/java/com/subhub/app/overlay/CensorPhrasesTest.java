package com.subhub.app.overlay;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CensorPhrasesTest {
    @Test
    public void customPhrasesAreNormalizedAndDeduplicated() {
        List<String> phrases = CensorPhrases.build(
                Collections.emptySet(),
                new LinkedHashSet<>(Arrays.asList("  obey   now ", "OBEY NOW")));
        assertEquals(1, phrases.size());
        assertEquals("OBEY NOW", phrases.get(0));
    }

    @Test
    public void emptySelectionFallsBackSafely() {
        assertTrue(CensorPhrases.build(Collections.emptySet(), Collections.emptySet())
                .contains("BLOCKED"));
    }
}
