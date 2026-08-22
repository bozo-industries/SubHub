package com.betasafe.app.stats;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.detection.TrackedObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Process-safe local counters compatible with the recovered statistics preference store. */
public final class StatsRepository {
    public static final String PREFS_NAME = "betablocker_stats";
    private static final String KEY_ACTIVE_DATES = "active_dates";
    private static final String KEY_ALL_CATEGORIES_CENSORS = "all_categories_censors";
    private static final String KEY_BORDER_COLOR_CHANGED = "border_color_changed";
    private static final String KEY_BORDER_EFFECTS_TRIED = "border_effects_tried";
    private static final String KEY_BROWSER_SESSIONS = "browser_sessions_count";
    private static final String KEY_BROWSER_PAGES = "browser_pages_visited";
    private static final String KEY_CENSOR_STYLES_TRIED = "censor_styles_tried";
    private static final String KEY_CENSOR_STYLE_CHANGES = "censor_style_changes";
    private static final String KEY_CURRENT_STREAK = "current_streak";
    private static final String KEY_CUSTOM_PHRASES_COUNT = "custom_phrases_count";
    private static final String KEY_EXPORTED_IMAGES = "exported_images_count";
    private static final String KEY_LAST_SESSION_DATE = "last_session_date";
    private static final String KEY_LONGEST_SESSION = "longest_session_seconds";
    private static final String KEY_PEAK_SESSION_BLOCKS = "peak_session_blocks";
    private static final String KEY_PROFILES_COUNT = "profiles_count";
    private static final String KEY_SESSIONS_COUNT = "sessions_count";
    private static final String KEY_SESSION_HISTORY = "session_history";
    private static final String KEY_TOTAL_BLOCKS = "total_blocks_all_time";
    private static final String KEY_TOTAL_PROTECTED_TIME = "total_protected_time";
    private static final String KEY_TOTAL_SESSION_TIME = "total_session_time";
    private static final int SESSION_HISTORY_MAX = 30;
    private static final Object SESSION_LOCK = new Object();
    private static final Set<Integer> SEEN_TRACK_IDS = new HashSet<>();
    private static final Set<String> ALL_CATEGORIES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("genitals_female", "genitals_male", "breasts",
                    "buttocks", "anus", "face", "belly", "male_chest", "feet",
                    "armpits", "genitals_covered", "breasts_covered", "buttocks_covered",
                    "anus_covered", "belly_covered", "feet_covered", "armpits_covered")));
    private static long sessionStartMs;
    private static int sessionBlocks;

    private final SharedPreferences preferences;

    public StatsRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void startSession() {
        synchronized (SESSION_LOCK) {
            if (sessionStartMs != 0) return;
            sessionStartMs = System.currentTimeMillis();
            sessionBlocks = 0;
            SEEN_TRACK_IDS.clear();
            preferences.edit()
                    .putInt(KEY_SESSIONS_COUNT, preferences.getInt(KEY_SESSIONS_COUNT, 0) + 1)
                    .apply();
            updateStreak();
        }
    }

    public boolean onTracks(List<TrackedObject> tracks) {
        return onTrackIds(trackIds(tracks), null);
    }

    public boolean onTracks(List<TrackedObject> tracks, Set<String> enabledCategories) {
        return onTrackIds(trackIds(tracks), enabledCategories);
    }

    public boolean onTrackIds(List<Integer> trackIds, Set<String> enabledCategories) {
        synchronized (SESSION_LOCK) {
            int added = 0;
            for (Integer id : trackIds) if (id != null && SEEN_TRACK_IDS.add(id)) added++;
            if (added == 0) return false;
            sessionBlocks += added;
            SharedPreferences.Editor edit = preferences.edit()
                    .putLong(KEY_TOTAL_BLOCKS, preferences.getLong(KEY_TOTAL_BLOCKS, 0) + added);
            if (sessionBlocks > preferences.getInt(KEY_PEAK_SESSION_BLOCKS, 0)) {
                edit.putInt(KEY_PEAK_SESSION_BLOCKS, sessionBlocks);
            }
            Set<String> dates = mutableSet(KEY_ACTIVE_DATES);
            if (dates.add(today())) edit.putStringSet(KEY_ACTIVE_DATES, dates);
            if (enabledCategories != null && enabledCategories.containsAll(ALL_CATEGORIES)) {
                edit.putLong(KEY_ALL_CATEGORIES_CENSORS,
                        preferences.getLong(KEY_ALL_CATEGORIES_CENSORS, 0) + added);
            }
            edit.apply();
            return true;
        }
    }

    public void endSession() {
        synchronized (SESSION_LOCK) {
            if (sessionStartMs == 0) return;
            long started = sessionStartMs;
            long durationSeconds = Math.max(0, (System.currentTimeMillis() - started) / 1000);
            SharedPreferences.Editor edit = preferences.edit()
                    .putLong(KEY_TOTAL_SESSION_TIME,
                            preferences.getLong(KEY_TOTAL_SESSION_TIME, 0) + durationSeconds)
                    .putLong(KEY_TOTAL_PROTECTED_TIME,
                            preferences.getLong(KEY_TOTAL_PROTECTED_TIME, 0) + durationSeconds);
            if (durationSeconds > preferences.getLong(KEY_LONGEST_SESSION, 0)) {
                edit.putLong(KEY_LONGEST_SESSION, durationSeconds);
            }
            edit.apply();
            appendSessionHistory(started, durationSeconds, sessionBlocks);
            sessionStartMs = 0;
            sessionBlocks = 0;
            SEEN_TRACK_IDS.clear();
        }
    }

    public StatsSnapshot load() {
        synchronized (SESSION_LOCK) {
            long protectedSeconds = preferences.getLong(KEY_TOTAL_PROTECTED_TIME, 0);
            long totalSessionSeconds = preferences.getLong(KEY_TOTAL_SESSION_TIME, 0);
            if (sessionStartMs > 0) {
                long live = Math.max(0, (System.currentTimeMillis() - sessionStartMs) / 1000);
                protectedSeconds += live;
                totalSessionSeconds += live;
            }
            return new StatsSnapshot(preferences.getLong(KEY_TOTAL_BLOCKS, 0),
                    totalSessionSeconds, preferences.getInt(KEY_SESSIONS_COUNT, 0),
                    preferences.getInt(KEY_CURRENT_STREAK, 0),
                    preferences.getString(KEY_LAST_SESSION_DATE, ""), protectedSeconds,
                    sessionBlocks, preferences.getInt(KEY_PEAK_SESSION_BLOCKS, 0),
                    preferences.getLong(KEY_LONGEST_SESSION, 0),
                    preferences.getInt(KEY_BROWSER_SESSIONS, 0),
                    preferences.getInt(KEY_BROWSER_PAGES, 0),
                    getLongCompat(KEY_EXPORTED_IMAGES),
                    preferences.getInt(KEY_PROFILES_COUNT, 0),
                    preferences.getInt(KEY_CUSTOM_PHRASES_COUNT, 0),
                    preferences.getInt(KEY_CENSOR_STYLE_CHANGES, 0),
                    preferences.getBoolean(KEY_BORDER_COLOR_CHANGED, false),
                    mutableSet(KEY_CENSOR_STYLES_TRIED), mutableSet(KEY_BORDER_EFFECTS_TRIED),
                    mutableSet(KEY_ACTIVE_DATES),
                    preferences.getLong(KEY_ALL_CATEGORIES_CENSORS, 0));
        }
    }

    public List<SessionEntry> getSessionHistory() {
        List<SessionEntry> result = new ArrayList<>();
        String raw = preferences.getString(KEY_SESSION_HISTORY, "");
        if (raw == null || raw.trim().isEmpty()) return result;
        for (String encoded : raw.split(";")) {
            String[] parts = encoded.split(",", -1);
            if (parts.length != 3) continue;
            try {
                long start = Long.parseLong(parts[0]);
                long duration = Long.parseLong(parts[1]);
                int blocks = Integer.parseInt(parts[2]);
                if (start > 0 && duration >= 0 && blocks >= 0) {
                    result.add(new SessionEntry(start, duration, blocks));
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy entries without discarding the remaining history.
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void recordCensorStyleTried(String style) { addToSet(KEY_CENSOR_STYLES_TRIED, style); }
    public void recordBorderEffectTried(String effect) { addToSet(KEY_BORDER_EFFECTS_TRIED, effect); }
    public void incrementCensorStyleChanges() {
        preferences.edit().putInt(KEY_CENSOR_STYLE_CHANGES,
                preferences.getInt(KEY_CENSOR_STYLE_CHANGES, 0) + 1).apply();
    }
    public void setBorderColorChanged() {
        preferences.edit().putBoolean(KEY_BORDER_COLOR_CHANGED, true).apply();
    }
    public void setCustomPhrasesCount(int count) {
        preferences.edit().putInt(KEY_CUSTOM_PHRASES_COUNT, Math.max(0, count)).apply();
    }
    public void setProfilesCount(int count) {
        preferences.edit().putInt(KEY_PROFILES_COUNT, Math.max(0, count)).apply();
    }
    public void addExportedImages(int count) {
        if (count <= 0) return;
        preferences.edit().putLong(KEY_EXPORTED_IMAGES,
                getLongCompat(KEY_EXPORTED_IMAGES) + count).apply();
    }
    public void recordBrowserSession() {
        preferences.edit().putInt(KEY_BROWSER_SESSIONS,
                preferences.getInt(KEY_BROWSER_SESSIONS, 0) + 1).apply();
    }
    public void addBrowserPage() {
        preferences.edit().putInt(KEY_BROWSER_PAGES,
                preferences.getInt(KEY_BROWSER_PAGES, 0) + 1).apply();
    }

    private void updateStreak() {
        String today = today();
        String previous = preferences.getString(KEY_LAST_SESSION_DATE, "");
        int streak;
        if (today.equals(previous)) streak = preferences.getInt(KEY_CURRENT_STREAK, 0);
        else if (LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                .equals(previous)) streak = preferences.getInt(KEY_CURRENT_STREAK, 0) + 1;
        else streak = 1;
        preferences.edit().putInt(KEY_CURRENT_STREAK, streak)
                .putString(KEY_LAST_SESSION_DATE, today).apply();
    }

    private void appendSessionHistory(long started, long duration, int blocks) {
        List<String> entries = new ArrayList<>();
        String raw = preferences.getString(KEY_SESSION_HISTORY, "");
        if (raw != null) for (String item : raw.split(";")) {
            if (!item.trim().isEmpty()) entries.add(item);
        }
        entries.add(started + "," + duration + "," + blocks);
        while (entries.size() > SESSION_HISTORY_MAX) entries.remove(0);
        preferences.edit().putString(KEY_SESSION_HISTORY, String.join(";", entries)).apply();
    }

    private void addToSet(String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        Set<String> values = mutableSet(key);
        if (values.add(value.trim())) preferences.edit().putStringSet(key, values).apply();
    }

    private Set<String> mutableSet(String key) {
        Set<String> stored = preferences.getStringSet(key, Collections.emptySet());
        return new LinkedHashSet<>(stored == null ? Collections.emptySet() : stored);
    }

    private long getLongCompat(String key) {
        Object value = preferences.getAll().get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static List<Integer> trackIds(List<TrackedObject> tracks) {
        List<Integer> ids = new ArrayList<>(tracks.size());
        for (TrackedObject track : tracks) ids.add(track.getId());
        return ids;
    }
    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static final class SessionEntry {
        private final long startMillis;
        private final long durationSeconds;
        private final int blocks;
        public SessionEntry(long startMillis, long durationSeconds, int blocks) {
            this.startMillis = startMillis;
            this.durationSeconds = durationSeconds;
            this.blocks = blocks;
        }
        public long getStartMillis() { return startMillis; }
        public long getDurationSeconds() { return durationSeconds; }
        public int getBlocks() { return blocks; }
    }
}
