package com.betasafe.app.commitment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;
import com.betasafe.app.settings.SettingsRepository;

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
    }

    @After public void tearDown() {
        CommitmentManager.emergencyRelease(context);
    }

    @Test public void codeIsHashedAndCanReleaseThePact() {
        assertTrue(CommitmentManager.start(context, 2L * 60L * 60L * 1000L, "keeper-code"));
        assertTrue(CommitmentManager.isActive(context));
        SharedPreferences preferences = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        for (Object stored : preferences.getAll().values()) {
            assertNotEquals("keeper-code", stored);
        }
        assertFalse(CommitmentManager.verifyAndRelease(context, "wrong-code"));
        assertTrue(CommitmentManager.isActive(context));
        assertTrue(CommitmentManager.verifyAndRelease(context, "keeper-code"));
        assertFalse(CommitmentManager.isActive(context));
    }

    @Test public void durationIsBoundedAndEmergencyReleaseIsUnconditional() {
        assertTrue(CommitmentManager.start(context, 1L, "1234"));
        assertEquals(CommitmentManager.MIN_DURATION_MS,
                CommitmentManager.originalDurationMillis(context));
        CommitmentManager.emergencyRelease(context);
        assertFalse(CommitmentManager.isActive(context));
    }

    @Test public void activePactShowsCountdownAndSafetyRelease() {
        assertTrue(CommitmentManager.start(context, CommitmentManager.MIN_DURATION_MS, "1234"));
        try (ActivityScenario<CommitmentActivity> scenario =
                     ActivityScenario.launch(CommitmentActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.setup_panel).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.active_panel).getVisibility());
                assertFalse(((TextView) activity.findViewById(R.id.countdown))
                        .getText().toString().isEmpty());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.button_emergency_release).getVisibility());
            });
        }
    }

    @Test public void pactNeverDisablesMainProtectionControl() {
        assertTrue(CommitmentManager.start(context, CommitmentManager.MIN_DURATION_MS, "1234"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.button_protection).isEnabled());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.commitment_card).getVisibility());
            });
        }
    }
}
