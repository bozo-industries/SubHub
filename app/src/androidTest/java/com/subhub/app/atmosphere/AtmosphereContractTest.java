package com.subhub.app.atmosphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.MainActivity;
import com.subhub.app.R;
import com.subhub.app.popup.PopupStormSettings;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.settings.FeatureModuleManager;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AtmosphereContractTest {
    @Test public void homeExposesOneCombinedAtmosphereEntry() {
        Context context = ApplicationProvider.getApplicationContext();
        new FeatureModuleManager(context).save(true, true, true, true);
        PopupStormSettings.preferences(context).edit()
                .putBoolean(PopupStormSettings.K_ENABLED, true)
                .commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View card = activity.findViewById(R.id.home_atmosphere_card);
                TextView status = activity.findViewById(R.id.home_atmosphere_status);
                assertNotNull(card);
                assertEquals(View.VISIBLE, card.getVisibility());
                assertTrue(status.getText().toString().contains("Whispers on"));
                assertTrue(status.getText().toString().contains("Popup Storm on"));
            });
        }
    }

    @Test public void atmosphereHubRemainsReadableInSubSpace() {
        ControllerPinManager.enterDomMode();
        try (ActivityScenario<AtmosphereActivity> scenario =
                     ActivityScenario.launch(AtmosphereActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button_edit_lock).performClick();
                assertEquals(activity.getString(R.string.atmosphere_subtitle_sub),
                        ((TextView) activity.findViewById(R.id.atmosphere_subtitle))
                                .getText().toString());
                assertNotNull(activity.findViewById(R.id.whispers_card));
                assertNotNull(activity.findViewById(R.id.popup_storm_card));
                assertEquals(activity.getString(R.string.atmosphere_unlock_to_edit),
                        ((TextView) activity.findViewById(R.id.button_whispers))
                                .getText().toString());
            });
        }
    }
}
