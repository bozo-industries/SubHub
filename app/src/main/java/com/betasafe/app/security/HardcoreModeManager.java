package com.betasafe.app.security;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.betasafe.app.R;
import com.betasafe.app.appmode.AppModeManager;
import com.betasafe.app.appmode.ResumeNotificationManager;
import com.betasafe.app.settings.SettingsRepository;

/**
 * Explicit, reversible Device Admin friction for a consensual Hardcore Mode.
 *
 * <p>This class deliberately declares no destructive device policies. Active admin status only
 * adds Android's normal deactivate-before-uninstall step. It cannot grant capture, keep a process
 * alive, hide system settings, or prevent the user from revoking admin access.</p>
 */
public final class HardcoreModeManager {
    public static final String KEY_REQUESTED = "hardcore_mode_requested";

    private final Context context;
    private final SharedPreferences preferences;
    private final DevicePolicyManager policies;
    private final ComponentName admin;

    public HardcoreModeManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        policies = this.context.getSystemService(DevicePolicyManager.class);
        admin = new ComponentName(this.context, HardcoreModeReceiver.class);
    }

    public ComponentName getAdminComponent() {
        return admin;
    }

    public boolean isAdminActive() {
        return policies != null && policies.isAdminActive(admin);
    }

    public boolean isRequested() {
        return preferences.getBoolean(KEY_REQUESTED, false);
    }

    public boolean isEnabled() {
        return isRequested() && isAdminActive();
    }

    public Intent activationIntent() {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        context.getString(R.string.hardcore_admin_system_explanation));
    }

    public Intent adminSettingsIntent() {
        return new Intent(Settings.ACTION_SECURITY_SETTINGS);
    }

    public void beginActivation() {
        preferences.edit().putBoolean(KEY_REQUESTED, true).commit();
    }

    public boolean finishActivation() {
        boolean active = isAdminActive();
        preferences.edit().putBoolean(KEY_REQUESTED, active).commit();
        if (active) reinforceAutomaticMode();
        return active;
    }

    public void cancelPendingActivation() {
        if (!isAdminActive()) preferences.edit().putBoolean(KEY_REQUESTED, false).commit();
    }

    public void disable() {
        preferences.edit().putBoolean(KEY_REQUESTED, false).commit();
        if (policies != null && policies.isAdminActive(admin)) {
            policies.removeActiveAdmin(admin);
        }
    }

    public void onAdminEnabled() {
        preferences.edit().putBoolean(KEY_REQUESTED, true).commit();
        reinforceAutomaticMode();
    }

    public void onAdminDisabled() {
        preferences.edit().putBoolean(KEY_REQUESTED, false).commit();
    }

    /** Restores only the user's scanning intent; Android permissions remain independently revocable. */
    public void applyBootPolicy() {
        if (isEnabled()) reinforceAutomaticMode();
    }

    private void reinforceAutomaticMode() {
        AppModeManager mode = new AppModeManager(context);
        mode.save(true, mode.getMode(), true, mode.getSelectedPackages());
        ResumeNotificationManager.show(context);
    }
}
