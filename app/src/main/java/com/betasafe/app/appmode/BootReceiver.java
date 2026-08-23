package com.betasafe.app.appmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.betasafe.app.commitment.CommitmentManager;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.security.HardcoreModeManager;
import com.betasafe.app.penance.HardcoreAutoPayManager;

/** Restores persisted intent after boot without silently starting MediaProjection. */
public final class BootReceiver extends BroadcastReceiver {
    public static final String ACTION_DISARM = "com.betasafe.app.action.DISARM_APP_MODE";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        AppModeManager mode = new AppModeManager(context);
        if (ACTION_DISARM.equals(action)) {
            if (!CommitmentManager.mayStopProtection(context)) {
                CommitmentManager.reinforceProtection(context);
                return;
            }
            mode.setArmed(false);
            ProtectionSessionManager.markMediaProjectionExplicitlyStopped(context);
            ResumeNotificationManager.cancel(context);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            ControllerPinManager.enterSubMode();
            mode.applyBootPolicy();
            CommitmentManager.applyBootPolicy(context);
            new HardcoreModeManager(context).applyBootPolicy();
            HardcoreAutoPayManager.schedule(context);
        }
        ResumeNotificationManager.show(context);
    }
}
