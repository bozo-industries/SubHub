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

import java.util.EnumMap;
import java.util.Map;

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
        manager.configure(true, 100, 500, 2_000, 10);
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
        manager.configure(true, 125, 500, 2_000, 0);
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
        manager.configure(true, 100, 500, 2_000, 0);
        manager.recordStrikes(2, now);
        PenanceManager.Settlement settlement = manager.beginSettlement(now);
        manager.bindOrder(settlement.getId(), "ORDER-123", "https://paypal.example.test/approve");
        manager.forgiveAllUnpaid();
        PenanceSnapshot snapshot = manager.snapshot(now);
        assertEquals(0, snapshot.getDueCents());
        assertEquals(0, snapshot.getCheckoutCents());
        assertTrue(manager.getActiveOrderId().isEmpty());
    }

    @Test public void eachRuleHasItsOwnCostAndDisabledRulesDoNothing() {
        long now = 1_725_552_000_000L;
        Map<PenanceInfraction, Integer> rules = new EnumMap<>(PenanceInfraction.class);
        rules.put(PenanceInfraction.CENSORED_DWELL, 175);
        rules.put(PenanceInfraction.WATCHED_APP_OPEN, 50);
        manager.configure(true, rules, 500, 2_000, 0, 10, 1);
        assertEquals(175, manager.recordInfraction(
                PenanceInfraction.CENSORED_DWELL, 1, now));
        assertEquals(0, manager.recordInfraction(
                PenanceInfraction.CENSORED_TAP, 1, now));
        assertEquals(50, manager.recordInfraction(
                PenanceInfraction.WATCHED_APP_OPEN, 1, now));
        assertTrue(manager.snapshot(now).getEvents().stream().anyMatch(event ->
                event.getInfraction() == PenanceInfraction.WATCHED_APP_OPEN));
    }

    @Test public void newDetectionRuleBillsOnlyAtConfiguredBatchBoundary() {
        long now = 1_725_552_000_000L;
        Map<PenanceInfraction, Integer> rules = new EnumMap<>(PenanceInfraction.class);
        rules.put(PenanceInfraction.NEW_DETECTION, 100);
        manager.configure(true, rules, 500, 2_000, 0, 10, 5);
        assertEquals(0, manager.recordInfraction(
                PenanceInfraction.NEW_DETECTION, 2, now));
        assertEquals(100, manager.recordInfraction(
                PenanceInfraction.NEW_DETECTION, 3, now + 1));
        assertEquals(200, manager.recordInfraction(
                PenanceInfraction.NEW_DETECTION, 12, now + 2));
        assertEquals(300, manager.snapshot(now + 2).getDueCents());
    }

    @Test public void styledTreasuryAndMainEntryRemainAvailable() {
        try (ActivityScenario<PenanceActivity> scenario =
                     ActivityScenario.launch(PenanceActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.button_clear_unpaid).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.button_test_strike).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.payment_availability).getVisibility());
                assertEquals(4, ((android.widget.GridLayout)
                        activity.findViewById(R.id.rule_grid)).getChildCount());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.detection_batch).getVisibility());
                android.widget.GridLayout rules = activity.findViewById(R.id.rule_grid);
                assertEquals(rules.getChildAt(0).getTop(), rules.getChildAt(1).getTop());
                assertTrue(rules.getChildAt(2).getTop() > rules.getChildAt(0).getTop());
                assertEquals(rules.getChildAt(0).getWidth(), rules.getChildAt(1).getWidth());
                int[] detectionLocation = new int[2];
                int[] dwellLocation = new int[2];
                activity.findViewById(R.id.rule_detection_amount)
                        .getLocationOnScreen(detectionLocation);
                activity.findViewById(R.id.rule_dwell_amount)
                        .getLocationOnScreen(dwellLocation);
                assertEquals(detectionLocation[1], dwellLocation[1]);
            });
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.penance_card).getVisibility()));
        }
    }
}
