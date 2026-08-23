package com.betasafe.app.detection.text;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.betasafe.app.detection.Detection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded extraction of visible native app text from a user-enabled Accessibility session. */
public final class AccessibilityTextSmutDetector {
    private static final int MAX_NODES = 400;
    private static final int MAX_TEXT_LENGTH = 2_000;
    private final TextSmutDetectionFactory factory =
            new TextSmutDetectionFactory(new SmutTextClassifier());

    public List<Detection> detect(
            AccessibilityNodeInfo root,
            TextSmutConfig config,
            int width,
            int height) {
        if (root == null || config == null || !config.isEnabled()) return Collections.emptyList();
        List<Detection> result = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        pending.add(root);
        int visited = 0;
        while (!pending.isEmpty() && visited++ < MAX_NODES) {
            AccessibilityNodeInfo node = pending.removeFirst();
            boolean ownsNode = node != root;
            try {
                if (!node.isVisibleToUser()) continue;
                String text = textOf(node);
                if (!text.isEmpty()) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    Detection detection = factory.create(text, bounds, config, width, height);
                    if (detection != null) result.add(detection);
                }
                for (int index = 0; index < node.getChildCount(); index++) {
                    AccessibilityNodeInfo child = node.getChild(index);
                    if (child != null) pending.addLast(child);
                }
            } finally {
                if (ownsNode) node.recycle();
            }
        }
        while (!pending.isEmpty()) pending.removeFirst().recycle();
        return Collections.unmodifiableList(result);
    }

    private static String textOf(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String first = text == null ? "" : text.toString().trim();
        String second = description == null ? "" : description.toString().trim();
        String combined = first;
        if (!second.isEmpty() && !second.equalsIgnoreCase(first)) {
            combined = first.isEmpty() ? second : first + " " + second;
        }
        return combined.length() <= MAX_TEXT_LENGTH
                ? combined : combined.substring(0, MAX_TEXT_LENGTH);
    }
}
