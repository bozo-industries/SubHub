package com.subhub.app.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.subhub.app.R;
import com.subhub.app.security.ControllerPinManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DiagnosticsCensorLabActivityTest {
    @Before public void resetSession() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        if (!ControllerPinManager.isConfigured(context)) {
            ControllerPinManager.setPin(context, "2468");
        }
        ControllerPinManager.enterDomMode();
        if (CensorLabRecorder.isActive()) CensorLabRecorder.stop(context);
    }

    @Test public void telemetryFallbackStopExposesMarkerAndShareState() {
        try (ActivityScenario<DiagnosticsActivity> scenario =
                     ActivityScenario.launch(DiagnosticsActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    CensorLabRecorder.start(activity);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
                activity.findViewById(R.id.button_refresh).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertTrue(CensorLabRecorder.isActive());
                assertTrue(activity.findViewById(R.id.button_censor_lab_stop).isEnabled());
            });

            scenario.onActivity(activity ->
                    activity.findViewById(R.id.button_censor_lab_stop).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertTrue(
                    activity.findViewById(R.id.censor_lab_sync_marker).getVisibility()
                            == View.VISIBLE));
            SystemClock.sleep(1_500L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertFalse(CensorLabRecorder.isActive());
                assertTrue(activity.findViewById(R.id.button_censor_lab_start).isEnabled());
                assertTrue(activity.findViewById(R.id.button_censor_lab_attach).isEnabled());
                assertTrue(activity.findViewById(R.id.button_censor_lab_share_trace).isEnabled());
            });
        }
    }
}
