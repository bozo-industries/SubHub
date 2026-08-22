package com.betasafe.app.penance;

import java.util.Objects;

/** Immutable ledger entry for a locally detected strike. */
public final class PenanceEvent {
    public enum Status { OPEN, CHECKOUT, PAID, FORGIVEN }

    private final String id;
    private final long createdAtMillis;
    private final long mercyEndsAtMillis;
    private final int amountCents;
    private final int strikeCount;
    private final Status status;
    private final String settlementId;

    public PenanceEvent(String id, long createdAtMillis, long mercyEndsAtMillis,
            int amountCents, int strikeCount, Status status, String settlementId) {
        this.id = Objects.requireNonNull(id);
        this.createdAtMillis = createdAtMillis;
        this.mercyEndsAtMillis = mercyEndsAtMillis;
        this.amountCents = amountCents;
        this.strikeCount = strikeCount;
        this.status = Objects.requireNonNull(status);
        this.settlementId = settlementId == null ? "" : settlementId;
    }

    public String getId() { return id; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getMercyEndsAtMillis() { return mercyEndsAtMillis; }
    public int getAmountCents() { return amountCents; }
    public int getStrikeCount() { return strikeCount; }
    public Status getStatus() { return status; }
    public String getSettlementId() { return settlementId; }

    public boolean isInMercy(long nowMillis) {
        return status == Status.OPEN && mercyEndsAtMillis > nowMillis;
    }

    public boolean isDue(long nowMillis) {
        return status == Status.OPEN && mercyEndsAtMillis <= nowMillis;
    }

    public boolean countsTowardCaps() {
        return status != Status.FORGIVEN;
    }

    public PenanceEvent withStatus(Status next, String nextSettlementId) {
        return new PenanceEvent(id, createdAtMillis, mercyEndsAtMillis, amountCents,
                strikeCount, next, nextSettlementId);
    }
}
