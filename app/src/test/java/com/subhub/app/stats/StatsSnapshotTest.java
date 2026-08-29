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

    @Test
    public void unlockedAchievementProgressNeverRegresses() {
        AchievementManager.Progress progress =
                new AchievementManager.Progress(0, 3, true);

        assertEquals(3, progress.getCurrent());
        assertEquals(100, progress.percent());
    }

    @Test
    public void serviceHistoryAggregatesEveryDiscreteFeatureEvent() {
        StatsRepository.SessionEntry entry = new StatsRepository.SessionEntry(
                1L, 60L, 4, 30_000L, 2L, 3L, 750L, 5L, 6L, 1L);

        assertEquals(20L, entry.getActivityEvents());
        assertEquals(30_000L, entry.getLimitedAppMillis());
        assertEquals(750L, entry.getTributeCents());
        assertEquals(1L, entry.getTamperEvents());
    }
}
