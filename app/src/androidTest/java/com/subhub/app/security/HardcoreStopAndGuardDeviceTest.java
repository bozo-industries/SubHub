package com.subhub.app.security;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.MainActivity;
import com.subhub.app.R;
import com.subhub.app.appmode.AppModeManager;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Physical/emulator contract; activate the test Device Admin before invoking this class. */
@RunWith(AndroidJUnit4.class)
public final class HardcoreStopAndGuardDeviceTest {
    @Test public void stopRequiresControllerPin() {
        Context context = ApplicationProvider.getApplicationContext();
        HardcoreModeManager hardcore = new HardcoreModeManager(context);
        Assume.assumeTrue(hardcore.isAdminActive());
        hardcore.onAdminEnabled();
        AppModeManager appMode = new AppModeManager(context);
        try {
            try (ActivityScenario<MainActivity> scenario =
                         ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    ControllerPinManager.enterSubMode();
                    appMode.setArmed(true);
                    activity.findViewById(R.id.button_protection).performClick();
                    assertTrue(appMode.isArmed());
                });
                onView(withHint(R.string.controller_pin_label))
                        .check(matches(isDisplayed()))
                        .perform(replaceText("2468"));
                onView(withId(android.R.id.button1)).perform(click());
                scenario.onActivity(activity -> {
                    assertTrue(ControllerPinManager.isDomModeActive());
                    assertFalse(appMode.isArmed());
                });
            }

        } finally {
            ControllerPinManager.enterDomMode();
            appMode.setArmed(false);
            hardcore.disable();
        }
    }
}
