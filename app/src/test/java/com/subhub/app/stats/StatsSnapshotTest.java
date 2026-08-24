package com.subhub.app.stats;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class StatsSnapshotTest {
    @Test
    public void formatsMinutesAndHoursCompactly() {
        assertEquals("0m", StatsSnapshot.formatDuration(12));
        assertEquals("1h 01m", StatsSnapshot.formatDuration(3661));
        assertEquals("00:12", StatsSnapshot.formatClock(12));
        assertEquals("01:01:01", StatsSnapshot.formatClock(3661));
    }
}
