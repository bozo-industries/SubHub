package com.betasafe.app.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class StatsAndAchievementsTest {
    @Test
    public void recoveredCountersHistoryMilestonesAndAchievementsWork() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(StatsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("betablocker_achievements", Context.MODE_PRIVATE)
                .edit().clear().commit();

        StatsRepository repository = new StatsRepository(context);
        repository.startSession();
        SystemClock.sleep(1100L);
        Set<String> allCategories = new LinkedHashSet<>(Arrays.asList(
                "genitals_female", "genitals_male", "breasts", "buttocks", "anus",
                "face", "belly", "male_chest", "feet", "armpits", "genitals_covered",
                "breasts_covered", "buttocks_covered", "anus_covered", "belly_covered",
                "feet_covered", "armpits_covered"));
        repository.onTrackIds(Arrays.asList(7, 7, 8), allCategories);
        StatsSnapshot live = repository.load();
        assertEquals(2, live.getTotalBlocks());
        assertEquals(2, live.getCurrentSessionBlocks());
        assertTrue(live.getCurrentSessionSeconds() >= 1);
        assertEquals(2, live.getAllCategoryCensors());
        assertEquals(1, live.getCurrentStreak());
        assertFalse(live.getActiveDates().isEmpty());
        repository.endSession();
        assertEquals(1, repository.getSessionHistory().size());

        for (int index = 0; index < 35; index++) {
            repository.startSession();
            repository.endSession();
        }
        assertEquals(30, repository.getSessionHistory().size());

        AchievementManager achievements = new AchievementManager(context);
        assertFalse(achievements.isUnlocked("first_block"));
        assertFalse(achievements.checkAchievements(repository.load()).isEmpty());
        assertTrue(new AchievementManager(context).isUnlocked("first_block"));

        MilestoneManager.Result milestone = MilestoneManager.takeUnseen(context, 10);
        assertNotNull(milestone);
        assertEquals(10, milestone.getValue());
        assertTrue(milestone.isMajor());
        assertEquals(15, MilestoneManager.next(10));
    }
}
