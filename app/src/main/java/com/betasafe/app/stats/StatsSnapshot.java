package com.betasafe.app.stats;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Immutable aggregate matching the recovered Beta Blocker statistics schema. */
public final class StatsSnapshot {
    private final long totalBlocks;
    private final long totalSessionSeconds;
    private final int sessions;
    private final int currentStreak;
    private final String lastSessionDate;
    private final long totalProtectedSeconds;
    private final int currentSessionBlocks;
    private final int peakSessionBlocks;
    private final long longestSessionSeconds;
    private final int browserSessions;
    private final int browserPages;
    private final long exportedImages;
    private final int profiles;
    private final int customPhrases;
    private final int censorStyleChanges;
    private final boolean borderColorChanged;
    private final Set<String> censorStylesTried;
    private final Set<String> borderEffectsTried;
    private final Set<String> activeDates;
    private final long allCategoryCensors;

    StatsSnapshot(long totalBlocks, long totalSessionSeconds, int sessions, int currentStreak,
            String lastSessionDate, long totalProtectedSeconds, int currentSessionBlocks,
            int peakSessionBlocks, long longestSessionSeconds, int browserSessions,
            int browserPages, long exportedImages, int profiles, int customPhrases,
            int censorStyleChanges, boolean borderColorChanged, Set<String> censorStylesTried,
            Set<String> borderEffectsTried, Set<String> activeDates, long allCategoryCensors) {
        this.totalBlocks = totalBlocks;
        this.totalSessionSeconds = totalSessionSeconds;
        this.sessions = sessions;
        this.currentStreak = currentStreak;
        this.lastSessionDate = lastSessionDate;
        this.totalProtectedSeconds = totalProtectedSeconds;
        this.currentSessionBlocks = currentSessionBlocks;
        this.peakSessionBlocks = peakSessionBlocks;
        this.longestSessionSeconds = longestSessionSeconds;
        this.browserSessions = browserSessions;
        this.browserPages = browserPages;
        this.exportedImages = exportedImages;
        this.profiles = profiles;
        this.customPhrases = customPhrases;
        this.censorStyleChanges = censorStyleChanges;
        this.borderColorChanged = borderColorChanged;
        this.censorStylesTried = immutable(censorStylesTried);
        this.borderEffectsTried = immutable(borderEffectsTried);
        this.activeDates = immutable(activeDates);
        this.allCategoryCensors = allCategoryCensors;
    }

    public long getTotalBlocks() { return totalBlocks; }
    public long getTotalSessionSeconds() { return totalSessionSeconds; }
    public int getSessions() { return sessions; }
    public int getCurrentStreak() { return currentStreak; }
    public String getLastSessionDate() { return lastSessionDate; }
    public long getTotalProtectedSeconds() { return totalProtectedSeconds; }
    public int getCurrentSessionBlocks() { return currentSessionBlocks; }
    public int getPeakSessionBlocks() { return peakSessionBlocks; }
    public long getLongestSessionSeconds() { return longestSessionSeconds; }
    public int getBrowserSessions() { return browserSessions; }
    public int getBrowserPages() { return browserPages; }
    public long getExportedImages() { return exportedImages; }
    public int getProfiles() { return profiles; }
    public int getCustomPhrases() { return customPhrases; }
    public int getCensorStyleChanges() { return censorStyleChanges; }
    public boolean isBorderColorChanged() { return borderColorChanged; }
    public Set<String> getCensorStylesTried() { return censorStylesTried; }
    public Set<String> getBorderEffectsTried() { return borderEffectsTried; }
    public Set<String> getActiveDates() { return activeDates; }
    public long getAllCategoryCensors() { return allCategoryCensors; }

    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        return String.format(Locale.ROOT, "%dm", minutes);
    }

    private static Set<String> immutable(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
