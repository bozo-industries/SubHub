package com.subhub.app.pack;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SubHubPackArchiveTest {
    @Test public void roundTripPreservesPortableSectionsLocksRecommendationsAndAssets()
            throws Exception {
        JSONObject censor = new JSONObject()
                .put("censor_type", "box")
                .put("show_border", true);
        JSONObject recommendations = new JSONObject()
                .put("hardcoreSuggested", true)
                .put("serviceDurationMillis", 86_400_000L);
        byte[] art = "private-art".getBytes(StandardCharsets.UTF_8);
        SubHubPack source = new SubHubPack(UUID.randomUUID().toString(), "Night Rules",
                "Keeper", "Portable scene", "2.0.0", 10L, 20L, "0.6.0",
                Map.of(SubHubPackSchema.CENSOR, censor), Set.of(SubHubPackSchema.CENSOR),
                recommendations, Map.of("assets/censor/image-00.png", art));

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        SubHubPackArchive.write(source, encoded);
        SubHubPack decoded = SubHubPackArchive.read(
                new ByteArrayInputStream(encoded.toByteArray()));

        assertEquals("Night Rules", decoded.getName());
        assertEquals(source.getOriginDeviceId(), decoded.getOriginDeviceId());
        assertEquals("box", decoded.getSection(SubHubPackSchema.CENSOR)
                .getString("censor_type"));
        assertTrue(decoded.getLockGroups().contains(SubHubPackSchema.CENSOR));
        assertEquals(86_400_000L,
                decoded.getRecommendations().getLong("serviceDurationMillis"));
        assertArrayEquals(art, decoded.getAssets().get("assets/censor/image-00.png"));
    }

    @Test public void secretAndRuntimeFieldsCannotEnterPortableSection() throws Exception {
        JSONObject wallet = new JSONObject()
                .put("enabled", true)
                .put("daily_cap_cents", 1000)
                .put("paypal_client_id", "must-not-export")
                .put("saved_wallet_id", "must-not-export")
                .put("history", "must-not-export");
        JSONObject clean = SubHubPackSchema.sanitizeSection(SubHubPackSchema.WALLET, wallet);
        assertTrue(clean.getBoolean("enabled"));
        assertTrue(clean.has("daily_cap_cents"));
        assertFalse(clean.has("paypal_client_id"));
        assertFalse(clean.has("saved_wallet_id"));
        assertFalse(clean.has("history"));
    }

    @Test public void unsupportedRecommendationsAreDropped() throws Exception {
        JSONObject clean = SubHubPackSchema.sanitizeRecommendations(new JSONObject()
                .put("hardcoreSuggested", true)
                .put("serviceDurationMillis", 1234L)
                .put("enableDeviceAdmin", true));
        assertTrue(clean.getBoolean("hardcoreSuggested"));
        assertFalse(clean.has("serviceDurationMillis"));
        assertFalse(clean.has("enableDeviceAdmin"));
    }

    @Test public void unsafeZipPathIsRejected() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("../manifest.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThrows(java.io.IOException.class, () -> SubHubPackArchive.read(
                new ByteArrayInputStream(output.toByteArray())));
    }

    @Test public void invalidAssetNamespaceIsNeverAccepted() {
        assertTrue(SubHubPackArchive.isSafeAssetPath("assets/censor/one.png"));
        assertTrue(SubHubPackArchive.isSafeAssetPath("assets/popup/one.webp"));
        assertFalse(SubHubPackArchive.isSafeAssetPath("assets/../../secret"));
        assertFalse(SubHubPackArchive.isSafeAssetPath("assets/censor/.."));
        assertFalse(SubHubPackArchive.isSafeAssetPath("assets/censor/./one.png"));
        assertFalse(SubHubPackArchive.isSafeAssetPath("assets/censor/"));
        assertFalse(SubHubPackArchive.isSafeAssetPath("sections/censor.json"));
        assertFalse(SubHubPackArchive.isSafeAssetPath("C:/secret"));
    }
}
