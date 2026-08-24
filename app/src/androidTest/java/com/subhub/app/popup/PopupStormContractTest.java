package com.subhub.app.popup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PopupStormContractTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = PopupStormSettings.preferences(context);
        preferences.edit().clear().commit();
        PopupStormManager.get().stop();
    }

    @After public void tearDown() {
        PopupStormManager.get().stop();
        preferences.edit().clear().commit();
    }

    @Test public void settingsClampUntrustedPreferenceValues() {
        preferences.edit()
                .putFloat(PopupStormSettings.K_SPAWN_RATE, 900f)
                .putInt(PopupStormSettings.K_MAX_SIMULTANEOUS, 900)
                .putInt(PopupStormSettings.K_MIN_SIZE, -5)
                .putString(PopupStormSettings.K_DENIAL_CAPTION_TEXT,
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .putString(PopupStormSettings.K_DETECTION_MODE, "invalid")
                .commit();
        PopupStormSettings settings = PopupStormSettings.load(context);
        assertEquals(8f, settings.getSpawnRate(), 0f);
        assertEquals(15, settings.getMaxSimultaneous());
        assertEquals(40, settings.getMinSize());
        assertEquals(32, settings.getDenialCaptionText().length());
        assertEquals("off", settings.getDetectionMode());
        assertFalse(settings.isEnabled());
    }

    @Test public void recoveredIntensityPresetsWriteExpectedBounds() {
        IntensityPresets.OVERLOAD.apply(context);
        PopupStormSettings overload = PopupStormSettings.load(context);
        assertEquals(7f, overload.getSpawnRate(), 0f);
        assertEquals(.5f, overload.getDisplayDuration(), 0f);
        assertEquals(15, overload.getMaxSimultaneous());
        assertTrue(overload.isBurstEnabled());

        IntensityPresets.GENTLE.apply(context);
        PopupStormSettings gentle = PopupStormSettings.load(context);
        assertEquals(.5f, gentle.getSpawnRate(), 0f);
        assertEquals(3, gentle.getMaxSimultaneous());
        assertFalse(gentle.isBurstEnabled());
    }

    @Test public void generatedSampleAndDenialFiltersDecode() throws Exception {
        Bitmap sample = BitmapFactory.decodeStream(
                context.getAssets().open("popup_storm/guardian_shield.webp"));
        assertNotNull(sample);
        Bitmap pixelated = DenialFilter.pixelate(sample, 75);
        Bitmap blurred = DenialFilter.blur(sample, 75);
        assertEquals(sample.getWidth(), pixelated.getWidth());
        assertEquals(sample.getHeight(), blurred.getHeight());
        pixelated.recycle();
        blurred.recycle();
        sample.recycle();
    }

    @Test public void configurationScreenUsesGeneratedGuardianStyle() {
        try (ActivityScenario<PopupStormActivity> scenario =
                     ActivityScenario.launch(PopupStormActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.popup_title),
                        ((TextView) activity.findViewById(R.id.popup_header_title))
                                .getText().toString());
                assertNotNull(activity.findViewById(R.id.button_preview));
                assertNotNull(activity.findViewById(R.id.button_add_folder));
                assertNotNull(activity.findViewById(R.id.dynamic_settings));
            });
        }
    }
}
