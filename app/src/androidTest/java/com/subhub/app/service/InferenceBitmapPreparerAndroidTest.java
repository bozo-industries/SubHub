package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class InferenceBitmapPreparerAndroidTest {
    private static final String TAG = "UltraPreprocessTest";

    @Test public void ultraReadsBackModelSizedHardwareFrame() {
        Bitmap software = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888);
        new Canvas(software).drawColor(Color.rgb(74, 20, 95));
        Bitmap hardware = software.copy(Bitmap.Config.HARDWARE, false);
        assertNotNull(hardware);

        long[] fullCopyNanos = new long[5];
        long[] preparedNanos = new long[5];
        Bitmap lastPrepared = null;
        try {
            for (int index = 0; index < fullCopyNanos.length; index++) {
                long started = SystemClock.elapsedRealtimeNanos();
                Bitmap full = hardware.copy(Bitmap.Config.ARGB_8888, false);
                fullCopyNanos[index] = SystemClock.elapsedRealtimeNanos() - started;
                assertNotNull(full);
                full.recycle();

                started = SystemClock.elapsedRealtimeNanos();
                InferenceBitmapPreparer.Prepared prepared =
                        InferenceBitmapPreparer.prepare(hardware, 512, false);
                preparedNanos[index] = SystemClock.elapsedRealtimeNanos() - started;
                assertNotNull(prepared);
                if (lastPrepared != null) lastPrepared.recycle();
                lastPrepared = prepared.bitmap;
            }
            assertNotNull(lastPrepared);
            assertFalse(lastPrepared.isRecycled());
            assertEquals(Bitmap.Config.ARGB_8888, lastPrepared.getConfig());
            assertEquals(230, lastPrepared.getWidth());
            assertEquals(512, lastPrepared.getHeight());
            long fullBytes = 1080L * 2400L * 4L;
            assertTrue(lastPrepared.getAllocationByteCount() * 8L < fullBytes);

            Arrays.sort(fullCopyNanos);
            Arrays.sort(preparedNanos);
            Log.i(TAG, "median full readback=" + fullCopyNanos[2] / 1_000_000f
                    + " ms, prepared=" + preparedNanos[2] / 1_000_000f
                    + " ms, bytes=" + fullBytes + " -> "
                    + lastPrepared.getAllocationByteCount());
        } finally {
            if (lastPrepared != null && !lastPrepared.isRecycled()) lastPrepared.recycle();
            hardware.recycle();
            software.recycle();
        }
    }

    @Test public void sourceEffectsRetainTheModelSizedFrame() {
        Bitmap software = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888);
        Bitmap hardware = software.copy(Bitmap.Config.HARDWARE, false);
        assertNotNull(hardware);
        InferenceBitmapPreparer.Prepared prepared = null;
        try {
            prepared = InferenceBitmapPreparer.prepare(hardware, 512, true);
            assertNotNull(prepared);
            assertTrue(prepared.retainedSourceFrame);
            assertEquals(230, prepared.bitmap.getWidth());
            assertEquals(512, prepared.bitmap.getHeight());
        } finally {
            if (prepared != null && !prepared.bitmap.isRecycled()) prepared.bitmap.recycle();
            hardware.recycle();
            software.recycle();
        }
    }
}
