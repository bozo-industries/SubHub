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

    static String maskedPayer(String email, String accountId) {
        String cleanEmail = email == null ? "" : email.trim();
        int at = cleanEmail.indexOf('@');
        if (at > 0 && at < cleanEmail.length() - 1) {
            String local = cleanEmail.substring(0, at);
            String domain = cleanEmail.substring(at + 1);
            int dot = domain.lastIndexOf('.');
            String domainName = dot > 0 ? domain.substring(0, dot) : domain;
            String suffix = dot > 0 ? domain.substring(dot) : "";
            return maskPart(local) + "@" + maskPart(domainName) + suffix;
        }
        String cleanAccount = accountId == null ? "" : accountId.trim();
        if (cleanAccount.length() > 4) {
            return "PayPal ••••" + cleanAccount.substring(cleanAccount.length() - 4);
        }
        return cleanAccount.isEmpty() ? "" : "PayPal " + cleanAccount;
    }

    private static String maskPart(String value) {
        if (value == null || value.isEmpty()) return "•";
        if (value.length() == 1) return value + "•••";
        return value.substring(0, 1) + "•••";
    }
}
