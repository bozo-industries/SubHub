package com.subhub.app.detection;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.providers.NNAPIFlags;

/** Owns the ONNX Runtime session and converts Android bitmaps to model input. */
public final class DetectionEngine implements AutoCloseable {
    private static final String TAG = "DetectionEngine";
    private static final String PROVIDER_PREFS = "detector_provider_cache";
    private static final String PROVIDER_CONFIG_REVISION = "ep-v3";
    private static final float INV_255 = 1f / 255f;
    private static final ExecutorService PREPROCESS_EXECUTOR =
            Executors.newFixedThreadPool(3, runnable -> {
                Thread thread = new Thread(runnable, "SubHub-model-input");
                thread.setDaemon(true);
                return thread;
            });

    private final Context context;
    private final OrtEnvironment environment;
    private final DetectionPostProcessor postProcessor = new DetectionPostProcessor();
    private final InferencePerformanceHints performanceHints;
    private final boolean latencyPriority;
    private DetectorConfig config;
    private OrtSession session;
    private String inputName = "images";
    private String activeProvider = "uninitialized";
    private String activeModel = "";
    private Bitmap letterbox;
    private Canvas letterboxCanvas;
    private int[] pixels;
    private float[] inputValues;
    private FloatBuffer directInput;
    private OnnxTensor inputTensor;
    private Map<String, OnnxTensor> inferenceInputs;
    private long lastInferenceMs;
    private long lastPreprocessMs;
    private long lastRuntimeMs;
    private long lastPostprocessMs;

    public DetectionEngine(Context context, DetectorConfig config) {
        this(context, config, true);
    }

