package com.subhub.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.os.SystemClock;
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

import com.subhub.app.appmode.AppModeActivity;
import com.subhub.app.penance.PenanceActivity;
import com.subhub.app.stats.StatsRepository;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.settings.GlobalSettingsActivity;
import com.subhub.app.settings.SettingsActivity;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.security.HardcoreModeManager;
import com.subhub.app.studio.StudioActivity;
import com.subhub.app.util.SubHubNavigation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/** Proves enabled product tabs plus the always-visible Settings tab navigate correctly. */
@RunWith(AndroidJUnit4.class)
public final class SubHubNavigationTest {
    @Before public void enterDomMode() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        if (!ControllerPinManager.isConfigured(context)) {
            ControllerPinManager.setPin(context, "2468");
        }
        ControllerPinManager.enterDomMode();
    }

    @Test public void disabledAreasDisappearWhileSettingsRemainsAvailable() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        FeatureModuleManager modules = new FeatureModuleManager(context);
        modules.save(false, true, false);
        try (ActivityScenario<GlobalSettingsActivity> scenario =
                     ActivityScenario.launch(GlobalSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.nav_censor).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_home).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_limits).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.nav_money).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_studio).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_settings).getVisibility());
            });
        } finally {
            modules.save(true, true, true);
        }
    }

    @Test public void subSpaceKeepsOnlyHomeAndStudio() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ControllerPinManager.enterSubMode();
                SubHubNavigation.bind(activity, activity.findViewById(android.R.id.content),
                        SubHubNavigation.Screen.HOME);
                assertFalse(ControllerPinManager.isDomModeActive());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_home).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.nav_studio).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.nav_censor).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.nav_limits).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.nav_money).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.nav_settings).getVisibility());
            });
        } finally {
            ControllerPinManager.enterDomMode();
        }
    }

    @Test public void primaryPillNavigatesAndUpdatesSelection() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        context
                .getSharedPreferences("betablocker_achievements", 0)
                .edit().clear().commit();
        context.getSharedPreferences(StatsRepository.PREFS_NAME, 0)
                .edit().clear().commit();
        FeatureModuleManager modules = new FeatureModuleManager(context);
        // Keep MainActivity's first-open permission coordinator idle while this test exercises
        // navigation. The feature tabs are restored immediately after that startup pass.
        modules.save(false, false, false, false);
        context.getSharedPreferences(com.subhub.app.settings.SettingsRepository.PREFERENCES_NAME, 0)
                .edit().putBoolean(HardcoreModeManager.KEY_REQUESTED, false).commit();
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        SystemClock.sleep(500L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        modules.save(true, true, true);
        scenario.onActivity(activity -> SubHubNavigation.bind(activity,
                activity.findViewById(android.R.id.content), SubHubNavigation.Screen.HOME));
        assertDestination(MainActivity.class, R.id.nav_home, R.id.nav_censor);

        onView(withId(R.id.nav_censor)).perform(click());
        assertDestination(SettingsActivity.class, R.id.nav_censor, R.id.nav_home);

        onView(withId(R.id.nav_limits)).perform(click());
        assertDestination(AppModeActivity.class, R.id.nav_limits, R.id.nav_censor);

        onView(withId(R.id.nav_money)).perform(click());
        assertDestination(PenanceActivity.class, R.id.nav_money, R.id.nav_limits);

        onView(withId(R.id.nav_studio)).perform(click());
        assertDestination(StudioActivity.class, R.id.nav_studio, R.id.nav_money);

        onView(withId(R.id.nav_settings)).perform(click());
        assertDestination(GlobalSettingsActivity.class, R.id.nav_settings, R.id.nav_studio);

        onView(withId(R.id.nav_home)).perform(click());
        assertDestination(MainActivity.class, R.id.nav_home, R.id.nav_settings);
        scenario.onActivity(Activity::finishAndRemoveTask);
    }

    private static void assertDestination(Class<? extends Activity> expected, int selectedId,
            int unselectedId) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Activity activity = resumedActivity(expected);
        assertEquals(expected, activity.getClass());
        View selected = activity.findViewById(selectedId);
        View unselected = activity.findViewById(unselectedId);
        assertNotNull(selected.getBackground());
        assertTrue(!(selected.getBackground() instanceof ColorDrawable));
        assertTrue(unselected.getBackground() instanceof ColorDrawable);
        int[] tabs = {R.id.nav_home, R.id.nav_censor, R.id.nav_limits,
                R.id.nav_money, R.id.nav_studio, R.id.nav_settings};
        int[] icons = {R.id.nav_home_icon, R.id.nav_censor_icon, R.id.nav_limits_icon,
                R.id.nav_money_icon, R.id.nav_studio_icon, R.id.nav_settings_icon};
        int[] labels = {R.id.nav_home_label, R.id.nav_censor_label, R.id.nav_limits_label,
                R.id.nav_money_label, R.id.nav_studio_label, R.id.nav_settings_label};
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

    private static Activity resumedActivity(Class<? extends Activity> expected) {
        AtomicReference<Activity> result = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + 3_000L;
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED);
                result.set(resumed.isEmpty() ? null : resumed.iterator().next());
            });
            if (result.get() != null) return result.get();
            SystemClock.sleep(50L);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("No activity reached RESUMED while waiting for "
                + expected.getSimpleName());
    }
}
