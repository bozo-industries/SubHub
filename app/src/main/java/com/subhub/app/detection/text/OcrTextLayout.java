package com.subhub.app.detection.text;

import android.graphics.Rect;

import com.subhub.app.detection.Detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Classifies sliding OCR line windows while preserving each rendered line's exact geometry. */
final class OcrTextLayout {
    private static final int MAX_WINDOW_LINES = 3;

    private OcrTextLayout() {}

    static List<Detection> classify(
            List<Line> lines,
            TextSmutConfig config,
            TextSmutDetectionFactory factory,
            int width,
            int height) {
        if (lines == null || lines.isEmpty() || config == null || !config.isEnabled()) {
            return Collections.emptyList();
        }
        List<Detection> result = new ArrayList<>();
        for (int start = 0; start < lines.size(); start++) {
            StringBuilder window = new StringBuilder();
            List<NormalizedLine> normalizedLines = new ArrayList<>();
            for (int end = start; end < lines.size()
                    && end < start + MAX_WINDOW_LINES; end++) {
                Line line = lines.get(end);
                if (line == null || line.bounds == null || line.bounds.isEmpty()) break;
                if (window.length() > 0) window.append('\n');
                window.append(line.text);
                normalizedLines.add(new NormalizedLine(
                        line, SmutTextClassifier.normalize(line.text)));
                SmutTextClassifier.Match match = factory.classify(
                        window.toString(), config, true);
                if (!match.isMatched()) continue;
                addMatchedLines(result, normalizedLines, match, factory, width, height);
            }
        }
        return Collections.unmodifiableList(
                DetectionFusion.merge(Collections.emptyList(), result));
    }

    private static void addMatchedLines(
            List<Detection> result,
            List<NormalizedLine> lines,
            SmutTextClassifier.Match match,
            TextSmutDetectionFactory factory,
            int width,
            int height) {
        int cursor = 0;
        boolean added = false;
        for (NormalizedLine line : lines) {
            int start = cursor;
            int end = start + line.normalized.length();
            cursor = end + 1;
            if (line.normalized.isEmpty() || match.getEndIndex() <= start
                    || match.getStartIndex() >= end) continue;
            Detection detection = factory.createExact(
                    line.source.bounds, match, width, height, "TEXT_SMUT_OCR_");
            if (detection != null) {
                result.add(detection);
                added = true;
            }
        }
        // Semantic matches intentionally cover the complete short window. Defensive fallback for
        // normalization edge cases keeps their exact line geometry instead of reverting to a
        // coarse block rectangle.
        if (!added && match.isMatched()) {
            for (NormalizedLine line : lines) {
                Detection detection = factory.createExact(
                        line.source.bounds, match, width, height, "TEXT_SMUT_OCR_");
                if (detection != null) result.add(detection);
            }
        }
    }

    static final class Line {
        final String text;
        final Rect bounds;

        Line(String text, Rect bounds) {
            this.text = text == null ? "" : text;
            this.bounds = bounds == null ? null : new Rect(bounds);
        }
    }

    private static final class NormalizedLine {
        final Line source;
        final String normalized;

        NormalizedLine(Line source, String normalized) {
            this.source = source;
            this.normalized = normalized;
        }
    }
}
