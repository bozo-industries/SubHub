package com.betasafe.app.appmode;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.settings.SettingsRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/** Daily, opt-in usage budgets for apps explicitly selected in App Mode. */
public final class AppTimerManager {
    public static final String KEY_PER_APP_ENABLED = "app_timer_per_app_enabled";
    public static final String KEY_PER_APP_MINUTES = "app_timer_per_app_minutes";
    public static final String KEY_TOTAL_ENABLED = "app_timer_total_enabled";
    public static final String KEY_TOTAL_MINUTES = "app_timer_total_minutes";

    private static final String USAGE_PREFERENCES = "betasafe_app_timer_usage";
    private static final String KEY_DAY = "day";
    private static final String KEY_TOTAL_USED = "total_used_ms";
    private static final String PACKAGE_PREFIX = "package_used_ms:";
    private static final int DEFAULT_PER_APP_MINUTES = 30;
    private static final int DEFAULT_TOTAL_MINUTES = 120;
    private static final int MAX_DAILY_MINUTES = 24 * 60;

    public enum LimitStatus { NONE, PER_APP, COMBINED }

    public static final class Settings {
        public final boolean perAppEnabled;
        public final int perAppMinutes;
        public final boolean totalEnabled;
        public final int totalMinutes;

        private Settings(boolean perAppEnabled, int perAppMinutes,
                boolean totalEnabled, int totalMinutes) {
            this.perAppEnabled = perAppEnabled;
            this.perAppMinutes = perAppMinutes;
            this.totalEnabled = totalEnabled;
            this.totalMinutes = totalMinutes;
        }

        public boolean anyEnabled() {
            return perAppEnabled || totalEnabled;
        }
    }

    public static final class UsageSnapshot {
        public final long appUsedMillis;
        public final long totalUsedMillis;

        private UsageSnapshot(long appUsedMillis, long totalUsedMillis) {
            this.appUsedMillis = Math.max(0L, appUsedMillis);
            this.totalUsedMillis = Math.max(0L, totalUsedMillis);
        }
    }

    private final SharedPreferences settingsPreferences;
    private final SharedPreferences usagePreferences;

    public AppTimerManager(Context context) {
        Context application = context.getApplicationContext();
        settingsPreferences = application.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        usagePreferences = application.getSharedPreferences(
                USAGE_PREFERENCES, Context.MODE_PRIVATE);
    }

    public Settings loadSettings() {
        return new Settings(
                settingsPreferences.getBoolean(KEY_PER_APP_ENABLED, false),
                sanitizeMinutes(settingsPreferences.getInt(
                        KEY_PER_APP_MINUTES, DEFAULT_PER_APP_MINUTES)),
                settingsPreferences.getBoolean(KEY_TOTAL_ENABLED, false),
                sanitizeMinutes(settingsPreferences.getInt(
                        KEY_TOTAL_MINUTES, DEFAULT_TOTAL_MINUTES)));
    }

    public void saveSettings(boolean perAppEnabled, int perAppMinutes,
            boolean totalEnabled, int totalMinutes) {
        settingsPreferences.edit()
                .putBoolean(KEY_PER_APP_ENABLED, perAppEnabled)
                .putInt(KEY_PER_APP_MINUTES, sanitizeMinutes(perAppMinutes))
                .putBoolean(KEY_TOTAL_ENABLED, totalEnabled)
                .putInt(KEY_TOTAL_MINUTES, sanitizeMinutes(totalMinutes))
                .commit();
    }

    public synchronized void recordUsage(String packageName, long elapsedMillis,
            Set<String> selectedPackages, long nowMillis) {
        Settings settings = loadSettings();
        if (!settings.anyEnabled() || elapsedMillis <= 0L
                || !isSelected(packageName, selectedPackages)) return;
        ensureCurrentDay(nowMillis);
        String packageKey = packageKey(packageName);
        long appUsed = safeAdd(usagePreferences.getLong(packageKey, 0L), elapsedMillis);
        long totalUsed = safeAdd(usagePreferences.getLong(KEY_TOTAL_USED, 0L), elapsedMillis);
        usagePreferences.edit()
                .putLong(packageKey, appUsed)
                .putLong(KEY_TOTAL_USED, totalUsed)
                .apply();
    }

    public synchronized UsageSnapshot snapshot(String packageName, long nowMillis) {
        ensureCurrentDay(nowMillis);
        return new UsageSnapshot(
                usagePreferences.getLong(packageKey(packageName), 0L),
                usagePreferences.getLong(KEY_TOTAL_USED, 0L));
    }

    public synchronized LimitStatus limitStatus(String packageName,
            Set<String> selectedPackages, long nowMillis) {
        if (!isSelected(packageName, selectedPackages)) return LimitStatus.NONE;
        Settings settings = loadSettings();
        if (!settings.anyEnabled()) return LimitStatus.NONE;
        UsageSnapshot usage = snapshot(packageName, nowMillis);
        if (settings.perAppEnabled
                && usage.appUsedMillis >= minutesToMillis(settings.perAppMinutes)) {
            return LimitStatus.PER_APP;
        }
        if (settings.totalEnabled
                && usage.totalUsedMillis >= minutesToMillis(settings.totalMinutes)) {
            return LimitStatus.COMBINED;
        }
        return LimitStatus.NONE;
    }

    public static int sanitizeMinutes(int minutes) {
        return Math.max(1, Math.min(MAX_DAILY_MINUTES, minutes));
    }

    public static long minutesToMillis(int minutes) {
        return sanitizeMinutes(minutes) * 60_000L;
    }

    /** Keeps device tests isolated without exposing a reset control in the product UI. */
    synchronized void clearUsageForTesting() {
        usagePreferences.edit().clear().commit();
    }

    private void ensureCurrentDay(long nowMillis) {
        String today = dayKey(nowMillis);
        if (today.equals(usagePreferences.getString(KEY_DAY, ""))) return;
        usagePreferences.edit().clear().putString(KEY_DAY, today).commit();
    }

    private static boolean isSelected(String packageName, Set<String> selectedPackages) {
        return packageName != null && !packageName.isEmpty()
                && selectedPackages != null && selectedPackages.contains(packageName);
    }

    private static String packageKey(String packageName) {
        return PACKAGE_PREFIX + (packageName == null ? "" : packageName);
    }

    private static String dayKey(long nowMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(nowMillis));
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return Math.max(0L, left + right);
    }
}
