package com.betasafe.app.appmode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WeeklySchedulePolicyTest {
    private static final int MONDAY = 0;
    private static final int TUESDAY = 1;

    @Test public void sameDayWindowUsesSelectedDayAndEndIsExclusive() {
        int monday = 1 << MONDAY;
        assertTrue(WeeklySchedulePolicy.isActive(true, monday,
                9 * 60, 17 * 60, MONDAY, 9 * 60));
        assertFalse(WeeklySchedulePolicy.isActive(true, monday,
                9 * 60, 17 * 60, MONDAY, 17 * 60));
        assertFalse(WeeklySchedulePolicy.isActive(true, monday,
                9 * 60, 17 * 60, TUESDAY, 10 * 60));
    }

    @Test public void overnightWindowContinuesIntoFollowingMorning() {
        int monday = 1 << MONDAY;
        assertTrue(WeeklySchedulePolicy.isActive(true, monday,
                22 * 60, 2 * 60, MONDAY, 23 * 60));
        assertTrue(WeeklySchedulePolicy.isActive(true, monday,
                22 * 60, 2 * 60, TUESDAY, 60));
        assertFalse(WeeklySchedulePolicy.isActive(true, monday,
                22 * 60, 2 * 60, TUESDAY, 2 * 60));
    }

    @Test public void equalTimesMeanFullSelectedDay() {
        int monday = 1 << MONDAY;
        assertTrue(WeeklySchedulePolicy.isActive(true, monday,
                12 * 60, 12 * 60, MONDAY, 0));
        assertTrue(WeeklySchedulePolicy.isActive(true, monday,
                12 * 60, 12 * 60, MONDAY, 23 * 60 + 59));
        assertFalse(WeeklySchedulePolicy.isActive(true, monday,
                12 * 60, 12 * 60, TUESDAY, 0));
    }
}
