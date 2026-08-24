package com.subhub.app.detection.text;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.detection.Detection;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OcrTextSmutDetectorAndroidTest {
    @Test public void bundledOcrFeedsSemanticClassifier() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TextSmutConfig config = new TextSmutConfig(
                true, TextSmutConfig.SENSITIVITY_BALANCED, TextSmutConfig.DEFAULT_CATEGORIES);
        Bitmap bitmap = Bitmap.createBitmap(1_400, 280, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(52f);
        canvas.drawText("I want to feel your body pressed against mine tonight", 30f, 150f, paint);

        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
        OcrTextSmutDetector detector = new OcrTextSmutDetector(
                new SmutTextClassifier(context));
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<List<Detection>> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        detector.detect(bitmap, config, bitmap.getWidth(), bitmap.getHeight(), callbackExecutor,
                new OcrTextSmutDetector.Callback() {
                    @Override public void onComplete(List<Detection> detections) {
                        result.set(detections);
                        finished.countDown();
                    }

                    @Override public void onFailure(Exception error) {
                        failure.set(error);
                        finished.countDown();
                    }
                });

        assertTrue("OCR timed out", finished.await(15, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertFalse(result.get().isEmpty());
        detector.close();
        callbackExecutor.shutdownNow();
        bitmap.recycle();
    }
}
