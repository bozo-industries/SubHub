package com.subhub.app.penance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the protected state after a purchased pause expires. */
public final class PaidPauseExpiryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        new PaidPauseManager(context).finish();
    }
}
