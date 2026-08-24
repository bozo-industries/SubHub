package com.subhub.app.penance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class PenancePolicyTest {
    private static final long NOW = 1_725_552_000_000L;
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test public void strikeChargeIsBoundedByDailyAndWeeklyCaps() {
        List<PenanceEvent> events = new ArrayList<>();
        events.add(event("one", NOW - 1_000L, 350, PenanceEvent.Status.OPEN));
        assertEquals(150, PenancePolicy.boundedCharge(events, NOW,
                4, 100, 500, 2_000, UTC));
        events.add(event("two", NOW - 2_000L, 150, PenanceEvent.Status.PAID));
        assertEquals(0, PenancePolicy.boundedCharge(events, NOW,
                1, 100, 500, 2_000, UTC));
    }

    @Test public void forgivenEntriesDoNotConsumeTheCap() {
        List<PenanceEvent> events = List.of(
                event("forgiven", NOW - 1_000L, 500, PenanceEvent.Status.FORGIVEN));
        assertEquals(200, PenancePolicy.boundedCharge(events, NOW,
                2, 100, 500, 2_000, UTC));
    }

    @Test public void invalidRuleInputsAreClampedToHardLimits() {
        assertEquals(1, PenancePolicy.clampStrikeCents(-10));
        assertEquals(10_000, PenancePolicy.clampStrikeCents(50_000));
        assertEquals(1_440, PenancePolicy.clampMercyMinutes(99_999));
        assertEquals(1, PenancePolicy.clampDetectionBatch(0));
        assertEquals(100, PenancePolicy.clampDetectionBatch(500));
    }

    private static PenanceEvent event(String id, long at, int cents,
            PenanceEvent.Status status) {
        return new PenanceEvent(id, at, at, cents, 1, status, "");
    }
}
