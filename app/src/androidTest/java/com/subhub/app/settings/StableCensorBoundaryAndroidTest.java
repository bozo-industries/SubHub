package com.subhub.app.settings;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.service.ScreenCaptureService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** No live capture, account data, network writes or experimental detector is needed. */
@RunWith(AndroidJUnit4.class)
public final class StableCensorBoundaryAndroidTest {
    @After public void cleanup() { ControllerPinManager.enterSubMode(); }

    @Test public void wholePersonControlAndExperimentalClassesAreAbsent() {
        ControllerPinManager.enterDomMode();
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> assertEquals(0, activity.getResources().getIdentifier(
                    "radio_coverage_person", "id", activity.getPackageName())));
        }
        for (String name : new String[]{"detection.CensorCoverage", "service.ContentSpaceRegionCache",
                "service.QualityBackfillCoordinator", "diagnostics.CensorLabRecordingService"}) {
            try { Class.forName("com.subhub.app." + name); fail("Experimental class packaged: " + name); }
            catch (ClassNotFoundException expected) { }
        }
    }

    @Test public void recordingParticipationFollowsCensorModuleWithoutChangingTheEngine() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsRepository repository = new SettingsRepository(context);
        boolean before = repository.preferences().getBoolean(FeatureModuleManager.KEY_CENSOR_ENABLED, true);
        ScreenCaptureService service = new ScreenCaptureService();
        java.lang.reflect.Field settings = ScreenCaptureService.class.getDeclaredField("settings");
        settings.setAccessible(true);
        settings.set(service, repository);
        java.lang.reflect.Method configured = ScreenCaptureService.class.getDeclaredMethod("censorConfigured");
        configured.setAccessible(true);
        try {
            repository.preferences().edit().putBoolean(FeatureModuleManager.KEY_CENSOR_ENABLED, false).commit();
            assertEquals(false, configured.invoke(service));
            repository.preferences().edit().putBoolean(FeatureModuleManager.KEY_CENSOR_ENABLED, true).commit();
            assertEquals(true, configured.invoke(service));
        } finally {
            repository.preferences().edit().putBoolean(FeatureModuleManager.KEY_CENSOR_ENABLED, before).commit();
        }
    }
}
