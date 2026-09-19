package com.subhub.app.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Versioned, content-free local profile format. Unknown/corrupt data never becomes a profile. */
final class ScrollProfileCodec {
    static final int VERSION = 1, MAX_ENTRIES = 32, MAX_BYTES = 262144;
    static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;

    static final class Entry {
        final ScrollCalibrationLearner.Profile profile;
        final long trainedAtWallMs;
        Entry(ScrollCalibrationLearner.Profile profile, long trainedAtWallMs) {
            this.profile = profile; this.trainedAtWallMs = trainedAtWallMs;
        }
    }
    static final class Decoded {
        final List<Entry> entries;
        final int rejected;
        final boolean malformed, unsupported, truncated;
        Decoded(List<Entry> entries, int rejected, boolean malformed, boolean unsupported, boolean truncated) {
            this.entries = List.copyOf(entries); this.rejected = rejected;
            this.malformed = malformed; this.unsupported = unsupported; this.truncated = truncated;
        }
    }

    static String encode(List<Entry> entries, long now) throws JSONException {
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many profiles");
        JSONArray values = new JSONArray();
        for (Entry entry : entries) {
            if (!valid(entry, now)) throw new IllegalArgumentException("Invalid profile");
            ScrollCalibrationLearner.Profile p = entry.profile;
            ScrollLearningKey k = p.key;
            values.put(new JSONObject().put("package", k.packageName).put("appVersion", k.appVersion)
                    .put("surface", k.surfaceDigest).put("width", k.width).put("height", k.height)
                    .put("density", k.densityDpi).put("rotation", k.rotation).put("refresh", k.refreshMilliHz)
                    .put("axis", k.axis.name()).put("evidence", k.evidence.name())
                    .put("scale", p.pixelsPerEventPixel).put("interval", p.eventIntervalMs)
                    .put("lag", p.deliveryLagMs).put("jitter", p.deliveryJitterMs)
                    .put("error", p.validationMeanErrorPx).put("training", p.trainingSamples)
                    .put("validation", p.validationSamples).put("gestures", p.gestures)
                    .put("trainedAt", entry.trainedAtWallMs));
        }
        return new JSONObject().put("schemaVersion", VERSION).put("profiles", values).toString();
    }

    static Decoded decode(String json, long now) {
        if (json == null || json.length() > MAX_BYTES) return failed(true, false);
        try {
            JSONObject root = new JSONObject(json);
            if (integer(root, "schemaVersion") != VERSION) return failed(false, true);
            if (root.length() != 2) return failed(true, false);
            JSONArray entries = root.getJSONArray("profiles");
            Map<ScrollLearningKey, Entry> retained = new LinkedHashMap<>();
            int rejected = 0;
            for (int index = 0; index < Math.min(entries.length(), MAX_ENTRIES); index++) {
                try {
                    JSONObject value = entries.getJSONObject(index);
                    if (value.length() != 19) throw new IllegalArgumentException("Unknown profile fields");
                    ScrollLearningKey key = new ScrollLearningKey(string(value, "package"),
                            integer(value, "appVersion"), string(value, "surface"),
                            smallInteger(value, "width"), smallInteger(value, "height"),
                            smallInteger(value, "density"), smallInteger(value, "rotation"),
                            smallInteger(value, "refresh"), ScrollLearningKey.Axis.valueOf(string(value, "axis")),
                            ScrollLearningKey.Evidence.valueOf(string(value, "evidence")));
                    ScrollCalibrationLearner.Profile profile = new ScrollCalibrationLearner.Profile(key,
                            number(value, "scale"), number(value, "interval"), number(value, "lag"),
                            number(value, "jitter"), number(value, "error"), smallInteger(value, "training"),
                            smallInteger(value, "validation"), smallInteger(value, "gestures"));
                    Entry entry = new Entry(profile, integer(value, "trainedAt"));
                    if (!valid(entry, now)) throw new IllegalArgumentException("Stale or invalid profile");
                    Entry previous = retained.get(key);
                    if (previous != null) rejected++;
                    if (previous == null || previous.trainedAtWallMs < entry.trainedAtWallMs) retained.put(key, entry);
                } catch (JSONException | IllegalArgumentException failure) { rejected++; }
            }
            return new Decoded(new ArrayList<>(retained.values()), rejected, false, false,
                    entries.length() > MAX_ENTRIES);
        } catch (JSONException | IllegalArgumentException failure) { return failed(true, false); }
    }

    static boolean valid(Entry entry, long now) {
        if (entry == null || entry.profile == null || entry.profile.key == null
                || now <= 0 || entry.trainedAtWallMs <= 0 || entry.trainedAtWallMs > now
                || now - entry.trainedAtWallMs > MAX_AGE_MS) return false;
        ScrollCalibrationLearner.Profile p = entry.profile;
        return bounded(p.pixelsPerEventPixel, .125, 8) && bounded(p.eventIntervalMs, 16, 300)
                && bounded(p.deliveryLagMs, 0, ScrollCalibrationLearner.MAX_EVIDENCE_AGE_MS)
                && bounded(p.deliveryJitterMs, 0, ScrollCalibrationLearner.MAX_EVIDENCE_AGE_MS)
                && bounded(p.validationMeanErrorPx, 0, 10000)
                && p.trainingSamples >= 12 && p.trainingSamples <= 32
                && p.validationSamples >= 6 && p.validationSamples <= 32
                && p.gestures >= 3 && p.gestures <= 64;
    }

    private static boolean bounded(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
    private static Decoded failed(boolean malformed, boolean unsupported) {
        return new Decoded(List.of(), 0, malformed, unsupported, false);
    }
    private static double number(JSONObject value, String field) throws JSONException {
        Object item = value.get(field);
        if (!(item instanceof Number)) throw new IllegalArgumentException("Non-numeric field");
        double result = ((Number) item).doubleValue();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite field");
        return result;
    }
    private static String string(JSONObject value, String field) throws JSONException {
        Object item = value.get(field);
        if (!(item instanceof String)) throw new IllegalArgumentException("Non-string field");
        return (String) item;
    }
    private static long integer(JSONObject value, String field) throws JSONException {
        Object item = value.get(field);
        if (!(item instanceof Integer) && !(item instanceof Long)) throw new IllegalArgumentException("Non-integer field");
        return ((Number) item).longValue();
    }
    private static int smallInteger(JSONObject value, String field) throws JSONException {
        long result = integer(value, field);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException("Integer overflow");
        return (int) result;
    }
}
