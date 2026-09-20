package com.subhub.app.service;

import com.subhub.app.detection.PersonBoxDecoder;
import com.subhub.app.detection.SharedModelImage;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Latest-only optional work. Must share the fast executor, never run before its publication. */
final class PersonInferenceWorker implements AutoCloseable {
    interface Backend {
        List<PersonBoxDecoder.Person> detect(SharedModelImage image, int width, int height,
                BooleanSupplier cancelled) throws Exception;
        void cancel();
        void release();
    }
    interface Admission { AutoCloseable tryAcquire(); }
    private final Executor executor;
    private final Backend backend;
    private final Admission admission;
    private final Consumer<Exception> failureHandler;
    private Request pending;
    private volatile long generation;
    private boolean queued, releaseRequested, closed;
    private volatile boolean failed;
    private long submitted, dropped, preempted, denied;
    private long resourceWake;

    PersonInferenceWorker(Executor executor, Backend backend, Admission admission) {
        this(executor, backend, admission, failure -> {});
    }

    PersonInferenceWorker(Executor executor, Backend backend, Admission admission,
            Consumer<Exception> failureHandler) {
        this.executor = executor;
        this.backend = backend;
        this.admission = admission;
        this.failureHandler = failureHandler;
    }

    synchronized void submit(SharedModelImage image, int width, int height,
            BooleanSupplier valid, Consumer<List<PersonBoxDecoder.Person>> publish) {
        if (closed || failed || image == null) return;
        submitted++;
        if (pending != null) dropped++;
        generation++;
        backend.cancel();
        pending = new Request(generation, image, width, height, valid, publish);
        schedule();
    }

    synchronized void preempt() {
        preempted++;
        if (pending != null) dropped++;
        generation++;
        pending = null;
        backend.cancel();
    }

    synchronized void reset() {
        preempt();
        failed = false;
        releaseRequested = true;
        schedule();
    }

    @Override public synchronized void close() {
        closed = true;
        reset();
    }

    private void schedule() {
        if (queued) return;
        queued = true;
        try { executor.execute(this::drainOne); }
        catch (RejectedExecutionException rejected) {
            queued = false;
            pending = null;
            // Shutdown owner must close before shutting down the shared executor.
        }
    }

    synchronized String counters() {
        return " submitted=" + submitted + " dropped=" + dropped
                + " preemptions=" + preempted + " denied=" + denied;
    }

    synchronized boolean hasPending() { return pending != null && !closed && !failed; }

    /** Resource owner calls after unlocking. No polling, blocking, or unbounded retry queue. */
    synchronized void resourceAvailable() {
        resourceWake++;
        if (hasPending()) schedule();
    }

    private void drainOne() {
        Request request;
        boolean release;
        long observedWake;
        boolean deferred = false;
        synchronized (this) {
            request = pending;
            pending = null;
            release = releaseRequested;
            releaseRequested = false;
            observedWake = resourceWake;
        }
        try {
            if (release) backend.release();
            if (request == null || cancelled(request)) return;
            try (AutoCloseable permit = admission.tryAcquire()) {
                if (permit == null) {
                    boolean valid = !cancelled(request);
                    synchronized (this) {
                        denied++;
                        if (valid && !closed && request.generation == generation && pending == null) {
                            pending = request;
                            deferred = true;
                        }
                    }
                    return;
                }
                if (cancelled(request)) return;
                List<PersonBoxDecoder.Person> people = backend.detect(request.image,
                        request.width, request.height, () -> cancelled(request));
                if (!cancelled(request)) request.publish.accept(people);
            }
        } catch (Exception failure) {
            // Avoid retrying a broken optional model on every fast frame. Settings reset may
            // retry, but failure is never turned into an empty successful model observation.
            failed = true;
            backend.release();
            failureHandler.accept(failure);
        } finally {
            synchronized (this) {
                queued = false;
                if (releaseRequested || pending != null && (!deferred || observedWake != resourceWake)) schedule();
            }
        }
    }

    private boolean cancelled(Request request) {
        return failed || generation != request.generation || !request.valid.getAsBoolean();
    }

    private static final class Request {
        final long generation;
        final SharedModelImage image;
        final int width, height;
        final BooleanSupplier valid;
        final Consumer<List<PersonBoxDecoder.Person>> publish;
        Request(long generation, SharedModelImage image, int width, int height,
                BooleanSupplier valid, Consumer<List<PersonBoxDecoder.Person>> publish) {
            this.generation = generation;
            this.image = image;
            this.width = width;
            this.height = height;
            this.valid = valid;
            this.publish = publish;
        }
    }
}
