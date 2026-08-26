package com.subhub.app.update;

import org.json.JSONArray;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class GitHubReleaseRepositoryTest {
    @Test public void ignoresDraftsButKeepsPublishedPrereleases() throws Exception {
        JSONArray releases = new JSONArray("["
                + release("v0.6.0", true, false, true) + ","
                + release("v0.5.0-beta.1", false, true, true) + ","
                + release("bad-tag", false, false, true) + "]");
        List<GitHubReleaseRepository.Release> parsed =
                GitHubReleaseRepository.parseReleases(releases);
        assertEquals(1, parsed.size());
        assertEquals("v0.5.0-beta.1", parsed.get(0).tag);
        assertEquals("2026-08-26T12:00:00Z", parsed.get(0).publishedAt);
        assertEquals(true, parsed.get(0).prerelease);
    }

    @Test public void releaseWithoutUpdaterManifestCannotInstall() throws Exception {
        JSONArray releases = new JSONArray("[" + release("v0.5.0", false, false, false) + "]");
        assertEquals("", GitHubReleaseRepository.parseReleases(releases).get(0).manifestUrl);
    }

    @Test public void sixHourScheduleIsStable() {
        assertEquals(6L * 60L * 60L * 1000L, UpdateScheduler.INTERVAL_MILLIS);
    }

    @Test public void manifestChangelogWinsOverReleaseDownloadGuidance() throws Exception {
        UpdateManifest manifest = UpdateManifest.parse("{\"schema\":1,"
                + "\"packageName\":\"com.subhub.app\",\"versionName\":\"0.6.0\","
                + "\"versionCode\":8,\"minSdk\":26,\"tag\":\"v0.6.0\","
                + "\"releaseNotes\":\"## Fixed\\n\\n- Installer handoff\",\"assets\":[{"
                + "\"abi\":\"universal\",\"name\":\"SubHub-0.6.0-universal.apk\","
                + "\"url\":\"https://github.com/bozo-industries/SubHub/releases/download/"
                + "v0.6.0/SubHub-0.6.0-universal.apk\","
                + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"size\":42}]}");
        String notes = GitHubReleaseRepository.releaseNotes(manifest,
                "## Choose your APK\\n\\nUniversal works everywhere");
        assertEquals("## Fixed\n\n- Installer handoff", notes);
        assertFalse(notes.contains("Universal"));
    }

    private static String release(String tag, boolean draft, boolean prerelease, boolean manifest) {
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        String assets = manifest ? "[{\"name\":\"SubHub-" + version
                + "-update.json\",\"browser_download_url\":\"https://github.com/manifest\"}]" : "[]";
        return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft
                + ",\"prerelease\":" + prerelease + ",\"body\":\"notes\","
                + "\"published_at\":\"2026-08-26T12:00:00Z\","
                + "\"html_url\":\"https://github.com/release\",\"assets\":" + assets + "}";
    }
}
