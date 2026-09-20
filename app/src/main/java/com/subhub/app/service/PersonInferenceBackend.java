package com.subhub.app.service;

import android.content.Context;
import android.os.SystemClock;
import android.os.Process;
import com.subhub.app.diagnostics.CensorLabLog;
import com.subhub.app.detection.PersonBoxDecoder;
import com.subhub.app.detection.PersonDetectionEngine;
import com.subhub.app.detection.SharedModelImage;
import java.util.List;
import java.util.function.BooleanSupplier;

/** One lazily created session; cancellation never waits for the native invocation monitor. */
final class PersonInferenceBackend implements PersonInferenceWorker.Backend {
    private final Context context;
    private volatile PersonDetectionEngine engine;
    private static final java.util.concurrent.atomic.AtomicLong RUN_IDS = new java.util.concurrent.atomic.AtomicLong();
    private volatile long lastRunId;

    PersonInferenceBackend(Context context) { this.context = context.getApplicationContext(); }

    @Override public synchronized List<PersonBoxDecoder.Person> detect(SharedModelImage image,
            int width, int height, BooleanSupplier cancelled) throws Exception {
        if (cancelled.getAsBoolean()) return java.util.Collections.emptyList();
        if (engine == null) engine = new PersonDetectionEngine(context);
        lastRunId = RUN_IDS.incrementAndGet();
        long started = SystemClock.uptimeMillis();
        boolean success = false;
        int previousPriority = Process.getThreadPriority(Process.myTid());
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            List<PersonBoxDecoder.Person> result = engine.detect(image, width, height, cancelled);
            success = !cancelled.getAsBoolean();
            return result;
        } finally {
            Process.setThreadPriority(previousPriority);
            CensorLabLog.i("PersonInference", "PERSON_MODEL v=1 run=" + lastRunId
                    + " totalMs=" + Math.max(0L, SystemClock.uptimeMillis() - started)
                    + " prepMs=" + engine.getPreprocessMs() + " runtimeMs=" + engine.getRuntimeMs()
                    + " postMs=" + engine.getPostprocessMs()
                    + " cancelled=" + cancelled.getAsBoolean() + " success=" + success);
        }
    }

    long lastRunId() { return lastRunId; }

    @Override public void cancel() {
        PersonDetectionEngine current = engine;
        if (current != null) current.cancel();
    }

    @Override public void release() {
        cancel();
        synchronized (this) {
            if (engine != null) engine.close();
            engine = null;
        }
    }
}
