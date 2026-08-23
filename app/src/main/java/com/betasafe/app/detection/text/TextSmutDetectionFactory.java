package com.betasafe.app.detection.text;

import android.graphics.Rect;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;

/** Converts a classified text rectangle into the existing visual-tracker contract. */
final class TextSmutDetectionFactory {
    private static final int PADDING_PX = 8;

    private final SmutTextClassifier classifier;

    TextSmutDetectionFactory(SmutTextClassifier classifier) {
        this.classifier = classifier;
    }

    Detection create(String text, Rect source, TextSmutConfig config, int width, int height) {
        if (source == null || source.isEmpty() || width <= 0 || height <= 0) return null;
        SmutTextClassifier.Match match = classifier.classify(text, config);
        if (!match.isMatched()) return null;
        int left = clamp(source.left - PADDING_PX, 0, width - 1);
        int top = clamp(source.top - PADDING_PX, 0, height - 1);
        int right = clamp(source.right + PADDING_PX, left + 1, width);
        int bottom = clamp(source.bottom + PADDING_PX, top + 1, height);
        return new Detection(
                "TEXT_SMUT_" + match.getCategory().toUpperCase(java.util.Locale.ROOT),
                "text_smut",
                match.getConfidence(),
                new BBox(left, top, right - left, bottom - top),
                true,
                false);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
