package com.betasafe.app.detection.text;

import com.betasafe.app.detection.BBox;

/** Projects a matched text span onto one or more bounded visual text lines. */
final class TextRegionProjector {
    private static final int HORIZONTAL_PADDING_PX = 4;
    private static final int VERTICAL_PADDING_PX = 2;
    private static final int MAX_PROJECTED_LINES = 12;

    private TextRegionProjector() {}

    static BBox project(
            String source,
            SmutTextClassifier.Match match,
            int sourceLeft,
            int sourceTop,
            int sourceRight,
            int sourceBottom,
            int screenWidth,
            int screenHeight) {
        if (match == null || !match.isMatched() || sourceRight <= sourceLeft
                || sourceBottom <= sourceTop || screenWidth <= 0 || screenHeight <= 0) return null;

        int left = clamp(sourceLeft, 0, screenWidth - 1);
        int top = clamp(sourceTop, 0, screenHeight - 1);
        int right = clamp(sourceRight, left + 1, screenWidth);
        int bottom = clamp(sourceBottom, top + 1, screenHeight);
        int width = right - left;
        int height = bottom - top;

        int targetLineHeight = clamp(Math.round(screenWidth * 0.045f), 32, 68);
        int normalizedLength = Math.max(1, match.getNormalizedLength());
        int explicitLines = explicitLineCount(source);
        int charactersPerLine = Math.max(12,
                Math.round(width / Math.max(8f, targetLineHeight * 0.48f)));
        int wrappedLines = Math.max(1,
                (normalizedLength + charactersPerLine - 1) / charactersPerLine);
        int linesFromHeight = Math.max(1, Math.round(height / (float) targetLineHeight));
        int lineCount = Math.max(explicitLines, wrappedLines);
        lineCount = Math.max(1, Math.min(MAX_PROJECTED_LINES,
                Math.min(lineCount, Math.max(explicitLines, linesFromHeight))));

        int naturalTextHeight = Math.min(height, targetLineHeight * lineCount);
        boolean tightTextNode = height <= Math.round(naturalTextHeight * 1.45f);
        int textHeight = tightTextNode ? height : naturalTextHeight;
        float lineHeight = textHeight / (float) lineCount;
        int charactersInLine = Math.max(1,
                (normalizedLength + lineCount - 1) / lineCount);
        int firstLine = clamp(match.getStartIndex() / charactersInLine, 0, lineCount - 1);
        int finalCharacter = Math.max(match.getStartIndex(), match.getEndIndex() - 1);
        int lastLine = clamp(finalCharacter / charactersInLine, firstLine, lineCount - 1);

        int projectedTop = top + Math.round(firstLine * lineHeight) - VERTICAL_PADDING_PX;
        int projectedBottom = top + Math.round((lastLine + 1) * lineHeight)
                + VERTICAL_PADDING_PX;
        int projectedLeft = left - HORIZONTAL_PADDING_PX;
        int projectedRight = right + HORIZONTAL_PADDING_PX;
        projectedLeft = clamp(projectedLeft, 0, screenWidth - 1);
        projectedTop = clamp(projectedTop, 0, screenHeight - 1);
        projectedRight = clamp(projectedRight, projectedLeft + 1, screenWidth);
        projectedBottom = clamp(projectedBottom, projectedTop + 1, screenHeight);
        return new BBox(projectedLeft, projectedTop,
                projectedRight - projectedLeft, projectedBottom - projectedTop);
    }

    private static int explicitLineCount(String source) {
        if (source == null || source.isEmpty()) return 1;
        int count = 1;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') count++;
        }
        return count;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
