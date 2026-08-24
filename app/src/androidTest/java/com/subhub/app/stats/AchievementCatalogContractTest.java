package com.subhub.app.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

/** Contract coverage for the expanded illustrated achievement catalog. */
@RunWith(AndroidJUnit4.class)
public final class AchievementCatalogContractTest {
    @Test
    public void catalogKeepsLegacyIdsAndAddsFeatureMilestones() {
        Context context = ApplicationProvider.getApplicationContext();
        AchievementManager manager = new AchievementManager(context);

        assertEquals(56, manager.getTotalCount());
        Set<String> ids = new HashSet<>();
        for (AchievementManager.Achievement achievement : manager.all()) {
            assertTrueNonEmpty(achievement.getId());
            assertTrueNonEmpty(achievement.getCategory());
            assertNotEquals(0, achievement.getBadgeArtRes());
            if (!ids.add(achievement.getId())) {
                throw new AssertionError("Duplicate achievement " + achievement.getId());
            }
        }

        assertEquals(R.drawable.achievement_badge_app_mode,
                find(manager, "app_mode_guardian").getBadgeArtRes());
        assertEquals(R.drawable.achievement_badge_limits,
                find(manager, "limits_setter").getBadgeArtRes());
        assertEquals(R.drawable.achievement_badge_pact,
                find(manager, "pact_sealed").getBadgeArtRes());
        assertEquals(R.drawable.achievement_badge_hardcore,
                find(manager, "hardcore_guardian").getBadgeArtRes());
        assertEquals(R.drawable.achievement_badge_wallet,
                find(manager, "paypal_vault").getBadgeArtRes());
        assertFalse(find(manager, "app_mode_guardian").getCategory().isEmpty());
        assertFalse(find(manager, "paid_pause").getCategory().isEmpty());
    }

    private static AchievementManager.Achievement find(AchievementManager manager, String id) {
        for (AchievementManager.Achievement achievement : manager.all()) {
            if (id.equals(achievement.getId())) return achievement;
        }
        throw new AssertionError("Missing achievement " + id);
    }

    private static void assertTrueNonEmpty(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new AssertionError("Expected non-empty achievement metadata");
        }
    }
}
