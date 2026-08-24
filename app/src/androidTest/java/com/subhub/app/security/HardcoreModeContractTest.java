package com.subhub.app.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.appmode.AppModeManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Ensures Hardcore Mode remains explicit, reversible, and policy-minimal. */
@RunWith(AndroidJUnit4.class)
public final class HardcoreModeContractTest {
    private Context context;
    private HardcoreModeManager manager;
    private AppModeManager appMode;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        manager = new HardcoreModeManager(context);
        appMode = new AppModeManager(context);
        appMode.setArmed(false);
        manager.cancelPendingActivation();
    }

    @After public void tearDown() {
        appMode.setArmed(false);
        manager.cancelPendingActivation();
    }

    @Test public void receiverIsGuardedAndDeclaresNoDestructivePolicies() throws Exception {
        ResolveInfo receiver = new ResolveInfo();
        receiver.activityInfo = context.getPackageManager().getReceiverInfo(
                manager.getAdminComponent(), PackageManager.GET_META_DATA);
        assertTrue(receiver.activityInfo.exported);
        assertEquals(Manifest.permission.BIND_DEVICE_ADMIN, receiver.activityInfo.permission);
        DeviceAdminInfo admin = new DeviceAdminInfo(context, receiver);
        int[] policies = {DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD,
                DeviceAdminInfo.USES_POLICY_WATCH_LOGIN,
                DeviceAdminInfo.USES_POLICY_RESET_PASSWORD,
                DeviceAdminInfo.USES_POLICY_FORCE_LOCK,
                DeviceAdminInfo.USES_POLICY_WIPE_DATA,
                DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD,
                DeviceAdminInfo.USES_ENCRYPTED_STORAGE,
                DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA,
                DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES};
        for (int policy : policies) assertFalse(admin.usesPolicy(policy));
    }

    @Test public void activationRequiresAndroidSystemApproval() {
        assertFalse(manager.isEnabled());
        manager.beginActivation();
        assertTrue(manager.isRequested());
        assertFalse(manager.isEnabled());
        Intent intent = manager.activationIntent();
        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.getAction());
        assertEquals(manager.getAdminComponent(),
                intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN));
        assertNotNull(intent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION));
        manager.cancelPendingActivation();
        assertFalse(manager.isRequested());
    }

    @Test public void enablingAdminDoesNotArmPreviouslyDisarmedProtection() {
        appMode.setArmed(false);

        manager.onAdminEnabled();

        assertFalse(appMode.isArmed());
    }

    @Test public void enablingAdminPreservesAlreadyArmedProtection() {
        appMode.setArmed(true);

        manager.onAdminEnabled();

        assertTrue(appMode.isArmed());
    }
}
