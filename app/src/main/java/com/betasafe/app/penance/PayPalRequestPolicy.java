package com.betasafe.app.penance;

/** Pure policy for PayPal idempotency keys and bounded transient retry decisions. */
public final class PayPalRequestPolicy {
    private PayPalRequestPolicy() {}

    public static String createRequestId(String settlementId) {
        return settlementId + "-create";
    }

    public static String captureRequestId(String settlementId) {
        return settlementId + "-capture";
    }

    public static boolean isTransientStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }
}
