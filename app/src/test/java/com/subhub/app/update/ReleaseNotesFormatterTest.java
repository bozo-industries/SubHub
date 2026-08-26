package com.subhub.app.update;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReleaseNotesFormatterTest {
    @Test public void extractsChangesWithoutApkGuidanceFromLegacyReleaseBody() {
        String body = "## Choose your APK\n\nUniversal works everywhere.\n\n"
                + "## Changes\n\n### Fixed\n\n- Installer opens\n\n"
                + "**Full Changelog**: https://example.invalid";
        assertEquals("### Fixed\n\n- Installer opens",
                ReleaseNotesFormatter.changelogOnly(body));
    }

    @Test public void stopsBeforeDownloadSectionInNewReleaseBody() {
        String body = "## What’s new in SubHub 0.6.0\n\n### New\n\n- Better Home\n\n"
                + "## Choose your APK\n\nUniversal works everywhere.";
        String notes = ReleaseNotesFormatter.changelogOnly(body);
        assertTrue(notes.contains("Better Home"));
        assertFalse(notes.contains("Universal"));
    }

    @Test public void rendersSimpleReadableText() {
        assertEquals("Fixed\n\n- Installer opens",
                ReleaseNotesFormatter.forDisplay("### Fixed\n\n- **Installer opens**", "fallback"));
        assertEquals("fallback", ReleaseNotesFormatter.forDisplay("", "fallback"));
        assertEquals("New\n\n- Better Home",
                ReleaseNotesFormatter.forDisplay(
                        "## What’s new in SubHub 0.6.0\n\n### New\n\n- Better Home", "fallback"));
    }
}
