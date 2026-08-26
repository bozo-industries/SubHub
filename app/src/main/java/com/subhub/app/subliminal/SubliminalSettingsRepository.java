package com.subhub.app.subliminal;

import android.content.Context;
import android.content.SharedPreferences;

import com.subhub.app.settings.SettingsRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Stores and resolves the standalone on-device subliminal phrase library. */
public final class SubliminalSettingsRepository {
    public static final String KEY_PRESET = "subliminal_preset";
    public static final String KEY_ADVANCED = "subliminal_advanced";
    public static final String KEY_OPACITY = "subliminal_opacity_percent";
    public static final String KEY_VISIBLE_MS = "subliminal_visible_ms";
    public static final String KEY_MIN_INTERVAL_MS = "subliminal_min_interval_ms";
    public static final String KEY_MAX_INTERVAL_MS = "subliminal_max_interval_ms";
    public static final String KEY_TEXT_SIZE = "subliminal_text_size_sp";
    public static final String KEY_PACKS = "subliminal_enabled_packs";
    public static final String KEY_CUSTOM = "subliminal_custom_phrases";

    public static final String PACK_OBEDIENCE = "obedience";
    public static final String PACK_FOCUS = "focus";
    public static final String PACK_BETA = "beta";
    public static final String PACK_FINDOM = "findom";
    public static final String PACK_CUSTOM = "custom";

