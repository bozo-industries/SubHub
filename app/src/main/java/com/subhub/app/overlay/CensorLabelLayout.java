package com.subhub.app.overlay;

import java.util.Collections;
import java.util.List;

/** Deterministic phrase selection and last-resort ellipsis for censor labels. */
public final class CensorLabelLayout {
    private CensorLabelLayout() {}

    public interface TextMeasurer {
        float width(String text);
    }

    public static String selectPhrase(
            List<String> phrases,
            int stableId,
            float maximumWidth,
            TextMeasurer measurer) {
        List<String> candidates = phrases == null || phrases.isEmpty()
                ? Collections.singletonList("BLOCKED") : phrases;
        int start = Math.floorMod(stableId, candidates.size());
        String narrowest = normalized(candidates.get(start));
        float narrowestWidth = measurer.width(narrowest);
        for (int offset = 0; offset < candidates.size(); offset++) {
            String candidate = normalized(candidates.get((start + offset) % candidates.size()));
            float width = measurer.width(candidate);
            if (width <= maximumWidth) return candidate;
            if (width < narrowestWidth) {
                narrowest = candidate;
                narrowestWidth = width;
            }
        }
        return narrowest;
    }

    public static String ellipsize(
            String text,
            float maximumWidth,
            TextMeasurer measurer) {
        String value = normalized(text);
        while (value.length() > 2 && measurer.width(value) > maximumWidth) {
            value = value.substring(0, value.length() - 2).trim() + "…";
        }
        return value;
    }

    private static String normalized(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "BLOCKED" : trimmed;
    }
}
