package com.subhub.app.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded worker-owned calibration observer. It has no renderer/camera authority. */
final class AutomaticScrollLearningObserver implements AutoCloseable {
    interface Clock { long now(); }
    interface Source {
        boolean active(Scope scope);
        /** Consumes no event ownership; returned anchors transfer to the observer. */
        Acquired acquire(Event event, long deadline);
    }
    interface Store {
        ScrollCalibrationLearner.Profile candidate(ScrollLearningKey key);
        boolean save(ScrollCalibrationLearner.Profile profile, boolean durable);
        void remove(ScrollLearningKey key);
    }

    static final class Scope {
        final String packageName;
        final long captureEpoch, documentEpoch, producer;
        final int windowId, width, height, densityDpi, rotation, refreshMilliHz;
        final ScrollLearningKey.Axis axis;
        final ScrollLearningKey.Evidence evidence;
        Scope(String packageName, long captureEpoch, long documentEpoch, long producer,
                int windowId, int width, int height, int densityDpi, int rotation,
                int refreshMilliHz, ScrollLearningKey.Axis axis, ScrollLearningKey.Evidence evidence) {
            this.packageName = packageName; this.captureEpoch = captureEpoch;
            this.documentEpoch = documentEpoch; this.producer = producer; this.windowId = windowId;
            this.width = width; this.height = height; this.densityDpi = densityDpi;
            this.rotation = rotation; this.refreshMilliHz = refreshMilliHz;
            this.axis = axis; this.evidence = evidence;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Scope)) return false;
            Scope value = (Scope) other;
            return Objects.equals(packageName, value.packageName) && captureEpoch == value.captureEpoch
                    && documentEpoch == value.documentEpoch && producer == value.producer
                    && windowId == value.windowId && width == value.width && height == value.height
                    && densityDpi == value.densityDpi && rotation == value.rotation
                    && refreshMilliHz == value.refreshMilliHz && axis == value.axis && evidence == value.evidence;
        }
        @Override public int hashCode() {
            return Objects.hash(packageName, captureEpoch, documentEpoch, producer, windowId,
                    width, height, densityDpi, rotation, refreshMilliHz, axis, evidence);
        }
    }

    /** Optional platform attachment lives only in the bounded queue and is always closed. */
    static class Event implements AutoCloseable {
        final Scope scope;
        final long gesture, time, received;
        final double delta;
        Event(Scope scope, long gesture, long time, long received, double delta) {
            this.scope = scope; this.gesture = gesture; this.time = time;
            this.received = received; this.delta = delta;
        }
        @Override public void close() { }
    }

    static final class Acquired {
        final ScrollLearningKey key;
        final boolean durable;
        final List<AsyncViewportAnchorSampler.Anchor> anchors;
        Acquired(ScrollLearningKey key, boolean durable, List<AsyncViewportAnchorSampler.Anchor> anchors) {
            this.key = key; this.durable = durable; this.anchors = anchors;
        }
    }

    private static final class Interval {
        final long start, end, received, gesture;
        final double delta;
        Interval(long start, Event event) {
            this.start = start; end = event.time; received = event.received;
            gesture = event.gesture; delta = event.delta;
        }
    }

    private final Clock clock;
    private final AsyncViewportAnchorSampler.Worker worker;
    private final Source source;
    private final Store store;
    private final ArrayDeque<Event> queue = new ArrayDeque<>();
    private final ArrayDeque<Interval> pending = new ArrayDeque<>();
    private final ScrollLearningBudget budget = new ScrollLearningBudget();
    private final ViewportAnchorGeometry geometry = new ViewportAnchorGeometry();
    private final ScrollReferenceAligner aligner = new ScrollReferenceAligner();
    private final ScrollCalibrationLearner learner = new ScrollCalibrationLearner();
    private final AtomicLong sourceFailures = new AtomicLong();
    private List<AsyncViewportAnchorSampler.Anchor> anchors = Collections.emptyList();
    private volatile boolean closed;
    private volatile Scope latestScope;
    private volatile Handoff handoff;
    private volatile long invalidationGeneration;
    private long observedInvalidation;
    private volatile String diagnostics = "{\"schemaVersion\":1,\"state\":\"IDLE\",\"applied\":false}";
    private boolean drainQueued, invalidationQueued, queueGap, durable;
    private Scope scope;
    private ScrollLearningKey key;
    private long fence, tickGeneration, lastTime = -1, lastGesture, lastReceived;
    private long queueDrops, acquisitions, failedAcquisitions, reads, readFailures, alignmentRejects;
    private long storeFailures, saved, maxReadMs;
    private long lastValidatedReference = -1;

    AutomaticScrollLearningObserver(Clock clock, AsyncViewportAnchorSampler.Worker worker,
            Source source, Store store) {
        this.clock = clock; this.worker = worker; this.source = source; this.store = store;
    }

    synchronized void offer(Event event) {
        if (closed || event == null || event.scope == null || !sourceActive(event.scope)) {
            if (event != null) event.close();
            return;
        }
        latestScope = event.scope;
        if (queue.size() == 8) { queue.removeFirst().close(); queueDrops++; queueGap = true; }
        queue.addLast(event);
        if (!drainQueued) {
            drainQueued = true;
            worker.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            Event event;
            synchronized (this) {
                if (queueGap) { lastTime = -1; pending.clear(); queueGap = false; }
                event = queue.pollFirst();
                if (event == null) { drainQueued = false; return; }
            }
            try { consume(event); }
            catch (RuntimeException failure) {
                readFailures++; releaseAnchors(); learner.disable(); key = null; lastTime = -1;
                budget.stop(clock.now());
            }
            finally { event.close(); publish(); }
        }
    }

    private boolean active() {
        return !closed && observedInvalidation == invalidationGeneration && scope != null
                && scope.equals(latestScope) && sourceActive(scope);
    }

    private boolean sourceActive(Scope value) {
        try { return source.active(value); }
        catch (RuntimeException failure) { sourceFailures.incrementAndGet(); return false; }
    }

    private void consume(Event event) {
        if (closed || !event.scope.equals(latestScope) || !sourceActive(event.scope)) return;
        resetIfInvalidated();
        if (!event.scope.equals(scope)) {
            releaseAnchors(); pending.clear(); learner.disable();
            scope = event.scope; key = null; fence++; lastTime = -1;
        }
        long now = clock.now();
        if (event.time < 0 || event.received < event.time || event.received > now
                || now - event.time > 500 || event.time <= lastTime || event.gesture <= 0
                || !Double.isFinite(event.delta)) return;
        lastReceived = event.received;
        if (anchors.isEmpty()) acquire(event);
        if (active() && key != null && lastTime >= 0 && lastGesture == event.gesture
                && event.time - lastTime <= 300) {
            if (pending.size() == 8) { pending.removeFirst(); alignmentRejects++; }
            pending.addLast(new Interval(lastTime, event));
        }
        lastTime = event.time; lastGesture = event.gesture;
        alignPending();
    }

    private void acquire(Event event) {
        long start = clock.now();
        if (!budget.begin(active(), start)) return;
        long ticket = budget.reserve(active(), start);
        if (ticket == 0) return;
        acquisitions++;
        Acquired acquired = null;
        boolean accepted = false;
        try {
            acquired = source.acquire(event, start + 16);
            if (!active() || acquired == null || acquired.key == null
                    || acquired.anchors == null || acquired.anchors.size() != 3
                    || clock.now() - start > 16) return;
            List<ViewportAnchorGeometry.Bounds> bounds = read(acquired.anchors, start + 16);
            if (!active() || clock.now() - start > 16) return;
            if (!acquired.key.equals(key)) {
                key = acquired.key; durable = acquired.durable;
                learner.begin(true, key, fence);
            }
            // Reacquisition establishes a new independent origin, never a predicted camera pose.
            geometry.reset(bounds, 0, 0, fence, start);
            aligner.begin(key, fence); pending.clear(); lastTime = -1;
            anchors = acquired.anchors;
            accepted = true;
        } catch (RuntimeException failure) {
            readFailures++;
        } finally {
            long now = clock.now();
            maxReadMs = Math.max(maxReadMs, now - start);
            budget.finish(ticket, now - start, now);
            if (!accepted) {
                failedAcquisitions++;
                if (acquired != null && acquired.anchors != null) closeAnchors(acquired.anchors);
                budget.stop(now);
            }
        }
        if (accepted) {
            if (!active()) { releaseAnchors(); return; }
            if (durable && learner.state() == ScrollCalibrationLearner.State.LEARNING
                    && learner.acceptedSamples() == 0) {
                try { learner.useCandidate(store.candidate(key)); }
                catch (RuntimeException failure) { storeFailures++; }
            }
            long generation = ++tickGeneration;
            worker.schedule(() -> tick(generation), 16);
        }
    }

    private void tick(long generation) {
        if (generation != tickGeneration) return;
        long start = clock.now();
        if (!active() || start - lastReceived > 250 || anchors.isEmpty()) {
            releaseAnchors(); publish(); return;
        }
        long ticket = budget.reserve(true, start);
        if (ticket == 0) { releaseAnchors(); publish(); return; }
        boolean accepted = false;
        try {
            reads++;
            List<ViewportAnchorGeometry.Bounds> bounds = read(anchors, start + 16);
            long end = clock.now();
            if (active() && end - start <= 16) {
                ViewportAnchorGeometry.Result result = geometry.estimate(bounds, fence, start, end, end, 32);
                accepted = aligner.addGeometry(key, fence, result, end);
            }
        } catch (RuntimeException failure) {
            readFailures++;
        } finally {
            long now = clock.now();
            maxReadMs = Math.max(maxReadMs, now - start);
            budget.finish(ticket, now - start, now);
        }
        alignPending();
        if (!accepted) { releaseAnchors(); budget.stop(clock.now()); }
        else worker.schedule(() -> tick(generation), Math.max(1, 16 - (clock.now() - start)));
        publish();
    }

    private List<ViewportAnchorGeometry.Bounds> read(List<AsyncViewportAnchorSampler.Anchor> values,
            long deadline) {
        List<ViewportAnchorGeometry.Bounds> bounds = new ArrayList<>(3);
        for (AsyncViewportAnchorSampler.Anchor value : values) {
            if (!active() || clock.now() >= deadline) throw new IllegalStateException("Read budget");
            bounds.add(value.read());
        }
        return bounds;
    }

    private void alignPending() {
        if (!active() || key == null) return;
        while (!pending.isEmpty()) {
            Interval value = pending.peekFirst();
            ScrollReferenceAligner.Alignment alignment = aligner.align(key, fence, value.gesture,
                    value.start, value.end, value.received, value.delta, clock.now());
            if (alignment.status == ScrollReferenceAligner.Status.WAITING) return;
            pending.removeFirst();
            if (alignment.sample == null) { alignmentRejects++; continue; }
            ScrollCalibrationLearner.State before = learner.state();
            ScrollCalibrationLearner.Result result = learner.observe(alignment.sample, clock.now());
            if (result == ScrollCalibrationLearner.Result.READY) lastValidatedReference = clock.now();
            if (!active()) return;
            try {
                if (result == ScrollCalibrationLearner.Result.INVALIDATED && durable) store.remove(key);
                if (learner.state() == ScrollCalibrationLearner.State.READY
                        && before != ScrollCalibrationLearner.State.READY && durable) {
                    if (store.save(learner.profile(), true)) saved++;
                    else storeFailures++;
                }
            } catch (RuntimeException failure) { storeFailures++; }
        }
    }

    private void releaseAnchors() {
        tickGeneration++;
        closeAnchors(anchors); anchors = Collections.emptyList(); geometry.clear(); pending.clear();
    }

    private static void closeAnchors(List<AsyncViewportAnchorSampler.Anchor> values) {
        for (AsyncViewportAnchorSampler.Anchor anchor : values) {
            try { anchor.close(); } catch (RuntimeException ignored) { /* Continue releasing ownership. */ }
        }
    }

    private synchronized void publish() {
        ScrollCalibrationLearner.Profile profile = learner.profile();
        handoff = profile != null && active()
                ? new Handoff(scope, profile, observedInvalidation, lastValidatedReference) : null;
        diagnostics = "{\"schemaVersion\":1,\"state\":\"" + (closed ? "DISABLED" : learner.state())
                + "\",\"applied\":false,\"collecting\":" + (!closed && !anchors.isEmpty())
                + ",\"durable\":" + durable + ",\"queueDrops\":" + queueDrops
                + ",\"acquisitions\":" + acquisitions + ",\"failedAcquisitions\":" + failedAcquisitions
                + ",\"reads\":" + reads + ",\"readFailures\":" + readFailures
                + ",\"maxReadMs\":" + maxReadMs + ",\"burstWorkMs\":" + budget.workMillis()
                + ",\"cooldownUntil\":" + budget.cooldownUntil()
                + ",\"alignmentRejects\":" + alignmentRejects
                + ",\"acceptedReferences\":" + aligner.acceptedReferences()
                + ",\"rejectedReferences\":" + aligner.rejectedReferences()
                + ",\"acceptedSamples\":" + learner.acceptedSamples()
                + ",\"rejectedSamples\":" + learner.rejectedSamples()
                + ",\"saved\":" + saved + ",\"storeFailures\":" + storeFailures
                + ",\"sourceFailures\":" + sourceFailures.get()
                + ",\"candidateScale\":" + (profile == null ? "null" : profile.pixelsPerEventPixel) + "}";
    }

    String diagnostics() { return diagnostics; }

    /** Immutable worker publication. A persisted candidate alone never reaches this handoff. */
    static final class Handoff {
        final Scope scope;
        final ScrollCalibrationLearner.Profile profile;
        final long generation, validatedAt;
        Handoff(Scope scope, ScrollCalibrationLearner.Profile profile, long generation, long validatedAt) {
            this.scope = scope; this.profile = profile; this.generation = generation;
            this.validatedAt = validatedAt;
        }
    }

    synchronized ScrollCalibrationLearner.Profile validatedProfile(Scope requested) {
        Handoff value = handoff;
        return !closed && value != null && requested != null
                && value.generation == invalidationGeneration
                && clock.now() >= value.validatedAt && clock.now() - value.validatedAt <= 35000
                && requested.equals(latestScope) && requested.equals(value.scope)
                && sourceActive(requested) ? value.profile : null;
    }

    /** An unpairable producer/event breaks interval lineage even if the next key looks identical. */
    synchronized void invalidate() {
        if (closed) return;
        invalidationGeneration++; latestScope = null;
        while (!queue.isEmpty()) queue.removeFirst().close();
        if (!invalidationQueued) {
            invalidationQueued = true;
            worker.execute(() -> {
                synchronized (this) { invalidationQueued = false; }
                resetIfInvalidated(); publish();
            });
        }
    }

    private void resetIfInvalidated() {
        if (observedInvalidation == invalidationGeneration) return;
        releaseAnchors(); learner.disable(); key = null; scope = null; lastTime = -1;
        observedInvalidation = invalidationGeneration;
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; latestScope = null;
        while (!queue.isEmpty()) queue.removeFirst().close();
        worker.execute(() -> { releaseAnchors(); learner.disable(); publish(); worker.shutdown(); });
    }
}
