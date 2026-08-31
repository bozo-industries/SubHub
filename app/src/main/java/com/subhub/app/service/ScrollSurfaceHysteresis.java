package com.subhub.app.service;

/** Keeps a proven scroll owner through transient low-confidence companion events. */
final class ScrollSurfaceHysteresis {
    static final int MAX_LOW_REUSE_EVENTS = 8;
    static final long MAX_LOW_REUSE_MS = 1_000L;

    enum Decision { USE_OBSERVED, REUSE_ACTIVE, DISABLE }

    private ScrollSurfaceHysteresis() {}

    static Decision decide(
            boolean observedCacheable,
            int observedWindowId,
            boolean activePresent,
            int activeWindowId,
            long nowUptime,
            long lastTrustedUptime,
            int lowReuseCount) {
        if (observedCacheable) return Decision.USE_OBSERVED;
        boolean withinTime = lastTrustedUptime > 0L && nowUptime >= lastTrustedUptime
                && nowUptime - lastTrustedUptime <= MAX_LOW_REUSE_MS;
        if (activePresent && observedWindowId >= 0 && observedWindowId == activeWindowId
                && withinTime && lowReuseCount < MAX_LOW_REUSE_EVENTS) {
            return Decision.REUSE_ACTIVE;
        }
        return Decision.DISABLE;
    }
}
