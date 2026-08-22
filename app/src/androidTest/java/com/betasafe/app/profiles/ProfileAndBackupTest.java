package com.betasafe.app.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.settings.SettingsRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@RunWith(AndroidJUnit4.class)
public final class ProfileAndBackupTest {
    @Test
    public void profileAndJsonBackupRoundTripTypedSettings() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences settings = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        ProfileManager profiles = new ProfileManager(context);
        String profile = "instrumented-profile";
        profiles.delete(profile);
        settings.edit()
                .putString(SettingsRepository.KEY_CENSOR_TYPE, "glitch")
                .putInt(SettingsRepository.KEY_CENSOR_INTENSITY, 73)
                .putBoolean(SettingsRepository.KEY_SHOW_BORDER, false)
                .commit();
        assertTrue(profiles.save(profile));

        settings.edit().putString(SettingsRepository.KEY_CENSOR_TYPE, "box").commit();
        assertTrue(profiles.load(profile));
        assertEquals("glitch", settings.getString(SettingsRepository.KEY_CENSOR_TYPE, ""));
        assertEquals(73, settings.getInt(SettingsRepository.KEY_CENSOR_INTENSITY, 0));

        SettingsBackupManager backup = new SettingsBackupManager(context);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        backup.exportTo(encoded);
        settings.edit().putInt(SettingsRepository.KEY_CENSOR_INTENSITY, 12).commit();
        assertTrue(backup.importFrom(new ByteArrayInputStream(encoded.toByteArray())));
        assertEquals(73, settings.getInt(SettingsRepository.KEY_CENSOR_INTENSITY, 0));
        profiles.delete(profile);
    }
}
