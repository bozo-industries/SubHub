package com.betasafe.app.appmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.betasafe.app.security.HardcoreModeManager;

/** Restores persisted intent after boot without silently starting MediaProjection. */
public final class BootReceiver extends BroadcastReceiver {
    public static final String ACTION_DISARM = "com.betasafe.app.action.DISARM_APP_MODE";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        AppModeManager mode = new AppModeManager(context);
        if (ACTION_DISARM.equals(action)) {
            mode.setArmed(false);
            WeeklyScheduleManager schedule = new WeeklyScheduleManager(context);
            WeeklyScheduleManager.Settings settings = schedule.load();
            schedule.save(false, settings.dayMask, settings.startMinute, settings.endMinute);
            ProtectionSessionManager.markMediaProjectionExplicitlyStopped(context);
            ResumeNotificationManager.cancel(context);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            mode.applyBootPolicy();
            new HardcoreModeManager(context).applyBootPolicy();
        }
        ResumeNotificationManager.show(context);
    }
}
