package com.betasafe.app.pack;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Compatibility verifier for the original manifest SHA-256 integrity digest. */
public final class PackVerifier {
    private PackVerifier() {}

    public static boolean hasIntegrityDigest(JSONObject manifest) {
        return manifest != null && !manifest.optString("signature", "").trim().isEmpty();
    }

    public static boolean verifyIntegrityDigest(JSONObject manifest) {
        if (manifest == null) return false;
        String expected = manifest.optString("signature", "").trim();
        if (expected.isEmpty()) return true;
        try {
            JSONObject copy = deepCopy(manifest);
            copy.put("signature", "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalize(copy).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return MessageDigest.isEqual(
                    expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                    hex.toString().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception error) {
            return false;
        }
    }

    /** Copies JSON without round-tripping through text, which would turn values like 4.0 into 4. */
    static JSONObject deepCopy(JSONObject source) throws org.json.JSONException {
        JSONObject copy = new JSONObject();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            copy.put(key, deepCopyValue(source.opt(key)));
        }
        return copy;
    }

    private static Object deepCopyValue(Object value) throws org.json.JSONException {
        if (value instanceof JSONObject) return deepCopy((JSONObject) value);
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray copy = new JSONArray();
            for (int index = 0; index < source.length(); index++) {
                copy.put(deepCopyValue(source.opt(index)));
            }
            return copy;
        }
        return value;
    }

    static String canonicalize(Object value) {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof Boolean) return (Boolean) value ? "true" : "false";
        if (value instanceof Number) return value.toString();
        if (value instanceof String) return jsonQuote((String) value);
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) result.append(',');
                String key = keys.get(index);
                result.append(jsonQuote(key)).append(':').append(canonicalize(object.opt(key)));
            }
            return result.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder("[");
            for (int index = 0; index < array.length(); index++) {
                if (index > 0) result.append(',');
                result.append(canonicalize(array.opt(index)));
            }
            return result.append(']').toString();
        }
        return jsonQuote(value.toString());
    }

    private static String jsonQuote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (character < ' ' || character >= 127) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else result.append(character);
            }
        }
        return result.append('"').toString();
    }
}
