package com.betasafe.app.profiles;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.settings.SettingsRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Named snapshots of the recovered visual, detector, category, and phrase settings. */
public final class ProfileManager {
    private static final String PREFS_NAME = "betablocker_profiles";
    private static final String KEY_NAMES = "profile_names";
    private static final String PROFILE_PREFIX = "profile_";
    private static final int MAX_PROFILES = 50;
    private static final List<String> BOOL_KEYS = Arrays.asList(
            SettingsRepository.KEY_SHOW_BORDER,
            SettingsRepository.KEY_ANIMATE_BORDER,
            SettingsRepository.KEY_SHOW_TEXT,
            SettingsRepository.KEY_REVERSE_MODE);
    private static final List<String> STRING_KEYS = Arrays.asList(
            SettingsRepository.KEY_DETECTION_PRESET,
            SettingsRepository.KEY_CENSOR_TYPE,
            SettingsRepository.KEY_BORDER_EFFECT,
            SettingsRepository.KEY_BORDER_COLOR,
            SettingsRepository.KEY_REVERSE_CUTOUT_SHAPE,
            SettingsRepository.KEY_ERROR_TITLE,
            SettingsRepository.KEY_ERROR_TEXT);
    private static final List<String> INT_KEYS = Arrays.asList(
            SettingsRepository.KEY_CENSOR_INTENSITY,
            SettingsRepository.KEY_CONFIDENCE);
    private static final List<String> FLOAT_KEYS = Arrays.asList(
            SettingsRepository.KEY_CENSOR_SIZE_PADDING,
            SettingsRepository.KEY_REVERSE_STRENGTH);
    private static final List<String> STRING_SET_KEYS = Arrays.asList(
            SettingsRepository.KEY_ENABLED_CATEGORIES,
            SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES,
            SettingsRepository.KEY_CUSTOM_PHRASES);

    private final Context context;
    private final SharedPreferences profiles;

    public ProfileManager(Context context) {
        this.context = context.getApplicationContext();
        profiles = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<String> listProfiles() {
        Set<String> names = profiles.getStringSet(KEY_NAMES, Collections.emptySet());
        List<String> result = new ArrayList<>(names == null ? Collections.emptySet() : names);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(result);
    }

    public boolean save(String requestedName) {
        String name = normalizeName(requestedName);
        if (name.isEmpty()) return false;
        Set<String> names = new LinkedHashSet<>(listProfiles());
        if (!names.contains(name) && names.size() >= MAX_PROFILES) return false;
        JSONObject snapshot = snapshot(context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE));
        names.add(name);
        profiles.edit()
                .putString(PROFILE_PREFIX + name, snapshot.toString())
                .putStringSet(KEY_NAMES, names)
                .apply();
        return true;
    }

    public boolean load(String name) {
        String raw = profiles.getString(PROFILE_PREFIX + name, null);
        if (raw == null) return false;
        try {
            return apply(context.getSharedPreferences(
                    SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE),
                    new JSONObject(raw));
        } catch (Exception error) {
            return false;
        }
    }

    public void delete(String name) {
        Set<String> names = new LinkedHashSet<>(listProfiles());
        if (!names.remove(name)) return;
        profiles.edit().remove(PROFILE_PREFIX + name).putStringSet(KEY_NAMES, names).apply();
    }

    static JSONObject snapshot(SharedPreferences source) {
        JSONObject result = new JSONObject();
        try {
            for (String key : BOOL_KEYS) if (source.contains(key)) result.put(key, source.getBoolean(key, false));
            for (String key : STRING_KEYS) if (source.contains(key)) result.put(key, source.getString(key, ""));
            for (String key : INT_KEYS) if (source.contains(key)) result.put(key, source.getInt(key, 0));
            for (String key : FLOAT_KEYS) if (source.contains(key)) result.put(key, source.getFloat(key, 0f));
            for (String key : STRING_SET_KEYS) {
                Set<String> values = source.getStringSet(key, null);
                if (values != null) result.put(key, new JSONArray(values));
            }
        } catch (Exception impossible) {
            throw new IllegalStateException("Could not encode settings snapshot", impossible);
        }
        return result;
    }

    static boolean apply(SharedPreferences target, JSONObject snapshot) {
        try {
            SharedPreferences.Editor edit = target.edit();
            for (String key : BOOL_KEYS) if (snapshot.has(key)) edit.putBoolean(key, snapshot.optBoolean(key));
            for (String key : STRING_KEYS) if (snapshot.has(key)) edit.putString(key, snapshot.optString(key));
            for (String key : INT_KEYS) if (snapshot.has(key)) edit.putInt(key, snapshot.optInt(key));
            for (String key : FLOAT_KEYS) if (snapshot.has(key)) edit.putFloat(key, (float) snapshot.optDouble(key));
            for (String key : STRING_SET_KEYS) {
                JSONArray values = snapshot.optJSONArray(key);
                if (values == null) continue;
                Set<String> decoded = new LinkedHashSet<>();
                for (int index = 0; index < values.length(); index++) {
                    String value = values.optString(index, "").trim();
                    if (!value.isEmpty()) decoded.add(value);
                }
                edit.putStringSet(key, decoded);
            }
            return edit.commit();
        } catch (Exception error) {
            return false;
        }
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("[\\p{Cntrl}/\\\\]", "");
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
    }
}
