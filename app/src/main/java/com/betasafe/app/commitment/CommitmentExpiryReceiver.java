package com.betasafe.app.commitment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Ends protection when the selected pact timer expires. */
public final class CommitmentExpiryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (CommitmentManager.remainingMillis(context) <= 0L) {
            CommitmentManager.expire(context);
        }
    }
}
