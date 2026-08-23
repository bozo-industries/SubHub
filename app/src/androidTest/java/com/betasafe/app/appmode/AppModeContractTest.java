package com.betasafe.app.appmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.GridLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.betasafe.app.R;
import com.betasafe.app.settings.SettingsRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class AppModeContractTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        preferences.edit().remove(AppModeManager.KEY_ARMED)
                .remove(AppModeManager.KEY_MODE)
                .remove(AppModeManager.KEY_SELECTED_PACKAGES)
                .remove(AppModeManager.KEY_AUTO_RESUME).commit();
        ProtectionSessionManager.markMediaProjectionExplicitlyStopped(context);
    }

    @After public void tearDown() {
        preferences.edit().remove(AppModeManager.KEY_ARMED)
                .remove(AppModeManager.KEY_MODE)
                .remove(AppModeManager.KEY_SELECTED_PACKAGES)
                .remove(AppModeManager.KEY_AUTO_RESUME).commit();
        ProtectionSessionManager.markMediaProjectionExplicitlyStopped(context);
        ResumeNotificationManager.cancel(context);
    }

    @Test public void selectedAppsAndBootIntentArePersisted() {
        AppModeManager manager = new AppModeManager(context);
        manager.save(true, AppModePolicy.Mode.SELECTED_APPS, true,
                Set.of("com.example.watched"));
        AppModeManager restored = new AppModeManager(context);
        assertTrue(restored.isArmed());
        assertEquals(AppModePolicy.Mode.SELECTED_APPS, restored.getMode());
        assertTrue(restored.getSelectedPackages().contains("com.example.watched"));
        restored.applyBootPolicy();
        assertTrue(restored.isArmed());
    }

    @Test public void disabledAutoResumeDisarmsAtBoot() {
        AppModeManager manager = new AppModeManager(context);
        manager.save(true, AppModePolicy.Mode.ALWAYS, false, Set.of());
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertFalse(new AppModeManager(context).isArmed());
    }

    @Test public void disarmNotificationActionAlsoClearsProjectionIntent() {
        AppModeManager manager = new AppModeManager(context);
        manager.setArmed(true);
        ProtectionSessionManager.markMediaProjectionStarted(context);
        new BootReceiver().onReceive(context, new Intent(BootReceiver.ACTION_DISARM));
        assertFalse(manager.isArmed());
        assertFalse(ProtectionSessionManager.needsMediaProjectionResume(context));
    }

    @Test public void appModeScreenUsesTheStyledSafetySurface() {
        new AppModeManager(context).save(true, AppModePolicy.Mode.SELECTED_APPS, true,
                Set.of("com.android.chrome"));
        try (ActivityScenario<AppModeActivity> scenario = ActivityScenario.launch(
                AppModeActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.button_accessibility_settings).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.button_save).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.app_list_card).getVisibility());
            });
        }
    }

    @Test public void limitsAndAppSelectorStayVisibleInAllAppsMode() {
        new AppModeManager(context).save(false, AppModePolicy.Mode.ALWAYS, true, Set.of());
        try (ActivityScenario<AppModeActivity> scenario = ActivityScenario.launch(
                AppModeActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.timer_card).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.app_list_card).getVisibility());
            });
        }
    }

    @Test public void launcherQueryIsDeclaredWithoutQueryAllPackages() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        @SuppressWarnings("deprecation")
        List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(
                launcher, PackageManager.MATCH_ALL);
        assertFalse(apps.isEmpty());
        try {
            @SuppressWarnings("deprecation")
            ActivityInfo receiver = context.getPackageManager().getReceiverInfo(
                    new android.content.ComponentName(context, BootReceiver.class), 0);
            assertFalse(receiver.exported);
        } catch (PackageManager.NameNotFoundException error) {
            throw new AssertionError(error);
        }
    }

    @Test public void launcherPickerUsesACompactTwoOrThreeRowHorizontalGrid() throws Exception {
        try (ActivityScenario<AppModeActivity> scenario =
                     ActivityScenario.launch(AppModeActivity.class)) {
            Thread.sleep(750L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                GridLayout grid = activity.findViewById(R.id.app_list);
                int expected = activity.getResources().getInteger(R.integer.app_picker_rows);
                assertTrue(expected == 2 || expected == 3);
                assertEquals(expected, grid.getRowCount());
                assertTrue(grid.getChildCount() > expected);
                assertEquals(grid.getChildAt(0).getLeft(), grid.getChildAt(1).getLeft());
                assertTrue(grid.getChildAt(expected).getLeft() > grid.getChildAt(0).getLeft());
            });
        }
    }
}
