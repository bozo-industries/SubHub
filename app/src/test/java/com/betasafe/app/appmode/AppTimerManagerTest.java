package com.betasafe.app.appmode;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AppTimerManagerTest {
    @Test public void minuteValuesStayWithinOneLocalDay() {
        assertEquals(1, AppTimerManager.sanitizeMinutes(-4));
        assertEquals(45, AppTimerManager.sanitizeMinutes(45));
        assertEquals(1440, AppTimerManager.sanitizeMinutes(9000));
    }

    @Test public void minuteConversionUsesExactMilliseconds() {
        assertEquals(60_000L, AppTimerManager.minutesToMillis(1));
        assertEquals(86_400_000L, AppTimerManager.minutesToMillis(1440));
    }
}
