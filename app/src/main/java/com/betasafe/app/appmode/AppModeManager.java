package com.betasafe.app.appmode;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;

import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.settings.FeatureModuleManager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persisted user intent for always-on and selected-app recognition. */
public final class AppModeManager {
    public static final String KEY_ARMED = "app_mode_armed";
    public static final String KEY_MODE = "app_mode_kind";
    public static final String KEY_SELECTED_PACKAGES = "app_mode_selected_packages";
    public static final String KEY_AUTO_RESUME = "app_mode_auto_resume_boot";
    private static final String MODE_ALWAYS = "always";
    private static final String MODE_SELECTED = "selected";

    private final Context context;
    private final SharedPreferences preferences;

    public AppModeManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public boolean isArmed() {
        return preferences.getBoolean(KEY_ARMED, false);
    }

    public void setArmed(boolean armed) {
        preferences.edit().putBoolean(KEY_ARMED, armed).commit();
    }

    public AppModePolicy.Mode getMode() {
        return MODE_SELECTED.equals(preferences.getString(KEY_MODE, MODE_ALWAYS))
                ? AppModePolicy.Mode.SELECTED_APPS : AppModePolicy.Mode.ALWAYS;
    }

    public boolean isAutoResumeEnabled() {
        return preferences.getBoolean(KEY_AUTO_RESUME, true);
    }

    public Set<String> getSelectedPackages() {
        Set<String> stored = preferences.getStringSet(KEY_SELECTED_PACKAGES,
                Collections.emptySet());
        return AppModePolicy.sanitizePackages(stored);
    }

    public void save(boolean armed, AppModePolicy.Mode mode, boolean autoResume,
            Set<String> selectedPackages) {
        preferences.edit()
                .putBoolean(KEY_ARMED, armed)
                .putString(KEY_MODE,
                        mode == AppModePolicy.Mode.SELECTED_APPS ? MODE_SELECTED : MODE_ALWAYS)
                .putBoolean(KEY_AUTO_RESUME, autoResume)
                .putStringSet(KEY_SELECTED_PACKAGES,
                        new LinkedHashSet<>(AppModePolicy.sanitizePackages(selectedPackages)))
                .commit();
    }

    public boolean shouldRecognize(String foregroundPackage) {
        if (!new FeatureModuleManager(context).isCensorEnabled()) return false;
        return AppModePolicy.shouldRecognize(isEffectivelyArmed(System.currentTimeMillis()),
                getMode(), getSelectedPackages(),
                foregroundPackage, context.getPackageName(), inputMethodPackage());
    }

    public boolean isEffectivelyArmed(long nowMillis) {
        return isArmed() || new WeeklyScheduleManager(context).isActive(nowMillis);
    }

    public boolean isAccessibilityEnabled() {
        AccessibilityManager accessibility = context.getSystemService(AccessibilityManager.class);
        if (accessibility == null) return false;
        for (AccessibilityServiceInfo service : accessibility
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (service.getResolveInfo() != null
                    && context.getPackageName().equals(
                    service.getResolveInfo().serviceInfo.packageName)) return true;
        }
        return false;
    }

    /** A boot never grants capture authority. It only preserves or disarms prior user intent. */
    public void applyBootPolicy() {
        if (!isAutoResumeEnabled()) setArmed(false);
    }

    public String inputMethodPackage() {
        String setting = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (setting == null) return "";
        int separator = setting.indexOf('/');
        return separator < 0 ? setting : setting.substring(0, separator);
    }
}
