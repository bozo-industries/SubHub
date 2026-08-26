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
        if (runtimeFeatureEnabled && !accessibilityReady) {
            result.add(Requirement.ACCESSIBILITY);
        }
        if (screenRecordingCensorEnabled && !overlayReady) {
            result.add(Requirement.OVERLAY);
        }
        if (runtimeFeatureEnabled && notificationPermissionApplies && !notificationsReady) {
            result.add(Requirement.NOTIFICATIONS);
        }
        if (hardcoreRequested && !deviceAdminReady) {
            result.add(Requirement.DEVICE_ADMIN);
        }
        return Collections.unmodifiableList(result);
    }
}
