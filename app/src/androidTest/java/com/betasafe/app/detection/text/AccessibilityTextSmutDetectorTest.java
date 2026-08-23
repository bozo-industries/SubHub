package com.betasafe.app.detection.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class AccessibilityTextSmutDetectorTest {
    @Test public void visibleNativeTextProducesOneAlignedLineDetection() {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        try {
            node.setVisibleToUser(true);
            node.setText("ordinary first line that is safe touch yourself like a needy pet");
            node.setBoundsInScreen(new Rect(70, 700, 1010, 810));

            List<Detection> detections = new AccessibilityTextSmutDetector().detect(
                    node, balanced(), 1080, 2400);

            assertEquals(1, detections.size());
            BBox box = detections.get(0).getBox();
            assertTrue("Expected the matched lower line: " + box, box.getY() >= 748);
            assertTrue("Expected line-height geometry: " + box, box.getHeight() <= 65);
        } finally {
            node.recycle();
        }
    }

    @Test public void explicitLeafDescriptionRemainsSupportedWithoutOcr() {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        try {
            node.setVisibleToUser(true);
            node.setContentDescription("life is just rapeslop");
            node.setBoundsInScreen(new Rect(70, 900, 1010, 980));

            List<Detection> detections = new AccessibilityTextSmutDetector().detect(
                    node, balanced(), 1080, 2400);

            assertEquals(1, detections.size());
            assertTrue(detections.get(0).getBox().getHeight() <= 84);
        } finally {
            node.recycle();
        }
    }

    private static TextSmutConfig balanced() {
        return new TextSmutConfig(true, TextSmutConfig.SENSITIVITY_BALANCED,
                TextSmutConfig.DEFAULT_CATEGORIES);
    }
}
