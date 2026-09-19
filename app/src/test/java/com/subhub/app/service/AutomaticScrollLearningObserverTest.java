package com.subhub.app.service;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import org.json.JSONObject;
import org.junit.Test;

public final class AutomaticScrollLearningObserverTest {
    private static final AutomaticScrollLearningObserver.Scope SCOPE = scope(1);

    @Test public void disarmedAndClosedNeverAcquireAndAlwaysReleaseEvents() throws Exception {
        Rig rig = new Rig();
        rig.enabled = false;
        Event first = rig.offer(SCOPE, 1, 100);
        rig.worker.until(200);
        assertEquals(1, first.closed);
        assertEquals(0, rig.acquisitions);
        rig.enabled = true;
        rig.observer.close();
        Event second = rig.offer(SCOPE, 1, 200);
        rig.worker.until(400);
        assertEquals(1, second.closed);
        assertEquals(0, rig.acquisitions);
        assertTrue(rig.worker.shutdown);
        assertEquals("DISABLED", rig.stats().getString("state"));
    }

    @Test public void boundedQueueReleasesOldAttachmentsAndCountsDrops() throws Exception {
        Rig rig = new Rig();
        List<Event> events = new ArrayList<>();
        for (int index = 0; index < 20; index++) events.add(rig.offer(SCOPE, 1, 100 + index));
        rig.worker.until(119);
        assertEquals(12, rig.stats().getInt("queueDrops"));
        for (Event event : events) assertEquals(1, event.closed);
        assertEquals(1, rig.acquisitions);
    }

    @Test public void idleReleasesAnchorsAndStopsBackgroundReads() throws Exception {
        Rig rig = new Rig();
        rig.offer(SCOPE, 1, 100);
        rig.worker.until(1000);
        assertEquals(3, rig.closedAnchors);
        int reads = rig.anchorReads;
        rig.worker.until(10000);
        assertEquals(reads, rig.anchorReads);
        assertFalse(rig.stats().getBoolean("collecting"));
        assertFalse(rig.stats().getBoolean("applied"));
    }

    @Test public void slowNodeStopsRemainingReadsAndEnforcesCooldown() throws Exception {
        Rig rig = new Rig();
        rig.nodeCost = 17;
        rig.offer(SCOPE, 1, 100);
        rig.worker.until(150);
        assertEquals(1, rig.anchorReads);
        assertEquals(3, rig.closedAnchors);
        assertEquals(1, rig.stats().getInt("failedAcquisitions"));
        assertTrue(rig.stats().getLong("cooldownUntil") >= 30117);
        rig.offer(SCOPE, 1, 200);
        rig.worker.until(250);
        assertEquals(1, rig.acquisitions);
    }

    @Test public void scopeChangeDuringAcquisitionCannotPublishOrRetainNodes() throws Exception {
        Rig rig = new Rig();
        rig.disableDuringAcquire = true;
        rig.offer(SCOPE, 1, 100);
        rig.worker.until(200);
        assertEquals(3, rig.closedAnchors);
        assertEquals(0, rig.anchorReads);
        assertEquals(0, rig.stats().getInt("acceptedSamples"));
        assertEquals(0, rig.saved);
    }

    @Test public void newestProducerFencesQueuedOldProducer() throws Exception {
        Rig rig = new Rig();
        Event first = rig.offer(SCOPE, 1, 100);
        Event second = rig.offer(scope(2), 1, 101);
        rig.worker.until(101);
        assertEquals(1, rig.acquisitions);
        assertEquals(2, rig.lastProducer);
        assertEquals(1, first.closed);
        assertEquals(1, second.closed);
    }

