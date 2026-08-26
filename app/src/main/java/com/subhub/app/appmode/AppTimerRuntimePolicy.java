package com.subhub.app.appmode;

/** Runtime boundary for app-limit accounting and enforcement. */
public final class AppTimerRuntimePolicy {
    private AppTimerRuntimePolicy() {}

    public static boolean shouldRun(boolean protectionArmed, boolean limitsEnabled) {
        return protectionArmed && limitsEnabled;
    }
}
