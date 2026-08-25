package com.subhub.app.update;

import org.json.JSONArray;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

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
    }

    @Test public void releaseWithoutUpdaterManifestCannotInstall() throws Exception {
        JSONArray releases = new JSONArray("[" + release("v0.5.0", false, false, false) + "]");
        assertEquals("", GitHubReleaseRepository.parseReleases(releases).get(0).manifestUrl);
    }

    @Test public void sixHourScheduleIsStable() {
        assertEquals(6L * 60L * 60L * 1000L, UpdateScheduler.INTERVAL_MILLIS);
    }

    private static String release(String tag, boolean draft, boolean prerelease, boolean manifest) {
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        String assets = manifest ? "[{\"name\":\"SubHub-" + version
                + "-update.json\",\"browser_download_url\":\"https://github.com/manifest\"}]" : "[]";
        return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft
                + ",\"prerelease\":" + prerelease + ",\"body\":\"notes\","
                + "\"html_url\":\"https://github.com/release\",\"assets\":" + assets + "}";
    }
}
