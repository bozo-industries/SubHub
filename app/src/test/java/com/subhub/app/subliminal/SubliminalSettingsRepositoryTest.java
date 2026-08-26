package com.subhub.app.subliminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SubliminalSettingsRepositoryTest {
    @Test public void presetsStayWithinFaintSafeBounds() {
        for (SubliminalSettings.Preset preset : SubliminalSettings.Preset.values()) {
            SubliminalSettingsRepository.Values values =
                    SubliminalSettingsRepository.valuesFor(preset);
            assertTrue(values.opacity >= 1 && values.opacity <= 15);
            assertTrue(values.visible >= 800L && values.visible <= 4_000L);
            assertTrue(values.minimum >= 5_000L);
            assertTrue(values.maximum >= values.minimum);
            assertTrue(values.textSize >= 14 && values.textSize <= 28);
        }
    }

    @Test public void ultraIsFasterButStillFaint() {
        SubliminalSettingsRepository.Values normal =
                SubliminalSettingsRepository.valuesFor(SubliminalSettings.Preset.NORMAL);
        SubliminalSettingsRepository.Values ultra =
                SubliminalSettingsRepository.valuesFor(SubliminalSettings.Preset.ULTRA);
        assertTrue(ultra.minimum < normal.minimum);
        assertTrue(ultra.maximum < normal.maximum);
        assertTrue(ultra.opacity > normal.opacity);
        assertEquals(10, ultra.opacity);
    }
}
