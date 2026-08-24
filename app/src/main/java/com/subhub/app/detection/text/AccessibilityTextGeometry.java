package com.subhub.app.detection.text;

import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/** Resolves exact rendered text lines when an Accessibility provider exposes character bounds. */
final class AccessibilityTextGeometry {
    private static final int EDGE_PADDING_PX = 2;

    private AccessibilityTextGeometry() {}

    static Rect resolve(
            AccessibilityNodeInfo node,
            String classifiedText,
            SmutTextClassifier.Match match,
            int screenWidth,
            int screenHeight) {
        if (node == null || match == null || !match.isMatched() || classifiedText == null
                || classifiedText.isEmpty()) return null;
        CharSequence nodeText = node.getText();
        if (nodeText == null || !nodeText.toString().trim().equals(classifiedText)) return null;
        List<String> available = node.getAvailableExtraData();
        if (available == null
                || !available.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)) {
            return null;
        }
        String raw = nodeText.toString();
        int length = Math.min(raw.length(), 20_000);
        if (length <= 0) return null;
        Bundle arguments = new Bundle();
        arguments.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0);
        arguments.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, length);
        if (!node.refreshWithExtraData(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, arguments)) {
            return null;
        }
        Parcelable[] values = node.getExtras().getParcelableArray(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
        if (values == null || values.length == 0) return null;
        RectF[] characters = new RectF[Math.min(values.length, length)];
        for (int index = 0; index < characters.length; index++) {
            if (values[index] instanceof RectF) characters[index] = (RectF) values[index];
        }
        int normalizedLength = Math.max(1, match.getNormalizedLength());
        int rawStart = Math.round(match.getStartIndex() * raw.length()
                / (float) normalizedLength);
        int rawEnd = Math.round(match.getEndIndex() * raw.length()
                / (float) normalizedLength);
        return lineBounds(characters, rawStart, rawEnd, screenWidth, screenHeight);
    }

    /** Expands matched characters to their complete rendered lines. */
    static Rect lineBounds(
            RectF[] characters,
            int rawStart,
            int rawEnd,
            int screenWidth,
            int screenHeight) {
        if (characters == null || characters.length == 0
                || screenWidth <= 0 || screenHeight <= 0) return null;
        int start = clamp(rawStart, 0, characters.length - 1);
        int end = clamp(Math.max(start + 1, rawEnd), start + 1, characters.length);
        RectF matched = null;
        for (int index = start; index < end; index++) {
            RectF box = characters[index];
            if (!useful(box)) continue;
            if (matched == null) matched = new RectF(box);
            else matched.union(box);
        }
        if (matched == null) return null;

        RectF completeLines = new RectF(matched);
        for (RectF box : characters) {
            if (!useful(box) || !sharesRenderedLine(box, matched)) continue;
            completeLines.union(box);
        }
        int left = clamp((int) Math.floor(completeLines.left) - EDGE_PADDING_PX,
                0, screenWidth - 1);
        int top = clamp((int) Math.floor(completeLines.top) - EDGE_PADDING_PX,
                0, screenHeight - 1);
        int right = clamp((int) Math.ceil(completeLines.right) + EDGE_PADDING_PX,
                left + 1, screenWidth);
        int bottom = clamp((int) Math.ceil(completeLines.bottom) + EDGE_PADDING_PX,
                top + 1, screenHeight);
        return new Rect(left, top, right, bottom);
    }

    private static boolean sharesRenderedLine(RectF box, RectF matched) {
        float overlap = Math.min(box.bottom, matched.bottom) - Math.max(box.top, matched.top);
        return overlap > 0f && overlap >= Math.min(box.height(), matched.height()) * 0.45f;
    }

    private static boolean useful(RectF box) {
        return box != null && !box.isEmpty()
                && Float.isFinite(box.left) && Float.isFinite(box.top)
                && Float.isFinite(box.right) && Float.isFinite(box.bottom);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
