package com.betasafe.app;

import android.app.Activity;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.betasafe.app.appmode.AppModeActivity;
import com.betasafe.app.browser.BrowserActivity;
import com.betasafe.app.capture.CustomImagesActivity;
import com.betasafe.app.capture.ExportActivity;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.diagnostics.DiagnosticsActivity;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.pack.PacksActivity;
import com.betasafe.app.penance.PenanceActivity;
import com.betasafe.app.popup.PopupStormActivity;
import com.betasafe.app.profiles.ProfilesActivity;
import com.betasafe.app.settings.SettingsActivity;
import com.betasafe.app.stats.AchievementsActivity;
import com.betasafe.app.stats.StatsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** Captures the real running screens used by the design inventory. */
@RunWith(AndroidJUnit4.class)
public final class PageMapScreenshotTest {
    @Test public void captureAllPages() throws Exception {
        capture("01-censor-home", MainActivity.class);
        capture("02-limits", AppModeActivity.class);
        capture("03-money", PenanceActivity.class);
        captureScrolled("03b-money-rules-and-safety", PenanceActivity.class, 2);
        captureScrolled("03c-money-checkout-and-history", PenanceActivity.class, 5);
        capture("04-censor-settings", SettingsActivity.class);
        captureScrolled("04b-settings-detection-categories", SettingsActivity.class, 4);
        captureScrolled("04c-settings-phrases-and-tools", SettingsActivity.class, 7);
        capture("05-safe-browser", BrowserActivity.class);
        capture("06-censor-photos", ExportActivity.class);
        capture("07-help-safety", HelpActivity.class);
        capture("08-statistics", StatsActivity.class);
        capture("09-achievements", AchievementsActivity.class);
        capture("10-custom-images", CustomImagesActivity.class);
        capture("11-profiles", ProfilesActivity.class);
        capture("12-configuration-packs", PacksActivity.class);
        capture("13-popup-storm", PopupStormActivity.class);
        capture("14-diagnostics", DiagnosticsActivity.class);
        capture("15-commitment-pact", CommitmentActivity.class);
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
