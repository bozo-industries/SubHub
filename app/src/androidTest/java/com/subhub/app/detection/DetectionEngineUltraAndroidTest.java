package com.subhub.app.detection;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class DetectionEngineUltraAndroidTest {
    private static final String TAG = "UltraInferenceTest";

    @Test public void warmedRealtimeLaneIsFasterThanSettledQuality() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        DetectorConfig fastConfig = qualityConfig.toBuilder()
                .inferenceResolution(320).detectionIntervalMs(0L).build();
        Bitmap frame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        new Canvas(frame).drawColor(Color.rgb(74, 20, 95));
        try {
            try (DetectionEngine quality = new DetectionEngine(context, qualityConfig);
                 DetectionEngine fast = new DetectionEngine(context, fastConfig)) {
                quality.initialize();
                fast.initialize();
                long qualityMedian = medianNanos(quality, frame);
                long fastMedian = medianNanos(fast, frame);
                Log.i(TAG, "quality=" + qualityConfig.getInferenceResolution() + "@"
                        + quality.getActiveProvider() + ':' + qualityMedian / 1_000_000f
                        + " ms, fast=" + fastConfig.getInferenceResolution() + "@"
                        + fast.getActiveProvider() + ':' + fastMedian / 1_000_000f + " ms");
                assertTrue("320px real-time lane must beat 512px quality refinement",
                        fastMedian < qualityMedian);
            }
        } finally {
            frame.recycle();
        }
    }

    private static long medianNanos(DetectionEngine engine, Bitmap frame) throws Exception {
        long[] timings = new long[5];
        for (int index = 0; index < timings.length; index++) {
            long started = SystemClock.elapsedRealtimeNanos();
            assertNotNull(engine.detect(frame, 1080, 2400));
            timings[index] = SystemClock.elapsedRealtimeNanos() - started;
        }
        Arrays.sort(timings);
        return timings[2];
    }
}
