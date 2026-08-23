package com.betasafe.app.penance;

import java.util.Locale;

/** Pure decisions for vault readiness and account-capability errors. */
final class PayPalVaultPolicy {
    private PayPalVaultPolicy() {}

    static PayPalCredentialStore.VaultStatus resultStatus(
            String rawStatus, String vaultId, String customerId) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        boolean idsReady = vaultId != null && !vaultId.isEmpty()
                && customerId != null && !customerId.isEmpty();
        if ("VAULTED".equals(status) && idsReady) {
            return PayPalCredentialStore.VaultStatus.READY;
        }
        if ("APPROVED".equals(status) || "VAULTED".equals(status)) {
            return PayPalCredentialStore.VaultStatus.PENDING;
        }
        return PayPalCredentialStore.VaultStatus.REQUESTED;
    }

    static boolean isUnavailableIssue(String issue) {
        String normalized = issue == null ? "" : issue.toUpperCase(Locale.ROOT);
        boolean vaultRelated = normalized.contains("VAULT")
                || normalized.contains("BILLING_AGREEMENT")
                || normalized.contains("PAYMENT_TOKEN");
        boolean unavailable = normalized.contains("NOT_ENABLED")
                || normalized.contains("NOT_ELIGIBLE")
                || normalized.contains("UNAVAILABLE")
                || normalized.contains("PERMISSION_DENIED");
        return vaultRelated && unavailable;
    }
}