    @Test public void failedDiscoveryIsNotSuccessAndDoesNotBusyRetry() throws Exception {
        Rig rig = new Rig();
        rig.emptyAcquire = true;
        for (int index = 0; index < 50; index++) {
            rig.offer(SCOPE, 1, 100 + index * 100);
            rig.worker.until(100 + index * 100);
        }
        assertEquals(1, rig.acquisitions);
        assertEquals(1, rig.stats().getInt("failedAcquisitions"));
        assertEquals(0, rig.stats().getInt("acceptedSamples"));
    }

    @Test public void genuineGeometryProducesAcceptedPairsButNeverMotionAuthority() throws Exception {
        Rig rig = new Rig();
        for (int index = 0; index < 14; index++) {
            long time = 100 + index * 100;
            rig.worker.until(time);
            rig.offer(SCOPE, index < 7 ? 1 : 2, time);
            rig.worker.until(time);
        }
        rig.worker.until(1450);
        assertTrue(rig.stats().getInt("acceptedSamples") >= 8);
        assertFalse(rig.stats().getBoolean("applied"));
        assertEquals(0, rig.saved);
        assertTrue(rig.stats().getLong("acceptedReferences") > 20);
    }

    @Test public void probesHaveFiniteAttemptBudgetEvenWithContinuousMotion() throws Exception {
        Rig rig = new Rig();
        for (int index = 0; index < 80; index++) {
            long time = 100 + index * 100;
            rig.worker.until(time);
            rig.offer(SCOPE, 1 + index / 8, time);
            rig.worker.until(time);
        }
        assertEquals(1, rig.acquisitions);
        assertTrue(rig.stats().getLong("reads") <= 95);
        assertEquals(3, rig.closedAnchors);
        assertFalse(rig.stats().getBoolean("collecting"));
    }

    @Test public void closeDuringActiveCollectionReleasesExactlyOnce() throws Exception {
        Rig rig = new Rig();
        rig.offer(SCOPE, 1, 100);
        rig.worker.until(120);
        rig.observer.close(); rig.observer.close();
        rig.worker.until(1000);
        assertEquals(3, rig.closedAnchors);
        assertTrue(rig.worker.shutdown);
        assertFalse(rig.stats().getBoolean("collecting"));
    }

    @Test public void invalidationBreaksSameProducerLineageBeforeNextQueuedEvent() throws Exception {
        Rig rig = new Rig();
        for (int index = 0; index < 5; index++) {
            long time = 100 + index * 100;
            rig.worker.until(time); rig.offer(SCOPE, 1, time); rig.worker.until(time);
        }
        assertTrue(rig.stats().getInt("acceptedSamples") > 0);
        rig.observer.invalidate();
        rig.offer(SCOPE, 1, 550);
        rig.worker.until(550);
        assertEquals(0, rig.stats().getInt("acceptedSamples"));
        assertEquals(2, rig.acquisitions);
        assertEquals(3, rig.closedAnchors);
    }

    @Test public void repeatedUnpairableEventsCoalesceInvalidationWork() {
        Rig rig = new Rig();
        for (int index = 0; index < 100; index++) rig.observer.invalidate();
        assertEquals(1, rig.worker.immediate.size());
    }

    @Test public void failedScopeProbeReleasesNodesWithoutKillingWorker() throws Exception {
        Rig rig = new Rig();
        rig.offer(SCOPE, 1, 100); rig.worker.until(120);
        rig.failActive = true;
        rig.worker.until(200);
        assertEquals(3, rig.closedAnchors);
        assertTrue(rig.stats().getLong("sourceFailures") > 0);
        rig.observer.close(); rig.worker.until(300);
        assertTrue(rig.worker.shutdown);
    }

    @Test public void distinctGesturesAcrossBoundedBurstsCanValidateAndPersist() throws Exception {
        Rig rig = readyRig();
        assertEquals("READY", rig.stats().getString("state"));
        assertEquals(1.0, rig.stats().getDouble("candidateScale"), .00001);
        assertEquals(1, rig.saved);
        assertFalse(rig.stats().getBoolean("applied"));
        assertEquals(3, rig.acquisitions);
        assertNotNull(rig.observer.validatedProfile(SCOPE));
    }

