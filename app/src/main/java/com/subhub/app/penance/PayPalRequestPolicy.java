package com.subhub.app.penance;

/** Pure policy for PayPal idempotency keys and bounded transient retry decisions. */
public final class PayPalRequestPolicy {
    private PayPalRequestPolicy() {}

    public static String createRequestId(String settlementId) {
        return settlementId + "-create";
    }

    public static String standardCreateRequestId(String settlementId) {
        return settlementId + "-create-standard";
    }

    public static String captureRequestId(String settlementId) {
        return settlementId + "-capture";
    }

    public static String autoRequestId(String settlementId) {
        return settlementId + "-auto";
    }

    public static String vaultSetupRequestId(String nonce) {
        return nonce + "-vault-setup";
    }

    public static String vaultConfirmRequestId(String setupTokenId) {
        return setupTokenId + "-vault-confirm";
    }

    public static boolean hasRequestBody(String method, String body) {
        if (body == null || body.isEmpty()) return false;
        return !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
    }

    public static boolean isTransientStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }
}
