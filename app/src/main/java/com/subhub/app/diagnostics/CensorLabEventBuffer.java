package com.subhub.app.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, non-blocking event handoff used only while an explicit lab session is active. */
final class CensorLabEventBuffer {
    static final int DEFAULT_CAPACITY = 50_000;

    private final int capacity;
    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();

    CensorLabEventBuffer() {
        this(DEFAULT_CAPACITY);
    }

    CensorLabEventBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    boolean offer(Event event) {
        if (event == null) return false;
        int slot = accepted.getAndIncrement();
        if (slot >= capacity) {
            accepted.decrementAndGet();
            dropped.incrementAndGet();
            return false;
        }
        events.offer(event);
        return true;
    }

    List<Event> snapshot() {
        return new ArrayList<>(events);
    }

    int size() {
        return accepted.get();
    }

    long dropped() {
        return dropped.get();
    }

    static final class Event {
        final long sequence;
        final long elapsedNanos;
        final long wallMillis;
        final String thread;
        final String tag;
        final String message;

        Event(long sequence, long elapsedNanos, long wallMillis, String thread,
                String tag, String message) {
            this.sequence = sequence;
            this.elapsedNanos = elapsedNanos;
            this.wallMillis = wallMillis;
            this.thread = thread;
            this.tag = tag;
            this.message = message;
        }
    }
}
