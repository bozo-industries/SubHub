package com.betasafe.app.security;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives only Android's guarded Device Admin lifecycle callbacks. */
public final class HardcoreModeReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        new HardcoreModeManager(context).onAdminEnabled();
    }

    @Override public void onDisabled(Context context, Intent intent) {
        new HardcoreModeManager(context).onAdminDisabled();
        super.onDisabled(context, intent);
    }
}
