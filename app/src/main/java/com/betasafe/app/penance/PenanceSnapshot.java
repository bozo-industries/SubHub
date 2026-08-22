package com.betasafe.app.penance;

import java.util.Collections;
import java.util.List;

/** Read-only state used by the Penance Treasury UI. */
public final class PenanceSnapshot {
    private final boolean enabled;
    private final int dueCents;
    private final int mercyCents;
    private final int checkoutCents;
    private final int paidCents;
    private final List<PenanceEvent> events;

    public PenanceSnapshot(boolean enabled, int dueCents, int mercyCents,
            int checkoutCents, int paidCents, List<PenanceEvent> events) {
        this.enabled = enabled;
        this.dueCents = dueCents;
        this.mercyCents = mercyCents;
        this.checkoutCents = checkoutCents;
        this.paidCents = paidCents;
        this.events = Collections.unmodifiableList(events);
    }

    public boolean isEnabled() { return enabled; }
    public int getDueCents() { return dueCents; }
    public int getMercyCents() { return mercyCents; }
    public int getCheckoutCents() { return checkoutCents; }
    public int getPaidCents() { return paidCents; }
    public List<PenanceEvent> getEvents() { return events; }
}
