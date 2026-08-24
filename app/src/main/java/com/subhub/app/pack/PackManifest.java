package com.subhub.app.pack;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Strictly parsed subset of Beta Blocker format-v4 Android packs. */
public final class PackManifest {
    public static final int MIN_SUPPORTED_FORMAT = 4;
    private final JSONObject source;
    private final int formatVersion;
    private final String targetPlatform;
    private final String packId;
    private final String name;
    private final String author;
    private final String description;
    private final String version;
    private final JSONObject settings;
    private final Set<String> lockedSettings;
    private final List<String> customImageFiles;
    private final Set<String> enabledPhraseCategories;
    private final List<String> customPhrases;

    private PackManifest(
            JSONObject source,
            int formatVersion,
            String targetPlatform,
            String packId,
            String name,
            String author,
            String description,
            String version,
            JSONObject settings,
            Set<String> lockedSettings,
            List<String> customImageFiles,
            Set<String> enabledPhraseCategories,
            List<String> customPhrases) {
        this.source = source;
        this.formatVersion = formatVersion;
        this.targetPlatform = targetPlatform;
        this.packId = packId;
        this.name = name;
        this.author = author;
        this.description = description;
        this.version = version;
        this.settings = settings;
        this.lockedSettings = lockedSettings;
        this.customImageFiles = customImageFiles;
        this.enabledPhraseCategories = enabledPhraseCategories;
        this.customPhrases = customPhrases;
    }

    public static PackManifest parse(JSONObject json)
            throws IllegalArgumentException, org.json.JSONException {
        JSONObject source = PackVerifier.deepCopy(json);
        int format = source.optInt("formatVersion", source.optInt("format_version", 0));
        if (format < MIN_SUPPORTED_FORMAT) throw new IllegalArgumentException("Unsupported pack format");
        String target = clean(source.optString("targetPlatform", source.optString("target_platform", "android")), 24);
        if (!target.equalsIgnoreCase("android") && !target.equalsIgnoreCase("both")) {
            throw new IllegalArgumentException("Pack does not target Android");
        }
        String id = clean(source.optString("packId", source.optString("pack_id", "")), 80);
        if (!id.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("Invalid pack id");
        String name = clean(source.optString("name", id), 100);
        String author = clean(source.optString("author", "Unknown"), 100);
        String description = clean(source.optString("description", ""), 500);
        String version = clean(source.optString("version", "1"), 40);
        JSONObject settings = source.optJSONObject("settings");
        if (settings == null) settings = new JSONObject();
        Set<String> locked = stringSet(source.optJSONArray("lockedSettings"));
        if (locked.isEmpty()) locked = stringSet(source.optJSONArray("locked_settings"));
        List<String> images = safePaths(source.optJSONArray("customImageFiles"));
        if (images.isEmpty()) images = safePaths(source.optJSONArray("custom_image_files"));
        if (images.isEmpty()) images = safePaths(source.optJSONArray("images"));
        Set<String> phraseCategories = stringSet(source.optJSONArray("enabledPhraseCategories"));
        if (phraseCategories.isEmpty()) {
            phraseCategories = stringSet(settings.optJSONArray("enabled_phrase_categories"));
        }
        List<String> phrases = strings(source.optJSONArray("customPhrases"), 100, 80);
        if (phrases.isEmpty()) phrases = strings(settings.optJSONArray("custom_phrases"), 100, 80);
        return new PackManifest(
                source, format, target, id, name, author, description,
                version, settings, Collections.unmodifiableSet(locked),
                Collections.unmodifiableList(images), Collections.unmodifiableSet(phraseCategories),
                Collections.unmodifiableList(phrases));
    }

    public JSONObject source() { return source; }
    public int getFormatVersion() { return formatVersion; }
    public String getTargetPlatform() { return targetPlatform; }
    public String getPackId() { return packId; }
    public String getName() { return name; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public JSONObject getSettings() { return settings; }
    public Set<String> getLockedSettings() { return lockedSettings; }
    public List<String> getCustomImageFiles() { return customImageFiles; }
    public Set<String> getEnabledPhraseCategories() { return enabledPhraseCategories; }
    public List<String> getCustomPhrases() { return customPhrases; }
    public boolean hasIntegrityDigest() { return PackVerifier.hasIntegrityDigest(source); }
    public boolean integrityDigestValid() { return PackVerifier.verifyIntegrityDigest(source); }

    private static Set<String> stringSet(JSONArray array) {
        Set<String> result = new LinkedHashSet<>();
        if (array == null) return result;
        for (int index = 0; index < Math.min(array.length(), 100); index++) {
            String value = clean(array.optString(index, ""), 100);
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static List<String> strings(JSONArray array, int maximumCount, int maximumLength) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (int index = 0; index < Math.min(array.length(), maximumCount); index++) {
            String value = clean(array.optString(index, ""), maximumLength);
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static List<String> safePaths(JSONArray array) {
        List<String> result = new ArrayList<>();
        for (String value : strings(array, 64, 180)) {
            String normalized = value.replace('\\', '/');
            if (normalized.startsWith("/") || normalized.contains("../")
                    || normalized.contains(":") || normalized.contains("\u0000")) continue;
            result.add(normalized);
        }
        return result;
    }

    private static String clean(String value, int maximum) {
        String safe = value == null ? "" : value.trim().replaceAll("[\\p{Cntrl}]", "");
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
