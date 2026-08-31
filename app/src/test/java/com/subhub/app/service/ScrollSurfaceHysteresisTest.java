package com.subhub.app.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ScrollSurfaceHysteresisTest {
    @Test public void stableObservationAlwaysWins() {
        assertEquals(ScrollSurfaceHysteresis.Decision.USE_OBSERVED,
                ScrollSurfaceHysteresis.decide(true, 7, true, 7,
                        2_000L, 1_500L, 0));
    }

    @Test public void lowConfidenceCompanionReusesProvenSameWindowSurface() {
        assertEquals(ScrollSurfaceHysteresis.Decision.REUSE_ACTIVE,
                ScrollSurfaceHysteresis.decide(false, 7, true, 7,
                        2_000L, 1_500L, 3));
    }

    @Test public void lowConfidenceDifferentWindowCannotReuseHistory() {
        assertEquals(ScrollSurfaceHysteresis.Decision.DISABLE,
                ScrollSurfaceHysteresis.decide(false, 8, true, 7,
                        2_000L, 1_500L, 0));
    }

    @Test public void lowConfidenceCannotCreateFirstSurface() {
        assertEquals(ScrollSurfaceHysteresis.Decision.DISABLE,
                ScrollSurfaceHysteresis.decide(false, 7, false, -1,
                        2_000L, 1_500L, 0));
    }

    @Test public void lowConfidenceGraceExpiresByTime() {
        assertEquals(ScrollSurfaceHysteresis.Decision.DISABLE,
                ScrollSurfaceHysteresis.decide(false, 7, true, 7,
                        2_501L, 1_500L, 0));
    }

    @Test public void lowConfidenceGraceExpiresByEventCount() {
        assertEquals(ScrollSurfaceHysteresis.Decision.DISABLE,
                ScrollSurfaceHysteresis.decide(false, 7, true, 7,
                        2_000L, 1_500L, ScrollSurfaceHysteresis.MAX_LOW_REUSE_EVENTS));
    }
}
