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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(AndroidJUnit4.class)
public final class DetectionEngineUltraAndroidTest {
    private static final String TAG = "UltraInferenceTest";

    @Test public void warmedRealtimeLaneIsFasterThanSettledQuality() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        DetectorConfig fastConfig = qualityConfig.toBuilder()
                .inferenceResolution(320).detectionIntervalMs(0L).build();
        Bitmap qualityFrame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        Bitmap fastFrame = Bitmap.createBitmap(144, 320, Bitmap.Config.ARGB_8888);
        new Canvas(qualityFrame).drawColor(Color.rgb(74, 20, 95));
        new Canvas(fastFrame).drawColor(Color.rgb(74, 20, 95));
        try {
            try (DetectionEngine quality = new DetectionEngine(context, qualityConfig, false);
                 DetectionEngine fast = new DetectionEngine(context, fastConfig, true)) {
                quality.initialize();
                fast.initialize();
                long qualityMedian = medianNanos(quality, qualityFrame);
                long fastMedian = medianNanos(fast, fastFrame);
                Log.i(TAG, "quality=" + qualityConfig.getInferenceResolution() + "@"
                        + quality.getActiveProvider() + ':' + qualityMedian / 1_000_000f
                        + " ms(pre=" + quality.getLastPreprocessMs()
                        + ", runtime=" + quality.getLastRuntimeMs() + ')'
                        + " ms, fast=" + fastConfig.getInferenceResolution() + "@"
                        + fast.getActiveProvider() + ':' + fastMedian / 1_000_000f
                        + " ms(pre=" + fast.getLastPreprocessMs()
                        + ", runtime=" + fast.getLastRuntimeMs() + ")");
                assertTrue("320px real-time lane must beat 512px quality refinement",
                        fastMedian < qualityMedian);
            }
        } finally {
            qualityFrame.recycle();
            fastFrame.recycle();
        }
    }

    @Test public void cpuAndNnapiLanesOverlapInWallClockTime() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        DetectorConfig fastConfig = qualityConfig.toBuilder()
                .inferenceResolution(320).detectionIntervalMs(0L).build();
        Bitmap qualityFrame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        Bitmap fastFrame = Bitmap.createBitmap(144, 320, Bitmap.Config.ARGB_8888);
        new Canvas(qualityFrame).drawColor(Color.rgb(74, 20, 95));
        new Canvas(fastFrame).drawColor(Color.rgb(74, 20, 95));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            try (DetectionEngine quality = new DetectionEngine(context, qualityConfig, false);
                 DetectionEngine fast = new DetectionEngine(context, fastConfig, true)) {
                quality.initialize();
                fast.initialize();
                quality.detect(qualityFrame, 1080, 2400);
                fast.detect(fastFrame, 1080, 2400);
                long[] sequential = new long[5];
                long[] concurrent = new long[5];
                for (int index = 0; index < sequential.length; index++) {
                    long sequentialStarted = SystemClock.elapsedRealtimeNanos();
                    fast.detect(fastFrame, 1080, 2400);
                    quality.detect(qualityFrame, 1080, 2400);
                    sequential[index] = SystemClock.elapsedRealtimeNanos() - sequentialStarted;

                    long concurrentStarted = SystemClock.elapsedRealtimeNanos();
                    Future<?> fastRun = workers.submit(() -> detectUnchecked(
                            fast, fastFrame));
                    Future<?> qualityRun = workers.submit(() -> detectUnchecked(
                            quality, qualityFrame));
                    fastRun.get();
                    qualityRun.get();
                    concurrent[index] = SystemClock.elapsedRealtimeNanos() - concurrentStarted;
                }
                Arrays.sort(sequential);
                Arrays.sort(concurrent);
                long sequentialMedian = sequential[2];
                long concurrentMedian = concurrent[2];
                Log.i(TAG, "sequential=" + sequentialMedian / 1_000_000f
                        + " ms concurrent=" + concurrentMedian / 1_000_000f
                        + " ms fastProvider=" + fast.getActiveProvider()
                        + " qualityProvider=" + quality.getActiveProvider());
                assertTrue("Independent providers should overlap rather than serialize",
                        concurrentMedian < sequentialMedian);
            }
        } finally {
            workers.shutdownNow();
            qualityFrame.recycle();
            fastFrame.recycle();
        }
    }

    @Test public void benchmarksNnapiFp16RelaxationWithoutEnablingItBlindly() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        Bitmap qualityFrame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        new Canvas(qualityFrame).drawColor(Color.rgb(74, 20, 95));
        try {
            ProviderTiming strict = measureProviderVariant(
                    context, qualityConfig, qualityFrame, false);
            ProviderTiming relaxed = measureProviderVariant(
                    context, qualityConfig, qualityFrame, true);
            Log.i(TAG, "nnapiStrict=" + strict.provider + ':'
                    + strict.medianNanos / 1_000_000f + "ms detections=" + strict.detections
                    + " nnapiFp16=" + relaxed.provider + ':'
                    + relaxed.medianNanos / 1_000_000f + "ms detections=" + relaxed.detections);
            assertTrue(strict.medianNanos > 0L);
            assertTrue(relaxed.medianNanos > 0L);
        } finally {
            qualityFrame.recycle();
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

    private static void detectUnchecked(DetectionEngine engine, Bitmap frame) {
        try {
            engine.detect(frame, 1080, 2400);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static ProviderTiming measureProviderVariant(
            Context context,
            DetectorConfig config,
            Bitmap frame,
            boolean nnapiFp16) throws Exception {
        try (DetectionEngine engine = new DetectionEngine(
                context, config, false, nnapiFp16)) {
            engine.initializeForProvider("NNAPI");
            List<Detection> detections = engine.detect(frame, 1080, 2400);
            return new ProviderTiming(
                    engine.getActiveProvider(), medianNanos(engine, frame), detections.size());
        }
    }

    private static final class ProviderTiming {
        final String provider;
        final long medianNanos;
        final int detections;

        ProviderTiming(String provider, long medianNanos, int detections) {
            this.provider = provider;
            this.medianNanos = medianNanos;
            this.detections = detections;
        }
    }
}
