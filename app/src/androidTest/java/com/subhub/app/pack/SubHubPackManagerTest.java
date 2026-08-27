package com.subhub.app.pack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.settings.SettingsRepository;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class SubHubPackManagerTest {
    private Context context;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        if (!ControllerPinManager.isConfigured(context)) ControllerPinManager.setPin(context, "2468");
        ControllerPinManager.enterDomMode();
        new SubHubPackManager(context).deactivate();
    }

    @Test public void activationLocksSelectedGroupAndDeactivationRestoresPreviousValue()
            throws Exception {
        SettingsRepository repository = new SettingsRepository(context);
        repository.preferences().edit().putString(SettingsRepository.KEY_CENSOR_TYPE, "blur")
                .commit();
        JSONObject section = new JSONObject().put(SettingsRepository.KEY_CENSOR_TYPE, "box");
        SubHubPack pack = new SubHubPack(UUID.randomUUID().toString(), "Test", "", "", "1.0.0",
                1L, 1L, "0.6.0", Map.of(SubHubPackSchema.CENSOR, section),
                Set.of(SubHubPackSchema.CENSOR), new JSONObject(), Map.of());
        SubHubPackManager manager = new SubHubPackManager(context);

        assertTrue(manager.activate(pack, Set.of(SubHubPackSchema.CENSOR)));
        assertEquals("box", repository.preferences().getString(
                SettingsRepository.KEY_CENSOR_TYPE, ""));
        assertTrue(SubHubPackLocks.isLocked(context, SubHubPackSchema.CENSOR));

        assertTrue(manager.deactivate());
        assertEquals("blur", repository.preferences().getString(
                SettingsRepository.KEY_CENSOR_TYPE, ""));
        assertFalse(SubHubPackLocks.isLocked(context, SubHubPackSchema.CENSOR));
    }
}
