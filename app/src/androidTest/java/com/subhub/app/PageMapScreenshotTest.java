package com.subhub.app;

import android.app.Activity;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.subhub.app.appmode.AppModeActivity;
import com.subhub.app.browser.BrowserActivity;
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
import com.subhub.app.stats.AchievementsActivity;
import com.subhub.app.stats.StatsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** Captures the real running screens used by the design inventory. */
@RunWith(AndroidJUnit4.class)
public final class PageMapScreenshotTest {
    @Test public void captureAllPages() throws Exception {
        new FeatureModuleManager(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .save(true, true, true);
        capture("01-censor-home", MainActivity.class);
        capture("02-limits", AppModeActivity.class);
        captureScrolled("02b-limits-app-picker", AppModeActivity.class, 6);
        capture("03-wallet", PenanceActivity.class);
        captureScrolled("03b-wallet-rules-and-safety", PenanceActivity.class, 2);
        captureScrolled("03c-wallet-checkout-and-history", PenanceActivity.class, 5);
        capture("04-global-settings", GlobalSettingsActivity.class);
        capture("05-censor-settings", SettingsActivity.class);
        captureScrolled("05b-settings-detection-categories", SettingsActivity.class, 4);
        captureScrolled("05c-settings-phrases-and-tools", SettingsActivity.class, 7);
        capture("06-safe-browser", BrowserActivity.class);
        capture("07-censor-photos", ExportActivity.class);
        capture("08-help-safety", HelpActivity.class);
        capture("09-statistics", StatsActivity.class);
        capture("10-achievements", AchievementsActivity.class);
        capture("11-custom-images", CustomImagesActivity.class);
        capture("12-profiles", ProfilesActivity.class);
        capture("13-configuration-packs", PacksActivity.class);
        capture("14-popup-storm", PopupStormActivity.class);
        capture("15-diagnostics", DiagnosticsActivity.class);
        capture("16-commitment-pact", CommitmentActivity.class);
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
            device.waitForIdle();
            Thread.sleep(350L);
            if (!device.takeScreenshot(new File(directory, name + ".png"))) {
                throw new IllegalStateException("Could not capture " + name);
            }
        }
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
            device.waitForIdle();
            int x = device.getDisplayWidth() / 2;
            int fromY = Math.round(device.getDisplayHeight() * 0.78f);
            int toY = Math.round(device.getDisplayHeight() * 0.24f);
            for (int index = 0; index < swipes; index++) {
                device.swipe(x, fromY, x, toY, 24);
                device.waitForIdle();
            }
            Thread.sleep(250L);
            if (!device.takeScreenshot(new File(directory, name + ".png"))) {
                throw new IllegalStateException("Could not capture " + name);
            }
        }
    }
}
