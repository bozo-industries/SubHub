package com.betasafe.app.stats;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class StatsSnapshotTest {
    @Test
    public void formatsMinutesAndHoursCompactly() {
        assertEquals("0m", StatsSnapshot.formatDuration(12));
        assertEquals("1h 01m", StatsSnapshot.formatDuration(3661));
    }
}
