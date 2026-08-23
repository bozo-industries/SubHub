package com.betasafe.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import com.betasafe.app.R;
import com.betasafe.app.capture.CustomImagePool;
import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.settings.CensorAppearance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Full-screen, touch-through renderer for every recovered censor style and reverse mode. */
final class CensorOverlayView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint diagnosticsFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint diagnosticsText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint nearestPaint = new Paint();
    private final Paint filteredPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint cyanShiftPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint redShiftPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tapeRedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tapeYellowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawRect = new RectF();
    private final Rect sourceRect = new Rect();
    private final Rect scratchRect = new Rect();
    private final Rect effectSourceRect = new Rect();
    private final Rect bandSourceRect = new Rect();
    private final RectF effectRect = new RectF();
    private final RectF bandRect = new RectF();
    private final CustomImagePool customImages;

    private List<TrackedObject> tracks = new ArrayList<>();
    private CensorAppearance appearance = CensorAppearance.defaults();
    private int captureWidth = 1;
    private int captureHeight = 1;
    private Bitmap frame;
    private Bitmap effectScratch;
    private Canvas effectCanvas;
    private Bitmap noiseBitmap;
    private int[] noisePixels;
    private long noiseTick = Long.MIN_VALUE;
    private String diagnostics = "";

    CensorOverlayView(Context context) {
        super(context);
        customImages = new CustomImagePool(context);
        nearestPaint.setFilterBitmap(false);
        cyanShiftPaint.setAlpha(90);
        cyanShiftPaint.setColorFilter(new PorterDuffColorFilter(
                Color.rgb(0, 180, 255), PorterDuff.Mode.SRC_ATOP));
        redShiftPaint.setAlpha(90);
        redShiftPaint.setColorFilter(new PorterDuffColorFilter(
                Color.rgb(255, 0, 80), PorterDuff.Mode.SRC_ATOP));
        tapeRedPaint.setColor(Color.rgb(229, 57, 53));
        tapeRedPaint.setStrokeCap(Paint.Cap.SQUARE);
        tapeYellowPaint.setColor(Color.rgb(243, 211, 59));
        tapeYellowPaint.setStrokeCap(Paint.Cap.SQUARE);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        label.setColor(context.getColor(R.color.text_primary));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(dp(11));
        label.setFakeBoldText(true);
        label.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
        diagnosticsFill.setColor(Color.rgb(13, 13, 20));
        diagnosticsFill.setAlpha(225);
        diagnosticsText.setColor(context.getColor(R.color.text_primary));
        diagnosticsText.setTextSize(dp(10));
        diagnosticsText.setFakeBoldText(true);
    }

    void setTracks(
            List<TrackedObject> value,
            int sourceWidth,
            int sourceHeight,
            Bitmap latestFrame) {
        tracks = new ArrayList<>(value);
        captureWidth = Math.max(1, sourceWidth);
        captureHeight = Math.max(1, sourceHeight);
        if (frame != null && frame != latestFrame && !frame.isRecycled()) frame.recycle();
        frame = latestFrame;
        Set<Integer> activeIds = new HashSet<>();
        for (TrackedObject track : tracks) activeIds.add(track.getId());
        customImages.retainAssignments(activeIds);
        setVisibility(VISIBLE);
        invalidate();
    }

    /** Hide all censor pixels without treating an empty track list as reverse-mode content. */
    void clearContent() {
        tracks.clear();
        if (frame != null && !frame.isRecycled()) frame.recycle();
        frame = null;
        customImages.retainAssignments(new HashSet<>());
        setVisibility(INVISIBLE);
        invalidate();
    }

    void setAppearance(CensorAppearance value) {
        CensorAppearance.Type previous = appearance.getType();
        appearance = value;
        if (value.getType() == CensorAppearance.Type.CUSTOM
                || previous == CensorAppearance.Type.CUSTOM) {
            customImages.reloadAsync(this::postInvalidate);
        }
        invalidate();
    }

    void setDiagnostics(String value) {
        diagnostics = value == null ? "" : value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (appearance.isReverseMode()) drawReverse(canvas);
        else drawNormal(canvas);
        drawDiagnostics(canvas);
        if (isAnimated()) postInvalidateDelayed(50);
    }

    private void drawDiagnostics(Canvas canvas) {
        if (diagnostics.isEmpty()) return;
        String[] lines = diagnostics.split("\\n", 3);
        float padding = dp(10);
        float lineHeight = dp(15);
        float width = 0;
        for (String line : lines) width = Math.max(width, diagnosticsText.measureText(line));
        float left = dp(12);
        float top = dp(32);
        RectF panel = new RectF(left, top, left + width + padding * 2,
                top + lines.length * lineHeight + padding * 2);
        canvas.drawRoundRect(panel, dp(8), dp(8), diagnosticsFill);
        diagnosticsFill.setColor(getContext().getColor(R.color.accent));
        diagnosticsFill.setAlpha(255);
        canvas.drawRoundRect(new RectF(panel.left, panel.top, panel.left + dp(3), panel.bottom),
                dp(2), dp(2), diagnosticsFill);
        diagnosticsFill.setColor(Color.rgb(13, 13, 20));
        diagnosticsFill.setAlpha(225);
        float baseline = panel.top + padding - diagnosticsText.ascent();
        for (String line : lines) {
            canvas.drawText(line, panel.left + padding, baseline, diagnosticsText);
            baseline += lineHeight;
        }
    }

    private void drawNormal(Canvas canvas) {
        float scaleX = (float) getWidth() / captureWidth;
        float scaleY = (float) getHeight() / captureHeight;
        for (TrackedObject track : tracks) {
            setPaddedRect(track.getBox(), scaleX, scaleY);
            drawEffect(canvas, drawRect, track.getId(), appearance.getType(), appearance.getIntensity());
            if (appearance.isShowBorder()) drawBorder(canvas, drawRect);
            if (appearance.isShowText() && drawRect.height() >= dp(22)
                    && drawRect.width() >= dp(44)
                    && appearance.getType() != CensorAppearance.Type.ERROR_POPUP) {
                drawLabel(canvas, drawRect, appearance.phraseFor(track.getId()));
            }
        }
    }

    private void drawReverse(Canvas canvas) {
        RectF whole = new RectF(0, 0, getWidth(), getHeight());
        int layer = canvas.saveLayer(whole, null);
        CensorAppearance.Type type = appearance.getType();
        if (type == CensorAppearance.Type.BOX || type == CensorAppearance.Type.BAR
                || type == CensorAppearance.Type.CUSTOM) type = CensorAppearance.Type.PIXELATE;
        drawEffect(canvas, whole, 0, type, appearance.getReverseStrength());

        float scaleX = (float) getWidth() / captureWidth;
        float scaleY = (float) getHeight() / captureHeight;
        List<RectF> holes = new ArrayList<>();
        for (TrackedObject track : tracks) {
            setPaddedRect(track.getBox(), scaleX, scaleY);
            RectF hole = new RectF(drawRect);
            holes.add(hole);
            drawShape(canvas, hole, clear);
        }
        canvas.restoreToCount(layer);
        if (appearance.isShowBorder()) {
            for (RectF hole : holes) drawBorder(canvas, hole);
        }
    }

    private void setPaddedRect(BBox box, float scaleX, float scaleY) {
        float horizontal = box.getWidth() * appearance.getSizePadding() * scaleX;
        float vertical = box.getHeight() * appearance.getSizePadding() * scaleY;
        drawRect.set(
                Math.max(0, box.getX() * scaleX - horizontal),
                Math.max(0, box.getY() * scaleY - vertical),
                Math.min(getWidth(), box.getRight() * scaleX + horizontal),
                Math.min(getHeight(), box.getBottom() * scaleY + vertical));
    }

    private void drawEffect(
            Canvas canvas,
            RectF rect,
            int stableId,
            CensorAppearance.Type type,
            int intensity) {
        switch (type) {
            case PIXELATE:
                if (!drawPixelatedFrame(canvas, rect, intensity)) drawSolid(canvas, rect, intensity);
                break;
            case BLUR:
                if (!drawBlurredFrame(canvas, rect, intensity)) drawPixelatedFrame(canvas, rect, intensity);
                break;
            case CUSTOM:
                if (!drawCustom(canvas, rect, stableId)) drawSolid(canvas, rect, intensity);
                break;
            case STATIC:
                drawStatic(canvas, rect, stableId, intensity);
                break;
            case GLITCH:
                drawGlitch(canvas, rect, stableId, intensity);
                break;
            case TAPE:
                drawTape(canvas, rect, stableId, intensity);
                break;
            case ERROR_POPUP:
                drawErrorPopup(canvas, rect);
                break;
            case BAR:
                drawBar(canvas, rect);
                break;
            case BOX:
            default:
                drawSolid(canvas, rect, intensity);
                break;
        }
    }

    private void drawSolid(Canvas canvas, RectF rect, int intensity) {
        fill.setShader(null);
        fill.setColor(Color.BLACK);
        fill.setAlpha(255);
        canvas.drawRoundRect(rect, dp(8), dp(8), fill);
    }

    private void drawBar(Canvas canvas, RectF rect) {
        RectF bar = new RectF(rect);
        float halfHeight = Math.max(dp(12), rect.height() * 0.22f);
        bar.top = rect.centerY() - halfHeight;
        bar.bottom = rect.centerY() + halfHeight;
        fill.setShader(null);
        fill.setColor(Color.BLACK);
        fill.setAlpha(255);
        canvas.drawRoundRect(bar, dp(6), dp(6), fill);
    }

    private boolean drawPixelatedFrame(Canvas canvas, RectF rect, int intensity) {
        if (!prepareSourceRect(rect)) return false;
        int clamped = Math.max(1, Math.min(100, intensity));
        int minimum = Math.max(1, Math.min(sourceRect.width(), sourceRect.height()));
        float fraction = (clamped - 1) / 99f;
        int minimumBlock = Math.max(3, Math.round(minimum * .025f));
        int maximumBlock = Math.max(minimumBlock + 1, Math.round(minimum * .45f));
        int block = Math.max(2, Math.min(minimum,
                Math.round(minimumBlock + (maximumBlock - minimumBlock)
                        * fraction * fraction)));
        int smallWidth = Math.min(192, Math.max(1,
                (int) Math.ceil(sourceRect.width() / (float) block)));
        int smallHeight = Math.min(192, Math.max(1,
                (int) Math.ceil(sourceRect.height() / (float) block)));
        ensureScratch(smallWidth, smallHeight);
        scratchRect.set(0, 0, smallWidth, smallHeight);
        effectCanvas.drawBitmap(frame, sourceRect, scratchRect, filteredPaint);
        canvas.drawBitmap(effectScratch, scratchRect, rect, nearestPaint);
        return true;
    }

    private boolean drawBlurredFrame(Canvas canvas, RectF rect, int intensity) {
        if (!prepareSourceRect(rect)) return false;
        int clamped = Math.max(1, Math.min(100, intensity));
        int divisor = Math.max(2, Math.round(2 + (clamped - 1) / 99f * 18f));
        int smallWidth = Math.min(192, Math.max(1, sourceRect.width() / divisor));
        int smallHeight = Math.min(192, Math.max(1, sourceRect.height() / divisor));
        ensureScratch(smallWidth, smallHeight);
        scratchRect.set(0, 0, smallWidth, smallHeight);
        effectCanvas.drawBitmap(frame, sourceRect, scratchRect, filteredPaint);
        canvas.drawBitmap(effectScratch, scratchRect, rect, filteredPaint);
        return true;
    }

    private void ensureScratch(int width, int height) {
        if (effectScratch != null && !effectScratch.isRecycled()
                && effectScratch.getWidth() >= width && effectScratch.getHeight() >= height) return;
        if (effectScratch != null && !effectScratch.isRecycled()) effectScratch.recycle();
        effectScratch = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height),
                Bitmap.Config.ARGB_8888);
        effectCanvas = new Canvas(effectScratch);
    }

    private boolean drawCustom(Canvas canvas, RectF rect, int stableId) {
        CustomImagePool.PreparedImage prepared = customImages.imageFor(stableId);
        Bitmap bitmap = prepared == null ? null : prepared.bitmap();
        if (bitmap == null || bitmap.isRecycled()) return false;
        Rect source = prepared.cropFor(rect.width() / Math.max(1f, rect.height()));
        canvas.drawBitmap(bitmap, source, rect, bitmapPaint);
        return true;
    }

    private void drawStatic(Canvas canvas, RectF rect, int stableId, int intensity) {
        long tick = SystemClock.uptimeMillis() / 90L;
        if (noiseBitmap == null) {
            noiseBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            noisePixels = new int[128 * 128];
        }
        if (noiseTick != tick) {
            int clamped = Math.max(1, Math.min(100, intensity));
            float contrast = .35f + clamped / 100f * .65f;
            long seed = tick * 6364136223846793005L + stableId * 1103515245L;
            for (int index = 0; index < noisePixels.length; index++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int raw = (int) ((seed >>> 56) & 0xff);
                int value = Math.max(0, Math.min(255,
                        Math.round(128 * (1f - contrast) + raw * contrast)));
                noisePixels[index] = Color.rgb(value, value, value);
            }
            noiseBitmap.setPixels(noisePixels, 0, 128, 0, 0, 128, 128);
            noiseTick = tick;
        }
        int cell = Math.max(2, Math.min(7, 7 - Math.max(1, Math.min(100, intensity)) / 20));
        int cellsWide = Math.min(128, Math.max(1, (int) Math.ceil(rect.width() / cell)));
        int cellsHigh = Math.min(128, Math.max(1, (int) Math.ceil(rect.height() / cell)));
        effectSourceRect.set(0, 0, cellsWide, cellsHigh);
        canvas.drawBitmap(noiseBitmap, effectSourceRect, rect, nearestPaint);
        fill.setShader(null);
        fill.setColor(Color.BLACK);
        fill.setAlpha(70);
        float scanline = Math.max(4, cell * 2f);
        for (float y = rect.top; y < rect.bottom; y += scanline) {
            canvas.drawRect(rect.left, y, rect.right, Math.min(rect.bottom, y + 1f), fill);
        }
    }

    private void drawGlitch(Canvas canvas, RectF rect, int stableId, int intensity) {
        if (!prepareSourceRect(rect)) {
            drawStatic(canvas, rect, stableId, intensity);
            return;
        }
        int save = canvas.save();
        canvas.clipRect(rect);
        canvas.drawBitmap(frame, sourceRect, rect, bitmapPaint);
        float strength = Math.max(1, Math.min(100, intensity)) / 100f;
        float shift = Math.max(2f, (.02f + .06f * strength) * rect.width());
        effectRect.set(rect);
        effectRect.offset(-shift, 0);
        canvas.drawBitmap(frame, sourceRect, effectRect, cyanShiftPaint);
        effectRect.set(rect);
        effectRect.offset(shift, 0);
        canvas.drawBitmap(frame, sourceRect, effectRect, redShiftPaint);
        int bands = Math.max(3, Math.round(4 + strength * 8));
        int sourceBandHeight = Math.max(2, sourceRect.height() / 14);
        long tick = SystemClock.uptimeMillis() / 90L;
        for (int band = 0; band < bands; band++) {
            int available = Math.max(1, sourceRect.height() - sourceBandHeight);
            int sourceTop = sourceRect.top + hashInt(
                    tick + stableId * 131L + band * 31L) % available;
            int sourceBottom = Math.min(sourceRect.bottom, sourceTop + sourceBandHeight);
            float topRatio = (sourceTop - sourceRect.top) / (float) sourceRect.height();
            float bottomRatio = (sourceBottom - sourceRect.top) / (float) sourceRect.height();
            float offset = ((band % 3) - 1) * shift;
            bandSourceRect.set(sourceRect.left, sourceTop, sourceRect.right, sourceBottom);
            bandRect.set(rect.left + offset, rect.top + rect.height() * topRatio,
                    rect.right + offset, rect.top + rect.height() * bottomRatio);
            canvas.drawBitmap(frame, bandSourceRect, bandRect, bitmapPaint);
            fill.setColor(Color.WHITE);
            fill.setAlpha(48);
            canvas.drawRect(rect.left, bandRect.top, rect.right, bandRect.bottom, fill);
        }
        canvas.restoreToCount(save);
    }

    private void drawTape(Canvas canvas, RectF rect, int stableId, int intensity) {
        int save = canvas.save();
        canvas.clipRect(rect);
        if (prepareSourceRect(rect)) {
            bitmapPaint.setAlpha(70);
            canvas.drawBitmap(frame, sourceRect, rect, bitmapPaint);
            bitmapPaint.setAlpha(255);
        }
        fill.setShader(null);
        fill.setColor(Color.rgb(18, 18, 22));
        fill.setAlpha(210);
        canvas.drawRect(rect, fill);
        float spacing = Math.max(16f, Math.min(52f,
                50f - Math.max(1, Math.min(100, intensity)) * .25f));
        float shift = (SystemClock.uptimeMillis() / 25f + stableId * 7f) % spacing;
        tapeRedPaint.setStrokeWidth(Math.max(5f, spacing / 3f));
        tapeYellowPaint.setStrokeWidth(Math.max(3f, spacing / 5f));
        float rise = rect.height() * .45f;
        for (float x = rect.left - rect.width(); x < rect.right + rect.width(); x += spacing) {
            canvas.drawLine(x + shift, rect.bottom, x + shift + rise, rect.top, tapeRedPaint);
        }
        for (float x = rect.left - rect.width() + spacing / 2f;
             x < rect.right + rect.width(); x += spacing) {
            canvas.drawLine(x + shift, rect.bottom, x + shift + rise, rect.top, tapeYellowPaint);
        }
        canvas.restoreToCount(save);
    }

    private void drawErrorPopup(Canvas canvas, RectF rect) {
        fill.setShader(null);
        if (rect.width() < dp(82) || rect.height() < dp(46)) {
            fill.setColor(Color.rgb(245, 245, 245));
            fill.setAlpha(255);
            canvas.drawRect(rect, fill);
            fill.setColor(Color.rgb(215, 38, 48));
            float radius = Math.max(dp(4), Math.min(rect.width(), rect.height()) / 10f);
            float cx = rect.left + dp(6) + radius;
            canvas.drawCircle(cx, rect.centerY(), radius, fill);
            border.setColor(Color.WHITE);
            border.setStrokeWidth(Math.max(dp(1), radius / 3f));
            float cross = radius * .45f;
            canvas.drawLine(cx - cross, rect.centerY() - cross,
                    cx + cross, rect.centerY() + cross, border);
            canvas.drawLine(cx + cross, rect.centerY() - cross,
                    cx - cross, rect.centerY() + cross, border);
            return;
        }
        effectRect.set(rect);
        effectRect.offset(dp(3), dp(3));
        fill.setColor(Color.argb(58, 0, 0, 0));
        canvas.drawRect(effectRect, fill);
        fill.setColor(Color.rgb(240, 240, 240));
        fill.setAlpha(255);
        canvas.drawRect(rect, fill);
        border.setColor(Color.rgb(118, 118, 118));
        border.setStrokeWidth(dp(1));
        canvas.drawRect(rect, border);
        float headerHeight = Math.max(dp(21), Math.min(dp(32), rect.height() * .22f));
        fill.setColor(Color.WHITE);
        canvas.drawRect(rect.left + dp(1), rect.top + dp(1), rect.right - dp(1),
                rect.top + headerHeight, fill);

        float iconSize = Math.max(dp(14), Math.min(dp(38),
                Math.min(rect.width(), rect.height()) * .22f));
        float iconLeft = rect.left + Math.max(dp(10), rect.width() * .07f);
        float iconTop = rect.top + headerHeight + Math.max(dp(8), rect.height() * .08f);
        fill.setColor(Color.rgb(215, 38, 48));
        canvas.drawCircle(iconLeft + iconSize / 2f, iconTop + iconSize / 2f,
                iconSize / 2f, fill);
        border.setColor(Color.WHITE);
        border.setStrokeWidth(Math.max(dp(2), iconSize / 8f));
        border.setStrokeCap(Paint.Cap.ROUND);
        float crossA = iconSize * .30f;
        float crossB = iconSize * .70f;
        canvas.drawLine(iconLeft + crossA, iconTop + crossA,
                iconLeft + crossB, iconTop + crossB, border);
        canvas.drawLine(iconLeft + crossB, iconTop + crossA,
                iconLeft + crossA, iconTop + crossB, border);
        border.setStrokeCap(Paint.Cap.BUTT);

        label.clearShadowLayer();
        label.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        label.setTextAlign(Paint.Align.LEFT);
        label.setColor(Color.rgb(20, 20, 20));
        label.setTextSize(Math.max(dp(8), Math.min(dp(12), headerHeight - dp(8))));
        canvas.drawText(appearance.getErrorTitle(), rect.left + dp(10),
                rect.top + headerHeight * .68f, label);
        float messageLeft = iconLeft + iconSize + Math.max(dp(9), rect.width() * .05f);
        label.setTextSize(Math.max(dp(8), Math.min(dp(13), rect.height() * .12f)));
        drawWrappedText(canvas, appearance.getErrorMessage(), messageLeft,
                iconTop - dp(2) + label.getTextSize(), rect.right - dp(12),
                rect.bottom - dp(12));

        float buttonHeight = Math.max(dp(20), Math.min(dp(30), rect.height() * .20f));
        float buttonWidth = Math.max(dp(58), Math.min(dp(92), rect.width() * .28f));
        effectRect.set(rect.right - buttonWidth - dp(12), rect.bottom - buttonHeight - dp(10),
                rect.right - dp(12), rect.bottom - dp(10));
        fill.setColor(Color.rgb(250, 250, 250));
        canvas.drawRect(effectRect, fill);
        border.setColor(Color.rgb(0, 120, 215));
        border.setStrokeWidth(dp(1));
        canvas.drawRect(effectRect, border);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(Math.max(dp(8), Math.min(dp(11), buttonHeight - dp(10))));
        canvas.drawText("OK", effectRect.centerX(),
                effectRect.centerY() - (label.ascent() + label.descent()) / 2f, label);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        resetLabelPaint();
    }

    private void drawWrappedText(
            Canvas canvas, String text, float left, float baseline, float right, float bottom) {
        String line = "";
        float lineHeight = label.getTextSize() * 1.18f;
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (label.measureText(candidate) <= right - left || line.isEmpty()) {
                line = candidate;
            } else {
                if (baseline > bottom) return;
                canvas.drawText(line, left, baseline, label);
                baseline += lineHeight;
                line = word;
            }
        }
        if (!line.isEmpty() && baseline <= bottom) canvas.drawText(line, left, baseline, label);
    }

    private static long mix(long seed) {
        long first = (seed ^ (seed >>> 33)) * -49064778989728563L;
        long second = (first ^ (first >>> 33)) * -4265267296055464877L;
        return second ^ (second >>> 33);
    }

    private static int hashInt(long seed) {
        return (int) (mix(seed) & 0x7fffffffL);
    }

    private void drawBorder(Canvas canvas, RectF rect) {
        border.setStrokeWidth(dp(2));
        border.setShader(null);
        border.setColor(appearance.getBorderColor());
        border.setAlpha(255);
        switch (appearance.getBorderEffect()) {
            case GLOW:
                for (int step = 4; step >= 1; step--) {
                    border.setStrokeWidth(dp(2 + step * 2));
                    border.setAlpha(30 + step * 12);
                    drawShape(canvas, rect, border);
                }
                border.setStrokeWidth(dp(2));
                border.setAlpha(255);
                break;
            case GRADIENT:
                border.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                        appearance.getBorderColor(), Color.WHITE, Shader.TileMode.CLAMP));
                break;
            case RAINBOW:
                float phase = appearance.isAnimateBorder()
                        ? (SystemClock.uptimeMillis() % 4000L) / 4000f * 360f : 0f;
                border.setShader(new SweepGradient(rect.centerX(), rect.centerY(),
                        new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                                Color.BLUE, Color.MAGENTA, Color.RED}, null));
                canvas.save();
                canvas.rotate(phase, rect.centerX(), rect.centerY());
                drawShape(canvas, rect, border);
                canvas.restore();
                border.setShader(null);
                return;
            case CLASSIC:
            default:
                break;
        }
        drawShape(canvas, rect, border);
        border.setShader(null);
        border.setAlpha(255);
    }

    private void drawShape(Canvas canvas, RectF rect, Paint paint) {
        if (appearance.isReverseMode()
                && "ellipse".equals(appearance.getReverseCutoutShape())) canvas.drawOval(rect, paint);
        else if (appearance.isReverseMode()
                && "rounded".equals(appearance.getReverseCutoutShape())) {
            canvas.drawRoundRect(rect, Math.min(rect.width(), rect.height()) * 0.22f,
                    Math.min(rect.width(), rect.height()) * 0.22f, paint);
        } else canvas.drawRoundRect(rect, dp(8), dp(8), paint);
    }

    private void drawLabel(Canvas canvas, RectF rect, String text) {
        resetLabelPaint();
        label.setTextSize(Math.min(dp(11), Math.max(dp(8), rect.height() * 0.20f)));
        fill.setShader(null);
        fill.setColor(Color.BLACK);
        fill.setAlpha(205);
        float bandHeight = Math.min(rect.height(), Math.max(dp(22), label.getTextSize() * 1.75f));
        RectF band = new RectF(rect.left, rect.centerY() - bandHeight / 2f,
                rect.right, rect.centerY() + bandHeight / 2f);
        canvas.drawRoundRect(band, dp(5), dp(5), fill);
        float baseline = rect.centerY() - (label.ascent() + label.descent()) / 2f;
        drawText(canvas, rect.centerX(), baseline, text, rect.width() - dp(12));
    }

    private void drawText(
            Canvas canvas, float x, float baseline, String text, float requestedMaximumWidth) {
        float maximumWidth = Math.max(dp(24), requestedMaximumWidth);
        String value = text;
        while (value.length() > 4 && label.measureText(value) > maximumWidth) {
            value = value.substring(0, value.length() - 2) + "…";
        }
        canvas.drawText(value, x, baseline, label);
    }

    private void resetLabelPaint() {
        label.setColor(getContext().getColor(R.color.text_primary));
        label.setTextSize(dp(11));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setFakeBoldText(true);
        label.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
    }

    private boolean prepareSourceRect(RectF destination) {
        if (frame == null || frame.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return false;
        int left = Math.max(0, Math.min(frame.getWidth() - 1,
                Math.round(destination.left / getWidth() * frame.getWidth())));
        int top = Math.max(0, Math.min(frame.getHeight() - 1,
                Math.round(destination.top / getHeight() * frame.getHeight())));
        int right = Math.max(left + 1, Math.min(frame.getWidth(),
                Math.round(destination.right / getWidth() * frame.getWidth())));
        int bottom = Math.max(top + 1, Math.min(frame.getHeight(),
                Math.round(destination.bottom / getHeight() * frame.getHeight())));
        sourceRect.set(left, top, right, bottom);
        return true;
    }

    private boolean isAnimated() {
        return appearance.isAnimateBorder()
                || appearance.getType() == CensorAppearance.Type.STATIC
                || appearance.getType() == CensorAppearance.Type.GLITCH
                || appearance.getType() == CensorAppearance.Type.TAPE;
    }

    void release() {
        if (frame != null && !frame.isRecycled()) frame.recycle();
        frame = null;
        if (effectScratch != null && !effectScratch.isRecycled()) effectScratch.recycle();
        effectScratch = null;
        effectCanvas = null;
        if (noiseBitmap != null && !noiseBitmap.isRecycled()) noiseBitmap.recycle();
        noiseBitmap = null;
        noisePixels = null;
        customImages.close();
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
