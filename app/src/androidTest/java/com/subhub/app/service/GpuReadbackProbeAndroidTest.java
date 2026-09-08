package com.subhub.app.service;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Isolated probe only: no service, protection settings, tracker or production renderer changes. */
@RunWith(AndroidJUnit4.class)
public final class GpuReadbackProbeAndroidTest {
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test public void compareSmallGpuReadbackWithSoftwareReference() {
        assumeTrue(Build.VERSION.SDK_INT >= 29);
        Bitmap source = Bitmap.createBitmap(1344, 2992, Bitmap.Config.ARGB_8888);
        int[] row = new int[1344];
        for (int y = 0; y < 2992; y++) {
            for (int x = 0; x < row.length; x++) {
                row[x] = Color.rgb((x * 255 / 1344), y * 255 / 2992,
                        ((x / 19 + y / 23) % 2) * 255);
            }
            source.setPixels(row, 0, row.length, 0, y, row.length, 1);
        }
        Bitmap hardware = source.copy(Bitmap.Config.HARDWARE, false);
        assertNotNull(hardware);
        long[] gpuTimes = new long[9], cpuTimes = new long[9];
        RenderNode node = new RenderNode("readback-probe");
        node.setPosition(0, 0, 144, 320);
        HardwareRenderer renderer = new HardwareRenderer();
        try (ImageReader reader = ImageReader.newInstance(144, 320, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)) {
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
            renderer.setOpaque(true);
            double worstMae = 0;
            for (int i = -2; i < 9; i++) {
                long start = System.nanoTime();
                InferenceBitmapPreparer.Prepared cpu = InferenceBitmapPreparer.prepare(hardware, 320, false);
                long cpuTime = System.nanoTime() - start;
                assertNotNull(cpu);
                Bitmap smallHardware = null, gpu = null;
                try {
                    start = System.nanoTime();
                    // Mark every trial dirty. An unchanged retained display list can skip drawing,
                    // so sync success alone does not guarantee a newly queued ImageReader frame.
                    Canvas canvas = node.beginRecording();
                    canvas.drawBitmap(hardware, null, new Rect(0, 0, 144, 320),
                            new Paint(Paint.FILTER_BITMAP_FLAG));
                    node.endRecording();
                    int result = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
                    assertEquals("GPU render failed", 0, result & ~HardwareRenderer.SYNC_REDRAW_REQUESTED);
                    try (Image image = reader.acquireNextImage()) {
                        assertNotNull("No rendered frame", image);
                        try (HardwareBuffer buffer = image.getHardwareBuffer()) {
                            assertNotNull(buffer);
                            smallHardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                            assertNotNull(smallHardware);
                            gpu = smallHardware.copy(Bitmap.Config.ARGB_8888, false);
                            assertNotNull(gpu);
                        }
                    }
                    long gpuTime = System.nanoTime() - start;
                    int[] a = new int[144 * 320], b = new int[a.length];
                    cpu.bitmap.getPixels(a, 0, 144, 0, 0, 144, 320);
                    gpu.getPixels(b, 0, 144, 0, 0, 144, 320);
                    long error = 0;
                    for (int p = 0; p < a.length; p++) {
                        error += Math.abs(Color.red(a[p]) - Color.red(b[p]));
                        error += Math.abs(Color.green(a[p]) - Color.green(b[p]));
                        error += Math.abs(Color.blue(a[p]) - Color.blue(b[p]));
                    }
                    worstMae = Math.max(worstMae, error / (double)(a.length * 3));
                    if (i >= 0) { gpuTimes[i] = gpuTime; cpuTimes[i] = cpuTime; }
                } finally {
                    cpu.bitmap.recycle();
                    if (gpu != null) gpu.recycle();
                    if (smallHardware != null) smallHardware.recycle();
                }
            }
            Arrays.sort(gpuTimes); Arrays.sort(cpuTimes);
            Log.i("GpuReadbackProbe", "GPU_PROBE cpuMedianMs=" + cpuTimes[4] / 1e6
                    + " gpuMedianMs=" + gpuTimes[4] / 1e6 + " gpuMaxMs=" + gpuTimes[8] / 1e6
                    + " worstChannelMae=" + worstMae);
            assertTrue("GPU image fidelity mismatch", worstMae < 3.0);
        } finally {
            renderer.destroy(); node.discardDisplayList(); hardware.recycle(); source.recycle();
        }
    }
}
