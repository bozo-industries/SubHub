package com.betasafe.app.appmode;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.settings.SettingsRepository;

import java.util.Calendar;

/** Persists one recurring weekly automatic-recognition window. */
public final class WeeklyScheduleManager {
    public static final String KEY_ENABLED = "weekly_schedule_enabled";
    public static final String KEY_DAY_MASK = "weekly_schedule_days";
    public static final String KEY_START_MINUTE = "weekly_schedule_start";
    public static final String KEY_END_MINUTE = "weekly_schedule_end";
    public static final int DEFAULT_START_MINUTE = 20 * 60;
    public static final int DEFAULT_END_MINUTE = 23 * 60;

    private final SharedPreferences preferences;

    public WeeklyScheduleManager(Context context) {
        preferences = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public Settings load() {
        return new Settings(
                preferences.getBoolean(KEY_ENABLED, false),
                preferences.getInt(KEY_DAY_MASK, 0),
                WeeklySchedulePolicy.sanitizeMinute(preferences.getInt(
                        KEY_START_MINUTE, DEFAULT_START_MINUTE)),
                WeeklySchedulePolicy.sanitizeMinute(preferences.getInt(
                        KEY_END_MINUTE, DEFAULT_END_MINUTE)));
    }

    public void save(boolean enabled, int dayMask, int startMinute, int endMinute) {
        preferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_DAY_MASK, dayMask & 0x7f)
                .putInt(KEY_START_MINUTE, WeeklySchedulePolicy.sanitizeMinute(startMinute))
                .putInt(KEY_END_MINUTE, WeeklySchedulePolicy.sanitizeMinute(endMinute))
                .commit();
    }

    public boolean isActive(long nowMillis) {
        Settings settings = load();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        int calendarDay = calendar.get(Calendar.DAY_OF_WEEK);
        int mondayBasedDay = (calendarDay + 5) % 7;
        int minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        return WeeklySchedulePolicy.isActive(settings.enabled, settings.dayMask,
                settings.startMinute, settings.endMinute, mondayBasedDay, minute);
    }

    public static final class Settings {
        public final boolean enabled;
        public final int dayMask;
        public final int startMinute;
        public final int endMinute;

        private Settings(boolean enabled, int dayMask, int startMinute, int endMinute) {
            this.enabled = enabled;
            this.dayMask = dayMask;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }
    }
}
