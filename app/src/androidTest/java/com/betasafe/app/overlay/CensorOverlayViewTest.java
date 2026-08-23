package com.betasafe.app.overlay;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;
import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.detection.ObjectTracker;
import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.settings.CensorAppearance;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

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

    private static int exactly(int pixels) {
        return View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY);
    }
}
