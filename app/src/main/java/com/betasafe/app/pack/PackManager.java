package com.betasafe.app.pack;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.betasafe.app.capture.CustomImageManager;
import com.betasafe.app.settings.SettingsRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Private pack installer with strict archive limits, path validation, and reversible activation. */
public final class PackManager {
    private static final String STATE_PREFS = "betablocker_pack_state";
    private static final String KEY_ACTIVE = "active_pack_id";
    private static final String KEY_BACKUP = "active_pack_backup";
    private static final long MAX_ARCHIVE_BYTES = 50L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 25L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 100L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 256;
    private static final Set<String> ALLOWED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    SettingsRepository.KEY_ENABLED_CATEGORIES,
                    SettingsRepository.KEY_CONFIDENCE,
                    SettingsRepository.KEY_CENSOR_TYPE,
                    SettingsRepository.KEY_CENSOR_INTENSITY,
                    SettingsRepository.KEY_SHOW_BORDER,
                    SettingsRepository.KEY_SHOW_TEXT,
                    SettingsRepository.KEY_BORDER_COLOR,
                    SettingsRepository.KEY_DETECTION_PRESET,
                    SettingsRepository.KEY_CENSOR_SIZE_PADDING,
                    SettingsRepository.KEY_ANIMATE_BORDER,
                    SettingsRepository.KEY_BORDER_EFFECT,
                    SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES,
                    SettingsRepository.KEY_CUSTOM_PHRASES,
                    SettingsRepository.KEY_REVERSE_MODE,
                    SettingsRepository.KEY_REVERSE_STRENGTH,
                    SettingsRepository.KEY_REVERSE_CUTOUT_SHAPE,
                    SettingsRepository.KEY_ERROR_TITLE,
                    SettingsRepository.KEY_ERROR_TEXT)));
    private static final Map<String, String> KEY_ALIASES = aliases();

    private final Context context;
    private final File root;
    private final SharedPreferences settings;
    private final SharedPreferences state;

    public PackManager(Context context) {
        this.context = context.getApplicationContext();
        root = new File(this.context.getFilesDir(), "bbpacks");
        if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("Could not create pack store");
        settings = this.context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        state = this.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        restoreActiveLockState();
    }

    public List<PackInfo> listInstalled() {
        List<PackInfo> values = new ArrayList<>();
        File[] directories = root.listFiles(File::isDirectory);
        if (directories != null) {
            for (File directory : directories) {
                if (directory.getName().startsWith(".staging-")) continue;
                try {
                    PackInfo info = readInstalled(directory);
                    if (info != null) values.add(info);
                } catch (Exception ignored) {
                    // Invalid private remnants are omitted from the installed list.
                }
            }
        }
        values.sort((left, right) -> left.manifest.getName().compareToIgnoreCase(right.manifest.getName()));
        return Collections.unmodifiableList(values);
    }

    public synchronized PackInfo importPack(Uri uri) throws IOException {
        File archive = File.createTempFile("pack-import-", ".bbpack", context.getCacheDir());
        File staging = new File(root, ".staging-" + UUID.randomUUID());
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(archive)) {
                if (input == null) throw new IOException("Selected pack is unavailable");
                copyBounded(input, output, MAX_ARCHIVE_BYTES);
            }
            if (!staging.mkdirs()) throw new IOException("Could not create pack staging directory");
            extractArchive(archive, staging);
            File manifestFile = new File(staging, "manifest.json");
            if (!manifestFile.isFile()) throw new IOException("Pack has no root manifest.json");
            PackManifest manifest = PackManifest.parse(
                    new JSONObject(readBounded(manifestFile, 512 * 1024)));
            if (!manifest.integrityDigestValid()) throw new IOException("Pack integrity digest does not match");
            prepareCustomImages(staging, manifest);
            File destination = new File(root, manifest.getPackId());
            assertInsideRoot(destination);
            if (manifest.getPackId().equals(activePackId())) deactivate();
            if (destination.exists()) deleteTree(destination);
            if (!staging.renameTo(destination)) throw new IOException("Could not finalize private pack install");
            return new PackInfo(destination, manifest);
        } catch (Exception error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException(error.getMessage(), error);
        } finally {
            if (staging.exists()) deleteTree(staging);
            if (archive.exists() && !archive.delete()) archive.deleteOnExit();
        }
    }

    public synchronized boolean activate(String packId) {
        PackInfo pack = find(packId);
        if (pack == null) return false;
        if (packId.equals(activePackId())) return true;
        if (activePackId() != null) deactivate();
        try {
            Map<String, Object> changes = changesFor(pack);
            JSONObject backup = backup(changes.keySet());
            if (!state.edit().putString(KEY_ACTIVE, packId)
                    .putString(KEY_BACKUP, backup.toString()).commit()) return false;
            SharedPreferences.Editor edit = settings.edit();
            for (Map.Entry<String, Object> change : changes.entrySet()) {
                applyValue(edit, change.getKey(), change.getValue());
            }
            edit.apply();
            applyLocks(pack.manifest);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public synchronized void deactivate() {
        String backup = state.getString(KEY_BACKUP, null);
        if (backup != null) restoreBackup(backup);
        settings.edit()
                .remove(CustomImageManager.PACK_DIR_KEY)
                .putLong(CustomImageManager.REVISION_KEY, System.currentTimeMillis())
                .apply();
        state.edit().remove(KEY_ACTIVE).remove(KEY_BACKUP).commit();
        LockedSettings.clear();
    }

    public synchronized void delete(String packId) {
        if (packId.equals(activePackId())) deactivate();
        PackInfo pack = find(packId);
        if (pack != null) deleteTree(pack.root);
    }

    public String activePackId() { return state.getString(KEY_ACTIVE, null); }

    private Map<String, Object> changesFor(PackInfo pack) throws Exception {
        Map<String, Object> changes = new LinkedHashMap<>();
        JSONObject source = pack.manifest.getSettings();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String sourceKey = keys.next();
            String key = translate(sourceKey);
            if (key == null || !ALLOWED_KEYS.contains(key)) continue;
            changes.put(key, jsonValue(source.opt(sourceKey)));
        }
        if (!pack.manifest.getEnabledPhraseCategories().isEmpty()) {
            changes.put(SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES,
                    pack.manifest.getEnabledPhraseCategories());
        }
        if (!pack.manifest.getCustomPhrases().isEmpty()) {
            changes.put(SettingsRepository.KEY_CUSTOM_PHRASES,
                    new LinkedHashSet<>(pack.manifest.getCustomPhrases()));
        }
        File customImages = new File(pack.root, "custom_images_active");
        if (customImages.isDirectory()) {
            changes.put(CustomImageManager.PACK_DIR_KEY, customImages.getAbsolutePath());
            changes.put(CustomImageManager.REVISION_KEY, System.currentTimeMillis());
        }
        return changes;
    }

    private void prepareCustomImages(File staging, PackManifest manifest) throws IOException {
        if (manifest.getCustomImageFiles().isEmpty()) return;
        File destination = new File(staging, "custom_images_active");
        if (!destination.mkdirs()) throw new IOException("Could not prepare pack images");
        int index = 0;
        for (String relative : manifest.getCustomImageFiles()) {
            File source = new File(staging, relative);
            assertInside(staging, source);
            if (!source.isFile()) throw new IOException("Pack image is missing: " + relative);
            String lower = relative.toLowerCase(java.util.Locale.ROOT);
            String extension = lower.endsWith(".png") ? ".png"
                    : lower.endsWith(".webp") ? ".webp"
                    : lower.endsWith(".jpeg") ? ".jpeg" : ".jpg";
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(
                         new File(destination, "image-" + index++ + extension))) {
                copyBounded(input, output, MAX_ENTRY_BYTES);
            }
        }
    }

    private void extractArchive(File archive, File destination) throws IOException {
        int entries = 0;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("Pack contains too many entries");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains(":")
                        || name.indexOf('\u0000') >= 0) throw new IOException("Unsafe pack path");
                File target = new File(destination, name);
                assertInside(destination, target);
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) throw new IOException("Could not create pack folder");
                    continue;
                }
                File parent = target.getParentFile();
                if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                    throw new IOException("Could not create pack path");
                }
                long entryBytes = 0;
                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        entryBytes += read;
                        total += read;
                        if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                            throw new IOException("Pack extraction limit exceeded");
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private JSONObject backup(Set<String> keys) throws Exception {
        JSONObject result = new JSONObject();
        Map<String, ?> all = settings.getAll();
        for (String key : keys) {
            JSONObject value = new JSONObject();
            value.put("present", all.containsKey(key));
            if (all.containsKey(key)) encode(value, all.get(key));
            result.put(key, value);
        }
        return result;
    }

    private void restoreBackup(String raw) {
        try {
            JSONObject backup = new JSONObject(raw);
            SharedPreferences.Editor edit = settings.edit();
            Iterator<String> keys = backup.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject value = backup.optJSONObject(key);
                if (value == null || !value.optBoolean("present", false)) edit.remove(key);
                else applyEncoded(edit, key, value);
            }
            edit.apply();
        } catch (Exception ignored) {
            // A corrupt private backup is cleared below rather than partially trusted.
        }
    }

    private void restoreActiveLockState() {
        String id = activePackId();
        if (id == null) {
            LockedSettings.clear();
            return;
        }
        PackInfo pack = find(id);
        if (pack == null) {
            deactivate();
            return;
        }
        applyLocks(pack.manifest);
    }

    private void applyLocks(PackManifest manifest) {
        Set<String> keys = new LinkedHashSet<>();
        for (String value : manifest.getLockedSettings()) {
            if ("phrases".equals(value)) {
                keys.add(SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES);
                keys.add(SettingsRepository.KEY_CUSTOM_PHRASES);
            } else if ("images".equals(value)) keys.add(CustomImageManager.PREFS_KEY);
            else {
                String translated = translate(value);
                if (translated != null) keys.add(translated);
            }
        }
        LockedSettings.set(keys);
    }

    private PackInfo find(String id) {
        for (PackInfo pack : listInstalled()) if (pack.manifest.getPackId().equals(id)) return pack;
        return null;
    }

    private PackInfo readInstalled(File directory) throws Exception {
        File manifestFile = new File(directory, "manifest.json");
        if (!manifestFile.isFile()) return null;
        PackManifest manifest = PackManifest.parse(
                new JSONObject(readBounded(manifestFile, 512 * 1024)));
        if (!manifest.integrityDigestValid()) return null;
        return new PackInfo(directory, manifest);
    }

    private static Object jsonValue(Object value) {
        if (value instanceof JSONArray) {
            Set<String> result = new LinkedHashSet<>();
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                String item = array.optString(index, "").trim();
                if (!item.isEmpty()) result.add(item);
            }
            return result;
        }
        if (value == JSONObject.NULL) return null;
        return value;
    }

    private static void applyValue(SharedPreferences.Editor edit, String key, Object value) {
        if (value == null) edit.remove(key);
        else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) edit.putInt(key, (Integer) value);
        else if (value instanceof Long) edit.putLong(key, (Long) value);
        else if (value instanceof Number) edit.putFloat(key, ((Number) value).floatValue());
        else if (value instanceof Set) {
            @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) value;
            edit.putStringSet(key, new LinkedHashSet<>(strings));
        } else edit.putString(key, value.toString());
    }

    private static void encode(JSONObject target, Object value) throws Exception {
        if (value instanceof Boolean) { target.put("type", "bool"); target.put("value", value); }
        else if (value instanceof Integer) { target.put("type", "int"); target.put("value", value); }
        else if (value instanceof Long) { target.put("type", "long"); target.put("value", value); }
        else if (value instanceof Float) { target.put("type", "float"); target.put("value", value); }
        else if (value instanceof String) { target.put("type", "string"); target.put("value", value); }
        else if (value instanceof Set) { target.put("type", "stringSet"); target.put("value", new JSONArray((Set<?>) value)); }
        else { target.put("type", "string"); target.put("value", String.valueOf(value)); }
    }

    private static void applyEncoded(SharedPreferences.Editor edit, String key, JSONObject value) {
        switch (value.optString("type")) {
            case "bool": edit.putBoolean(key, value.optBoolean("value")); break;
            case "int": edit.putInt(key, value.optInt("value")); break;
            case "long": edit.putLong(key, value.optLong("value")); break;
            case "float": edit.putFloat(key, (float) value.optDouble("value")); break;
            case "stringSet":
                Set<String> values = new LinkedHashSet<>();
                JSONArray array = value.optJSONArray("value");
                if (array != null) for (int index = 0; index < array.length(); index++) {
                    values.add(array.optString(index));
                }
                edit.putStringSet(key, values);
                break;
            default: edit.putString(key, value.optString("value"));
        }
    }

    private static String translate(String value) {
        if (value == null) return null;
        if (ALLOWED_KEYS.contains(value)) return value;
        return KEY_ALIASES.get(value);
    }

    private static Map<String, String> aliases() {
        Map<String, String> values = new HashMap<>();
        values.put("preset", SettingsRepository.KEY_DETECTION_PRESET);
        values.put("style", SettingsRepository.KEY_CENSOR_TYPE);
        values.put("censorStyle", SettingsRepository.KEY_CENSOR_TYPE);
        values.put("intensity", SettingsRepository.KEY_CENSOR_INTENSITY);
        values.put("showBorder", SettingsRepository.KEY_SHOW_BORDER);
        values.put("showText", SettingsRepository.KEY_SHOW_TEXT);
        values.put("borderColor", SettingsRepository.KEY_BORDER_COLOR);
        values.put("borderEffect", SettingsRepository.KEY_BORDER_EFFECT);
        values.put("animateBorder", SettingsRepository.KEY_ANIMATE_BORDER);
        values.put("padding", SettingsRepository.KEY_CENSOR_SIZE_PADDING);
        values.put("reverseMode", SettingsRepository.KEY_REVERSE_MODE);
        values.put("reverseStrength", SettingsRepository.KEY_REVERSE_STRENGTH);
        values.put("categories", SettingsRepository.KEY_ENABLED_CATEGORIES);
        return Collections.unmodifiableMap(values);
    }

    private void assertInsideRoot(File file) throws IOException { assertInside(root, file); }

    private static void assertInside(File parent, File child) throws IOException {
        String rootPath = parent.getCanonicalPath() + File.separator;
        String childPath = child.getCanonicalPath();
        if (!childPath.startsWith(rootPath)) throw new IOException("Pack path escaped private storage");
    }

    private void deleteTree(File target) {
        try {
            assertInsideRoot(target);
        } catch (IOException unsafe) {
            return;
        }
        File[] children = target.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!target.delete()) target.deleteOnExit();
    }

    private static void copyBounded(InputStream input, FileOutputStream output, long limit)
            throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Pack input limit exceeded");
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static String readBounded(File file, int limit) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IOException("Manifest is too large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static final class PackInfo {
        private final File root;
        private final PackManifest manifest;

        PackInfo(File root, PackManifest manifest) {
            this.root = root;
            this.manifest = manifest;
        }

        public PackManifest getManifest() { return manifest; }
        public File getRoot() { return root; }
    }
}
