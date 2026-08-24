package com.subhub.app.penance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PayPalVaultPolicyTest {
    @Test public void vaultedRequiresBothEnvironmentBoundIds() {
        assertEquals(PayPalCredentialStore.VaultStatus.READY,
                PayPalVaultPolicy.resultStatus("VAULTED", "vault-1", "customer-1"));
        assertEquals(PayPalCredentialStore.VaultStatus.PENDING,
                PayPalVaultPolicy.resultStatus("VAULTED", "vault-1", ""));
    }

    @Test public void asynchronousApprovalStaysPending() {
        assertEquals(PayPalCredentialStore.VaultStatus.PENDING,
                PayPalVaultPolicy.resultStatus("APPROVED", "", ""));
        assertEquals(PayPalCredentialStore.VaultStatus.REQUESTED,
                PayPalVaultPolicy.resultStatus("", "", ""));
    }

    @Test public void onlyVaultCapabilityIssuesMarkUnavailable() {
        assertTrue(PayPalVaultPolicy.isUnavailableIssue("NOT_ENABLED_FOR_VAULTING"));
        assertTrue(PayPalVaultPolicy.isUnavailableIssue(
                "NOT_ENABLED_TO_VAULT_PAYMENT_SOURCE"));
        assertTrue(PayPalVaultPolicy.isUnavailableIssue(
                "BILLING_AGREEMENT_NOT_ELIGIBLE"));
        assertFalse(PayPalVaultPolicy.isUnavailableIssue("INSTRUMENT_DECLINED"));
        assertFalse(PayPalVaultPolicy.isUnavailableIssue("PERMISSION_DENIED"));
    }

    @Test public void vaultPayloadErrorsRetryAsStandardCheckout() {
        assertTrue(PayPalVaultPolicy.shouldRetryWithoutVault(422,
                "INVALID_PARAMETER_VALUE /payment_source/paypal/attributes/vault/usage_pattern"));
        assertTrue(PayPalVaultPolicy.shouldRetryWithoutVault(403,
                "PERMISSION_DENIED save_payment_method"));
        assertFalse(PayPalVaultPolicy.shouldRetryWithoutVault(422,
                "CURRENCY_NOT_SUPPORTED EUR"));
        assertFalse(PayPalVaultPolicy.shouldRetryWithoutVault(401,
                "VAULT_NOT_ENABLED"));
    }

    @Test public void payerIdentityIsMaskedBeforeDisplay() {
        assertEquals("a•••@e•••.com",
                PayPalVaultPolicy.maskedPayer("alice@example.com", ""));
        assertEquals("PayPal ••••5678",
                PayPalVaultPolicy.maskedPayer("", "payer-12345678"));
    }

    @Test public void onlyApprovedSetupTokensCanBeExchanged() {
        assertTrue(PayPalVaultPolicy.isSetupApproved("APPROVED"));
        assertTrue(PayPalVaultPolicy.isSetupApproved(" vaulted "));
        assertFalse(PayPalVaultPolicy.isSetupApproved("PAYER_ACTION_REQUIRED"));
        assertFalse(PayPalVaultPolicy.isSetupApproved("CREATED"));
    }
}
