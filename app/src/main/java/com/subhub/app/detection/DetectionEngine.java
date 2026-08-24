package com.subhub.app.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/** Owns the ONNX Runtime session and converts Android bitmaps to model input. */
public final class DetectionEngine implements AutoCloseable {
    private static final String TAG = "DetectionEngine";
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
    private float[] input;
    private long lastInferenceMs;

    public DetectionEngine(Context context, DetectorConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.environment = OrtEnvironment.getEnvironment();
        allocateBuffers(config.getInferenceResolution());
    }

    public synchronized void initialize() throws IOException, OrtException {
        closeSession();
        List<String> candidates = new ArrayList<>();
        candidates.add(config.getModelFilename());
        if (!"320n.onnx".equals(config.getModelFilename())) candidates.add("320n.onnx");

        Exception lastFailure = null;
        for (String model : candidates) {
            byte[] bytes;
            try {
                bytes = readAsset(model);
            } catch (IOException error) {
                lastFailure = error;
                continue;
            }
            for (String provider : new String[]{"NNAPI", "XNNPACK", "CPU"}) {
                OrtSession candidate = null;
                try (OrtSession.SessionOptions options = optionsFor(provider)) {
                    candidate = environment.createSession(bytes, options);
                    warmUp(candidate);
                    inputName = candidate.getInputNames().iterator().next();
                    session = candidate;
                    candidate = null;
                    activeProvider = provider;
                    activeModel = model;
                    Log.i(TAG, "Loaded " + model + " using " + provider);
                    return;
                } catch (Exception error) {
                    lastFailure = error;
                    Log.w(TAG, provider + " rejected " + model + ": " + error.getMessage());
                } finally {
                    closeQuietly(candidate);
                }
            }
        }
        if (lastFailure instanceof IOException) throw (IOException) lastFailure;
        if (lastFailure instanceof OrtException) throw (OrtException) lastFailure;
        throw new IOException("No compatible ONNX model asset was found", lastFailure);
    }

    public synchronized List<Detection> detect(Bitmap frame) throws OrtException {
        if (session == null || frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
            return Collections.emptyList();
        }
        long started = System.currentTimeMillis();
        int size = config.getInferenceResolution();
        float scale = (float) size / Math.max(frame.getWidth(), frame.getHeight());
        letterbox.eraseColor(Color.BLACK);
        letterboxCanvas.drawBitmap(
                frame,
                null,
                new RectF(0f, 0f, frame.getWidth() * scale, frame.getHeight() * scale),
                null);
        letterbox.getPixels(pixels, 0, size, 0, 0, size, size);

        int plane = size * size;
        for (int index = 0; index < plane; index++) {
            int pixel = pixels[index];
            input[index] = ((pixel >>> 16) & 0xff) * INV_255;
            input[plane + index] = ((pixel >>> 8) & 0xff) * INV_255;
            input[plane * 2 + index] = (pixel & 0xff) * INV_255;
        }

        FloatBuffer buffer = FloatBuffer.wrap(input);
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment, buffer, new long[]{1, 3, size, size})) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put(inputName, tensor);
            try (OrtSession.Result result = session.run(inputs)) {
                OnnxValue value = result.get(0);
                Object raw = value.getValue();
                if (!(raw instanceof float[][][])) {
                    Log.e(TAG, "Unexpected model output type: " + raw.getClass());
                    return Collections.emptyList();
                }
                float[][][] batch = (float[][][]) raw;
                if (batch.length == 0) return Collections.emptyList();
                List<Detection> detections = postProcessor.decode(
                        batch[0], frame.getWidth(), frame.getHeight(), size, config);
                lastInferenceMs = System.currentTimeMillis() - started;
                return detections;
            }
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

    private void warmUp(OrtSession candidate) throws OrtException {
        int size = config.getInferenceResolution();
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.allocate(size * size * 3),
                new long[]{1, 3, size, size})) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put(candidate.getInputNames().iterator().next(), tensor);
            try (OrtSession.Result ignored = candidate.run(inputs)) {
                // A completed warm-up proves that the provider accepts this model on this device.
            }
        }
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
        pixels = new int[size * size];
        input = new float[size * size * 3];
        if (letterbox != null) letterbox.recycle();
        letterbox = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        letterboxCanvas = new Canvas(letterbox);
    }

    private void closeSession() {
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

    @Override
    public synchronized void close() {
        closeSession();
        if (letterbox != null) letterbox.recycle();
        letterbox = null;
        letterboxCanvas = null;
    }
}
