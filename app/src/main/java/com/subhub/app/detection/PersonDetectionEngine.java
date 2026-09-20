package com.subhub.app.detection;

import android.content.Context;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/** Lazy optional CPU session. Caller must admit this through the shared low-priority budget. */
public final class PersonDetectionEngine implements AutoCloseable {
    private static final String[] SCORE_NAMES = {"792", "814", "836"};
    private static final String[] DISTANCE_NAMES = {"795", "817", "839"};
    private final Context context;
    private final PersonBoxDecoder decoder = new PersonBoxDecoder();
    private final AtomicReference<Run> active = new AtomicReference<>();
    private OrtSession session;
    private OnnxTensor tensor;
    private FloatBuffer input;
    private Map<String, OnnxTensor> inputs;
    private volatile long preprocessMs, runtimeMs, postprocessMs;
    private boolean closed;

    public PersonDetectionEngine(Context context) { this.context = context.getApplicationContext(); }

    public synchronized List<PersonBoxDecoder.Person> detect(SharedModelImage image,
            int sourceWidth, int sourceHeight, BooleanSupplier cancelled) throws IOException, OrtException {
        if (closed || cancelled.getAsBoolean()) return Collections.emptyList();
        preprocessMs = runtimeMs = postprocessMs = 0;
        try (Run run = new Run()) {
            active.set(run);
            try {
                if (cancelled.getAsBoolean()) run.cancel();
                if (run.cancelled) return Collections.emptyList();
                initialize();
                if (run.cancelled || cancelled.getAsBoolean()) return Collections.emptyList();
                long started = SystemClock.uptimeMillis();
                PersonBoxDecoder.Letterbox geometry = new PersonBoxDecoder.Letterbox(sourceWidth, sourceHeight);
                image.writePersonTensor(input, geometry);
                long runtimeStart = SystemClock.uptimeMillis();
                preprocessMs = runtimeStart - started;
                if (run.cancelled || cancelled.getAsBoolean()) return Collections.emptyList();
                try (OrtSession.Result result = session.run(inputs, run.options)) {
                    long runtimeEnd = SystemClock.uptimeMillis();
                    runtimeMs = runtimeEnd - runtimeStart;
                    if (run.cancelled || cancelled.getAsBoolean()) return Collections.emptyList();
                    FloatBuffer[] scores = new FloatBuffer[3], distances = new FloatBuffer[3];
                    for (int level = 0; level < 3; level++) {
                        int side = PersonBoxDecoder.INPUT_SIZE / PersonBoxDecoder.STRIDES[level];
                        scores[level] = output(result, SCORE_NAMES[level], side * side, 80);
                        distances[level] = output(result, DISTANCE_NAMES[level], side * side, 32);
                    }
                    List<PersonBoxDecoder.Person> people = decoder.decode(scores, distances, geometry, .35f);
                    postprocessMs = SystemClock.uptimeMillis() - runtimeEnd;
                    return run.cancelled || cancelled.getAsBoolean() ? Collections.emptyList() : people;
                } catch (OrtException failure) {
                    if (run.cancelled || cancelled.getAsBoolean()) return Collections.emptyList();
                    throw failure;
                }
            } finally {
                active.compareAndSet(run, null);
            }
        }
    }

    private static FloatBuffer output(OrtSession.Result result, String name, int rows, int features) {
        Object value = result.get(name).orElse(null);
        if (!(value instanceof OnnxTensor)) throw new IllegalStateException("Missing person output");
        OnnxTensor tensor = (OnnxTensor) value;
        long[] shape = tensor.getInfo().getShape();
        if (!java.util.Arrays.equals(shape, new long[]{1, rows, features})) {
            throw new IllegalStateException("Invalid person output shape");
        }
        return tensor.getFloatBuffer();
    }

    private void initialize() throws IOException, OrtException {
        if (session != null) return;
        byte[] bytes;
        try (InputStream asset = context.getAssets().open("person_nanodet_416_static.onnx");
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = asset.read(buffer)) != -1) output.write(buffer, 0, count);
            bytes = output.toByteArray();
        }
        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        OrtSession created = null;
        try {
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
                options.setIntraOpNumThreads(1);
                options.setInterOpNumThreads(1);
                options.addConfigEntry("session.intra_op.allow_spinning", "0");
                options.addConfigEntry("session.inter_op.allow_spinning", "0");
                options.addCPU(true);
                created = environment.createSession(bytes, options);
            }
            if (created.getInputNames().size() != 1) throw new IOException("Invalid person input count");
            String name = created.getInputNames().iterator().next();
            Object info = created.getInputInfo().get(name).getInfo();
            if (!(info instanceof TensorInfo)
                    || !java.util.Arrays.equals(((TensorInfo) info).getShape(), new long[]{1, 3, 416, 416})) {
                throw new IOException("Invalid person input shape");
            }
            input = ByteBuffer.allocateDirect(3 * 416 * 416 * Float.BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            tensor = OnnxTensor.createTensor(environment, input, new long[]{1, 3, 416, 416});
            inputs = Collections.singletonMap(name, tensor);
            session = created;
            created = null;
        } finally {
            if (created != null) created.close();
        }
    }

    public void cancel() {
        Run run = active.get();
        if (run != null) run.cancel();
    }

    public long getPreprocessMs() { return preprocessMs; }
    public long getRuntimeMs() { return runtimeMs; }
    public long getPostprocessMs() { return postprocessMs; }

    @Override public void close() {
        cancel();
        synchronized (this) {
            closed = true;
            if (tensor != null) tensor.close();
            if (session != null) {
                try { session.close(); } catch (OrtException ignored) { /* Already unusable. */ }
            }
            tensor = null;
            session = null;
            input = null;
            inputs = null;
        }
    }

    private static final class Run implements AutoCloseable {
        final OrtSession.RunOptions options = new OrtSession.RunOptions();
        volatile boolean cancelled;
        private boolean closed;

        Run() throws OrtException { }
        synchronized void cancel() {
            cancelled = true;
            if (!closed) {
                try { options.setTerminate(true); } catch (OrtException ignored) { /* Fence still rejects output. */ }
            }
        }
        @Override public synchronized void close() { closed = true; options.close(); }
    }
}
