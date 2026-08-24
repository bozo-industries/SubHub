package com.subhub.app.penance;

import android.content.Context;

import com.subhub.app.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Converts one immutable local settlement into matching PayPal order rows. */
final class PayPalOrderDetails {
    private PayPalOrderDetails() {}

    static List<PayPalOrdersClient.OrderItem> from(
            Context context, PenanceManager.Settlement settlement) {
        List<PayPalOrdersClient.OrderItem> items = new ArrayList<>();
        if (context == null || settlement == null) return items;
        DateFormat date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (PenanceEvent event : settlement.getEvents()) {
            String label = label(context, event.getInfraction());
            String name = context.getString(R.string.paypal_order_item_name,
                    label, event.getStrikeCount());
            String description = context.getString(R.string.paypal_order_item_when,
                    date.format(new Date(event.getCreatedAtMillis())));
            items.add(new PayPalOrdersClient.OrderItem(
                    name, description, event.getAmountCents()));
        }
        return items;
    }

    private static String label(Context context, PenanceInfraction infraction) {
        if (infraction == PenanceInfraction.PAID_PAUSE) {
            return context.getString(R.string.paid_pause_history_label);
        }
        if (infraction == PenanceInfraction.CENSORED_DWELL) {
            return context.getString(R.string.penance_history_dwell);
        }
        if (infraction == PenanceInfraction.CENSORED_TAP) {
            return context.getString(R.string.penance_history_tap);
        }
        if (infraction == PenanceInfraction.WATCHED_APP_OPEN) {
            return context.getString(R.string.penance_history_app_open);
        }
        return context.getString(R.string.penance_history_detection);
    }
}
