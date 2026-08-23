package com.betasafe.app.pack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.settings.SettingsRepository;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public final class PackManagerTest {
    @Test
    public void formatFivePreservesDecimalDigestAndLoadsImagesAndPhrases() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences settings = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        PackManager manager = new PackManager(context);
        manager.deactivate();
        manager.delete("instrumented.v5");

        JSONObject manifest = new JSONObject()
                .put("format_version", 5)
                .put("target_platform", "android")
                .put("pack_id", "instrumented.v5")
                .put("name", "Format Five")
                .put("author", "SubHub tests")
                .put("version", "1")
                .put("signature", "")
                .put("images", new JSONArray().put("images/sample.png"))
                .put("settings", new JSONObject()
                        .put("burst_duration", 4.0d)
                        .put("custom_phrases", new JSONArray().put("Synthetic phrase")));
        manifest.put("signature", digestForPreservingNumbers(manifest));

        File archive = zipWithImage(context, "format-five.bbpack", manifest);
        PackManager.PackInfo imported = manager.importPack(Uri.fromFile(archive));
        assertEquals("instrumented.v5", imported.getManifest().getPackId());
        assertTrue(imported.getManifest().integrityDigestValid());
        assertEquals(1, imported.getManifest().getCustomImageFiles().size());
        assertEquals(1, imported.getManifest().getCustomPhrases().size());
        assertTrue(manager.activate("instrumented.v5"));
        Set<String> phrases = settings.getStringSet(
                SettingsRepository.KEY_CUSTOM_PHRASES, java.util.Collections.emptySet());
        assertTrue(phrases.contains("Synthetic phrase"));

        manager.deactivate();
        manager.delete("instrumented.v5");
        archive.delete();
    }

    @Test
    public void integrityActivationRestorationAndTraversalPolicyWork() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences settings = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        PackManager manager = new PackManager(context);
        manager.delete("instrumented.pack");
        settings.edit()
                .putString(SettingsRepository.KEY_CENSOR_TYPE, "box")
                .putInt(SettingsRepository.KEY_CENSOR_INTENSITY, 20)
                .commit();

        JSONObject manifest = manifest();
        manifest.put("signature", digestFor(manifest));
        assertTrue(PackVerifier.verifyIntegrityDigest(manifest));
        JSONObject changed = new JSONObject(manifest.toString()).put("name", "Changed");
        assertFalse(PackVerifier.verifyIntegrityDigest(changed));

        File valid = zip(context, "valid.bbpack", manifest, false);
        PackManager.PackInfo imported = manager.importPack(Uri.fromFile(valid));
        assertEquals("instrumented.pack", imported.getManifest().getPackId());
        assertTrue(manager.activate("instrumented.pack"));
        assertEquals("glitch", settings.getString(SettingsRepository.KEY_CENSOR_TYPE, ""));
        assertEquals(77, settings.getInt(SettingsRepository.KEY_CENSOR_INTENSITY, 0));
        assertTrue(LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_TYPE));
        manager.deactivate();
        assertNull(manager.activePackId());
        assertEquals("box", settings.getString(SettingsRepository.KEY_CENSOR_TYPE, ""));
        assertEquals(20, settings.getInt(SettingsRepository.KEY_CENSOR_INTENSITY, 0));
        assertFalse(LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_TYPE));
        manager.delete("instrumented.pack");

        File malicious = zip(context, "malicious.bbpack", manifest, true);
        try {
            manager.importPack(Uri.fromFile(malicious));
            fail("Traversal entry should have been rejected");
        } catch (Exception expected) {
            assertFalse(new File(context.getFilesDir(), "bbpacks/escape.txt").exists());
        }
        valid.delete();
        malicious.delete();
    }

    private static JSONObject manifest() throws Exception {
        JSONObject settings = new JSONObject()
                .put(SettingsRepository.KEY_CENSOR_TYPE, "glitch")
                .put(SettingsRepository.KEY_CENSOR_INTENSITY, 77);
        return new JSONObject()
                .put("formatVersion", 4)
                .put("targetPlatform", "android")
                .put("packId", "instrumented.pack")
                .put("name", "Instrumented Pack")
                .put("author", "BetaSafe tests")
                .put("description", "Test pack")
                .put("version", "1")
                .put("signature", "")
                .put("settings", settings)
                .put("lockedSettings", new JSONArray().put(SettingsRepository.KEY_CENSOR_TYPE));
    }

    private static String digestFor(JSONObject manifest) throws Exception {
        JSONObject copy = new JSONObject(manifest.toString()).put("signature", "");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                PackVerifier.canonicalize(copy).getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static String digestForPreservingNumbers(JSONObject manifest) throws Exception {
        JSONObject copy = PackVerifier.deepCopy(manifest).put("signature", "");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                PackVerifier.canonicalize(copy).getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static File zipWithImage(Context context, String name, JSONObject manifest)
            throws Exception {
        File file = new File(context.getCacheDir(), name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            String rawManifest = manifest.toString().replace(
                    "\"burst_duration\":4", "\"burst_duration\":4.0");
            zip.write(rawManifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("images/sample.png"));
            zip.write(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
            zip.closeEntry();
        }
        return file;
    }

    private static File zip(Context context, String name, JSONObject manifest, boolean malicious)
            throws Exception {
        File file = new File(context.getCacheDir(), name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (malicious) {
                zip.putNextEntry(new ZipEntry("../escape.txt"));
                zip.write("escape".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }
}
