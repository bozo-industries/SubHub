package com.subhub.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.subhub.app.appmode.AppModeActivity;
import com.subhub.app.atmosphere.AtmosphereActivity;
import com.subhub.app.capture.CustomImagesActivity;
import com.subhub.app.capture.ExportActivity;
import com.subhub.app.commitment.CommitmentActivity;
import com.subhub.app.diagnostics.DiagnosticsActivity;
import com.subhub.app.help.HelpActivity;
import com.subhub.app.pack.PacksActivity;
import com.subhub.app.penance.PenanceActivity;
import com.subhub.app.popup.PopupStormActivity;
import com.subhub.app.profiles.ProfilesActivity;
import com.subhub.app.settings.SettingsActivity;
import com.subhub.app.settings.GlobalSettingsActivity;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.settings.SettingsRepository;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.stats.AchievementsActivity;
import com.subhub.app.stats.StatsActivity;
import com.subhub.app.studio.StudioActivity;
import com.subhub.app.subliminal.SubliminalSettingsActivity;
import com.subhub.app.update.UpdatesActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;

/** Captures the real running screens used by the design inventory. */
@RunWith(AndroidJUnit4.class)
public final class PageMapScreenshotTest {
    @Test public void captureCorePages() throws Exception {
        prepareFixture(true);
        captureMain("00-sub-home", true);
        captureInSpace("00b-sub-atmosphere", AtmosphereActivity.class, true);
        captureInSpace("00c-sub-studio", StudioActivity.class, true);

        ControllerPinManager.enterDomMode();
        captureMain("01-dom-home", false);
        capture("02-limits", AppModeActivity.class);
        capture("03-wallet", PenanceActivity.class);
        captureScrolled("03b-wallet-rules-and-safety", PenanceActivity.class, 2);
        captureScrolled("03c-wallet-checkout-and-history", PenanceActivity.class, 5);
        capture("04-global-settings", GlobalSettingsActivity.class);
    }

    @Test public void captureSupportingPages() throws Exception {
        prepareFixture(false);
        capture("05-censor-settings", SettingsActivity.class);
        captureScrolled("05b-settings-detection-categories", SettingsActivity.class, 4);
        captureScrolled("05c-settings-phrases-and-tools", SettingsActivity.class, 7);
        capture("07-censor-photos", ExportActivity.class);
        capture("08-help-safety", HelpActivity.class);
        capture("09-statistics", StatsActivity.class);
        capture("10-achievements", AchievementsActivity.class);
        capture("11-custom-images", CustomImagesActivity.class);
        capture("12-profiles", ProfilesActivity.class);
        capture("13-configuration-packs", PacksActivity.class);
        capture("14-atmosphere", AtmosphereActivity.class);
        capture("15-whispers", SubliminalSettingsActivity.class);
        capture("16-popup-storm", PopupStormActivity.class);
        capture("17-studio-library", StudioActivity.class);
        captureAfterClick("17b-studio-drafts", StudioActivity.class, R.id.tab_drafts);
        captureAfterClick("17c-studio-create", StudioActivity.class, R.id.tab_create);
        capture("18-updates", UpdatesActivity.class);
        capture("19-diagnostics", DiagnosticsActivity.class);
        captureActiveServiceLock("20-service-lock");
    }

    private static void prepareFixture(boolean subSpace) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new FeatureModuleManager(context).save(true, true, true);
        if (!ControllerPinManager.isConfigured(context)) {
            ControllerPinManager.setPin(context, "2468");
        }
        String service = context.getPackageName()
                + "/com.subhub.app.service.ScreenshotAccessibilityService";
        runShellCommand("settings put secure enabled_accessibility_services " + service);
        runShellCommand("settings put secure accessibility_enabled 1");
        runShellCommand("pm grant " + context.getPackageName()
                + " android.permission.POST_NOTIFICATIONS");
        runShellCommand("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        Thread.sleep(500L);
        if (subSpace) ControllerPinManager.enterSubMode();
        else ControllerPinManager.enterDomMode();
    }

