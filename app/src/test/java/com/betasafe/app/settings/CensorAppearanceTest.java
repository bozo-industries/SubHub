package com.betasafe.app.settings;

import static org.junit.Assert.assertEquals;

import android.graphics.Color;

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

    @Test
    public void recoveredStyleAliasesAreAccepted() {
        assertEquals(CensorAppearance.Type.PIXELATE,
                CensorAppearance.Type.fromPreference("mosaic"));
        assertEquals(CensorAppearance.Type.CUSTOM,
                CensorAppearance.Type.fromPreference("custom_image"));
        assertEquals(CensorAppearance.Type.ERROR_POPUP,
                CensorAppearance.Type.fromPreference("windows_error"));
    }

    @Test
    public void stablePhraseSelectionWrapsNegativeIds() {
        CensorAppearance appearance = new CensorAppearance(
                CensorAppearance.Type.BOX, 50, true, true, Color.MAGENTA);
        assertEquals("BLOCKED", appearance.phraseFor(-1));
    }
}
