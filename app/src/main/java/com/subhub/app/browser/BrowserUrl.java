package com.subhub.app.browser;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;

/** Normalizes user-entered browser text without allowing executable or local schemes. */
public final class BrowserUrl {
    private BrowserUrl() {}

    public static String fromInput(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return "https://www.google.com";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return isWebUrl(value) ? value : search(value);
        }
        if (value.matches(".*\\s+.*") || !value.contains(".")) return search(value);
        String candidate = "https://" + value;
        return isWebUrl(candidate) ? candidate : search(value);
    }

    public static boolean isWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String search(String value) {
        try {
            return "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
