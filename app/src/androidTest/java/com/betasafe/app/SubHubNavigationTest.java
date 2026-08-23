package com.betasafe.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.betasafe.app.appmode.AppModeActivity;
import com.betasafe.app.penance.PenanceActivity;
import com.betasafe.app.stats.StatsRepository;
import com.betasafe.app.settings.FeatureModuleManager;
import com.betasafe.app.settings.GlobalSettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/** Proves enabled product tabs plus the always-visible Settings tab navigate correctly. */
@RunWith(AndroidJUnit4.class)
public final class SubHubNavigationTest {
    @Test public void disabledAreasDisappearWhileSettingsRemainsAvailable() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        FeatureModuleManager modules = new FeatureModuleManager(context);
        modules.save(false, true, false);
        try (ActivityScenario<GlobalSettingsActivity> scenario =
                     ActivityScenario.launch(GlobalSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.nav_censor).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_limits).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.nav_money).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_settings).getVisibility());
            });
        } finally {
            modules.save(true, true, true);
        }
    }

    @Test public void primaryPillNavigatesAndUpdatesSelection() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        context
                .getSharedPreferences("betablocker_achievements", 0)
                .edit().clear().commit();
        context.getSharedPreferences(StatsRepository.PREFS_NAME, 0)
                .edit().clear().commit();
        new FeatureModuleManager(context).save(true, true, true);
        ActivityScenario.launch(MainActivity.class);
        assertDestination(MainActivity.class, R.id.nav_censor, R.id.nav_limits);

        onView(withId(R.id.nav_limits)).perform(click());
        onView(withId(R.id.app_mode_page)).check(matches(isDisplayed()));
        assertDestination(AppModeActivity.class, R.id.nav_limits, R.id.nav_censor);

        onView(withId(R.id.nav_money)).perform(click());
        onView(withId(R.id.money_page)).check(matches(isDisplayed()));
        assertDestination(PenanceActivity.class, R.id.nav_money, R.id.nav_limits);

        onView(withId(R.id.nav_settings)).perform(click());
        onView(withId(R.id.settings_page)).check(matches(isDisplayed()));
        assertDestination(GlobalSettingsActivity.class, R.id.nav_settings, R.id.nav_money);

        onView(withId(R.id.nav_censor)).perform(click());
        onView(withId(R.id.censor_page)).check(matches(isDisplayed()));
        assertDestination(MainActivity.class, R.id.nav_censor, R.id.nav_settings);
    }

    private static void assertDestination(Class<? extends Activity> expected, int selectedId,
            int unselectedId) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Activity activity = resumedActivity();
        assertEquals(expected, activity.getClass());
        View selected = activity.findViewById(selectedId);
        View unselected = activity.findViewById(unselectedId);
        assertTrue(selected.getBackground() instanceof GradientDrawable);
        assertTrue(unselected.getBackground() instanceof ColorDrawable);
        int[] tabs = {R.id.nav_censor, R.id.nav_limits, R.id.nav_money, R.id.nav_settings};
        int[] icons = {R.id.nav_censor_icon, R.id.nav_limits_icon, R.id.nav_money_icon,
                R.id.nav_settings_icon};
        int[] labels = {R.id.nav_censor_label, R.id.nav_limits_label, R.id.nav_money_label,
                R.id.nav_settings_label};
        for (int index = 0; index < tabs.length; index++) {
            LinearLayout tab = activity.findViewById(tabs[index]);
            ImageView icon = activity.findViewById(icons[index]);
            TextView label = activity.findViewById(labels[index]);
            assertEquals(View.VISIBLE, tab.getVisibility());
            assertTrue(tab.getAlpha() >= 0.8f);
            assertEquals(Gravity.CENTER, tab.getGravity() & Gravity.CENTER);
            assertEquals(LinearLayout.VERTICAL, tab.getOrientation());
            assertTrue(Color.alpha(label.getCurrentTextColor()) > 0);
            assertTrue(!label.getIncludeFontPadding());
            assertTrue(icon.getDrawable() != null);
            assertTrue(icon.getBottom() <= label.getTop());
        }
    }

    private static Activity resumedActivity() {
        AtomicReference<Activity> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            result.set(resumed.iterator().next());
        });
        return result.get();
    }
}