    public DetectionEngine(
            Context context, DetectorConfig config, boolean latencyPriority) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.latencyPriority = latencyPriority;
        this.environment = OrtEnvironment.getEnvironment();
        performanceHints = new InferencePerformanceHints(this.context);
        allocateBuffers(config.getInferenceResolution());
    }

    public synchronized void initialize() throws IOException, OrtException {
        closeSession();
        List<String> candidates = Collections.singletonList(config.getModelFilename());

        Exception lastFailure = null;
        for (String model : candidates) {
            byte[] bytes;
            try {
                bytes = readAsset(model);
            } catch (IOException error) {
                lastFailure = error;
                continue;
            }
            String cacheKey = providerCacheKey(model);
            SharedPreferences providerPrefs = context.getSharedPreferences(
                    PROVIDER_PREFS, Context.MODE_PRIVATE);
            String cachedProvider = providerPrefs.getString(cacheKey, null);
            ProviderCandidate fastest = cachedProvider == null ? null
                    : selectFastestProvider(bytes, model, new String[]{cachedProvider});
            if (fastest == null && cachedProvider != null) {
                providerPrefs.edit().remove(cacheKey).apply();
            }
            if (fastest == null) {
                fastest = selectFastestProvider(
                        bytes, model, new String[]{"NNAPI", "XNNPACK", "CPU"});
            }
            if (fastest != null) {
                session = fastest.session;
                inputName = fastest.session.getInputNames().iterator().next();
                activeProvider = fastest.provider;
                activeModel = model;
                Log.i(TAG, "Loaded " + model + " using fastest provider "
                        + fastest.provider + " (" + fastest.benchmarkNanos / 1_000_000f
                        + " ms" + (cachedProvider == null ? "" : ", cached")
                        + ", profile=" + (latencyPriority ? "realtime" : "quality") + ")");
                providerPrefs.edit().putString(cacheKey, fastest.provider).apply();
                return;
            }
        }
        if (lastFailure instanceof IOException) throw (IOException) lastFailure;
        if (lastFailure instanceof OrtException) throw (OrtException) lastFailure;
        throw new IOException("No compatible ONNX model asset was found", lastFailure);
    }

    public synchronized List<Detection> detect(Bitmap frame) throws OrtException {
        if (frame == null) return Collections.emptyList();
        return detect(frame, frame.getWidth(), frame.getHeight());
    }

    /** Detects from a pre-scaled bitmap while mapping results to the original source frame. */
    public synchronized List<Detection> detect(
            Bitmap frame, int sourceWidth, int sourceHeight) throws OrtException {
        if (session == null || frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
            return Collections.emptyList();
        }
        sourceWidth = Math.max(1, sourceWidth);
        sourceHeight = Math.max(1, sourceHeight);
        long started = SystemClock.elapsedRealtimeNanos();
        performanceHints.begin(latencyPriority
                ? config.getDetectionIntervalMs()
                : Math.max(200L, config.getDetectionIntervalMs()));
        int size = config.getInferenceResolution();
        float scale = (float) size / Math.max(sourceWidth, sourceHeight);
        int expectedWidth = Math.max(1, Math.round(sourceWidth * scale));
        int expectedHeight = Math.max(1, Math.round(sourceHeight * scale));
        if (frame.getWidth() == expectedWidth && frame.getHeight() == expectedHeight
                && expectedWidth <= size && expectedHeight <= size) {
            // Accessibility already paid for a hardware-accelerated downscale before readback.
            // Write that compact bitmap directly into the letterboxed pixel stride instead of
            // software-scaling/copying it through a second Canvas on every inference pass.
            Arrays.fill(pixels, Color.BLACK);
            frame.getPixels(pixels, 0, size, 0, 0, expectedWidth, expectedHeight);
        } else {
            letterbox.eraseColor(Color.BLACK);
            letterboxCanvas.drawBitmap(
                    frame,
                    null,
                    new RectF(0f, 0f, sourceWidth * scale, sourceHeight * scale),
                    null);
            letterbox.getPixels(pixels, 0, size, 0, 0, size, size);
        }

        int plane = size * size;
        populateInputValues(plane);
        directInput.clear();
        directInput.put(inputValues, 0, plane * 3);
        directInput.flip();

        ensureInputTensor(size);
        long runtimeStarted = SystemClock.elapsedRealtimeNanos();
        try (OrtSession.Result result = session.run(inferenceInputs)) {
            long runtimeFinished = SystemClock.elapsedRealtimeNanos();
            OnnxValue value = result.get(0);
            if (!(value instanceof OnnxTensor)) {
                Log.e(TAG, "Unexpected model output type: " + value.getClass());
                return Collections.emptyList();
            }
            OnnxTensor output = (OnnxTensor) value;
            TensorInfo info = output.getInfo();
            long[] shape = info.getShape();
            if (shape.length != 3 || shape[0] != 1L
                    || shape[1] < DetectionPostProcessor.OUTPUT_FEATURES
                    || shape[1] > Integer.MAX_VALUE || shape[2] <= 0L
                    || shape[2] > Integer.MAX_VALUE) {
                Log.e(TAG, "Unexpected model output shape: "
                        + java.util.Arrays.toString(shape));
                return Collections.emptyList();
            }
            FloatBuffer outputBuffer = output.getFloatBuffer();
            List<Detection> detections = postProcessor.decode(
                    outputBuffer,
                    (int) shape[1],
                    (int) shape[2],
                    sourceWidth,
                    sourceHeight,
                    size,
                    config);
            long finished = SystemClock.elapsedRealtimeNanos();
            lastPreprocessMs = nanosToMillis(runtimeStarted - started);
            lastRuntimeMs = nanosToMillis(runtimeFinished - runtimeStarted);
            lastPostprocessMs = nanosToMillis(finished - runtimeFinished);
            lastInferenceMs = nanosToMillis(finished - started);
            performanceHints.report(finished - started);
            return detections;
        }
    }

    public synchronized void setConfig(DetectorConfig value) {
        boolean resize = value.getInferenceResolution() != config.getInferenceResolution();
        config = value;
        if (resize) allocateBuffers(value.getInferenceResolution());
    }

    public String getActiveProvider() { return activeProvider; }
    public String getActiveModel() { return activeModel; }
    public long getLastInferenceMs() { return lastInferenceMs; }
    public long getLastPreprocessMs() { return lastPreprocessMs; }
    public long getLastRuntimeMs() { return lastRuntimeMs; }
    public long getLastPostprocessMs() { return lastPostprocessMs; }

    private static long nanosToMillis(long nanos) {
        return Math.max(0L, Math.round(nanos / 1_000_000d));
    }

    private OrtSession.SessionOptions optionsFor(String provider) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
        options.setInterOpNumThreads(1);
        // The real-time lane deliberately keeps its CPU workers hot between 334 ms captures; the
        // optional quality lane sleeps so it cannot consume the latency budget while idle.
        options.addConfigEntry("session.inter_op.allow_spinning", "0");
        int threads = Math.max(1, Math.min(config.getInferenceThreads(),
                Runtime.getRuntime().availableProcessors()));
        if ("NNAPI".equals(provider)) {
            options.addConfigEntry("session.intra_op.allow_spinning",
                    latencyPriority ? "1" : "0");
            options.setIntraOpNumThreads(threads);
            // NNAPI's reference CPU device is often slower than ORT's optimized CPU kernels.
            // Unsupported accelerator nodes still fall back to ORT without changing precision.
            options.addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED));
        } else if ("XNNPACK".equals(provider)) {
            // XNNPACK owns a separate intra-op pool. A second multi-threaded ORT pool competes
            // with it and can make mobile inference slower while consuming substantially more CPU.
            options.addConfigEntry("session.intra_op.allow_spinning", "0");
            options.setIntraOpNumThreads(1);
            options.addXnnpack(Collections.singletonMap(
                    "intra_op_num_threads", Integer.toString(threads)));
        } else {
            options.addConfigEntry("session.intra_op.allow_spinning",
                    latencyPriority ? "1" : "0");
            options.setIntraOpNumThreads(threads);
            options.addCPU(true);
        }
        return options;
    }

    private long benchmark(OrtSession candidate) throws OrtException {
        int size = config.getInferenceResolution();
        FloatBuffer benchmarkInput = ByteBuffer.allocateDirect(
                size * size * 3 * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int index = 0; index < benchmarkInput.capacity(); index++) {
            benchmarkInput.put((index & 0xff) * INV_255);
        }
        benchmarkInput.flip();
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                benchmarkInput,
                new long[]{1, 3, size, size})) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put(candidate.getInputNames().iterator().next(), tensor);
            long[] samples = new long[4];
            for (int pass = 0; pass < 5; pass++) {
                long started = SystemClock.elapsedRealtimeNanos();
                try (OrtSession.Result ignored = candidate.run(inputs)) {
                    // The first pass warms kernels; the next four select on sustained median speed.
                }
                long elapsed = SystemClock.elapsedRealtimeNanos() - started;
                if (pass > 0) samples[pass - 1] = elapsed;
            }
            Arrays.sort(samples);
            return samples[1] + (samples[2] - samples[1]) / 2L;
        }
    }

    private ProviderCandidate selectFastestProvider(
            byte[] modelBytes, String model, String[] providers) {
        OrtSession fastestSession = null;
        String fastestProvider = null;
        long fastestNanos = Long.MAX_VALUE;
        for (String provider : providers) {
            OrtSession candidate = null;
            try (OrtSession.SessionOptions options = optionsFor(provider)) {
                candidate = environment.createSession(modelBytes, options);
                long benchmarkNanos = benchmark(candidate);
                Log.i(TAG, provider + " benchmark for " + model + ": "
                        + benchmarkNanos / 1_000_000f + " ms profile="
                        + (latencyPriority ? "realtime" : "quality"));
                if (benchmarkNanos < fastestNanos) {
                    closeQuietly(fastestSession);
                    fastestSession = candidate;
                    candidate = null;
                    fastestProvider = provider;
                    fastestNanos = benchmarkNanos;
                }
            } catch (Exception error) {
                Log.w(TAG, provider + " rejected " + model + ": " + error.getMessage());
            } finally {
                closeQuietly(candidate);
            }
        }
        return fastestSession == null ? null
                : new ProviderCandidate(fastestSession, fastestProvider, fastestNanos);
    }

    private String providerCacheKey(String model) {
        String identity = android.os.Build.FINGERPRINT + '|' + model + '|'
                + config.getInferenceResolution() + '|' + config.getInferenceThreads()
                + '|' + (latencyPriority ? "realtime" : "quality")
                + '|' + PROVIDER_CONFIG_REVISION;
        return "provider_" + Integer.toHexString(identity.hashCode());
    }

    private byte[] readAsset(String name) throws IOException {
        try (InputStream source = context.getAssets().open(name);
             ByteArrayOutputStream target = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = source.read(chunk)) >= 0) target.write(chunk, 0, read);
            return target.toByteArray();
        }
    }

    private void allocateBuffers(int size) {
        closeInputTensor();
        pixels = new int[size * size];
        inputValues = new float[size * size * 3];
        directInput = ByteBuffer.allocateDirect(size * size * 3 * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        if (letterbox != null) letterbox.recycle();
        letterbox = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        letterboxCanvas = new Canvas(letterbox);
    }

    private void populateInputValues(int plane) {
        CountDownLatch finished = new CountDownLatch(3);
        PREPROCESS_EXECUTOR.execute(() -> fillChannel(plane, 0, 16, finished));
        PREPROCESS_EXECUTOR.execute(() -> fillChannel(plane, plane, 8, finished));
        PREPROCESS_EXECUTOR.execute(() -> fillChannel(plane, plane * 2, 0, finished));
        boolean interrupted = false;
        while (true) {
            try {
                finished.await();
                break;
            } catch (InterruptedException ignored) {
                // Complete the shared-buffer write before returning. Restore interruption after
                // all three disjoint channel workers have finished.
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private void fillChannel(int plane, int outputOffset, int shift, CountDownLatch finished) {
        try {
            for (int index = 0; index < plane; index++) {
                inputValues[outputOffset + index] = ((pixels[index] >>> shift) & 0xff) * INV_255;
            }
        } finally {
            finished.countDown();
        }
    }

    /** Keeps the native-backed tensor alive across frames instead of reallocating JNI storage. */
    private void ensureInputTensor(int size) throws OrtException {
        if (inputTensor != null && inferenceInputs != null) return;
        inputTensor = OnnxTensor.createTensor(
                environment, directInput, new long[]{1, 3, size, size});
        inferenceInputs = new LinkedHashMap<>(1);
        inferenceInputs.put(inputName, inputTensor);
    }

    private void closeInputTensor() {
        inferenceInputs = null;
        if (inputTensor != null) {
            inputTensor.close();
        }
        inputTensor = null;
    }

    private void closeSession() {
        closeInputTensor();
        closeQuietly(session);
        session = null;
    }

    private static void closeQuietly(OrtSession value) {
        if (value == null) return;
        try {
            value.close();
        } catch (OrtException error) {
            Log.w(TAG, "Could not close ONNX session cleanly", error);
        }
    }

    private static final class ProviderCandidate {
        final OrtSession session;
        final String provider;
        final long benchmarkNanos;

        ProviderCandidate(OrtSession session, String provider, long benchmarkNanos) {
            this.session = session;
            this.provider = provider;
            this.benchmarkNanos = benchmarkNanos;
        }
    }

    @Override
    public synchronized void close() {
        performanceHints.close();
        closeSession();
        directInput = null;
        inputValues = null;
        if (letterbox != null) letterbox.recycle();
        letterbox = null;
        letterboxCanvas = null;
    }
}
