package com.subhub.app.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One published SubHub version shown in the compact in-app changelog. */
public final class ReleaseHistoryItem {
    public final String versionName;
    public final String tag;
    public final String notes;
    public final String htmlUrl;
    public final String publishedAt;
    public final boolean prerelease;

    public ReleaseHistoryItem(String versionName, String tag, String notes, String htmlUrl,
            String publishedAt, boolean prerelease) {
        this.versionName = clean(versionName);
        this.tag = clean(tag);
        this.notes = clean(notes);
        this.htmlUrl = clean(htmlUrl);
        this.publishedAt = clean(publishedAt);
        this.prerelease = prerelease;
    }

    JSONObject json() throws JSONException {
        return new JSONObject()
                .put("versionName", versionName)
                .put("tag", tag)
                .put("notes", notes)
                .put("htmlUrl", htmlUrl)
                .put("publishedAt", publishedAt)
                .put("prerelease", prerelease);
    }

    static ReleaseHistoryItem parse(JSONObject json) {
        return new ReleaseHistoryItem(
                json.optString("versionName", ""),
                json.optString("tag", ""),
                json.optString("notes", ""),
                json.optString("htmlUrl", ""),
                json.optString("publishedAt", ""),
                json.optBoolean("prerelease", false));
    }

    public static String encode(List<ReleaseHistoryItem> items) throws JSONException {
        JSONArray array = new JSONArray();
        if (items != null) {
            for (ReleaseHistoryItem item : items) {
                if (item != null && !item.tag.isEmpty()) array.put(item.json());
            }
        }
        return array.toString();
    }

    public static List<ReleaseHistoryItem> decode(String value) throws JSONException {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        JSONArray array = new JSONArray(value);
        List<ReleaseHistoryItem> items = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject json = array.optJSONObject(index);
            if (json == null) continue;
            ReleaseHistoryItem item = parse(json);
            if (!item.tag.isEmpty()) items.add(item);
        }
        return Collections.unmodifiableList(items);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
