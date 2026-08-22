package com.betasafe.app.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CensorAppearanceTest {
    @Test
    public void unknownPreferenceFallsBackToBox() {
        assertEquals(CensorAppearance.Type.BOX, CensorAppearance.Type.fromPreference("future-style"));
    }

    @Test
    public void knownPreferencesAreCaseInsensitive() {
        assertEquals(CensorAppearance.Type.PIXELATE, CensorAppearance.Type.fromPreference("PIXELATE"));
    }
}
