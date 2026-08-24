package com.subhub.app.appmode;

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
            return !foreground.isEmpty() && !isSystemSurface(foreground);
        }
        return !foreground.isEmpty() && selectedPackages != null
                && selectedPackages.contains(foreground);
    }

    public static boolean shouldAcceptForegroundEvent(
            String packageName, String className, String ownPackage, String inputMethodPackage) {
        String candidate = clean(packageName);
        if (candidate.isEmpty() || candidate.equals(clean(inputMethodPackage))
                || isTransientSystemSurface(candidate)) return false;
        if (!candidate.equals(clean(ownPackage))) return true;
        // Accessibility overlays are hosted by this package too. Only Activity window events are
        // genuine foreground transitions; package-owned overlay views must not stop recognition.
        String candidateClass = clean(className);
        return candidateClass.startsWith(clean(ownPackage) + ".")
                && candidateClass.endsWith("Activity");
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

    /**
     * Windows owned by Android chrome do not replace the app underneath them. Treating a
     * notification shade, heads-up notification, volume panel, or permission sheet as a real
     * foreground transition suspends selected-app recognition without a guaranteed return event.
     */
    private static boolean isTransientSystemSurface(String packageName) {
        return isSystemSurface(packageName)
                || "com.android.permissioncontroller".equals(packageName)
                || "com.google.android.permissioncontroller".equals(packageName);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
