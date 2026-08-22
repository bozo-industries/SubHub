package com.betasafe.app.stats;

import java.util.Locale;

/** Read-only aggregate statistics shown on the home screen. */
public final class StatsSnapshot {
    private final long totalBlocks;
    private final long totalProtectedSeconds;
    private final int sessions;
    private final int currentSessionBlocks;
    private final int peakSessionBlocks;

    StatsSnapshot(
            long totalBlocks,
            long totalProtectedSeconds,
            int sessions,
            int currentSessionBlocks,
            int peakSessionBlocks) {
        this.totalBlocks = totalBlocks;
        this.totalProtectedSeconds = totalProtectedSeconds;
        this.sessions = sessions;
        this.currentSessionBlocks = currentSessionBlocks;
        this.peakSessionBlocks = peakSessionBlocks;
    }

    public long getTotalBlocks() { return totalBlocks; }
    public long getTotalProtectedSeconds() { return totalProtectedSeconds; }
    public int getSessions() { return sessions; }
    public int getCurrentSessionBlocks() { return currentSessionBlocks; }
    public int getPeakSessionBlocks() { return peakSessionBlocks; }

    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        return String.format(Locale.ROOT, "%dm", minutes);
    }
}