    private static final Set<String> KNOWN_PACKS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(PACK_OBEDIENCE, PACK_FOCUS, PACK_BETA,
                    PACK_FINDOM, PACK_CUSTOM)));
    private static final Set<String> DEFAULT_PACKS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(PACK_OBEDIENCE, PACK_FOCUS)));

    private final SharedPreferences preferences;

    public SubliminalSettingsRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public SubliminalSettings load() {
        SubliminalSettings.Preset preset = parsePreset(preferences.getString(KEY_PRESET, "normal"));
        Values defaults = valuesFor(preset);
        boolean advanced = preferences.getBoolean(KEY_ADVANCED, false);
        int opacity = advanced ? clamp(preferences.getInt(KEY_OPACITY, defaults.opacity), 1, 15)
                : defaults.opacity;
        long visible = advanced ? clamp(preferences.getLong(KEY_VISIBLE_MS, defaults.visible),
                800L, 4_000L) : defaults.visible;
        long minimum = advanced ? clamp(preferences.getLong(KEY_MIN_INTERVAL_MS, defaults.minimum),
                5_000L, 300_000L) : defaults.minimum;
        long maximum = advanced ? clamp(preferences.getLong(KEY_MAX_INTERVAL_MS, defaults.maximum),
                minimum, 300_000L) : defaults.maximum;
        int textSize = advanced ? clamp(preferences.getInt(KEY_TEXT_SIZE, defaults.textSize),
                14, 28) : defaults.textSize;
        Set<String> packs = cleanPacks(preferences.getStringSet(KEY_PACKS, DEFAULT_PACKS));
        return new SubliminalSettings(preset, advanced, opacity, visible, minimum, maximum,
                textSize, packs, normalizeCustom(preferences.getString(KEY_CUSTOM, "")));
    }

    public void savePreset(SubliminalSettings.Preset preset) {
        preferences.edit().putString(KEY_PRESET,
                (preset == null ? SubliminalSettings.Preset.NORMAL : preset)
                        .name().toLowerCase(Locale.ROOT)).apply();
    }

    public void saveAdvanced(boolean enabled, int opacity, long visible, long minimum,
            long maximum, int textSize) {
        long cleanMinimum = clamp(minimum, 5_000L, 300_000L);
        preferences.edit().putBoolean(KEY_ADVANCED, enabled)
                .putInt(KEY_OPACITY, clamp(opacity, 1, 15))
                .putLong(KEY_VISIBLE_MS, clamp(visible, 800L, 4_000L))
                .putLong(KEY_MIN_INTERVAL_MS, cleanMinimum)
                .putLong(KEY_MAX_INTERVAL_MS, clamp(maximum, cleanMinimum, 300_000L))
                .putInt(KEY_TEXT_SIZE, clamp(textSize, 14, 28)).apply();
    }

    public void savePacks(Set<String> packs) {
        preferences.edit().putStringSet(KEY_PACKS, new LinkedHashSet<>(cleanPacks(packs))).apply();
    }

    public void saveCustomPhrases(String phrases) {
        preferences.edit().putString(KEY_CUSTOM, normalizeCustom(phrases)).apply();
    }

    public List<String> phrases(SubliminalSettings settings) {
        if (settings == null) settings = load();
        List<String> result = new ArrayList<>();
        Set<String> packs = settings.getEnabledPacks();
        if (packs.contains(PACK_OBEDIENCE)) result.addAll(Arrays.asList(
                "Eyes forward.", "Wait for permission.", "Follow the rule.",
                "Service comes first.", "Good behavior is noticed.", "Hold your place.",
                "Obedience feels natural.", "Listen. Breathe. Obey."));
        if (packs.contains(PACK_FOCUS)) result.addAll(Arrays.asList(
                "Back to your task.", "Breathe. Focus.", "Your attention has a purpose.",
                "Stay present.", "Slow down and choose well.", "Keep your hands steady.",
                "One task. Full attention.", "Discipline is quiet."));
        if (packs.contains(PACK_BETA)) result.addAll(Arrays.asList(
                "Know your place.", "Behave, beta.", "Not yours to touch.",
                "Permission comes first.", "Eyes down.", "You can wait.",
                "Watching is enough.", "Good betas follow instructions."));
        if (packs.contains(PACK_FINDOM)) result.addAll(Arrays.asList(
                "Your tribute is remembered.", "Permission has a price.",
                "Your wallet knows who leads.", "Every slip belongs in the ledger.",
                "Spend with purpose.", "Tribute follows obedience.",
                "Your balance tells the truth.", "Pay attention. Then pay tribute."));
        if (packs.contains(PACK_CUSTOM)) {
            for (String line : settings.getCustomPhrases().split("\\R")) {
                String clean = line.trim();
                if (!clean.isEmpty()) result.add(clean);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static Values valuesFor(SubliminalSettings.Preset preset) {
        switch (preset == null ? SubliminalSettings.Preset.NORMAL : preset) {
            case GENTLE: return new Values(3, 2_400L, 45_000L, 90_000L, 18);
            case STRICT: return new Values(7, 1_600L, 15_000L, 40_000L, 20);
            case ULTRA: return new Values(10, 1_200L, 8_000L, 25_000L, 21);
            case NORMAL:
            default: return new Values(5, 2_000L, 25_000L, 60_000L, 19);
        }
    }

    private static SubliminalSettings.Preset parsePreset(String value) {
        try { return SubliminalSettings.Preset.valueOf(
                (value == null ? "NORMAL" : value).trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return SubliminalSettings.Preset.NORMAL; }
    }

    private static Set<String> cleanPacks(Set<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) for (String value : values) if (KNOWN_PACKS.contains(value)) {
            result.add(value);
        }
        return Collections.unmodifiableSet(result);
    }

    private static String normalizeCustom(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String clean = line.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
            if (clean.isEmpty()) continue;
            lines.add(clean.length() <= 120 ? clean : clean.substring(0, 120));
            if (lines.size() >= 200) break;
        }
        return String.join("\n", lines);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Values {
        public final int opacity;
        public final long visible;
        public final long minimum;
        public final long maximum;
        public final int textSize;
        Values(int opacity, long visible, long minimum, long maximum, int textSize) {
            this.opacity = opacity;
            this.visible = visible;
            this.minimum = minimum;
            this.maximum = maximum;
            this.textSize = textSize;
        }
    }
}
