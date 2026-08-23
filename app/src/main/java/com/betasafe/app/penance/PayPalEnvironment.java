package com.betasafe.app.penance;

import com.paypal.android.corepayments.CoreEnvironment;

/** The selected PayPal authorization boundary. */
public enum PayPalEnvironment {
    SANDBOX("https://api-m.sandbox.paypal.com", "api-m.sandbox.paypal.com",
            CoreEnvironment.SANDBOX),
    LIVE("https://api-m.paypal.com", "api-m.paypal.com", CoreEnvironment.LIVE);

    private final String apiRoot;
    private final String apiHost;
    private final CoreEnvironment coreEnvironment;

    PayPalEnvironment(String apiRoot, String apiHost, CoreEnvironment coreEnvironment) {
        this.apiRoot = apiRoot;
        this.apiHost = apiHost;
        this.coreEnvironment = coreEnvironment;
    }

    String apiRoot() { return apiRoot; }
    String apiHost() { return apiHost; }
    CoreEnvironment coreEnvironment() { return coreEnvironment; }

    static PayPalEnvironment stored(String value) {
        try { return valueOf(value == null ? "" : value); }
        catch (IllegalArgumentException ignored) { return SANDBOX; }
    }
}
