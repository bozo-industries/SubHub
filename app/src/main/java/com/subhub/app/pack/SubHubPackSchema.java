package com.subhub.app.pack;

import android.content.SharedPreferences;

import com.subhub.app.appmode.AppTimerManager;
import com.subhub.app.capture.CustomImageManager;
import com.subhub.app.penance.PenanceInfraction;
import com.subhub.app.penance.PenanceManager;
import com.subhub.app.popup.PopupStormSettings;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.settings.SettingsRepository;
import com.subhub.app.subliminal.SubliminalSettingsRepository;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Explicit allowlist for every portable field. Anything absent here cannot be exported. */
public final class SubHubPackSchema {
    public static final String MODULES = "modules";
    public static final String CENSOR = "censor";
    public static final String LIMITS = "limits";
    public static final String WALLET = "wallet";
    public static final String SUBLIMINAL = "subliminal";
    public static final String POPUP = "popup";
    public static final Set<String> SECTIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(MODULES, CENSOR, LIMITS, WALLET, SUBLIMINAL, POPUP)));

    private static final Map<String, Set<String>> MAIN_KEYS = new LinkedHashMap<>();
    private static final Set<String> WALLET_KEYS = new LinkedHashSet<>();
    static {
        MAIN_KEYS.put(MODULES, set(
                FeatureModuleManager.KEY_CENSOR_ENABLED,
                FeatureModuleManager.KEY_LIMITS_ENABLED,
                FeatureModuleManager.KEY_WALLET_ENABLED,
                FeatureModuleManager.KEY_SUBLIMINAL_ENABLED));
        MAIN_KEYS.put(CENSOR, set(
                SettingsRepository.KEY_ENABLED_CATEGORIES,
                SettingsRepository.KEY_CONFIDENCE,
                SettingsRepository.KEY_CENSOR_TYPE,
                SettingsRepository.KEY_CENSOR_INTENSITY,
                SettingsRepository.KEY_SHOW_BORDER,
                SettingsRepository.KEY_SHOW_TEXT,
                SettingsRepository.KEY_BORDER_COLOR,
                SettingsRepository.KEY_DETECTION_PRESET,
                SettingsRepository.KEY_CENSOR_SIZE_PADDING,
                SettingsRepository.KEY_ANIMATE_BORDER,
                SettingsRepository.KEY_BORDER_EFFECT,
                SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES,
                SettingsRepository.KEY_CUSTOM_PHRASES,
                SettingsRepository.KEY_REVERSE_MODE,
                SettingsRepository.KEY_REVERSE_STRENGTH,
                SettingsRepository.KEY_REVERSE_CUTOUT_SHAPE,
                SettingsRepository.KEY_ERROR_TITLE,
                SettingsRepository.KEY_ERROR_TEXT,
                SettingsRepository.KEY_TEXT_SMUT_ENABLED,
                SettingsRepository.KEY_TEXT_SMUT_SENSITIVITY,
                SettingsRepository.KEY_TEXT_SMUT_CATEGORIES,
                SettingsRepository.KEY_CAPTURE_METHOD));
        MAIN_KEYS.put(LIMITS, set(
                AppTimerManager.KEY_PER_APP_ENABLED,
                AppTimerManager.KEY_PER_APP_MINUTES,
                AppTimerManager.KEY_TOTAL_ENABLED,
                AppTimerManager.KEY_TOTAL_MINUTES));
        MAIN_KEYS.put(SUBLIMINAL, set(
                SubliminalSettingsRepository.KEY_PRESET,
                SubliminalSettingsRepository.KEY_ADVANCED,
                SubliminalSettingsRepository.KEY_OPACITY,
                SubliminalSettingsRepository.KEY_VISIBLE_MS,
                SubliminalSettingsRepository.KEY_MIN_INTERVAL_MS,
                SubliminalSettingsRepository.KEY_MAX_INTERVAL_MS,
                SubliminalSettingsRepository.KEY_TEXT_SIZE,
                SubliminalSettingsRepository.KEY_PACKS,
                SubliminalSettingsRepository.KEY_CUSTOM));
        MAIN_KEYS.put(POPUP, set(
                PopupStormSettings.K_AVOID_PADDING, PopupStormSettings.K_BOUNCING,
                PopupStormSettings.K_BOUNCING_SPEED, PopupStormSettings.K_BURST_DURATION,
                PopupStormSettings.K_BURST_ENABLED, PopupStormSettings.K_BURST_FREQUENCY,
                PopupStormSettings.K_BURST_MULTIPLIER, PopupStormSettings.K_CLICK_DISMISS_ALL,
                PopupStormSettings.K_DENIAL_CAPTION, PopupStormSettings.K_DENIAL_CAPTION_TEXT,
                PopupStormSettings.K_DENIAL_CHANCE, PopupStormSettings.K_DENIAL_INTENSITY,
                PopupStormSettings.K_DENIAL_STYLE, PopupStormSettings.K_DETECTION_MODE,
                PopupStormSettings.K_DISPLAY_DURATION, PopupStormSettings.K_ENABLED,
                PopupStormSettings.K_FADE_IN, PopupStormSettings.K_FADE_OUT,
                PopupStormSettings.K_FIXED_SIZE, PopupStormSettings.K_MAX_SIMULTANEOUS,
                PopupStormSettings.K_MAX_SIZE, PopupStormSettings.K_MIN_SIZE,
                PopupStormSettings.K_POSITION_MODE, PopupStormSettings.K_PRESET,
                PopupStormSettings.K_RANDOM_ROT, PopupStormSettings.K_ROT_MAX,
                PopupStormSettings.K_SIZE_MODE, PopupStormSettings.K_SPAWN_RATE));
        WALLET_KEYS.addAll(set("enabled", "strike_cents", "daily_cap_cents",
                "weekly_cap_cents", "mercy_minutes", "dwell_seconds", "detection_batch",
                "tamper_cooldown_minutes", "paid_pause_enabled", "paid_pause_price_cents",
                "paid_pause_duration_minutes"));
        for (PenanceInfraction infraction : PenanceInfraction.values()) {
            WALLET_KEYS.add("rule_enabled_" + infraction.preferenceKey());
            WALLET_KEYS.add("rule_cents_" + infraction.preferenceKey());
        }
    }

    private SubHubPackSchema() {}

    public static JSONObject captureMainSection(String section, SharedPreferences preferences) {
        JSONObject result = new JSONObject();
        Set<String> allowed = MAIN_KEYS.getOrDefault(section, Set.of());
        Map<String, ?> all = preferences.getAll();
        for (String key : allowed) if (all.containsKey(key)) putJson(result, key, all.get(key));
        if (CENSOR.equals(section)) {
            for (Map.Entry<String, ?> item : all.entrySet()) {
                if (item.getKey().startsWith(SettingsRepository.KEY_EFFECT_PALETTE_PREFIX)) {
                    putJson(result, item.getKey(), item.getValue());
                }
            }
        }
        return result;
    }

    public static JSONObject captureWallet(SharedPreferences preferences) {
        JSONObject result = new JSONObject();
        Map<String, ?> all = preferences.getAll();
        for (String key : WALLET_KEYS) if (all.containsKey(key)) putJson(result, key, all.get(key));
        return result;
    }

    public static JSONObject sanitizeSection(String section, JSONObject source) {
        JSONObject result = new JSONObject();
        Set<String> allowed = WALLET.equals(section) ? WALLET_KEYS
                : MAIN_KEYS.getOrDefault(section, Set.of());
        source.keys().forEachRemaining(key -> {
            if (allowed.contains(key) || CENSOR.equals(section)
                    && key.startsWith(SettingsRepository.KEY_EFFECT_PALETTE_PREFIX)) {
                Object value = source.opt(key);
                if (isPortableValue(value)) putJson(result, key, value);
            }
        });
        return result;
    }

    public static JSONObject sanitizeRecommendations(JSONObject source) {
        JSONObject result = new JSONObject();
        if (source == null) return result;
        if (source.has("hardcoreSuggested")) {
            putJson(result, "hardcoreSuggested", source.optBoolean("hardcoreSuggested"));
        }
        if (source.has("serviceDurationMillis")) {
            long value = source.optLong("serviceDurationMillis", 0L);
            if (value == -1L || value == 3_600_000L || value == 86_400_000L
                    || value == 604_800_000L || value == 2_592_000_000L) {
                putJson(result, "serviceDurationMillis", value);
            }
        }
        return result;
    }

    public static Set<String> keysFor(String section) {
        if (WALLET.equals(section)) return Collections.unmodifiableSet(WALLET_KEYS);
        return Collections.unmodifiableSet(MAIN_KEYS.getOrDefault(section, Set.of()));
    }

    public static String preferenceStore(String section) {
        return WALLET.equals(section) ? PenanceManager.PREFS_NAME
                : SettingsRepository.PREFERENCES_NAME;
    }

    public static boolean isSecretOrRuntimeKey(String key) {
        if (key == null) return true;
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("paypal") || lower.contains("secret") || lower.contains("client_id")
                || lower.contains("vault") || lower.contains("wallet_id")
                || lower.contains("selected_packages") || lower.contains("package_used")
                || lower.contains("allowance_minutes:") || lower.contains("armed")
                || lower.contains("commitment_") || lower.contains("hardcore_mode")
                || lower.contains("events") || lower.contains("history")
                || lower.contains("achievement") || lower.contains("stats")
                || lower.equals(CustomImageManager.PACK_DIR_KEY)
                || lower.equals(PopupStormSettings.K_FOLDERS)
                || lower.equals(PopupStormSettings.K_ACK);
    }

    private static boolean isPortableValue(Object value) {
        return value instanceof Boolean || value instanceof Number || value instanceof String
                || value instanceof Set<?>;
    }

    private static void putJson(JSONObject target, String key, Object value) {
        if (isSecretOrRuntimeKey(key)) return;
        try {
            if (value instanceof Set<?>) target.put(key, new org.json.JSONArray((Set<?>) value));
            else target.put(key, value);
        } catch (Exception ignored) {
            // Unsupported values are deliberately omitted instead of stringified.
        }
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
