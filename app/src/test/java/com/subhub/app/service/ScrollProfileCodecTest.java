package com.subhub.app.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ScrollProfileCodecTest {
    private static final long NOW = 1800000000000L;
    private static ScrollProfileCodec.Entry entry() {
        return new ScrollProfileCodec.Entry(ScrollCalibrationLearnerTest.ready().profile(), NOW - 1000);
    }
    private static JSONObject encoded() throws Exception {
        return new JSONObject(ScrollProfileCodec.encode(List.of(entry()), NOW));
    }

    @Test public void compactProfileRoundTripsEveryContextDimensionAndParameter() throws Exception {
        String json = ScrollProfileCodec.encode(List.of(entry()), NOW);
        ScrollProfileCodec.Decoded result = ScrollProfileCodec.decode(json, NOW);
        assertEquals(1, result.entries.size());
        assertFalse(result.malformed); assertFalse(result.unsupported); assertFalse(result.truncated);
        assertEquals(0, result.rejected);
        ScrollProfileCodec.Entry restored = result.entries.get(0);
        assertEquals(entry().profile.key, restored.profile.key);
        assertEquals(2, restored.profile.pixelsPerEventPixel, .001);
        assertEquals(100, restored.profile.eventIntervalMs, .001);
        assertEquals(20, restored.profile.deliveryLagMs, .001);
        assertEquals(NOW - 1000, restored.trainedAtWallMs);
        assertFalse(json.contains("frames"));
        assertFalse(json.contains("text"));
        assertFalse(json.contains("deviceId"));
    }

    @Test public void corruptUnsupportedOrUnknownSchemasAreNotSilentlyAccepted() throws Exception {
        assertTrue(ScrollProfileCodec.decode("{bad", NOW).malformed);
        JSONObject future = encoded().put("schemaVersion", 2);
        assertTrue(ScrollProfileCodec.decode(future.toString(), NOW).unsupported);
        JSONObject extraRoot = encoded().put("unexpected", true);
        assertTrue(ScrollProfileCodec.decode(extraRoot.toString(), NOW).malformed);
        JSONObject extraEntry = encoded();
        extraEntry.getJSONArray("profiles").getJSONObject(0).put("unknownParameter", 1);
        ScrollProfileCodec.Decoded result = ScrollProfileCodec.decode(extraEntry.toString(), NOW);
        assertEquals(1, result.rejected); assertTrue(result.entries.isEmpty());
    }

    @Test public void expirationClockRollbackAndAppVersionRemainExplicit() throws Exception {
        String json = encoded().toString();
        assertEquals(1, ScrollProfileCodec.decode(json, NOW + ScrollProfileCodec.MAX_AGE_MS).rejected);
        assertEquals(1, ScrollProfileCodec.decode(json, NOW - 2000).rejected);
        JSONObject changed = encoded();
        changed.getJSONArray("profiles").getJSONObject(0).put("appVersion", 2);
        assertNotEquals(entry().profile.key, ScrollProfileCodec.decode(changed.toString(), NOW).entries.get(0).profile.key);
    }

    @Test public void invalidNumbersAndUnvalidatedProfilesCannotRestore() throws Exception {
        for (String field : new String[]{"scale", "interval", "lag", "jitter", "training", "validation", "gestures"}) {
            JSONObject root = encoded();
            root.getJSONArray("profiles").getJSONObject(0).put(field, -1);
            assertEquals(field, 1, ScrollProfileCodec.decode(root.toString(), NOW).rejected);
        }
        JSONObject fractionalVersion = encoded();
        fractionalVersion.getJSONArray("profiles").getJSONObject(0).put("appVersion", 1.5);
        assertEquals(1, ScrollProfileCodec.decode(fractionalVersion.toString(), NOW).rejected);
        JSONObject stringScale = encoded();
        stringScale.getJSONArray("profiles").getJSONObject(0).put("scale", "2.0");
        assertEquals(1, ScrollProfileCodec.decode(stringScale.toString(), NOW).rejected);
    }

    @Test public void duplicateProfilesKeepNewestAndReportDiscardedEntry() throws Exception {
        JSONObject root = encoded();
        JSONObject newer = new JSONObject(root.getJSONArray("profiles").getJSONObject(0).toString()).put("trainedAt", NOW);
        root.getJSONArray("profiles").put(newer);
        ScrollProfileCodec.Decoded result = ScrollProfileCodec.decode(root.toString(), NOW);
        assertEquals(1, result.entries.size()); assertEquals(1, result.rejected);
        assertEquals(NOW, result.entries.get(0).trainedAtWallMs);
    }

    @Test public void excessiveEntriesAreBoundedAndMarkedTruncated() throws Exception {
        JSONObject root = encoded();
        JSONArray values = root.getJSONArray("profiles");
        JSONObject first = values.getJSONObject(0);
        for (int index = 1; index < 40; index++) values.put(new JSONObject(first.toString()).put("appVersion", index + 1));
        ScrollProfileCodec.Decoded result = ScrollProfileCodec.decode(root.toString(), NOW);
        assertEquals(32, result.entries.size()); assertTrue(result.truncated);
    }

    @Test public void fileReadIsByteBoundedBeforeJsonParsing() throws Exception {
        byte[] bytes = encoded().toString().getBytes(StandardCharsets.UTF_8);
        assertEquals(new String(bytes, StandardCharsets.UTF_8),
                ScrollProfileRepository.readBounded(new ByteArrayInputStream(bytes)));
        assertThrows(IOException.class, () -> ScrollProfileRepository.readBounded(
                new ByteArrayInputStream(new byte[ScrollProfileCodec.MAX_BYTES + 1])));
    }
}
