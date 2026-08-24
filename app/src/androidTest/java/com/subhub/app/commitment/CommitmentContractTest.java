package com.subhub.app.commitment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.MainActivity;
import com.subhub.app.R;
import com.subhub.app.appmode.AppModeManager;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.settings.FeatureModuleManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CommitmentContractTest {
    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        CommitmentManager.emergencyRelease(context);
        new AppModeManager(context).setArmed(false);
        new FeatureModuleManager(context).save(true, true, true);
        ControllerPinManager.enterSubMode();
    }

    @After public void tearDown() {
        CommitmentManager.emergencyRelease(context);
        new AppModeManager(context).setArmed(false);
        ControllerPinManager.enterSubMode();
    }

    @Test public void pactNeedsOnlyADurationAndEndsThroughTheDomRecoveryBoundary() {
        assertTrue(CommitmentManager.start(context, 2L * 60L * 60L * 1000L));
        assertTrue(CommitmentManager.isActive(context));
        CommitmentManager.emergencyRelease(context);
        assertFalse(CommitmentManager.isActive(context));
    }

    @Test public void durationIsBoundedAndEmergencyReleaseIsUnconditional() {
        assertTrue(CommitmentManager.start(context, 1L));
        assertEquals(CommitmentManager.MIN_DURATION_MS,
                CommitmentManager.originalDurationMillis(context));
        CommitmentManager.emergencyRelease(context);
        assertFalse(CommitmentManager.isActive(context));
        assertTrue(CommitmentManager.start(context, Long.MAX_VALUE));
        assertEquals(CommitmentManager.MAX_DURATION_MS,
                CommitmentManager.originalDurationMillis(context));
    }

    @Test public void sharedHomeOffersFourPactTimersAndProtectionControl() {
        ControllerPinManager.enterSubMode();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                if (activity.findViewById(R.id.commitment_card).getVisibility() != View.VISIBLE) {
                    activity.findViewById(R.id.button_edit_lock).performClick();
                }
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.commitment_start_panel).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.button_protection).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.commitment_timer_1h).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.commitment_timer_24h).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.commitment_timer_7d).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.commitment_timer_30d).getVisibility());
            });
        }
    }

    @Test public void activePactShowsCountdownAndHidesDomReleaseFromSubMode() {
        assertTrue(CommitmentManager.start(context, CommitmentManager.MIN_DURATION_MS));
        assertFalse(ControllerPinManager.isDomModeActive());
        try (ActivityScenario<CommitmentActivity> scenario =
                     ActivityScenario.launch(CommitmentActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button_edit_lock).performClick();
                assertFalse(ControllerPinManager.isDomModeActive());
                assertEquals(View.GONE, activity.findViewById(R.id.setup_panel).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.active_panel).getVisibility());
                assertFalse(((TextView) activity.findViewById(R.id.countdown))
                        .getText().toString().isEmpty());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.button_emergency_release).getVisibility());
            });
        }
    }

    @Test public void sealingArmsProtectionAndRequiresDomModeToStop() {
        assertTrue(CommitmentManager.start(context, CommitmentManager.MIN_DURATION_MS));
        assertTrue(new AppModeManager(context).isArmed());
        assertFalse(CommitmentManager.mayStopProtection(context));
        ControllerPinManager.enterDomMode();
        assertFalse(CommitmentManager.mayStopProtection(context));
    }

    @Test public void pactNeverDisablesMainProtectionControl() {
        assertTrue(CommitmentManager.start(context, CommitmentManager.MIN_DURATION_MS));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                if (activity.findViewById(R.id.commitment_card).getVisibility() != View.VISIBLE) {
                    activity.findViewById(R.id.button_edit_lock).performClick();
                }
                assertTrue(activity.findViewById(R.id.button_protection).isEnabled());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.commitment_card).getVisibility());
            });
        }
    }

    @Test public void pactAndHardcoreCopyAvoidsImplementationJargon() {
        String hardcore = context.getString(R.string.hardcore_title).toLowerCase();
        String pactSetup = context.getString(R.string.commitment_setup_body).toLowerCase();
        assertFalse(hardcore.contains("consensual"));
        assertFalse(pactSetup.contains("hash"));
        assertFalse(pactSetup.contains("salt"));
    }
}
