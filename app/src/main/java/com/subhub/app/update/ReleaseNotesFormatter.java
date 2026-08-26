package com.subhub.app.update;

import java.util.Locale;

/** Keeps user-facing changes separate from release download instructions. */
final class ReleaseNotesFormatter {
    private static final int DISPLAY_LIMIT = 4_000;

    private ReleaseNotesFormatter() { }

    static String changelogOnly(String releaseBody) {
        if (releaseBody == null || releaseBody.trim().isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        boolean reading = false;
        for (String line : releaseBody.replace("\r", "").split("\n", -1)) {
            String heading = heading(line);
            if (!reading) {
                if (heading.equals("what's new") || heading.equals("what’s new")
                        || heading.equals("whats new")
                        || heading.startsWith("what's new in subhub ")
                        || heading.startsWith("what’s new in subhub ")
                        || heading.startsWith("whats new in subhub ")
                        || heading.equals("changes") || heading.equals("release notes")) {
                    reading = true;
                }
                continue;
            }
            if (heading.equals("choose your apk") || heading.equals("download options")
                    || heading.equals("downloads") || heading.equals("installation")) {
                break;
            }
            if (line.toLowerCase(Locale.ROOT).contains("full changelog")) continue;
            result.append(line).append('\n');
        }
        return result.toString().trim();
    }

    static String forDisplay(String notes, String fallback) {
        String source = notes == null ? "" : notes.trim();
        if (source.isEmpty()) return fallback;
        String clean = source.replace("\r", "")
                .replaceFirst("(?i)^#{1,6}\\s*what(?:'|’)?s new(?: in subhub [^\\n]+)?\\n+", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replace("**", "").replace("`", "").trim();
        if (clean.isEmpty()) return fallback;
        return clean.length() > DISPLAY_LIMIT ? clean.substring(0, DISPLAY_LIMIT) + "…" : clean;
    }

    private static String heading(String line) {
        String value = line.trim();
        if (!value.startsWith("#")) return "";
        return value.replaceFirst("^#{1,6}\\s*", "").trim().toLowerCase(Locale.ROOT);
    }
}
