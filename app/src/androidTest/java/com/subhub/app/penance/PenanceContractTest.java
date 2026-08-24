package com.subhub.app.penance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.subhub.app.appmode.AppModeManager;
import com.subhub.app.commitment.CommitmentManager;
import com.subhub.app.security.ControllerPinManager;

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
        new HardcoreAutoPayManager(context).disable();
    }

    @After public void tearDown() {
        CommitmentManager.emergencyRelease(context);
        new PaidPauseManager(context).finish();
        context.getSharedPreferences(PenanceManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        new PayPalCredentialStore(context).clear();
        new HardcoreAutoPayManager(context).disable();
    }

    @Test public void paidPauseUsesItsExactConfiguredCheckoutAndStartsAfterPayment() {
        long now = System.currentTimeMillis();
        PaidPauseManager pause = new PaidPauseManager(context);
        pause.configure(true, 375, 12);
        CommitmentManager.start(context, 60L * 60L * 1000L);
        new AppModeManager(context).setArmed(true);

        assertTrue(manager.requestPaidPause(now));
        PenanceManager.Settlement settlement = manager.beginPaidPauseSettlement(now);
        assertNotNull(settlement);
        assertEquals(375, settlement.getAmountCents());
        assertTrue(manager.completeSettlement(settlement.getId(), 375));
        assertTrue(pause.isActive());
        assertFalse(new AppModeManager(context).isArmed());
    }

    @Test public void sandboxCredentialsAreEncryptedPerInstallAndCanBeCleared() {
        PayPalCredentialStore store = new PayPalCredentialStore(context);
        store.clear();
        assertTrue(store.save(PayPalEnvironment.SANDBOX,
                "sandbox-client-id", "sandbox-client-secret"));
        PayPalCredentialStore.Credentials loaded = store.load();
        assertEquals("sandbox-client-id", loaded.clientId());
        assertEquals("sandbox-client-secret", loaded.secret());
        assertFalse(store.hasVerifiedCredentials());
        assertTrue(store.markCredentialsVerified());
        assertTrue(store.hasVerifiedCredentials());
        assertEquals(PayPalCredentialStore.VaultStatus.REQUESTED,
                store.vaultState().status());
        for (Object raw : context.getSharedPreferences(
                PayPalCredentialStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getAll().values()) {
            assertNotNull(raw);
            assertFalse("sandbox-client-id".equals(raw));
            assertFalse("sandbox-client-secret".equals(raw));
        }
        store.selectEnvironment(PayPalEnvironment.LIVE);
        assertEquals(PayPalEnvironment.LIVE, store.selectedEnvironment());
        assertFalse(store.hasCredentials());
        assertFalse(store.hasVerifiedCredentials());
        assertEquals(PayPalCredentialStore.VaultStatus.DISCONNECTED,
                store.vaultState().status());
        store.clear();
        assertFalse(store.hasCredentials());
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

    @Test public void autoPayRequiresAReadyEnvironmentBoundSavedWallet() {
        PayPalCredentialStore store = new PayPalCredentialStore(context);
        assertTrue(store.save(PayPalEnvironment.SANDBOX, "client", "secret"));
        PayPalCredentialStore.Credentials credentials = store.load();
        store.recordVaultResult(credentials, "VAULTED", "vault-1", "customer-1",
                "payer@example.com", "PAYER-1234");
        assertEquals("p•••@e•••.com", store.vaultState().maskedPayer());
        HardcoreAutoPayManager auto = new HardcoreAutoPayManager(context);
        assertTrue(auto.enable());
        assertTrue(auto.isEnabled());
        store.selectEnvironment(PayPalEnvironment.LIVE);
        assertFalse(auto.isEnabled());
    }

    @Test public void automaticCheckoutIsNotMistakenForManualConfirmation() {
        long now = System.currentTimeMillis();
        manager.configure(true, 100, 500, 2_000, 0);
        manager.recordStrikes(1, now);
        PenanceManager.Settlement settlement = manager.beginSettlement(now);
        assertNotNull(settlement);
        manager.markAutomaticSettlement(settlement.getId(), "boundary");
        assertEquals(PenanceManager.CheckoutMode.HARDCORE_AUTO,
                manager.getActiveCheckoutMode());
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

    @Test public void enabledCensorTapCreatesItsOwnLedgerEntry() {
        long now = 1_725_552_000_000L;
        Map<PenanceInfraction, Integer> rules = new EnumMap<>(PenanceInfraction.class);
        rules.put(PenanceInfraction.CENSORED_TAP, 250);
        manager.configure(true, rules, 1_000, 5_000, 0, 10, 1);

        assertEquals(250, manager.recordInfraction(
                PenanceInfraction.CENSORED_TAP, 1, now));
        assertTrue(manager.snapshot(now).getEvents().stream().anyMatch(event ->
                event.getInfraction() == PenanceInfraction.CENSORED_TAP
                        && event.getAmountCents() == 250));
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

    @Test public void ordinaryRuleSaveKeepsBatchProgressButChangingBatchResetsIt() {
        long now = 1_725_552_000_000L;
        Map<PenanceInfraction, Integer> rules = new EnumMap<>(PenanceInfraction.class);
        rules.put(PenanceInfraction.NEW_DETECTION, 100);
        manager.configure(true, rules, 500, 2_000, 0, 10, 5);
        assertEquals(0, manager.recordInfraction(PenanceInfraction.NEW_DETECTION, 2, now));
        assertEquals(2, manager.getDetectionRemainder());

        rules.put(PenanceInfraction.NEW_DETECTION, 125);
        manager.configure(true, rules, 500, 2_000, 0, 10, 5);
        assertEquals(2, manager.getDetectionRemainder());

        manager.configure(true, rules, 500, 2_000, 0, 10, 3);
        assertEquals(0, manager.getDetectionRemainder());
    }

    @Test public void reachedDailyCapIsVisibleAndExplainsWhyNoMoneyWasAdded() {
        long now = System.currentTimeMillis();
        manager.configure(true, 100, 500, 2_000, 0);
        assertEquals(500, manager.recordStrikes(5, now));
        assertEquals(0, manager.getDailyRemainingCents(now));
        assertEquals(1_500, manager.getWeeklyRemainingCents(now));

        try (ActivityScenario<PenanceActivity> scenario =
                     ActivityScenario.launch(PenanceActivity.class)) {
            scenario.onActivity(activity -> assertEquals(
                    activity.getString(R.string.penance_daily_cap_reached),
                    ((TextView) activity.findViewById(R.id.rule_math_preview))
                            .getText().toString()));
        }
    }

    @Test public void fullHistoryDropsOldForgivenEntriesBeforeRejectingNewOnes() {
        long now = System.currentTimeMillis();
        manager.configure(true, 1, 50_000, 200_000, 0);
        for (int index = 0; index < 200; index++) {
            assertEquals(1, manager.recordStrikes(1, now + index));
        }
        manager.forgiveAllUnpaid();
        assertEquals(1, manager.recordStrikes(1, now + 201));
    }

    @Test public void styledTreasuryAndMainEntryRemainAvailable() {
        ControllerPinManager.enterDomMode();
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
        ControllerPinManager.enterSubMode();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.sub_wallet_card).getVisibility()));
        }
    }

    @Test public void detectionBatchInputFollowsNewDetectionRuleToggle() {
        ControllerPinManager.enterDomMode();
        try (ActivityScenario<PenanceActivity> scenario =
                     ActivityScenario.launch(PenanceActivity.class)) {
            scenario.onActivity(activity -> {
                android.widget.CheckBox toggle = activity.findViewById(
                        R.id.rule_detection_enabled);
                View batch = activity.findViewById(R.id.detection_batch);
                toggle.setChecked(false);
                assertFalse(batch.isEnabled());
                assertTrue(batch.getAlpha() < 0.5f);
                toggle.setChecked(true);
                assertTrue(batch.isEnabled());
                assertEquals(1f, batch.getAlpha(), 0.01f);
            });
        } finally {
            ControllerPinManager.enterSubMode();
        }
    }
}
