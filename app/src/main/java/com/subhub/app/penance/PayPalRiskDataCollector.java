package com.subhub.app.penance;

import android.content.Context;

import com.paypal.android.corepayments.CoreConfig;
import com.paypal.android.fraudprotection.PayPalDataCollector;
import com.paypal.android.fraudprotection.PayPalDataCollectorRequest;

/** Thin wrapper around PayPal's supported Android Magnes risk-data collector. */
final class PayPalRiskDataCollector {
    private final Context context;

    PayPalRiskDataCollector(Context context) {
        this.context = context.getApplicationContext();
    }

    String collect(String clientId, PayPalEnvironment environment) {
        if (clientId == null || clientId.trim().isEmpty()) return "";
        PayPalEnvironment selected = environment == null
                ? PayPalEnvironment.SANDBOX : environment;
        CoreConfig config = new CoreConfig(
                clientId.trim(), clientId.trim(), selected.coreEnvironment());
        PayPalDataCollector collector = new PayPalDataCollector(config);
        return collector.collectDeviceData(context, new PayPalDataCollectorRequest(false));
    }
}
