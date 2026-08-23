package com.betasafe.app.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;
import com.betasafe.app.settings.FeatureModuleManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies the product-wide Dom/Sub presentation boundary. */
@RunWith(AndroidJUnit4.class)
public final class ControllerEditSessionTest {
    @Before public void setUpControllerPin() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        if (!ControllerPinManager.isConfigured(context)) {
            assertTrue(ControllerPinManager.setPin(context, "2468"));
        }
        new FeatureModuleManager(context).save(true, true, true);
        ControllerPinManager.enterDomMode();
    }

    @Test public void subModeIsOnePageAndHidesConfigurationNavigation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button_edit_lock).performClick();
                assertFalse(ControllerPinManager.isDomModeActive());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.sub_dashboard).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.dom_content).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.bottom_navigation).getVisibility());
                assertFalse(activity.findViewById(R.id.button_censor_settings).isShown());
                assertTrue(activity.findViewById(R.id.button_protection).isEnabled());
            });
        }
    }

    @Test public void domModeShowsAllConfigurationAreas() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ControllerPinManager.enterDomMode();
                activity.recreate();
            });
            scenario.onActivity(activity -> {
                assertTrue(ControllerPinManager.isDomModeActive());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.sub_dashboard).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.dom_content).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.bottom_navigation).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.button_censor_settings).getVisibility());
            });
        }
    }

    @Test public void subDashboardOnlyShowsEnabledProductAreas() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                new FeatureModuleManager(activity).save(false, true, false);
                activity.findViewById(R.id.button_edit_lock).performClick();
                assertEquals(View.GONE,
                        activity.findViewById(R.id.sub_censor_card).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.sub_limits_card).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.sub_wallet_card).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.button_protection).getVisibility());
            });
        } finally {
            new FeatureModuleManager(context).save(true, true, true);
        }
    }
}
