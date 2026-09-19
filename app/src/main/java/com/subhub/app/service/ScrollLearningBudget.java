package com.subhub.app.service;

/** Single-worker-owned probe budget, including failed reads. Caller owns worker cancellation. */
final class ScrollLearningBudget {
    private static final long BURST_MS = 6000, COOLDOWN_MS = 30000, WORK_MS = 256;
    private static final int MAX_ATTEMPTS = 96;
    private long startedAt = -1, cooldownUntil, workMs;
    private int attempts;
    private long pendingToken, nextToken;

    boolean begin(boolean censoringActive, long now) {
        if (!censoringActive || now < 0 || now < cooldownUntil || pendingToken != 0) return false;
        if (startedAt >= 0) return true;
        startedAt = now; attempts = 0; workMs = 0;
        return true;
    }

    long reserve(boolean censoringActive, long now) {
        if (!censoringActive) { if (startedAt >= 0) stop(now); return 0; }
        if (pendingToken != 0 || startedAt < 0 || now < startedAt) return 0;
        if (now - startedAt >= BURST_MS || attempts >= MAX_ATTEMPTS || workMs >= WORK_MS) {
            stop(now);
            return 0;
        }
        attempts++;
        pendingToken = ++nextToken;
        return pendingToken;
    }

    void finish(long token, long elapsedMs, long now) {
        if (token == 0 || token != pendingToken) return;
        pendingToken = 0;
        if (elapsedMs < 0) { stop(now); return; }
        workMs = elapsedMs > Long.MAX_VALUE - workMs ? Long.MAX_VALUE : workMs + elapsedMs;
        if (elapsedMs > 16 || workMs >= WORK_MS) stop(now);
    }

    void stop(long now) {
        startedAt = -1;
        pendingToken = 0;
        cooldownUntil = Math.max(cooldownUntil, Math.max(0, now) + COOLDOWN_MS);
    }

    int attempts() { return attempts; }
    long workMillis() { return workMs; }
    long cooldownUntil() { return cooldownUntil; }
}
