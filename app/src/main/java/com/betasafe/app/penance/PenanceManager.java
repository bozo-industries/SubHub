package com.betasafe.app.penance;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.BuildConfig;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Local, bounded penance ledger. Detection can create debt entries, but this class never initiates
 * a network request or payment by itself.
 */
public final class PenanceManager {
    public static final String PREFS_NAME = "betasafe_penance";
    public static final int DEFAULT_STRIKE_CENTS = 100;
    public static final int DEFAULT_DAILY_CAP_CENTS = 500;
    public static final int DEFAULT_WEEKLY_CAP_CENTS = 2_000;
    public static final int DEFAULT_MERCY_MINUTES = 10;
    public static final String CURRENCY = "EUR";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STRIKE_CENTS = "strike_cents";
    private static final String KEY_DAILY_CAP_CENTS = "daily_cap_cents";
    private static final String KEY_WEEKLY_CAP_CENTS = "weekly_cap_cents";
    private static final String KEY_MERCY_MINUTES = "mercy_minutes";
    private static final String KEY_EVENTS = "events_v1";
    private static final String LEGACY_KEY_BACKEND_URL = "paypal_backend_url";
    private static final String KEY_ORDER_ID = "active_order_id";
    private static final String KEY_APPROVAL_URL = "active_approval_url";
    private static final int MAX_EVENTS = 200;
    private static final Object LOCK = new Object();

    private final SharedPreferences preferences;

    public PenanceManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public int getStrikeCents() {
        return preferences.getInt(KEY_STRIKE_CENTS, DEFAULT_STRIKE_CENTS);
    }

    public int getDailyCapCents() {
        return preferences.getInt(KEY_DAILY_CAP_CENTS, DEFAULT_DAILY_CAP_CENTS);
    }

    public int getWeeklyCapCents() {
        return preferences.getInt(KEY_WEEKLY_CAP_CENTS, DEFAULT_WEEKLY_CAP_CENTS);
    }

    public int getMercyMinutes() {
        return preferences.getInt(KEY_MERCY_MINUTES, DEFAULT_MERCY_MINUTES);
    }

    public String getBackendUrl() {
        return normalizeBackendUrl(BuildConfig.PAYPAL_BACKEND_URL);
    }