    @Test public void handoffRejectsDifferentProducerBeforeWorkerDrains() {
        Rig rig = readyRig();
        assertNotNull(rig.observer.validatedProfile(SCOPE));
        rig.offer(scope(99), 20, rig.worker.now);
        assertNull(rig.observer.validatedProfile(SCOPE));
        assertNull(rig.observer.validatedProfile(scope(99)));
    }

    @Test public void invalidationAndCloseRevokeHandoffSynchronously() {
        Rig rig = readyRig();
        rig.observer.invalidate();
        assertNull(rig.observer.validatedProfile(SCOPE));
        rig.offer(SCOPE, 20, rig.worker.now);
        assertNull(rig.observer.validatedProfile(SCOPE));
        Rig closed = readyRig();
        closed.observer.close();
        assertNull(closed.observer.validatedProfile(SCOPE));
    }

    @Test public void inactiveOrUnmonitoredProfileCannotRemainAuthoritative() {
        Rig rig = readyRig();
        rig.enabled = false;
        assertNull(rig.observer.validatedProfile(SCOPE));
        rig.enabled = true;
        rig.worker.until(rig.worker.now + 36000);
        assertNull(rig.observer.validatedProfile(SCOPE));
    }

    @Test public void trainingEvidenceIsNeverAnApplicationHandoff() {
        Rig rig = new Rig();
        for (int i = 0; i < 8; i++) {
            long time = 100 + i * 100L;
            rig.worker.until(time); rig.offer(SCOPE, 1, time); rig.worker.until(time);
            assertNull(rig.observer.validatedProfile(SCOPE));
        }
    }

    @Test public void independentGeometryReachesPhysicalIncrementAdapterWithoutFeedback() {
        Rig rig = readyRig(4);
        ScrollCalibrationLearner.Profile profile = rig.observer.validatedProfile(SCOPE);
        assertNotNull(profile);
        assertEquals(2, profile.pixelsPerEventPixel, .00001);
        ScrollMotionCalibration calibration = new ScrollMotionCalibration();
        ScrollMotionCalibration.Motion motion = calibration.apply(SCOPE, profile, 0, 200);
        assertTrue(motion.scaleChanged);
        assertEquals(400, motion.dy);
        // Applying the profile neither edits raw observer evidence nor starts a new baseline.
        assertSame(profile, rig.observer.validatedProfile(SCOPE));
        assertEquals(3, rig.acquisitions);
        rig.observer.invalidate();
        motion = calibration.apply(SCOPE, rig.observer.validatedProfile(SCOPE), 0, 200);
        assertTrue(motion.scaleChanged);
        assertEquals(200, motion.dy);
    }

    private static Rig readyRig() {
        return readyRig(2);
    }

    private static Rig readyRig(int geometryRate) {
        Rig rig = new Rig();
        rig.durable = true;
        rig.geometryRate = geometryRate;
        for (int burst = 0; burst < 3; burst++) {
            for (int index = 0; index < 14; index++) {
                long time = 100 + burst * 33000L + index * 100;
                rig.worker.until(time);
                rig.offer(SCOPE, 1 + burst * 2 + index / 7, time);
                rig.worker.until(time);
            }
            rig.worker.until(2100 + burst * 33000L);
        }
        return rig;
    }

    private static AutomaticScrollLearningObserver.Scope scope(long producer) {
        return new AutomaticScrollLearningObserver.Scope("com.example.app", 1, 1, producer,
                7, 1000, 2000, 420, 0, 60000, ScrollLearningKey.Axis.Y, ScrollLearningKey.Evidence.EXPLICIT);
    }

    private static final class Event extends AutomaticScrollLearningObserver.Event {
        int closed;
        Event(AutomaticScrollLearningObserver.Scope scope, long gesture, long time) {
            super(scope, gesture, time, time, 200);
        }
        @Override public void close() { closed++; }
    }

