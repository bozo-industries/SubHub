package com.betasafe.app.penance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PayPalRequestPolicyTest {
    @Test public void manualAndAutomaticFlowsUseSeparateStableIdempotencyKeys() {
        String settlement = "0f503239-84c9-45ee-94d4-900bed63ba7f";
        assertEquals(settlement + "-create",
                PayPalRequestPolicy.createRequestId(settlement));
        assertEquals(settlement + "-capture",
                PayPalRequestPolicy.captureRequestId(settlement));
        assertEquals(settlement + "-auto",
                PayPalRequestPolicy.autoRequestId(settlement));
    }

    @Test public void onlyTransientHttpFailuresAreRetried() {
        assertTrue(PayPalRequestPolicy.isTransientStatus(408));
        assertTrue(PayPalRequestPolicy.isTransientStatus(429));
        assertTrue(PayPalRequestPolicy.isTransientStatus(500));
        assertTrue(PayPalRequestPolicy.isTransientStatus(503));
        assertFalse(PayPalRequestPolicy.isTransientStatus(400));
        assertFalse(PayPalRequestPolicy.isTransientStatus(401));
        assertFalse(PayPalRequestPolicy.isTransientStatus(422));
    }
}
