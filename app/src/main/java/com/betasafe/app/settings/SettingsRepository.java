package com.betasafe.app.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.detection.DetectionPreset;
import com.betasafe.app.detection.NudeNetClassCatalog;
import com.betasafe.app.overlay.CensorPhrases;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Typed access to preference keys retained for compatibility with the original app. */
public final class SettingsRepository {
    public static final String PREFERENCES_NAME = "betablocker_settings";
    public static final String KEY_ENABLED_CATEGORIES = "enabled_categories";
    public static final String KEY_CONFIDENCE = "confidence_threshold_percent";
    public static final String KEY_CENSOR_TYPE = "censor_type";
    public static final String KEY_CENSOR_INTENSITY = "censor_intensity";
    public static final String KEY_SHOW_BORDER = "show_border";
    public static final String KEY_SHOW_TEXT = "show_text";
    public static final String KEY_BORDER_COLOR = "border_color";
    public static final String KEY_DETECTION_PRESET = "detection_preset";
    public static final String KEY_CENSOR_SIZE_PADDING = "censor_size_padding";
    public static final String KEY_ANIMATE_BORDER = "animate_border";
    public static final String KEY_BORDER_EFFECT = "border_effect";
    public static final String KEY_ENABLED_PHRASE_CATEGORIES = "enabled_phrase_categories";
    public static final String KEY_CUSTOM_PHRASES = "custom_phrases";
    public static final String KEY_REVERSE_MODE = "reverse_mode";
    public static final String KEY_REVERSE_STRENGTH = "reverse_strength";
    public static final String KEY_REVERSE_CUTOUT_SHAPE = "reverse_cutout_shape";
    public static final String KEY_ERROR_TITLE = "error_popup_title";
    public static final String KEY_ERROR_TEXT = "error_popup_text";

    private final SharedPreferences preferences;

    public SettingsRepository(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences preferences() { return preferences; }

    public DetectorConfig loadDetectorConfig() {
        Set<String> stored = preferences.getStringSet(KEY_ENABLED_CATEGORIES, null);
        Set<String> categories = stored == null
                ? new LinkedHashSet<>(NudeNetClassCatalog.DEFAULT_ENABLED)
                : new LinkedHashSet<>(stored);
        DetectionPreset preset = loadDetectionPreset();
        float confidence = preferences.contains(KEY_CONFIDENCE)
                ? preferences.getInt(KEY_CONFIDENCE, Math.round(preset.getConfidence() * 100)) / 100f
                : preset.getConfidence();
        DetectorConfig.Builder builder = preset.applyTo(DetectorConfig.builder());
        return builder
                .enabledCategories(categories)
                .confidenceThreshold(confidence)
                .build();
    }

    public DetectionPreset loadDetectionPreset() {
        return DetectionPreset.fromPreference(
                preferences.getString(KEY_DETECTION_PRESET, DetectionPreset.MEDIUM.preferenceValue()));
    }

    public void saveDetectionPreset(DetectionPreset preset) {
        preferences.edit()
                .putString(KEY_DETECTION_PRESET, preset.preferenceValue())
                .putInt(KEY_CONFIDENCE, Math.round(preset.getConfidence() * 100))
                .apply();
    }

    public CensorAppearance loadAppearance() {
        int borderColor;
        try {
            borderColor = Color.parseColor(preferences.getString(KEY_BORDER_COLOR, "#FF0080"));
        } catch (IllegalArgumentException error) {
            borderColor = Color.rgb(255, 0, 128);
        }
        return new CensorAppearance(
                CensorAppearance.Type.fromPreference(
                        preferences.getString(KEY_CENSOR_TYPE, "box")),
                preferences.getInt(KEY_CENSOR_INTENSITY, 50),
                preferences.getFloat(KEY_CENSOR_SIZE_PADDING, 0.20f),
                preferences.getBoolean(KEY_SHOW_BORDER, true),
                preferences.getBoolean(KEY_ANIMATE_BORDER, false),
                CensorAppearance.BorderEffect.fromPreference(
                        preferences.getString(KEY_BORDER_EFFECT, "classic")),
                preferences.getBoolean(KEY_SHOW_TEXT, true),
                borderColor,
                CensorPhrases.build(
                        preferences.getStringSet(
                                KEY_ENABLED_PHRASE_CATEGORIES, CensorPhrases.DEFAULT_ENABLED),
                        preferences.getStringSet(KEY_CUSTOM_PHRASES, Collections.emptySet())),
                preferences.getBoolean(KEY_REVERSE_MODE, false),
                Math.round(preferences.getFloat(KEY_REVERSE_STRENGTH, 1f) * 100),
                preferences.getString(KEY_REVERSE_CUTOUT_SHAPE, "rectangle"),
                preferences.getString(KEY_ERROR_TITLE, "BetaSafe"),
                preferences.getString(KEY_ERROR_TEXT, "Access blocked."));
    }

    public void saveAppearance(
            CensorAppearance.Type type,
            int intensity,
            boolean showBorder,
            boolean showText) {
        preferences.edit()
                .putString(KEY_CENSOR_TYPE, type.getPreferenceValue())
                .putInt(KEY_CENSOR_INTENSITY, intensity)
                .putBoolean(KEY_SHOW_BORDER, showBorder)
                .putBoolean(KEY_SHOW_TEXT, showText)
                .apply();
    }

    public void saveDetection(int confidencePercent, Set<String> categories) {
        preferences.edit()
                .putInt(KEY_CONFIDENCE, confidencePercent)
                .putStringSet(KEY_ENABLED_CATEGORIES, new LinkedHashSet<>(categories))
                .apply();
    }
}
