package com.subhub.app.appmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.subhub.app.R;
import com.subhub.app.commitment.CommitmentManager;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.security.HardcoreModeManager;
import com.subhub.app.settings.SettingsRepository;
import com.subhub.app.settings.GlobalSettingsActivity;

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
                .remove(AppModeManager.KEY_MODE_EXPLICIT)
                .remove(AppModeManager.KEY_SELECTED_PACKAGES)
                .remove(AppModeManager.KEY_TIMER_PACKAGES)
                .remove(HardcoreModeManager.KEY_REQUESTED).commit();
        ProtectionSessionManager.markMediaProjectionExplicitlyStopped(context);
        CommitmentManager.emergencyRelease(context);
        ControllerPinManager.enterSubMode();
    }

    @After public void tearDown() {
        preferences.edit().remove(AppModeManager.KEY_ARMED)
                .remove(AppModeManager.KEY_MODE)
                .remove(AppModeManager.KEY_SELECTED_PACKAGES)
                .remove(AppModeManager.KEY_TIMER_PACKAGES)
                .remove(HardcoreModeManager.KEY_REQUESTED).commit();
        ProtectionSessionManager.markMediaProjectionExplicitlyStopped(context);
        CommitmentManager.emergencyRelease(context);
        ControllerPinManager.enterSubMode();
        ResumeNotificationManager.cancel(context);
    }

    @Test public void selectedAppsAndBootIntentArePersisted() {
        AppModeManager manager = new AppModeManager(context);
        manager.save(true, AppModePolicy.Mode.SELECTED_APPS, Set.of("com.example.watched"));
        AppModeManager restored = new AppModeManager(context);
        assertTrue(restored.isArmed());
        assertEquals(AppModePolicy.Mode.SELECTED_APPS, restored.getMode());
        assertTrue(restored.getSelectedPackages().contains("com.example.watched"));
        restored.applyBootPolicy();
        assertTrue(restored.isArmed());
    }

    @Test public void censorAndTimerAssignmentsAreIndependent() {
        AppModeManager manager = new AppModeManager(context);
        manager.save(false, AppModePolicy.Mode.ALWAYS, Set.of());
        manager.saveAppSelections(Set.of("com.example.censor"), Set.of("com.example.timer"));
        assertEquals(Set.of("com.example.censor"), manager.getSelectedPackages());
        assertEquals(Set.of("com.example.timer"), manager.getTimerPackages());
        assertEquals(AppModePolicy.Mode.SELECTED_APPS, manager.getMode());
    }

    @Test public void existingExplicitAssignmentsOverrideAStaleAlwaysMode() {
        preferences.edit()
                .putString(AppModeManager.KEY_MODE, "always")
                .putStringSet(AppModeManager.KEY_SELECTED_PACKAGES,
                        Set.of("com.twitter.android"))
                .commit();

        AppModeManager manager = new AppModeManager(context);
        assertEquals(AppModePolicy.Mode.SELECTED_APPS, manager.getMode());
        assertTrue(manager.getSelectedPackages().contains("com.twitter.android"));
    }

    @Test public void normalBootPreservesTheLastArmedState() {
        AppModeManager manager = new AppModeManager(context);
        manager.save(true, AppModePolicy.Mode.ALWAYS, Set.of());
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertTrue(new AppModeManager(context).isArmed());

        manager.setArmed(false);
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertFalse(new AppModeManager(context).isArmed());
    }

    @Test public void sealedPactRearmsAtBootAndRejectsSubModeDisarm() {
        AppModeManager manager = new AppModeManager(context);
        manager.setArmed(false);
        assertTrue(CommitmentManager.start(
                context, CommitmentManager.MIN_DURATION_MS, "keeper-code"));
        assertTrue(manager.isArmed());

        manager.setArmed(false);
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertTrue(manager.isArmed());

        new BootReceiver().onReceive(context, new Intent(BootReceiver.ACTION_DISARM));
        assertTrue(manager.isArmed());
    }

    @Test public void disarmNotificationActionAlsoClearsProjectionIntent() {
        AppModeManager manager = new AppModeManager(context);
        manager.setArmed(true);
        ProtectionSessionManager.markMediaProjectionStarted(context);
        new BootReceiver().onReceive(context, new Intent(BootReceiver.ACTION_DISARM));
        assertFalse(manager.isArmed());
        assertFalse(ProtectionSessionManager.needsMediaProjectionResume(context));
    }

    @Test public void limitsScreenOnlyContainsAllowanceControls() {
        new AppModeManager(context).save(true, AppModePolicy.Mode.SELECTED_APPS,
                Set.of("com.android.chrome"));
        try (ActivityScenario<AppModeActivity> scenario = ActivityScenario.launch(
                AppModeActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.timer_card).getVisibility());
                assertNull(activity.findViewById(R.id.button_accessibility_settings));
                assertNull(activity.findViewById(R.id.app_list_card));
            });
        }
    }

    @Test public void globalSettingsOwnsRecognitionAndAppAssignments() {
        ControllerPinManager.enterDomMode();
        new AppModeManager(context).save(false, AppModePolicy.Mode.ALWAYS, Set.of());
        try (ActivityScenario<GlobalSettingsActivity> scenario = ActivityScenario.launch(
                GlobalSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.button_accessibility_settings).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.app_list_card).getVisibility());
                ViewGroup sections = activity.findViewById(R.id.settings_sections);
                View protection = activity.findViewById(R.id.settings_group_protection);
                View hardcore = activity.findViewById(R.id.hardcore_card);
                assertEquals(sections, hardcore.getParent());
                assertTrue(sections.indexOfChild(protection) < sections.indexOfChild(hardcore));
                View apps = activity.findViewById(R.id.apps_card);
                assertEquals(sections, apps.getParent());
                for (int id : new int[] {R.id.recognition_card, R.id.app_list_card,
                        R.id.android_access_card}) {
                    assertEquals(apps, activity.findViewById(id).getParent());
                }
                assertEquals(View.GONE, activity.findViewById(R.id.armed).getVisibility());
            });
        }
    }

    @Test public void censorScopeChangesPreserveServiceAndIndependentAssignments() {
        ControllerPinManager.enterDomMode();
        for (boolean armed : new boolean[] {false, true}) {
            AppModeManager manager = new AppModeManager(context);
            manager.saveAppSelections(Set.of("com.example.censor"),
                    Set.of("com.example.limited"), Set.of("com.example.messages"));
            manager.save(armed, AppModePolicy.Mode.SELECTED_APPS,
                    Set.of("com.example.censor"));
            try (ActivityScenario<GlobalSettingsActivity> scenario = ActivityScenario.launch(
                    GlobalSettingsActivity.class)) {
                scenario.onActivity(activity -> {
                    // Use the live scope listener before the asynchronous launcher refresh.
                    activity.findViewById(R.id.mode_always).performClick();
                    assertEquals(armed, manager.isArmed());
                    assertEquals(AppModePolicy.Mode.ALWAYS, manager.getMode());
                    assertEquals(Set.of("com.example.limited"), manager.getTimerPackages());
                    assertEquals(Set.of("com.example.messages"), manager.getSubliminalPackages());
                    activity.findViewById(R.id.mode_selected).performClick();
                    assertEquals(armed, manager.isArmed());
                    assertEquals(AppModePolicy.Mode.SELECTED_APPS, manager.getMode());
                });
            }
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

    @Test public void launcherPickerUsesACompactVerticalGrid() throws Exception {
        ControllerPinManager.enterDomMode();
        try (ActivityScenario<GlobalSettingsActivity> scenario =
                     ActivityScenario.launch(GlobalSettingsActivity.class)) {
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.button_toggle_apps).performClick());
            Thread.sleep(750L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                GridLayout grid = activity.findViewById(R.id.app_list);
                int expected = activity.getResources().getInteger(R.integer.app_picker_columns);
                assertTrue(expected >= 3 && expected <= 5);
                assertEquals(expected, grid.getColumnCount());
                assertTrue(grid.getChildCount() > expected);
                assertEquals(grid.getChildAt(0).getTop(), grid.getChildAt(1).getTop());
                assertTrue(grid.getChildAt(expected).getTop() > grid.getChildAt(0).getTop());
            });
        }
    }
}
