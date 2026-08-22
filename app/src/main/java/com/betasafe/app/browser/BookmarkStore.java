package com.betasafe.app.browser;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.settings.SettingsRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small local bookmark store compatible with the recovered browser preference name. */
final class BookmarkStore {
    private static final String KEY = "browser_bookmarks";
    private static final int MAX_BOOKMARKS = 100;
    private final SharedPreferences preferences;

    BookmarkStore(Context context) {
        preferences = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    List<Bookmark> load() {
        List<Bookmark> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject value = array.optJSONObject(index);
                if (value == null) continue;
                String url = value.optString("url", "");
                if (!BrowserUrl.isWebUrl(url)) continue;
                result.add(new Bookmark(value.optString("title", url), url));
            }
        } catch (Exception ignored) {
            // Corrupt imported bookmark data is treated as an empty list.
        }
        return Collections.unmodifiableList(result);
    }

    void add(String title, String url) {
        if (!BrowserUrl.isWebUrl(url)) return;
        List<Bookmark> values = new ArrayList<>(load());
        values.removeIf(item -> item.url.equals(url));
        values.add(0, new Bookmark(
                title == null || title.trim().isEmpty() ? url : title.trim(), url));
        if (values.size() > MAX_BOOKMARKS) values = values.subList(0, MAX_BOOKMARKS);
        save(values);
    }

    void remove(String url) {
        List<Bookmark> values = new ArrayList<>(load());
        values.removeIf(item -> item.url.equals(url));
        save(values);
    }

    private void save(List<Bookmark> values) {
        JSONArray array = new JSONArray();
        for (Bookmark bookmark : values) {
            JSONObject value = new JSONObject();
            try {
                value.put("title", bookmark.title);
                value.put("url", bookmark.url);
                array.put(value);
            } catch (Exception ignored) {
                // Both fields are ordinary strings and should always serialize.
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }

    static final class Bookmark {
        final String title;
        final String url;

        Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }
}
