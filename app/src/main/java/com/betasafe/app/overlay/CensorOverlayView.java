package com.betasafe.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
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
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawRect = new RectF();
    private final Rect sourceRect = new Rect();
    private final CustomImagePool customImages;

    private List<TrackedObject> tracks = new ArrayList<>();
    private CensorAppearance appearance = CensorAppearance.defaults();
    private int captureWidth = 1;
    private int captureHeight = 1;
    private Bitmap frame;

    CensorOverlayView(Context context) {
        super(context);
        customImages = new CustomImagePool(context);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        label.setColor(context.getColor(R.color.text_primary));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(dp(11));
        label.setFakeBoldText(true);
        label.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
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
        invalidate();
    }

    void setAppearance(CensorAppearance value) {
        CensorAppearance.Type previous = appearance.getType();
        appearance = value;
        if (value.getType() == CensorAppearance.Type.CUSTOM
                || previous == CensorAppearance.Type.CUSTOM) customImages.reload();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (appearance.isReverseMode()) drawReverse(canvas);
        else drawNormal(canvas);
        if (isAnimated()) postInvalidateDelayed(50);
    }

    private void drawNormal(Canvas canvas) {
        float scaleX = (float) getWidth() / captureWidth;
        float scaleY = (float) getHeight() / captureHeight;
        for (TrackedObject track : tracks) {
            setPaddedRect(track.getBox(), scaleX, scaleY);
            drawEffect(canvas, drawRect, track.getId(), appearance.getType(), appearance.getIntensity());
            if (appearance.isShowBorder()) drawBorder(canvas, drawRect);
            if (appearance.isShowText() && drawRect.height() >= dp(28)
                    && drawRect.width() >= dp(64)
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
                drawTape(canvas, rect);
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
        fill.setColor(Color.rgb(13, 13, 20));
        fill.setAlpha(180 + Math.round(Math.max(0, Math.min(100, intensity)) * 0.75f));
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
        int block = Math.max(3, Math.round(3 + intensity * 0.20f));
        int smallWidth = Math.max(1, sourceRect.width() / block);
        int smallHeight = Math.max(1, sourceRect.height() / block);
        Bitmap small = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888);
        Canvas smallCanvas = new Canvas(small);
        Paint nearest = new Paint();
        nearest.setFilterBitmap(false);
        smallCanvas.drawBitmap(frame, sourceRect, new Rect(0, 0, smallWidth, smallHeight), nearest);
        canvas.drawBitmap(small, null, rect, nearest);
        small.recycle();
        return true;
    }

    private boolean drawBlurredFrame(Canvas canvas, RectF rect, int intensity) {
        if (!prepareSourceRect(rect)) return false;
        int divisor = Math.max(3, 3 + intensity / 8);
        int smallWidth = Math.max(1, sourceRect.width() / divisor);
        int smallHeight = Math.max(1, sourceRect.height() / divisor);
        Bitmap small = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888);
        Canvas smallCanvas = new Canvas(small);
        Paint filtered = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        smallCanvas.drawBitmap(frame, sourceRect, new Rect(0, 0, smallWidth, smallHeight), filtered);
        canvas.drawBitmap(small, null, rect, filtered);
        small.recycle();
        return true;
    }

    private boolean drawCustom(Canvas canvas, RectF rect, int stableId) {
        Bitmap bitmap = customImages.bitmapFor(stableId);
        if (bitmap == null || bitmap.isRecycled()) return false;
        float sourceRatio = (float) bitmap.getWidth() / bitmap.getHeight();
        float targetRatio = rect.width() / rect.height();
        Rect source;
        if (sourceRatio > targetRatio) {
            int width = Math.round(bitmap.getHeight() * targetRatio);
            int left = (bitmap.getWidth() - width) / 2;
            source = new Rect(left, 0, left + width, bitmap.getHeight());
        } else {
            int height = Math.round(bitmap.getWidth() / targetRatio);
            int top = (bitmap.getHeight() - height) / 2;
            source = new Rect(0, top, bitmap.getWidth(), top + height);
        }
        canvas.drawBitmap(bitmap, source, rect, bitmapPaint);
        return true;
    }

    private void drawStatic(Canvas canvas, RectF rect, int stableId, int intensity) {
        int cell = Math.max(3, Math.round(dp(2 + intensity / 20f)));
        long seed = stableId * 1103515245L + SystemClock.uptimeMillis() / 80;
        fill.setShader(null);
        for (float y = rect.top; y < rect.bottom; y += cell) {
            for (float x = rect.left; x < rect.right; x += cell) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int value = (int) ((seed >>> 56) & 0xff);
                fill.setColor(Color.rgb(value, value, value));
                fill.setAlpha(255);
                canvas.drawRect(x, y, Math.min(rect.right, x + cell),
                        Math.min(rect.bottom, y + cell), fill);
            }
        }
    }

    private void drawGlitch(Canvas canvas, RectF rect, int stableId, int intensity) {
        if (!prepareSourceRect(rect)) {
            drawStatic(canvas, rect, stableId, intensity);
            return;
        }
        canvas.drawBitmap(frame, sourceRect, rect, bitmapPaint);
        int bands = 4 + intensity / 12;
        float bandHeight = rect.height() / bands;
        for (int band = 0; band < bands; band++) {
            float top = rect.top + band * bandHeight;
            float offset = ((band + stableId) % 3 - 1) * dp(3 + intensity / 12f);
            fill.setColor((band & 1) == 0 ? Color.CYAN : Color.MAGENTA);
            fill.setAlpha(70);
            canvas.drawRect(rect.left + offset, top, rect.right + offset,
                    Math.min(rect.bottom, top + bandHeight * 0.45f), fill);
        }
    }

    private void drawTape(Canvas canvas, RectF rect) {
        fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{Color.rgb(8, 8, 12), Color.rgb(255, 0, 128), Color.rgb(8, 8, 12)},
                null, Shader.TileMode.REPEAT));
        fill.setAlpha(255);
        canvas.drawRoundRect(rect, dp(4), dp(4), fill);
        fill.setShader(null);
    }

    private void drawErrorPopup(Canvas canvas, RectF rect) {
        fill.setShader(null);
        fill.setColor(Color.rgb(232, 232, 238));
        fill.setAlpha(255);
        canvas.drawRect(rect, fill);
        float headerHeight = Math.min(rect.height() * 0.32f, dp(34));
        fill.setColor(appearance.getBorderColor());
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + headerHeight, fill);
        label.setColor(Color.WHITE);
        label.setTextSize(Math.min(dp(12), headerHeight * 0.45f));
        drawText(canvas, rect.centerX(), rect.top + headerHeight * 0.65f,
                appearance.getErrorTitle(), rect.width() - dp(12));
        label.setColor(Color.rgb(20, 20, 28));
        label.setTextSize(Math.min(dp(11), rect.height() * 0.12f));
        drawText(canvas, rect.centerX(), rect.top + headerHeight + rect.height() * 0.28f,
                appearance.getErrorMessage(), rect.width() - dp(12));
        resetLabelPaint();
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
                || appearance.getType() == CensorAppearance.Type.GLITCH;
    }

    void release() {
        if (frame != null && !frame.isRecycled()) frame.recycle();
        frame = null;
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
