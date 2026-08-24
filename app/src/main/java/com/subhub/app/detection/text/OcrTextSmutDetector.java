package com.subhub.app.detection.text;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.subhub.app.detection.Detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ultra-only bundled Latin OCR feeding the same local text-risk classifier as Accessibility. */
public final class OcrTextSmutDetector implements AutoCloseable {
    public interface Callback {
        void onComplete(List<Detection> detections);
        void onFailure(Exception error);
    }

    private final TextRecognizer recognizer;
    private final TextSmutDetectionFactory factory;
    private final AtomicBoolean warmupRequested = new AtomicBoolean();

    public OcrTextSmutDetector(SmutTextClassifier classifier) {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        factory = new TextSmutDetectionFactory(classifier);
    }

    /** Starts bundled model initialization without delaying the first real screenshot. */
    public void warmUp(Executor callbackExecutor) {
        if (callbackExecutor == null || !warmupRequested.compareAndSet(false, true)) return;
        Bitmap warmup = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        warmup.eraseColor(android.graphics.Color.WHITE);
        recognizer.process(InputImage.fromBitmap(warmup, 0))
                .addOnCompleteListener(callbackExecutor, ignored -> warmup.recycle());
    }

    public void detect(
            Bitmap bitmap,
            TextSmutConfig config,
            int sourceWidth,
            int sourceHeight,
            Executor callbackExecutor,
            Callback callback) {
        if (bitmap == null || bitmap.isRecycled() || config == null || !config.isEnabled()) {
            callback.onComplete(Collections.emptyList());
            return;
        }
        int ocrWidth = bitmap.getWidth();
        int ocrHeight = bitmap.getHeight();
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(callbackExecutor, text -> callback.onComplete(mapDetections(
                        text, config, ocrWidth, ocrHeight, sourceWidth, sourceHeight)))
                .addOnFailureListener(callbackExecutor, callback::onFailure);
    }

    private List<Detection> mapDetections(
            Text result,
            TextSmutConfig config,
            int ocrWidth,
            int ocrHeight,
            int sourceWidth,
            int sourceHeight) {
        List<Detection> ocrDetections = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            boolean lineMatched = false;
            for (Text.Line line : block.getLines()) {
                Detection detection = create(line.getText(), line.getBoundingBox(),
                        config, ocrWidth, ocrHeight);
                if (detection != null) {
                    ocrDetections.add(detection);
                    lineMatched = true;
                }
            }
            // A semantic phrase can span line wrapping. Only add the block fallback when no
            // precise line already matched, keeping OCR boxes tightly aligned to visible text.
            if (!lineMatched) {
                Detection blockDetection = create(block.getText(), block.getBoundingBox(),
                        config, ocrWidth, ocrHeight);
                if (blockDetection != null) ocrDetections.add(blockDetection);
            }
        }
        List<Detection> fused = DetectionFusion.merge(
                Collections.emptyList(), ocrDetections);
        return TextDetectionCoordinateMapper.screenToCapture(
                fused, ocrWidth, ocrHeight, sourceWidth, sourceHeight);
    }

    private Detection create(
            String text,
            Rect bounds,
            TextSmutConfig config,
            int width,
            int height) {
        return bounds == null ? null
                : factory.create(text, bounds, config, width, height, true);
    }

    @Override public void close() {
        recognizer.close();
    }
}
