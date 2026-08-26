package com.subhub.app.update;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ReleaseHistoryCatalogTest {
    @Test public void liveMetadataKeepsBundledNotesWhenImmutableBodyIsBlank() {
        ReleaseHistoryItem bundled = item("0.5.2", "v0.5.2", "- Fixed timers", "", false);
        ReleaseHistoryItem live = item("0.5.2", "v0.5.2", "",
                "2026-08-26T13:19:10Z", false);
        List<ReleaseHistoryItem> merged = ReleaseHistoryCatalog.merge(
                Arrays.asList(live), Arrays.asList(bundled));
        assertEquals(1, merged.size());
        assertEquals("- Fixed timers", merged.get(0).notes);
        assertEquals("2026-08-26T13:19:10Z", merged.get(0).publishedAt);
    }

    @Test public void sortsSemanticVersionsAndRoundTripsTheCache() throws Exception {
        List<ReleaseHistoryItem> values = ReleaseHistoryCatalog.merge(Arrays.asList(
                item("0.5.3-beta.1", "v0.5.3-beta.1", "Preview", "", true),
                item("0.5.3", "v0.5.3", "Stable", "", false),
                item("0.5.2", "v0.5.2", "Older", "", false)), null);
        assertEquals("v0.5.3", values.get(0).tag);
        assertEquals("v0.5.3-beta.1", values.get(1).tag);
        List<ReleaseHistoryItem> decoded = ReleaseHistoryItem.decode(
                ReleaseHistoryItem.encode(values));
        assertEquals(3, decoded.size());
        assertTrue(decoded.get(1).prerelease);
    }

    @Test public void candidateNotesOverrideAStaleHistoryEntry() throws Exception {
        ReleaseHistoryItem stale = item("0.6.0", "v0.6.0", "Stale", "", false);
        UpdateManifest manifest = UpdateManifest.parse("{\"schema\":1,"
                + "\"packageName\":\"com.subhub.app\",\"versionName\":\"0.6.0\","
                + "\"versionCode\":9,\"minSdk\":26,\"tag\":\"v0.6.0\","
                + "\"releaseNotes\":\"Fresh\",\"assets\":[{"
                + "\"abi\":\"universal\",\"name\":\"SubHub-0.6.0-universal.apk\","
                + "\"url\":\"https://github.com/bozo-industries/SubHub/app.apk\","
                + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"size\":42}]}");
        UpdateCandidate candidate = new UpdateCandidate(manifest, "Fresh", "");
        List<ReleaseHistoryItem> merged = ReleaseHistoryCatalog.withCandidate(
                Arrays.asList(stale), candidate);
        assertEquals("Fresh", merged.get(0).notes);
    }

    private static ReleaseHistoryItem item(String version, String tag, String notes,
            String publishedAt, boolean preview) {
        return new ReleaseHistoryItem(version, tag, notes,
                "https://github.com/bozo-industries/SubHub/releases/tag/" + tag,
                publishedAt, preview);
    }
}
