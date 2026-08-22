package com.betasafe.app.appmode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public final class AppModePolicyTest {
    @Test public void selectedModeWakesOnlyForAnExactSelectedPackage() {
        Set<String> selected = Set.of("com.example.watched");
        assertTrue(AppModePolicy.shouldRecognize(true, AppModePolicy.Mode.SELECTED_APPS,
                selected, "com.example.watched", "com.betasafe.app", "com.example.ime"));
        assertFalse(AppModePolicy.shouldRecognize(true, AppModePolicy.Mode.SELECTED_APPS,
                selected, "com.example.other", "com.betasafe.app", "com.example.ime"));
        assertFalse(AppModePolicy.shouldRecognize(false, AppModePolicy.Mode.SELECTED_APPS,
                selected, "com.example.watched", "com.betasafe.app", "com.example.ime"));
    }

    @Test public void alwaysModeSleepsOnBetaSafeAndSystemSurfaces() {
        assertTrue(AppModePolicy.shouldRecognize(true, AppModePolicy.Mode.ALWAYS, Set.of(),
                "com.example.browser", "com.betasafe.app", "com.example.ime"));
        assertFalse(AppModePolicy.shouldRecognize(true, AppModePolicy.Mode.ALWAYS, Set.of(),
                "com.betasafe.app", "com.betasafe.app", "com.example.ime"));
        assertFalse(AppModePolicy.shouldRecognize(true, AppModePolicy.Mode.ALWAYS, Set.of(),
                "com.android.systemui", "com.betasafe.app", "com.example.ime"));
    }

    @Test public void keyboardWindowEventsDoNotReplaceTheForegroundApp() {
        assertFalse(AppModePolicy.shouldAcceptForegroundEvent(
                "com.example.ime", "com.example.ime.Keyboard", "com.betasafe.app",
                "com.example.ime"));
        assertTrue(AppModePolicy.shouldAcceptForegroundEvent(
                "com.example.watched", "com.example.watched.MainActivity", "com.betasafe.app",
                "com.example.ime"));
    }

    @Test public void ownOverlayEventsDoNotMasqueradeAsAppSwitches() {
        assertFalse(AppModePolicy.shouldAcceptForegroundEvent(
                "com.betasafe.app", "android.widget.FrameLayout", "com.betasafe.app",
                "com.example.ime"));
        assertTrue(AppModePolicy.shouldAcceptForegroundEvent(
                "com.betasafe.app", "com.betasafe.app.MainActivity", "com.betasafe.app",
                "com.example.ime"));
    }

    @Test public void malformedPackageNamesAreNotPersisted() {
        Set<String> clean = AppModePolicy.sanitizePackages(Set.of(
                "com.example.ok", "not a package", "", "../escape"));
        assertTrue(clean.contains("com.example.ok"));
        assertFalse(clean.contains("not a package"));
        assertFalse(clean.contains("../escape"));
    }
}
