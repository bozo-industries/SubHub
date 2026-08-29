package com.subhub.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;
import com.subhub.app.detection.DetectorConfig;
import com.subhub.app.detection.ObjectTracker;
import com.subhub.app.detection.TrackedObject;
import com.subhub.app.settings.CensorAppearance;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public final class CensorOverlayViewTest {
    @Test public void boxPixelsAreFullyOpaqueBlackAndExplicitClearHidesTheView() {
        Context context = ApplicationProvider.getApplicationContext();
        CensorOverlayView view = new CensorOverlayView(context);
        view.setAppearance(new CensorAppearance(
                CensorAppearance.Type.BOX, 1, 0f, false, false,
                CensorAppearance.BorderEffect.CLASSIC, false, Color.MAGENTA,
                List.of(), false, 100, "rectangle", "SubHub", "Blocked"));
        ObjectTracker tracker = new ObjectTracker(DetectorConfig.builder().build());
        List<TrackedObject> tracks = tracker.update(List.of(new Detection(
                "EXPOSED_TEST", "EXPOSED", 1f, new BBox(20, 20, 60, 60), true, true)));
        view.setTracks(tracks, 100, 100, null);
        view.measure(exactly(100), exactly(100));
        view.layout(0, 0, 100, 100);

        Bitmap rendered = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        rendered.eraseColor(Color.TRANSPARENT);
        view.draw(new Canvas(rendered));

        assertEquals(Color.BLACK, rendered.getPixel(50, 50));
        view.clearContent();
        assertEquals(View.INVISIBLE, view.getVisibility());
        rendered.recycle();
        view.release();
    }

    @Test public void everyLiveEffectRendersContinuouslyAndProducesVisualEvidence()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        List<TrackedObject> tracks = confirmedTracks();
        int tileWidth = 240;
        int tileHeight = 180;
        int labelHeight = 28;
        Bitmap gallery = Bitmap.createBitmap(
                tileWidth * 3, (tileHeight + labelHeight) * 3, Bitmap.Config.ARGB_8888);
        gallery.eraseColor(Color.rgb(12, 7, 18));
        Canvas galleryCanvas = new Canvas(gallery);
        Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        caption.setColor(Color.WHITE);
        caption.setTextSize(16f);

        long started = SystemClock.elapsedRealtimeNanos();
        int index = 0;
        for (CensorAppearance.Type type : CensorAppearance.Type.values()) {
            CensorOverlayView view = new CensorOverlayView(context);
            view.setAppearance(appearance(type, true));
            Bitmap frame = gradient(tileWidth, tileHeight, index * 19);
            view.setTracks(tracks, tileWidth, tileHeight, frame);
            view.measure(exactly(tileWidth), exactly(tileHeight));
            view.layout(0, 0, tileWidth, tileHeight);

            Bitmap rendered = Bitmap.createBitmap(
                    tileWidth, tileHeight, Bitmap.Config.ARGB_8888);
            rendered.eraseColor(Color.TRANSPARENT);
            view.draw(new Canvas(rendered));
            assertTrue(type + " should draw a stable censor region",
                    opaquePixels(rendered, 45, 30, 195, 150) > 400);
            assertEquals(type + " should leave a distant corner clear",
                    Color.TRANSPARENT, rendered.getPixel(4, 4));

            int column = index % 3;
            int row = index / 3;
            float left = column * tileWidth;
            float top = row * (tileHeight + labelHeight);
            galleryCanvas.drawBitmap(rendered, left, top, null);
            galleryCanvas.drawText(type.name(), left + 8, top + tileHeight + 20, caption);
            rendered.recycle();
            view.release();
            index++;
        }
        long elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L;
        assertTrue("Synthetic live renderer pass took " + elapsedMs + " ms", elapsedMs < 4_000L);

        File directory = new File(context.getExternalFilesDir(null), "renderer-map");
        assertTrue(directory.exists() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(
                new File(directory, "live-renderers.png"))) {
            assertTrue(gallery.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        gallery.recycle();
    }

    @Test public void customPhraseIsVisibleAndFrameRefreshNeverBlanksTheCensor() {
        Context context = ApplicationProvider.getApplicationContext();
        List<TrackedObject> tracks = confirmedTracks();
        CensorOverlayView view = new CensorOverlayView(context);
        view.setAppearance(appearance(CensorAppearance.Type.PIXELATE, true));
        view.measure(exactly(240), exactly(180));
        view.layout(0, 0, 240, 180);

        view.setTracks(tracks, 240, 180, gradient(240, 180, 0));
        Bitmap first = draw(view, 240, 180);
        view.setTracks(tracks, 240, 180, gradient(240, 180, 91));
        Bitmap second = draw(view, 240, 180);

        assertTrue(opaquePixels(first, 45, 30, 195, 150) > 400);
        assertTrue(opaquePixels(second, 45, 30, 195, 150) > 400);
        assertNotEquals(first.getPixel(90, 70), second.getPixel(90, 70));
        assertTrue("The configured phrase should draw light glyph pixels",
                lightPixels(second, 45, 55, 195, 125) > 8);
        first.recycle();
        second.recycle();
        view.release();
    }

    @Test public void scrollMotionMovesExistingCensorBeforeNextDetectorFrame() {
        Context context = ApplicationProvider.getApplicationContext();
        CensorOverlayView view = new CensorOverlayView(context);
        view.setAppearance(new CensorAppearance(
                CensorAppearance.Type.BOX, 1, 0f, false, false,
                CensorAppearance.BorderEffect.CLASSIC, false, Color.MAGENTA,
                List.of(), false, 100, "rectangle", "SubHub", "Blocked"));
        ObjectTracker tracker = new ObjectTracker(DetectorConfig.builder().build());
        List<TrackedObject> tracks = tracker.update(List.of(new Detection(
                "EXPOSED_TEST", "EXPOSED", 1f, new BBox(20, 20, 60, 60), true, true)));
        view.setTracks(tracks, 100, 100, null);
        view.measure(exactly(100), exactly(100));
        view.layout(0, 0, 100, 100);

        view.offsetContent(0, -20);
        // Accessibility deltas are compositor-smoothed for at most 56 ms. Verify that the
        // existing censor reaches the authoritative event position before another detector frame
        // is required, rather than restoring the obsolete first-draw teleport contract.
        view.setRenderTimeForTest(SystemClock.uptimeMillis() + 100L);
        Bitmap moved = draw(view, 100, 100);

        assertEquals(Color.BLACK, moved.getPixel(50, 30));
        assertEquals(Color.TRANSPARENT, moved.getPixel(50, 70));
        moved.recycle();
        view.release();
    }

    @Test public void animatedRainbowBorderNeverEscapesItsWideTrackedRectangle() {
        Context context = ApplicationProvider.getApplicationContext();
        CensorOverlayView view = new CensorOverlayView(context);
        view.setAppearance(new CensorAppearance(
                CensorAppearance.Type.BOX, 1, 0f, true, true,
                CensorAppearance.BorderEffect.RAINBOW, false, Color.MAGENTA,
                List.of(), false, 100, "rectangle", "SubHub", "Blocked"));
        view.setBorderAnimationTimeForTest(1_000L);
        ObjectTracker tracker = new ObjectTracker(DetectorConfig.builder().build());
        List<Detection> detections = List.of(new Detection(
                "EXPOSED_TEST", "EXPOSED", 1f,
                new BBox(20, 70, 200, 20), true, true));
        List<TrackedObject> tracks = List.of();
        for (int frame = 0; frame < 5; frame++) tracks = tracker.update(detections);
        view.setTracks(tracks, 240, 180, null);
        view.measure(exactly(240), exactly(180));
        view.layout(0, 0, 240, 180);

        Bitmap rendered = draw(view, 240, 180);

        assertTrue("The tracked censor should still render", opaquePixels(
                rendered, 20, 70, 220, 90) > 500);
        assertEquals("An animated border must not rotate into a display-spanning line",
                0, opaquePixelsOutside(rendered, 20, 70, 220, 90));
        rendered.recycle();
        view.release();
    }

    @Test public void replacingPooledSourceFrameReturnsOwnershipExactlyOnce() {
        Context context = ApplicationProvider.getApplicationContext();
        CensorOverlayView view = new CensorOverlayView(context);
        Bitmap pooled = gradient(100, 100, 0);
        AtomicInteger releases = new AtomicInteger();
        List<TrackedObject> tracks = confirmedTracks();

        view.setTracks(tracks, 100, 100, pooled, 0, 0, 0, 0,
                releases::incrementAndGet);
        view.setTracks(tracks, 100, 100, null);

        assertEquals(1, releases.get());
        assertTrue(!pooled.isRecycled());
        pooled.recycle();
        view.release();
    }

    private static CensorAppearance appearance(CensorAppearance.Type type, boolean text) {
        return new CensorAppearance(type, 82, .08f, true, false,
                CensorAppearance.BorderEffect.CLASSIC, text, Color.MAGENTA,
                Arrays.asList("CUSTOM PHRASE"), false, 88, "rectangle",
                "SubHub", "Access blocked");
    }

    private static List<TrackedObject> confirmedTracks() {
        ObjectTracker tracker = new ObjectTracker(DetectorConfig.builder().build());
        List<Detection> detections = new ArrayList<>();
        detections.add(new Detection("EXPOSED_TEST", "EXPOSED", 1f,
                new BBox(55, 40, 130, 100), true, true));
        List<TrackedObject> tracks = List.of();
        for (int frame = 0; frame < 5; frame++) tracks = tracker.update(detections);
        return tracks;
    }

    private static Bitmap gradient(int width, int height, int shift) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        for (int y = 0; y < height; y++) {
            paint.setColor(Color.rgb((y + shift) % 256, 50, (255 - y + shift) & 0xff));
            canvas.drawRect(0, y, width, y + 1, paint);
        }
        return bitmap;
    }

    private static Bitmap draw(View view, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static int opaquePixels(Bitmap bitmap, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y += 2) {
            for (int x = left; x < right; x += 2) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 240) count++;
            }
        }
        return count;
    }

    private static int lightPixels(Bitmap bitmap, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.red(pixel) > 190 && Color.green(pixel) > 190
                        && Color.blue(pixel) > 190) count++;
            }
        }
        return count;
    }

    private static int opaquePixelsOutside(
            Bitmap bitmap, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (x >= left && x < right && y >= top && y < bottom) continue;
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) count++;
            }
        }
        return count;
    }

    private static int exactly(int pixels) {
        return View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY);
    }
}