    private static final class Rig implements AutomaticScrollLearningObserver.Source,
            AutomaticScrollLearningObserver.Store {
        final Worker worker = new Worker();
        final AutomaticScrollLearningObserver observer = new AutomaticScrollLearningObserver(
                () -> worker.now, worker, this, this);
        boolean enabled = true, disableDuringAcquire, emptyAcquire, durable, failActive;
        int acquisitions, closedAnchors, anchorReads, saved, nodeCost;
        int geometryRate = 2;
        long lastProducer;
        Event offer(AutomaticScrollLearningObserver.Scope scope, long gesture, long time) {
            worker.now = Math.max(worker.now, time);
            Event event = new Event(scope, gesture, time);
            observer.offer(event);
            return event;
        }
        JSONObject stats() throws Exception { return new JSONObject(observer.diagnostics()); }
        @Override public boolean active(AutomaticScrollLearningObserver.Scope scope) {
            if (failActive) throw new IllegalStateException("scope unavailable");
            return enabled;
        }
        @Override public AutomaticScrollLearningObserver.Acquired acquire(
                AutomaticScrollLearningObserver.Event event, long deadline) {
            acquisitions++; lastProducer = event.scope.producer;
            if (emptyAcquire) return null;
            if (disableDuringAcquire) enabled = false;
            List<AsyncViewportAnchorSampler.Anchor> anchors = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                final int offset = index * 100;
                anchors.add(new AsyncViewportAnchorSampler.Anchor() {
                    boolean closed;
                    @Override public ViewportAnchorGeometry.Bounds read() {
                        if (closed) throw new AssertionError("Read after close");
                        anchorReads++; worker.now += nodeCost;
                        int y = offset + (int) worker.now * geometryRate;
                        return new ViewportAnchorGeometry.Bounds(offset, y, offset + 20, y + 20);
                    }
                    @Override public void close() {
                        if (closed) throw new AssertionError("Double close");
                        closed = true; closedAnchors++;
                    }
                });
            }
            ScrollLearningKey key = new ScrollLearningKey("com.example.app", 1,
                    "a".repeat(64), 1000, 2000, 420, 0, 60000,
                    ScrollLearningKey.Axis.Y, ScrollLearningKey.Evidence.EXPLICIT);
            return new AutomaticScrollLearningObserver.Acquired(key, durable, anchors);
        }
        @Override public ScrollCalibrationLearner.Profile candidate(ScrollLearningKey key) { return null; }
        @Override public boolean save(ScrollCalibrationLearner.Profile profile, boolean durable) { saved++; return true; }
        @Override public void remove(ScrollLearningKey key) { }
    }

    private static final class Worker implements AsyncViewportAnchorSampler.Worker {
        long now, sequence;
        boolean shutdown;
        final ArrayDeque<Runnable> immediate = new ArrayDeque<>();
        final PriorityQueue<Job> scheduled = new PriorityQueue<>();
        @Override public void execute(Runnable task) { immediate.addLast(task); }
        @Override public void schedule(Runnable task, long delayMs) {
            scheduled.add(new Job(now + delayMs, ++sequence, task));
        }
        @Override public void shutdown() { shutdown = true; }
        void until(long end) {
            while (true) {
                while (!immediate.isEmpty()) immediate.removeFirst().run();
                if (scheduled.isEmpty() || scheduled.peek().time > end) break;
                Job job = scheduled.remove(); now = Math.max(now, job.time); job.task.run();
            }
            now = Math.max(now, end);
        }
    }
    private static final class Job implements Comparable<Job> {
        final long time, sequence; final Runnable task;
        Job(long time, long sequence, Runnable task) { this.time = time; this.sequence = sequence; this.task = task; }
        @Override public int compareTo(Job other) {
            int order = Long.compare(time, other.time);
            return order == 0 ? Long.compare(sequence, other.sequence) : order;
        }
    }
}
