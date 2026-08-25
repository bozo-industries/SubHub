package com.subhub.app.penance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs a due saved-wallet settlement outside the UI. */
public final class HardcoreAutoPayReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !HardcoreAutoPayManager.ACTION_AUTO_PAY.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        HardcoreAutoPayEngine.run(context, pending::finish);
    }
}
