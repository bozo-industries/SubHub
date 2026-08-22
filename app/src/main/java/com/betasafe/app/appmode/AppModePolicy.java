package com.betasafe.app.appmode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure foreground-package decision policy for battery-aware accessibility capture. */
public final class AppModePolicy {
    public enum Mode { ALWAYS, SELECTED_APPS }

    private AppModePolicy() {}

    public static boolean shouldRecognize(boolean armed, Mode mode, Set<String> selectedPackages,
            String foregroundPackage, String ownPackage, String inputMethodPackage) {
        if (!armed) return false;
        String foreground = clean(foregroundPackage);
        if (foreground.equals(clean(ownPackage)) || foreground.equals(clean(inputMethodPackage))) {
            return false;
        }
        if (mode == Mode.ALWAYS) {
            return foreground.isEmpty() || !isSystemSurface(foreground);
        }
        return !foreground.isEmpty() && selectedPackages != null
                && selectedPackages.contains(foreground);
    }

    public static boolean shouldAcceptForegroundEvent(
            String packageName, String className, String ownPackage, String inputMethodPackage) {
        String candidate = clean(packageName);
        if (candidate.isEmpty() || candidate.equals(clean(inputMethodPackage))) return false;
        if (!candidate.equals(clean(ownPackage))) return true;
        // Accessibility overlays are hosted by this package and can emit framework-class events.
        // Accept only our real Activity classes so an overlay cannot masquerade as an app switch.
        return clean(className).startsWith(clean(ownPackage) + ".");
    }

    public static Set<String> sanitizePackages(Set<String> packages) {
        if (packages == null || packages.isEmpty()) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<>();
        for (String value : packages) {
            String clean = clean(value);
            if (clean.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) result.add(clean);
        }
        return Collections.unmodifiableSet(result);
    }

    private static boolean isSystemSurface(String packageName) {
        return "android".equals(packageName) || "com.android.systemui".equals(packageName);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
