package com.subhub.app.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HomePermissionPolicyTest {
    @Test public void noConfiguredRuntimeNeedsNoRuntimeGrants() {
        List<HomePermissionPolicy.Requirement> missing = HomePermissionPolicy.missing(
                false, false, true, false, false, false, false, false);
        assertTrue(missing.isEmpty());
    }

    @Test public void runtimeFeaturesNeedAccessibilityAndApplicableNotifications() {
        List<HomePermissionPolicy.Requirement> missing = HomePermissionPolicy.missing(
                true, false, true, false, true, false, false, false);
        assertEquals(Arrays.asList(
                HomePermissionPolicy.Requirement.ACCESSIBILITY,
                HomePermissionPolicy.Requirement.NOTIFICATIONS), missing);
    }

    @Test public void screenRecordingCensorAlsoNeedsOverlay() {
        List<HomePermissionPolicy.Requirement> missing = HomePermissionPolicy.missing(
                true, true, true, false, false, false, false, false);
        assertEquals(Arrays.asList(
                HomePermissionPolicy.Requirement.ACCESSIBILITY,
                HomePermissionPolicy.Requirement.OVERLAY,
                HomePermissionPolicy.Requirement.NOTIFICATIONS), missing);
    }

    @Test public void requestedHardcoreNeedsAccessibilityAndDeviceAdmin() {
        List<HomePermissionPolicy.Requirement> missing = HomePermissionPolicy.missing(
                false, false, false, false, true, true, true, false);
        assertEquals(Arrays.asList(
                HomePermissionPolicy.Requirement.ACCESSIBILITY,
                HomePermissionPolicy.Requirement.DEVICE_ADMIN), missing);
    }

    @Test public void requestedHardcoreStillNeedsAccessibilityWhenAdminIsAlreadyActive() {
        List<HomePermissionPolicy.Requirement> missing = HomePermissionPolicy.missing(
                false, false, false, false, true, true, true, true);
        assertEquals(Collections.singletonList(
                HomePermissionPolicy.Requirement.ACCESSIBILITY), missing);
    }

    @Test public void readyConfigurationHasNoMissingRequirements() {
        List<HomePermissionPolicy.Requirement> missing = HomePermissionPolicy.missing(
                true, true, true, true, true, true, true, true);
        assertTrue(missing.isEmpty());
    }
}