    private static void runShellCommand(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
             FileInputStream output = new FileInputStream(descriptor.getFileDescriptor())) {
            // Drain to EOF: closing the descriptor alone can race the command on an emulator.
            byte[] buffer = new byte[256];
            while (output.read(buffer) != -1) {
                // Shell setup commands normally have no output.
            }
        }
    }

    private static void captureAfterClick(String name, Class<? extends Activity> activityClass,
            int viewId) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "page-map");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create screenshot directory");
        }
        try (ActivityScenario<? extends Activity> scenario = ActivityScenario.launch(activityClass)) {
            scenario.onActivity(activity -> activity.findViewById(viewId).performClick());
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.waitForIdle(750L);
            Thread.sleep(350L);
            if (!device.takeScreenshot(new File(directory, name + ".png"))) {
                throw new IllegalStateException("Could not capture " + name);
            }
        }
    }

    private static void capture(String name, Class<? extends Activity> activityClass)
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "page-map");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create screenshot directory");
        }

        try (ActivityScenario<? extends Activity> scenario = ActivityScenario.launch(activityClass)) {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.waitForIdle(750L);
            Thread.sleep(350L);
            if (!device.takeScreenshot(new File(directory, name + ".png"))) {
                throw new IllegalStateException("Could not capture " + name);
            }
        }
    }

    private static void captureActiveServiceLock(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        context.getSharedPreferences(SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong("commitment_started_at", now)
                .putLong("commitment_ends_at", now + 3_600_000L)
                .putLong("commitment_duration", 3_600_000L)
                .commit();
        capture(name, CommitmentActivity.class);
    }

    private static void captureMain(String name, boolean subSpace) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "page-map");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create screenshot directory");
        }
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(MainActivity.EXTRA_SUPPRESS_PERMISSION_READINESS, true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> applyControllerSpace(activity, subSpace));
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.waitForIdle(750L);
            Thread.sleep(350L);
            if (!device.takeScreenshot(new File(directory, name + ".png"))) {
                throw new IllegalStateException("Could not capture " + name);
            }
        }
    }

    private static void captureInSpace(String name, Class<? extends Activity> activityClass,
            boolean subSpace) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "page-map");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create screenshot directory");
        }
        try (ActivityScenario<? extends Activity> scenario = ActivityScenario.launch(activityClass)) {
            scenario.onActivity(activity -> applyControllerSpace(activity, subSpace));
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.waitForIdle(750L);
            Thread.sleep(350L);
            if (!device.takeScreenshot(new File(directory, name + ".png"))) {
                throw new IllegalStateException("Could not capture " + name);
            }
        }
    }

    private static void applyControllerSpace(Activity activity, boolean subSpace) {
        if (!subSpace) {
            ControllerPinManager.enterDomMode();
            return;
        }
        View editLock = activity.findViewById(R.id.button_edit_lock);
        if (editLock instanceof TextView
                && "Lock".contentEquals(((TextView) editLock).getText())) {
            editLock.performClick();
        } else {
            ControllerPinManager.enterSubMode();
        }
        TextView studioMode = activity.findViewById(R.id.studio_mode);
        if (studioMode != null) studioMode.setText(R.string.studio_sub_space);
    }

    private static void captureScrolled(
            String name, Class<? extends Activity> activityClass, int swipes) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "page-map");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create screenshot directory");
        }
        try (ActivityScenario<? extends Activity> scenario = ActivityScenario.launch(activityClass)) {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.waitForIdle(750L);
            int x = device.getDisplayWidth() / 2;
            int fromY = Math.round(device.getDisplayHeight() * 0.78f);
            int toY = Math.round(device.getDisplayHeight() * 0.24f);
            for (int index = 0; index < swipes; index++) {
                device.swipe(x, fromY, x, toY, 24);
                device.waitForIdle(500L);
            }
            Thread.sleep(250L);
            if (!device.takeScreenshot(new File(directory, name + ".png"))) {
                throw new IllegalStateException("Could not capture " + name);
            }
        }
    }
}
