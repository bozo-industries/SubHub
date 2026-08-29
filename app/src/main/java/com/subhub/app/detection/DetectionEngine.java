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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/** Owns the ONNX Runtime session and converts Android bitmaps to model input. */
public final class DetectionEngine implements AutoCloseable {
    private static final String TAG = "DetectionEngine";
    private static final String PROVIDER_PREFS = "detector_provider_cache";
    private static final float INV_255 = 1f / 255f;

    private final Context context;
    private final OrtEnvironment environment;
    private final DetectionPostProcessor postProcessor = new DetectionPostProcessor();
    private DetectorConfig config;
    private OrtSession session;
    private String inputName = "images";
    private String activeProvider = "uninitialized";
    private String activeModel = "";
    private Bitmap letterbox;
    private Canvas letterboxCanvas;
    private int[] pixels;
    private FloatBuffer directInput;
    private OnnxTensor inputTensor;
    private Map<String, OnnxTensor> inferenceInputs;
    private long lastInferenceMs;
    private long lastPreprocessMs;
    private long lastRuntimeMs;
    private long lastPostprocessMs;

    public DetectionEngine(Context context, DetectorConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.environment = OrtEnvironment.getEnvironment();
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
                        + " ms" + (cachedProvider == null ? "" : ", cached") + ")");
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
        int size = config.getInferenceResolution();
        float scale = (float) size / Math.max(sourceWidth, sourceHeight);
        letterbox.eraseColor(Color.BLACK);
        letterboxCanvas.drawBitmap(
                frame,
                null,
                new RectF(0f, 0f, sourceWidth * scale, sourceHeight * scale),
                null);
        letterbox.getPixels(pixels, 0, size, 0, 0, size, size);

        int plane = size * size;
        directInput.clear();
        for (int index = 0; index < plane; index++) {
            int pixel = pixels[index];
            directInput.put(((pixel >>> 16) & 0xff) * INV_255);
        }
        for (int index = 0; index < plane; index++) {
            directInput.put(((pixels[index] >>> 8) & 0xff) * INV_255);
        }
        for (int index = 0; index < plane; index++) {
            directInput.put((pixels[index] & 0xff) * INV_255);
        }
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
        // Keep capture single-flight while letting ONNX parallelize individual kernels inside
        // the preset's bounded CPU/battery budget.
        int threads = Math.max(1, Math.min(config.getInferenceThreads(),
                Runtime.getRuntime().availableProcessors()));
        options.setIntraOpNumThreads(threads);
        options.setInterOpNumThreads(1);
        if ("NNAPI".equals(provider)) {
            options.addNnapi();
        } else if ("XNNPACK".equals(provider)) {
            options.addXnnpack(Collections.emptyMap());
        } else {
            options.addCPU(true);
        }
        return options;
    }

    private long benchmark(OrtSession candidate) throws OrtException {
        int size = config.getInferenceResolution();
        FloatBuffer benchmarkInput = ByteBuffer.allocateDirect(
                size * size * 3 * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                benchmarkInput,
                new long[]{1, 3, size, size})) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put(candidate.getInputNames().iterator().next(), tensor);
            long bestNanos = Long.MAX_VALUE;
            for (int pass = 0; pass < 3; pass++) {
                long started = SystemClock.elapsedRealtimeNanos();
                try (OrtSession.Result ignored = candidate.run(inputs)) {
                    // The first pass warms kernels; the next two select on observed device speed.
                }
                long elapsed = SystemClock.elapsedRealtimeNanos() - started;
                if (pass > 0) bestNanos = Math.min(bestNanos, elapsed);
            }
            return bestNanos;
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
                        + benchmarkNanos / 1_000_000f + " ms");
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
                + config.getInferenceResolution() + '|' + config.getInferenceThreads();
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
        directInput = ByteBuffer.allocateDirect(size * size * 3 * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        if (letterbox != null) letterbox.recycle();
        letterbox = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        letterboxCanvas = new Canvas(letterbox);
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
        closeSession();
        directInput = null;
        if (letterbox != null) letterbox.recycle();
        letterbox = null;
        letterboxCanvas = null;
    }
}
