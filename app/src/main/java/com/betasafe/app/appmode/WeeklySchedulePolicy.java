package com.betasafe.app.appmode;

/** Pure weekly-window policy. Day bit zero is Monday; equal times mean a full selected day. */
public final class WeeklySchedulePolicy {
    public static final int MINUTES_PER_DAY = 24 * 60;

    private WeeklySchedulePolicy() {}

    public static boolean isActive(
            boolean enabled, int dayMask, int startMinute, int endMinute,
            int mondayBasedDay, int minuteOfDay) {
        if (!enabled || dayMask == 0 || mondayBasedDay < 0 || mondayBasedDay > 6) return false;
        int start = sanitizeMinute(startMinute);
        int end = sanitizeMinute(endMinute);
        int minute = sanitizeMinute(minuteOfDay);
        if (start == end) return selected(dayMask, mondayBasedDay);
        if (start < end) {
            return selected(dayMask, mondayBasedDay) && minute >= start && minute < end;
        }
        if (minute >= start) return selected(dayMask, mondayBasedDay);
        int previousDay = (mondayBasedDay + 6) % 7;
        return minute < end && selected(dayMask, previousDay);
    }

    public static int sanitizeMinute(int minute) {
        return Math.max(0, Math.min(MINUTES_PER_DAY - 1, minute));
    }

    private static boolean selected(int mask, int day) {
        return (mask & (1 << day)) != 0;
    }
}