    public void configure(boolean enabled, int strikeCents, int dailyCapCents,
            int weeklyCapCents, int mercyMinutes) {
        int boundedStrike = PenancePolicy.clampStrikeCents(strikeCents);
        int boundedDaily = PenancePolicy.clampDailyCapCents(dailyCapCents, boundedStrike);
        int boundedWeekly = PenancePolicy.clampWeeklyCapCents(weeklyCapCents, boundedDaily);
        preferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_STRIKE_CENTS, boundedStrike)
                .putInt(KEY_DAILY_CAP_CENTS, boundedDaily)
                .putInt(KEY_WEEKLY_CAP_CENTS, boundedWeekly)
                .putInt(KEY_MERCY_MINUTES, PenancePolicy.clampMercyMinutes(mercyMinutes))
                .remove(LEGACY_KEY_BACKEND_URL)
                .apply();
    }

    /** Returns the bounded amount added to the ledger in cents. */
    public int recordStrikes(int strikes, long nowMillis) {
        if (!isEnabled() || strikes <= 0) return 0;
        synchronized (LOCK) {
            List<PenanceEvent> events = loadEvents();
            trimHistory(events);
            if (events.size() >= MAX_EVENTS) return 0;
            int amount = PenancePolicy.boundedCharge(events, nowMillis, strikes,
                    getStrikeCents(), getDailyCapCents(), getWeeklyCapCents(),
                    ZoneId.systemDefault());
            if (amount <= 0) return 0;
            long mercyEnds = nowMillis + getMercyMinutes() * 60_000L;
            events.add(new PenanceEvent(UUID.randomUUID().toString(), nowMillis, mercyEnds,
                    amount, strikes, PenanceEvent.Status.OPEN, ""));
            saveEvents(events);
            return amount;
        }
    }

    public PenanceSnapshot snapshot(long nowMillis) {
        synchronized (LOCK) {
            List<PenanceEvent> events = loadEvents();
            int due = 0;
            int mercy = 0;
            int checkout = 0;
            int paid = 0;
            for (PenanceEvent event : events) {
                if (event.isDue(nowMillis)) due += event.getAmountCents();
                else if (event.isInMercy(nowMillis)) mercy += event.getAmountCents();
                else if (event.getStatus() == PenanceEvent.Status.CHECKOUT) {
                    checkout += event.getAmountCents();
                } else if (event.getStatus() == PenanceEvent.Status.PAID) {
                    paid += event.getAmountCents();
                }
            }
            List<PenanceEvent> newestFirst = new ArrayList<>(events);
            newestFirst.sort(Comparator.comparingLong(PenanceEvent::getCreatedAtMillis).reversed());
            return new PenanceSnapshot(isEnabled(), due, mercy, checkout, paid, newestFirst);
        }
    }

    public boolean forgiveLatestInMercy(long nowMillis) {
        synchronized (LOCK) {
            List<PenanceEvent> events = loadEvents();
            for (int index = events.size() - 1; index >= 0; index--) {
                PenanceEvent event = events.get(index);
                if (event.isInMercy(nowMillis)) {
                    events.set(index, event.withStatus(PenanceEvent.Status.FORGIVEN, ""));
                    saveEvents(events);
                    return true;
                }
            }
            return false;
        }
    }

    /** Safety action: releases every unpaid ledger entry and cancels any local checkout state. */
    public void forgiveAllUnpaid() {
        synchronized (LOCK) {
            List<PenanceEvent> events = loadEvents();
            for (int index = 0; index < events.size(); index++) {
                PenanceEvent event = events.get(index);
                if (event.getStatus() == PenanceEvent.Status.OPEN
                        || event.getStatus() == PenanceEvent.Status.CHECKOUT) {
                    events.set(index, event.withStatus(PenanceEvent.Status.FORGIVEN, ""));
                }
            }
            saveEvents(events);
            clearOrderState();
        }
    }

    public Settlement beginSettlement(long nowMillis) {
        synchronized (LOCK) {
            List<PenanceEvent> events = loadEvents();
            String existingId = "";
            int existingAmount = 0;
            for (PenanceEvent event : events) {
                if (event.getStatus() == PenanceEvent.Status.CHECKOUT) {
                    existingId = event.getSettlementId();
                    existingAmount += event.getAmountCents();
                }
            }
            if (!existingId.isEmpty()) return new Settlement(existingId, existingAmount);

            String settlementId = UUID.randomUUID().toString();
            int amount = 0;
            for (int index = 0; index < events.size(); index++) {
                PenanceEvent event = events.get(index);
                if (event.isDue(nowMillis)) {
                    amount += event.getAmountCents();
                    events.set(index, event.withStatus(
                            PenanceEvent.Status.CHECKOUT, settlementId));
                }
            }
            if (amount <= 0) return null;
            saveEvents(events);
            clearOrderState();
            return new Settlement(settlementId, amount);
        }
    }

    public void bindOrder(String settlementId, String orderId, String approvalUrl) {
        if (settlementId == null || settlementId.isEmpty()
                || orderId == null || orderId.isEmpty()) return;
        synchronized (LOCK) {
            if (!settlementExists(loadEvents(), settlementId)) return;
            preferences.edit().putString(KEY_ORDER_ID, orderId)
                    .putString(KEY_APPROVAL_URL, approvalUrl == null ? "" : approvalUrl).apply();
        }
    }

    public String getActiveOrderId() {
        String value = preferences.getString(KEY_ORDER_ID, "");
        return value == null ? "" : value;
    }

    public String getActiveApprovalUrl() {
        String value = preferences.getString(KEY_APPROVAL_URL, "");
        return value == null ? "" : value;
    }

    public String getActiveSettlementId() {
        synchronized (LOCK) {
            for (PenanceEvent event : loadEvents()) {
                if (event.getStatus() == PenanceEvent.Status.CHECKOUT) {
                    return event.getSettlementId();
                }
            }
            return "";
        }
    }

    public void cancelSettlement(String settlementId) {
        if (settlementId == null || settlementId.isEmpty()) return;
        synchronized (LOCK) {
            List<PenanceEvent> events = loadEvents();
            for (int index = 0; index < events.size(); index++) {
                PenanceEvent event = events.get(index);
                if (event.getStatus() == PenanceEvent.Status.CHECKOUT
                        && settlementId.equals(event.getSettlementId())) {
                    events.set(index, event.withStatus(PenanceEvent.Status.OPEN, ""));
                }
            }
            saveEvents(events);
            clearOrderState();
        }
    }

    public boolean completeSettlement(String settlementId, int paidAmountCents) {
        if (settlementId == null || settlementId.isEmpty() || paidAmountCents <= 0) return false;
        synchronized (LOCK) {
            List<PenanceEvent> events = loadEvents();
            int expected = 0;
            for (PenanceEvent event : events) {
                if (event.getStatus() == PenanceEvent.Status.CHECKOUT
                        && settlementId.equals(event.getSettlementId())) {
                    expected += event.getAmountCents();
                }
            }
            if (expected != paidAmountCents) return false;
            for (int index = 0; index < events.size(); index++) {
                PenanceEvent event = events.get(index);
                if (event.getStatus() == PenanceEvent.Status.CHECKOUT
                        && settlementId.equals(event.getSettlementId())) {
                    events.set(index, event.withStatus(
                            PenanceEvent.Status.PAID, settlementId));
                }
            }
            saveEvents(events);
            clearOrderState();
            return true;
        }
    }

    private List<PenanceEvent> loadEvents() {
        String raw = preferences.getString(KEY_EVENTS, "");
        List<PenanceEvent> events = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return events;
        for (String line : raw.split(";")) {
            String[] parts = line.split(",", -1);
            if (parts.length != 7) continue;
            try {
                PenanceEvent.Status status = PenanceEvent.Status.valueOf(parts[5]);
                events.add(new PenanceEvent(parts[0], Long.parseLong(parts[1]),
                        Long.parseLong(parts[2]), Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]), status, parts[6]));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed private preference entries without losing valid history.
            }
        }
        return events;
    }

    private void saveEvents(List<PenanceEvent> events) {
        trimHistory(events);
        List<String> encoded = new ArrayList<>(events.size());
        for (PenanceEvent event : events) {
            encoded.add(String.join(",", event.getId(),
                    Long.toString(event.getCreatedAtMillis()),
                    Long.toString(event.getMercyEndsAtMillis()),
                    Integer.toString(event.getAmountCents()),
                    Integer.toString(event.getStrikeCount()),
                    event.getStatus().name(), event.getSettlementId()));
        }
        preferences.edit().putString(KEY_EVENTS, String.join(";", encoded)).apply();
    }

    private static void trimHistory(List<PenanceEvent> events) {
        while (events.size() > MAX_EVENTS) {
            int removable = -1;
            for (int index = 0; index < events.size(); index++) {
                PenanceEvent.Status status = events.get(index).getStatus();
                if (status == PenanceEvent.Status.PAID || status == PenanceEvent.Status.FORGIVEN) {
                    removable = index;
                    break;
                }
            }
            if (removable < 0) break;
            events.remove(removable);
        }
    }

    private static boolean settlementExists(List<PenanceEvent> events, String settlementId) {
        for (PenanceEvent event : events) {
            if (event.getStatus() == PenanceEvent.Status.CHECKOUT
                    && settlementId.equals(event.getSettlementId())) return true;
        }
        return false;
    }

    private void clearOrderState() {
        preferences.edit().remove(KEY_ORDER_ID).remove(KEY_APPROVAL_URL).apply();
    }

    private static String normalizeBackendUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    public static String formatMoney(int cents) {
        return String.format(Locale.ROOT, "€%.2f", Math.max(0, cents) / 100.0);
    }

    public static final class Settlement {
        private final String id;
        private final int amountCents;

        private Settlement(String id, int amountCents) {
            this.id = id;
            this.amountCents = amountCents;
        }

        public String getId() { return id; }
        public int getAmountCents() { return amountCents; }
    }
}
