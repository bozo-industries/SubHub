package com.subhub.app.appmode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AppTimerRuntimePolicyTest {
    @Test public void armedProtectionWithLimitsRunsTimers() {
        assertTrue(AppTimerRuntimePolicy.shouldRun(true, true));
    }

    @Test public void disarmedProtectionNeverRunsTimers() {
        assertFalse(AppTimerRuntimePolicy.shouldRun(false, true));
    }

    @Test public void disabledLimitsNeverRunTimers() {
        assertFalse(AppTimerRuntimePolicy.shouldRun(true, false));
    }
}
