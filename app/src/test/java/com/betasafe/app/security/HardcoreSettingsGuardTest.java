package com.betasafe.app.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.WindowManager;

import org.junit.Test;

public final class HardcoreSettingsGuardTest {
    @Test public void guardRequiresHardcoreSubModeAndAndroidSettings() {
        assertTrue(HardcoreSettingsGuard.shouldGuard(
                true, false, "com.android.settings"));
        assertFalse(HardcoreSettingsGuard.shouldGuard(
                false, false, "com.android.settings"));
        assertFalse(HardcoreSettingsGuard.shouldGuard(
                true, true, "com.android.settings"));
        assertFalse(HardcoreSettingsGuard.shouldGuard(
                true, false, "com.android.chrome"));
    }

    @Test public void destructiveSettingsLabelsAndIdsAreRecognized() {
        assertTrue(HardcoreSettingsGuard.isTargetControl(
                "com.android.settings:id/uninstall_button", "Uninstall", null));
        assertTrue(HardcoreSettingsGuard.isTargetControl(
                "com.android.settings:id/clear_data_button", "Clear storage", null));
        assertTrue(HardcoreSettingsGuard.isTargetControl(null, "Daten löschen", null));
        assertTrue(HardcoreSettingsGuard.isTargetControl(null, "Borrar datos", null));
        assertFalse(HardcoreSettingsGuard.isTargetControl(
                "com.android.settings:id/force_stop_button", "Force stop", null));
    }

    @Test public void badgeConsumesItsOwnBoundsWithoutCapturingTheWholeScreen() {
        int flags = HardcoreSettingsGuard.overlayFlags();
        assertFalse((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
    }
}
