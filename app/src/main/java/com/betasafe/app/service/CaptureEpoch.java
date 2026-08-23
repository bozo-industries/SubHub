package com.betasafe.app.service;

import java.util.concurrent.atomic.AtomicLong;

/** Invalidates asynchronous screenshot results whenever the foreground capture cycle changes. */
final class CaptureEpoch {
    private final AtomicLong value = new AtomicLong();

    long token() {
        return value.get();
    }

    void invalidate() {
        value.incrementAndGet();
    }

    boolean accepts(long token, boolean serviceRunning, boolean recognitionActive) {
        return serviceRunning && recognitionActive && token == value.get();
    }
}
