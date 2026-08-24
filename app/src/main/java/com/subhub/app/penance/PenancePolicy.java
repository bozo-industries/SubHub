package com.subhub.app.penance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/** Pure calculation rules for bounded strike amounts. */
public final class PenancePolicy {
    public static final int MIN_STRIKE_CENTS = 1;
    public static final int MAX_STRIKE_CENTS = 10_000;
    public static final int MAX_DAILY_CENTS = 50_000;
    public static final int MAX_WEEKLY_CENTS = 200_000;
    public static final int MAX_MERCY_MINUTES = 24 * 60;
    public static final int MIN_DWELL_SECONDS = 3;
    public static final int MAX_DWELL_SECONDS = 60;
    public static final int MIN_DETECTION_BATCH = 1;
    public static final int MAX_DETECTION_BATCH = 100;

    private PenancePolicy() {}

    public static int boundedCharge(List<PenanceEvent> events, long nowMillis,
            int strikes, int centsPerStrike, int dailyCapCents, int weeklyCapCents,
            ZoneId zoneId) {
        if (strikes <= 0) return 0;
        int strikeValue = clamp(centsPerStrike, MIN_STRIKE_CENTS, MAX_STRIKE_CENTS);
        int dailyCap = clamp(dailyCapCents, strikeValue, MAX_DAILY_CENTS);
        int weeklyCap = clamp(weeklyCapCents, dailyCap, MAX_WEEKLY_CENTS);
        long desired = Math.min((long) strikes * strikeValue, Integer.MAX_VALUE);
        int todayTotal = periodTotal(events, nowMillis, zoneId, true);
        int weekTotal = periodTotal(events, nowMillis, zoneId, false);
        int dailyRemaining = Math.max(0, dailyCap - todayTotal);
        int weeklyRemaining = Math.max(0, weeklyCap - weekTotal);
        return (int) Math.min(desired, Math.min(dailyRemaining, weeklyRemaining));
    }

    public static int clampStrikeCents(int value) {
        return clamp(value, MIN_STRIKE_CENTS, MAX_STRIKE_CENTS);
    }

    public static int clampDailyCapCents(int value, int strikeCents) {
        return clamp(value, clampStrikeCents(strikeCents), MAX_DAILY_CENTS);
    }

    public static int clampWeeklyCapCents(int value, int dailyCapCents) {
        return clamp(value, clampDailyCapCents(dailyCapCents, MIN_STRIKE_CENTS),
                MAX_WEEKLY_CENTS);
    }

    public static int clampMercyMinutes(int value) {
        return clamp(value, 0, MAX_MERCY_MINUTES);
    }

    public static int clampDwellSeconds(int value) {
        return clamp(value, MIN_DWELL_SECONDS, MAX_DWELL_SECONDS);
    }

    public static int clampDetectionBatch(int value) {
        return clamp(value, MIN_DETECTION_BATCH, MAX_DETECTION_BATCH);
    }

    static int periodTotal(List<PenanceEvent> events, long nowMillis,
            ZoneId zoneId, boolean day) {
        LocalDate now = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int nowWeek = now.get(weekFields.weekOfWeekBasedYear());
        int nowYear = now.get(weekFields.weekBasedYear());
        long total = 0;
        for (PenanceEvent event : events) {
            if (!event.countsTowardCaps()) continue;
            LocalDate created = Instant.ofEpochMilli(event.getCreatedAtMillis())
                    .atZone(zoneId).toLocalDate();
            boolean samePeriod = day ? created.equals(now)
                    : created.get(weekFields.weekOfWeekBasedYear()) == nowWeek
                    && created.get(weekFields.weekBasedYear()) == nowYear;
            if (samePeriod) total += event.getAmountCents();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
