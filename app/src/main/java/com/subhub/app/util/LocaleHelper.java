package com.subhub.app.util;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.subhub.app.settings.SettingsRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Persisted per-app language selection backed by AppCompat's locale recreation flow. */
public final class LocaleHelper {
    public static final String KEY_LANGUAGE = "app_language";
    public static final List<String> SUPPORTED = Collections.unmodifiableList(Arrays.asList(
            "system", "en", "fr", "es", "pt", "de", "ja", "zh-CN", "zh-TW", "ko", "ru"));

    private LocaleHelper() {}

    public static String getLanguage(Context context) {
        String value = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "system");
        return SUPPORTED.contains(value) ? value : "system";
    }

    public static void applySaved(Context context) {
        String language = getLanguage(context);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(
                "system".equals(language) ? "" : language));
    }

    public static void setLanguage(Context context, String language) {
        String safe = SUPPORTED.contains(language) ? language : "system";
        context.getSharedPreferences(SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, safe).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(
                "system".equals(safe) ? "" : safe));
    }
}
