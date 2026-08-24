package com.subhub.app.appmode;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;

import com.subhub.app.settings.SettingsRepository;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.stats.StatsRepository;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persisted user intent for always-on and selected-app recognition. */
public final class AppModeManager {
    public static final String KEY_ARMED = "app_mode_armed";
    public static final String KEY_MODE = "app_mode_kind";
    public static final String KEY_MODE_EXPLICIT = "app_mode_kind_explicit_v2";
    public static final String KEY_SELECTED_PACKAGES = "app_mode_selected_packages";
    public static final String KEY_TIMER_PACKAGES = "app_timer_selected_packages";
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
        boolean wasArmed = isArmed();
        preferences.edit().putBoolean(KEY_ARMED, armed).commit();
        syncProtectionSession(wasArmed, armed);
    }

    public AppModePolicy.Mode getMode() {
        String storedMode = preferences.getString(KEY_MODE, MODE_ALWAYS);
        if (preferences.getBoolean(KEY_MODE_EXPLICIT, false)) {
            return MODE_SELECTED.equals(storedMode)
                    ? AppModePolicy.Mode.SELECTED_APPS : AppModePolicy.Mode.ALWAYS;
        }
        // Per-app Censor checkboxes are the most specific intent. Older builds saved them
        // independently from this mode and could leave a stale "always" value behind, causing
        // recognition in every app despite an explicit X-only (or similar) assignment.
        if (!getSelectedPackages().isEmpty()) return AppModePolicy.Mode.SELECTED_APPS;
        return MODE_SELECTED.equals(storedMode)
                ? AppModePolicy.Mode.SELECTED_APPS : AppModePolicy.Mode.ALWAYS;
    }

    public Set<String> getSelectedPackages() {
        Set<String> stored = preferences.getStringSet(KEY_SELECTED_PACKAGES,
                Collections.emptySet());
        return AppModePolicy.sanitizePackages(stored);
    }

    public Set<String> getTimerPackages() {
        Set<String> stored = preferences.getStringSet(KEY_TIMER_PACKAGES, null);
        // Existing installs used one shared selection. Preserve that choice until the
        // controller explicitly saves the new two-column app picker.
        return stored == null ? getSelectedPackages() : AppModePolicy.sanitizePackages(stored);
    }

    public void saveAppSelections(Set<String> censorPackages, Set<String> timerPackages) {
        Set<String> cleanCensorPackages = AppModePolicy.sanitizePackages(censorPackages);
        SharedPreferences.Editor editor = preferences.edit()
                .putStringSet(KEY_SELECTED_PACKAGES,
                        new LinkedHashSet<>(cleanCensorPackages))
                .putStringSet(KEY_TIMER_PACKAGES,
                        new LinkedHashSet<>(AppModePolicy.sanitizePackages(timerPackages)));
        if (!cleanCensorPackages.isEmpty()) {
            editor.putString(KEY_MODE, MODE_SELECTED).putBoolean(KEY_MODE_EXPLICIT, true);
        }
        editor.commit();
    }

    public void save(boolean armed, AppModePolicy.Mode mode, Set<String> selectedPackages) {
        boolean wasArmed = isArmed();
        preferences.edit()
                .putBoolean(KEY_ARMED, armed)
                .putString(KEY_MODE,
                        mode == AppModePolicy.Mode.SELECTED_APPS ? MODE_SELECTED : MODE_ALWAYS)
                .putBoolean(KEY_MODE_EXPLICIT, true)
                .putStringSet(KEY_SELECTED_PACKAGES,
                        new LinkedHashSet<>(AppModePolicy.sanitizePackages(selectedPackages)))
                .commit();
        syncProtectionSession(wasArmed, armed);
    }

    public boolean shouldRecognize(String foregroundPackage) {
        if (!new FeatureModuleManager(context).isCensorEnabled()) return false;
        return AppModePolicy.shouldRecognize(isEffectivelyArmed(System.currentTimeMillis()),
                getMode(), getSelectedPackages(),
                foregroundPackage, context.getPackageName(), inputMethodPackage());
    }

    public boolean isEffectivelyArmed(long nowMillis) {
        return isArmed();
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

    /** App Mode already persists its exact armed/disarmed state across process and device restarts. */
    public void applyBootPolicy() {
        // Intentionally preserve KEY_ARMED. Android permissions remain independently revocable.
    }

    public String inputMethodPackage() {
        String setting = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (setting == null) return "";
        int separator = setting.indexOf('/');
        return separator < 0 ? setting : setting.substring(0, separator);
    }

    /** One statistics session follows the explicit armed lifetime, not foreground app changes. */
    private void syncProtectionSession(boolean wasArmed, boolean armed) {
        if (wasArmed == armed) return;
        StatsRepository stats = new StatsRepository(context);
        if (armed) stats.startSession();
        else stats.endSession();
    }
}
