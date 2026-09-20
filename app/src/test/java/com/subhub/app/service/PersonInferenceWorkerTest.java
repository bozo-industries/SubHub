package com.subhub.app.service;

import com.subhub.app.detection.SharedModelImage;
import com.subhub.app.detection.PersonBoxDecoder;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PersonInferenceWorkerTest {
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private final Executor executor = queue::add;
    private final FakeBackend backend = new FakeBackend();
    private final AtomicInteger published = new AtomicInteger();
    private final AtomicInteger releases = new AtomicInteger();
    private final PersonInferenceWorker worker = new PersonInferenceWorker(executor, backend,
            () -> () -> releases.incrementAndGet());

    private void submit(PersonInferenceWorker target, int width, BooleanSupplier valid) {
        target.submit(SharedModelImage.takeOwnership(new int[]{0xff000000}, 1, 1), width, 10,
                valid, people -> published.incrementAndGet());
    }
    private void drain() { while (!queue.isEmpty()) queue.remove().run(); }

    @Test public void newestPendingReplacesOlderWithoutGrowingExecutorQueue() {
        for (int i = 1; i <= 100; i++) submit(worker, i, () -> true);
        assertEquals(1, queue.size());
        drain();
        assertEquals(1, backend.runs);
        assertEquals(100, backend.width);
        assertEquals(1, published.get());
        assertEquals(1, releases.get());
    }

    @Test public void fastArrivalDropsPendingWorkBeforeModelStarts() {
        submit(worker, 1, () -> true);
        worker.preempt();
        drain();
        assertEquals(0, backend.runs);
        assertEquals(0, published.get());
    }

    @Test public void fastArrivalDuringNativeRunCancelsAndSuppressesResult() {
        backend.duringRun = worker::preempt;
        submit(worker, 1, () -> true);
        drain();
        assertTrue(backend.observedCancellation);
        assertEquals(0, published.get());
        assertEquals(1, releases.get());
    }

    @Test public void sourceInvalidBeforeRunDoesNotLoadModel() {
        submit(worker, 1, () -> false);
        drain();
        assertEquals(0, backend.runs);
    }

    @Test public void busyQualityAdmissionNeverWaitsOrRunsPerson() {
        PersonInferenceWorker denied = new PersonInferenceWorker(executor, backend, () -> null);
        submit(denied, 1, () -> true);
        drain();
        assertEquals(0, backend.runs);
        assertEquals(0, published.get());
        assertEquals(0, queue.size());
    }

    @Test public void modeOffReleasesOnWorkerAndInvalidatesQueuedImage() {
        submit(worker, 1, () -> true);
        worker.reset();
        assertEquals(0, backend.closed);
        drain();
        assertEquals(0, backend.runs);
        assertEquals(1, backend.closed);
    }

    @Test public void closeRejectsNewSubmissionsAndReleases() {
        worker.close();
        submit(worker, 1, () -> true);
        drain();
        assertEquals(0, backend.runs);
        assertEquals(1, backend.closed);
    }

    @Test public void modelFailureIsNotEmptySuccessAndDoesNotRetryEveryFrame() {
        backend.fail = true;
        submit(worker, 1, () -> true);
        drain();
        submit(worker, 2, () -> true);
        drain();
        assertEquals(1, backend.runs);
        assertEquals(0, published.get());
        assertEquals(1, backend.closed);
        assertEquals(1, releases.get());
        backend.fail = false;
        worker.reset();
        submit(worker, 3, () -> true);
        drain();
        assertEquals(2, backend.runs);
        assertEquals(1, published.get());
    }

    @Test public void newerSourceArrivingDuringRunGetsOneFollowupTask() {
        backend.duringRun = () -> {
            backend.duringRun = null;
            submit(worker, 2, () -> true);
            submit(worker, 3, () -> true);
        };
        submit(worker, 1, () -> true);
        drain();
        assertEquals(2, backend.runs);
        assertEquals(3, backend.width);
        assertEquals(1, published.get());
    }

    private static final class FakeBackend implements PersonInferenceWorker.Backend {
        int runs, width, closed;
        boolean observedCancellation, fail;
        Runnable duringRun;
        @Override public List<PersonBoxDecoder.Person> detect(SharedModelImage image,
                int width, int height, BooleanSupplier cancelled) throws Exception {
            runs++;
            this.width = width;
            if (duringRun != null) duringRun.run();
            observedCancellation = cancelled.getAsBoolean();
            if (fail) throw new Exception("fixture");
            return Collections.emptyList();
        }
        @Override public void cancel() {}
        @Override public void release() { closed++; }
    }
}
