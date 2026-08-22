package com.betasafe.app.penance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PenanceContractTest {
    private Context context;
    private PenanceManager manager;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(PenanceManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        manager = new PenanceManager(context);
    }

    @After public void tearDown() {
        context.getSharedPreferences(PenanceManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void mercyCanForgiveAFalsePositiveWithoutCreatingPayment() {
        long now = 1_725_552_000_000L;
        manager.configure(true, 100, 500, 2_000, 10, "https://payments.example.test");
        assertEquals(200, manager.recordStrikes(2, now));
        PenanceSnapshot snapshot = manager.snapshot(now);
        assertEquals(0, snapshot.getDueCents());
        assertEquals(200, snapshot.getMercyCents());
        assertTrue(manager.forgiveLatestInMercy(now));
        assertEquals(0, manager.snapshot(now).getMercyCents());
        assertTrue(manager.getActiveOrderId().isEmpty());
    }

    @Test public void settlementRequiresAnExactConfirmedAmount() {
        long now = 1_725_552_000_000L;
        manager.configure(true, 125, 500, 2_000, 0, "https://payments.example.test");
        assertEquals(250, manager.recordStrikes(2, now));
        PenanceManager.Settlement settlement = manager.beginSettlement(now);
        assertNotNull(settlement);
        assertEquals(250, settlement.getAmountCents());
        assertFalse(manager.completeSettlement(settlement.getId(), 249));
        assertEquals(250, manager.snapshot(now).getCheckoutCents());
        assertTrue(manager.completeSettlement(settlement.getId(), 250));
        assertEquals(0, manager.snapshot(now).getCheckoutCents());
        assertEquals(250, manager.snapshot(now).getPaidCents());
    }

    @Test public void safetyReleaseForgivesDueAndCheckoutEntries() {
        long now = 1_725_552_000_000L;
        manager.configure(true, 100, 500, 2_000, 0, "https://payments.example.test");
        manager.recordStrikes(2, now);
        PenanceManager.Settlement settlement = manager.beginSettlement(now);
        manager.bindOrder(settlement.getId(), "ORDER-123", "https://paypal.example.test/approve");
        manager.forgiveAllUnpaid();
        PenanceSnapshot snapshot = manager.snapshot(now);
        assertEquals(0, snapshot.getDueCents());
        assertEquals(0, snapshot.getCheckoutCents());
        assertTrue(manager.getActiveOrderId().isEmpty());
    }

    @Test public void styledTreasuryAndMainEntryRemainAvailable() {
        try (ActivityScenario<PenanceActivity> scenario =
                     ActivityScenario.launch(PenanceActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.button_clear_unpaid).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.button_test_strike).getVisibility());
            });
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.penance_card).getVisibility()));
        }
    }
}
