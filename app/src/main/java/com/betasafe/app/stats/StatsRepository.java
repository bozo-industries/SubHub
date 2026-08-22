package com.betasafe.app.stats;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.detection.TrackedObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Process-safe local counters, compatible with the original statistics preference store. */
public final class StatsRepository {
    private static final String PREFS_NAME = "betablocker_stats";
    private static final String KEY_TOTAL_BLOCKS = "total_blocks_all_time";
    private static final String KEY_TOTAL_PROTECTED_TIME = "total_protected_time";
    private static final String KEY_TOTAL_SESSION_TIME = "total_session_time";
    private static final String KEY_SESSIONS_COUNT = "sessions_count";
    private static final String KEY_PEAK_SESSION_BLOCKS = "peak_session_blocks";
    private static final String KEY_LONGEST_SESSION = "longest_session_seconds";
    private static final String KEY_EXPORTED_IMAGES = "exported_images_count";
    private static final String KEY_BROWSER_PAGES = "browser_pages_visited";

    private final SharedPreferences preferences;
    private final Set<Integer> seenTrackIds = new HashSet<>();
    private long sessionStartMs;
    private int sessionBlocks;

    public StatsRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void startSession() {
        sessionStartMs = System.currentTimeMillis();
        sessionBlocks = 0;
        seenTrackIds.clear();
        preferences.edit()
                .putInt(KEY_SESSIONS_COUNT, preferences.getInt(KEY_SESSIONS_COUNT, 0) + 1)
                .apply();
    }

    public synchronized void onTracks(List<TrackedObject> tracks) {
        int added = 0;
        for (TrackedObject track : tracks) {
            if (seenTrackIds.add(track.getId())) added++;
        }
        if (added == 0) return;
        sessionBlocks += added;
        SharedPreferences.Editor edit = preferences.edit()
                .putLong(KEY_TOTAL_BLOCKS, preferences.getLong(KEY_TOTAL_BLOCKS, 0) + added);
        if (sessionBlocks > preferences.getInt(KEY_PEAK_SESSION_BLOCKS, 0)) {
            edit.putInt(KEY_PEAK_SESSION_BLOCKS, sessionBlocks);
        }
        edit.apply();
    }

    public synchronized void endSession() {
        if (sessionStartMs == 0) return;
        long durationSeconds = Math.max(0, (System.currentTimeMillis() - sessionStartMs) / 1000);
        SharedPreferences.Editor edit = preferences.edit()
                .putLong(
                        KEY_TOTAL_SESSION_TIME,
                        preferences.getLong(KEY_TOTAL_SESSION_TIME, 0) + durationSeconds)
                .putLong(
                        KEY_TOTAL_PROTECTED_TIME,
                        preferences.getLong(KEY_TOTAL_PROTECTED_TIME, 0) + durationSeconds);
        if (durationSeconds > preferences.getLong(KEY_LONGEST_SESSION, 0)) {
            edit.putLong(KEY_LONGEST_SESSION, durationSeconds);
        }
        edit.apply();
        sessionStartMs = 0;
    }

    public synchronized StatsSnapshot load() {
        long protectedSeconds = preferences.getLong(KEY_TOTAL_PROTECTED_TIME, 0);
        if (sessionStartMs > 0) {
            protectedSeconds += Math.max(0, (System.currentTimeMillis() - sessionStartMs) / 1000);
        }
        return new StatsSnapshot(
                preferences.getLong(KEY_TOTAL_BLOCKS, 0),
                protectedSeconds,
                preferences.getInt(KEY_SESSIONS_COUNT, 0),
                sessionBlocks,
                preferences.getInt(KEY_PEAK_SESSION_BLOCKS, 0));
    }

    public synchronized void addExportedImages(int count) {
        if (count <= 0) return;
        preferences.edit().putInt(
                KEY_EXPORTED_IMAGES,
                preferences.getInt(KEY_EXPORTED_IMAGES, 0) + count).apply();
    }

    public synchronized void addBrowserPage() {
        preferences.edit().putInt(
                KEY_BROWSER_PAGES,
                preferences.getInt(KEY_BROWSER_PAGES, 0) + 1).apply();
    }
}
