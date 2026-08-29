package com.subhub.app.permissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Decides which configured Home features still need an Android grant. */
public final class HomePermissionPolicy {
    public enum Requirement {
        ACCESSIBILITY,
        OVERLAY,
        NOTIFICATIONS,
        DEVICE_ADMIN
    }

    private HomePermissionPolicy() {}

    public static List<Requirement> missing(
            boolean runtimeFeatureEnabled,
            boolean screenRecordingCensorEnabled,
            boolean notificationPermissionApplies,
            boolean accessibilityReady,
            boolean overlayReady,
            boolean notificationsReady,
            boolean hardcoreRequested,
            boolean deviceAdminReady) {
        List<Requirement> result = new ArrayList<>();
        // Hardcore's App Info guard is implemented by the accessibility service even when
        // censoring, limits and subliminals are all disabled. Device Admin only supplies
        // Android's native uninstall friction; it cannot guard Clear Storage by itself.
        // Ask for notifications first so the guidance toast remains visible while Android's
        // Accessibility page opens on the next step.
        if (runtimeFeatureEnabled && notificationPermissionApplies && !notificationsReady) {
            result.add(Requirement.NOTIFICATIONS);
        }
        if ((runtimeFeatureEnabled || hardcoreRequested) && !accessibilityReady) {
            result.add(Requirement.ACCESSIBILITY);
        }
        if (screenRecordingCensorEnabled && !overlayReady) {
            result.add(Requirement.OVERLAY);
        }
        if (hardcoreRequested && !deviceAdminReady) {
            result.add(Requirement.DEVICE_ADMIN);
        }
        return Collections.unmodifiableList(result);
    }
}
