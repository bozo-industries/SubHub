package com.subhub.app.service;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import androidx.annotation.RequiresApi;

/** Experimental worker-only synchronous preparation. Borrows source; returns owned software pixels.
 * Per-request renderer lifetime deliberately avoids shared state, stale images and teardown races.
 * No hard wall-time guarantee: native GPU synchronization can block, as can software readback.
 */
@RequiresApi(29)
final class GpuBitmapPreparer {
    private GpuBitmapPreparer() {}

    static InferenceBitmapPreparer.Prepared prepare(Bitmap source, int resolution, boolean retain) {
        if (source == null || source.isRecycled() || source.getConfig() != Bitmap.Config.HARDWARE
                || !ColorSpace.get(ColorSpace.Named.SRGB).equals(source.getColorSpace())) return null;
        int[] size = InferenceBitmapPreparer.targetDimensions(source.getWidth(), source.getHeight(), resolution);
        if (size[0] > 512 || size[1] > 512) return null;
        ImageReader reader = null;
        HardwareRenderer renderer = null;
        RenderNode node = null;
        Bitmap wrapped = null;
        Bitmap owned = null;
        long start = System.nanoTime();
        try {
            reader = ImageReader.newInstance(size[0], size[1], PixelFormat.RGBA_8888, 2,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            node = new RenderNode("inference-readback");
            node.setPosition(0, 0, size[0], size[1]);
            renderer = new HardwareRenderer();
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
            renderer.setOpaque(false);
            Canvas canvas = node.beginRecording();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(source, null, new Rect(0, 0, size[0], size[1]),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            node.endRecording();
            int status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            if ((status & ~HardwareRenderer.SYNC_REDRAW_REQUESTED) != 0) return null;
            long rendered = System.nanoTime();
            try (Image image = reader.acquireNextImage()) {
                if (image == null) return null;
                try (HardwareBuffer buffer = image.getHardwareBuffer()) {
                    if (buffer == null) return null;
                    wrapped = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                    if (wrapped == null) return null;
                    owned = wrapped.copy(Bitmap.Config.ARGB_8888, false);
                }
            }
            if (owned == null) return null;
            InferenceBitmapPreparer.Prepared result = new InferenceBitmapPreparer.Prepared(owned,
                    source.getWidth(), source.getHeight(), retain,
                    rendered - start, System.nanoTime() - rendered, true);
            owned = null;
            return result;
        } catch (RuntimeException unsupported) {
            // Caller uses the established software path; never return an old or partial frame.
            return null;
        } finally {
            if (owned != null) owned.recycle();
            if (wrapped != null) wrapped.recycle();
            if (renderer != null) renderer.destroy();
            if (node != null) node.discardDisplayList();
            if (reader != null) reader.close();
        }
    }
}
