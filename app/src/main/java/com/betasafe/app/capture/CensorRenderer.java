package com.betasafe.app.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;
import com.betasafe.app.detection.DetectionEngine;
import com.betasafe.app.settings.CensorAppearance;
import com.betasafe.app.settings.SettingsRepository;

import java.util.List;

import ai.onnxruntime.OrtException;

/** Applies the same recovered censor vocabulary to a standalone mutable image. */
public final class CensorRenderer implements AutoCloseable {
    private final Context context;
    private final CustomImagePool customImages;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filtered = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public CensorRenderer(Context context) {
        this.context = context.getApplicationContext();
        customImages = new CustomImagePool(this.context);
        customImages.reload();
        border.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    public RenderResult renderWithDetection(Bitmap source, DetectionEngine engine)
            throws OrtException {
        List<Detection> detections = engine.detect(source);
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        if (output == null) throw new IllegalStateException("Could not allocate export image");
        draw(output, source, detections, new SettingsRepository(context).loadAppearance());
        return new RenderResult(output, detections.size());
    }

    public void draw(
            Bitmap target,
            Bitmap source,
            List<Detection> detections,
            CensorAppearance appearance) {
        Canvas canvas = new Canvas(target);
        if (appearance.isReverseMode()) {
            drawEffect(canvas, source, new RectF(0, 0, target.getWidth(), target.getHeight()),
                    0, appearance.getType(), appearance.getReverseStrength(), appearance);
            for (int index = 0; index < detections.size(); index++) {
                RectF rect = paddedRect(detections.get(index).getBox(), target, appearance);
                int save = canvas.save();
                canvas.clipPath(shapePath(rect, appearance.getReverseCutoutShape()));
                canvas.drawBitmap(source, 0, 0, filtered);
                canvas.restoreToCount(save);
                if (appearance.isShowBorder()) drawBorder(canvas, rect, appearance);
            }
            return;
        }
        for (int index = 0; index < detections.size(); index++) {
            RectF rect = paddedRect(detections.get(index).getBox(), target, appearance);
            drawEffect(canvas, source, rect, index, appearance.getType(),
                    appearance.getIntensity(), appearance);
            if (appearance.isShowBorder()) drawBorder(canvas, rect, appearance);
            if (appearance.isShowText()
                    && appearance.getType() != CensorAppearance.Type.ERROR_POPUP
                    && rect.width() >= 64 && rect.height() >= 28) {
                drawLabel(canvas, rect, appearance.phraseFor(index), appearance);
            }
        }
    }

    private void drawEffect(
            Canvas canvas,
            Bitmap source,
            RectF rect,
            int id,
            CensorAppearance.Type requestedType,
            int intensity,
            CensorAppearance appearance) {
        CensorAppearance.Type type = requestedType;
        if (appearance.isReverseMode() && (type == CensorAppearance.Type.BOX
                || type == CensorAppearance.Type.BAR || type == CensorAppearance.Type.CUSTOM)) {
            type = CensorAppearance.Type.PIXELATE;
        }
        switch (type) {
            case PIXELATE:
                drawPixelate(canvas, source, rect, intensity, false);
                break;
            case BLUR:
                drawPixelate(canvas, source, rect, intensity, true);
                break;
            case CUSTOM:
                if (!drawCustom(canvas, rect, id)) drawSolid(canvas, rect, intensity);
                break;
            case STATIC:
                drawStatic(canvas, rect, id, intensity);
                break;
            case GLITCH:
                drawGlitch(canvas, source, rect, id, intensity);
                break;
            case TAPE:
                fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                        new int[]{Color.rgb(8, 8, 12), Color.rgb(255, 0, 128),
                                Color.rgb(8, 8, 12)}, null, Shader.TileMode.REPEAT));
                fill.setAlpha(255);
                canvas.drawRoundRect(rect, 8, 8, fill);
                fill.setShader(null);
                break;
            case ERROR_POPUP:
                drawErrorPopup(canvas, rect, appearance);
                break;
            case BAR:
                RectF bar = new RectF(rect.left, rect.centerY() - rect.height() * .22f,
                        rect.right, rect.centerY() + rect.height() * .22f);
                fill.setColor(Color.BLACK);
                fill.setAlpha(255);
                canvas.drawRoundRect(bar, 8, 8, fill);
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
        fill.setAlpha(180 + Math.round(Math.max(0, Math.min(100, intensity)) * .75f));
        canvas.drawRoundRect(rect, 10, 10, fill);
    }

    private void drawPixelate(
            Canvas canvas, Bitmap source, RectF destination, int intensity, boolean soften) {
        Rect sourceRect = sourceRect(source, destination);
        int divisor = Math.max(3, soften ? 3 + intensity / 8 : 3 + intensity / 5);
        int width = Math.max(1, sourceRect.width() / divisor);
        int height = Math.max(1, sourceRect.height() / divisor);
        Bitmap small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(soften
                ? Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG : 0);
        new Canvas(small).drawBitmap(source, sourceRect, new Rect(0, 0, width, height), paint);
        canvas.drawBitmap(small, null, destination, paint);
        small.recycle();
    }

    private boolean drawCustom(Canvas canvas, RectF rect, int id) {
        Bitmap bitmap = customImages.bitmapFor(id);
        if (bitmap == null || bitmap.isRecycled()) return false;
        float sourceRatio = (float) bitmap.getWidth() / bitmap.getHeight();
        float targetRatio = rect.width() / Math.max(1f, rect.height());
        Rect crop;
        if (sourceRatio > targetRatio) {
            int width = Math.round(bitmap.getHeight() * targetRatio);
            int left = (bitmap.getWidth() - width) / 2;
            crop = new Rect(left, 0, left + width, bitmap.getHeight());
        } else {
            int height = Math.round(bitmap.getWidth() / targetRatio);
            int top = (bitmap.getHeight() - height) / 2;
            crop = new Rect(0, top, bitmap.getWidth(), top + height);
        }
        canvas.drawBitmap(bitmap, crop, rect, filtered);
        return true;
    }

    private void drawStatic(Canvas canvas, RectF rect, int id, int intensity) {
        int cell = Math.max(3, 2 + intensity / 12);
        long seed = id * 1103515245L + Float.floatToIntBits(rect.left);
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

    private void drawGlitch(Canvas canvas, Bitmap source, RectF rect, int id, int intensity) {
        canvas.drawBitmap(source, sourceRect(source, rect), rect, filtered);
        int bands = 4 + intensity / 12;
        float height = rect.height() / bands;
        for (int index = 0; index < bands; index++) {
            float top = rect.top + index * height;
            float offset = ((index + id) % 3 - 1) * (3 + intensity / 10f);
            fill.setColor((index & 1) == 0 ? Color.CYAN : Color.MAGENTA);
            fill.setAlpha(75);
            canvas.drawRect(rect.left + offset, top, rect.right + offset,
                    Math.min(rect.bottom, top + height * .45f), fill);
        }
    }

    private void drawErrorPopup(Canvas canvas, RectF rect, CensorAppearance appearance) {
        fill.setColor(Color.rgb(232, 232, 238));
        fill.setAlpha(255);
        canvas.drawRect(rect, fill);
        float header = Math.min(rect.height() * .32f, Math.max(24, rect.height() * .2f));
        fill.setColor(appearance.getBorderColor());
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + header, fill);
        text.setColor(Color.WHITE);
        text.setTextSize(Math.max(10, header * .45f));
        drawFittedText(canvas, appearance.getErrorTitle(), rect.centerX(),
                rect.top + header * .68f, rect.width() - 12);
        text.setColor(Color.rgb(20, 20, 28));
        text.setTextSize(Math.max(9, Math.min(24, rect.height() * .12f)));
        drawFittedText(canvas, appearance.getErrorMessage(), rect.centerX(),
                rect.top + header + rect.height() * .28f, rect.width() - 12);
    }

    private void drawBorder(Canvas canvas, RectF rect, CensorAppearance appearance) {
        border.setStrokeWidth(Math.max(2, Math.min(rect.width(), rect.height()) * .025f));
        border.setColor(appearance.getBorderColor());
        border.setAlpha(255);
        border.setShader(null);
        if (appearance.getBorderEffect() == CensorAppearance.BorderEffect.GLOW) {
            for (int step = 4; step >= 1; step--) {
                border.setStrokeWidth(2 + step * 3);
                border.setAlpha(30 + step * 12);
                drawShape(canvas, rect, border, appearance);
            }
            border.setAlpha(255);
            border.setStrokeWidth(3);
        } else if (appearance.getBorderEffect() == CensorAppearance.BorderEffect.GRADIENT) {
            border.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    appearance.getBorderColor(), Color.WHITE, Shader.TileMode.CLAMP));
        } else if (appearance.getBorderEffect() == CensorAppearance.BorderEffect.RAINBOW) {
            border.setShader(new SweepGradient(rect.centerX(), rect.centerY(), new int[]{
                    Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE,
                    Color.MAGENTA, Color.RED}, null));
        }
        drawShape(canvas, rect, border, appearance);
        border.setShader(null);
        border.setAlpha(255);
    }

    private void drawShape(Canvas canvas, RectF rect, Paint paint, CensorAppearance appearance) {
        if (appearance.isReverseMode() && "ellipse".equals(appearance.getReverseCutoutShape())) {
            canvas.drawOval(rect, paint);
        } else if (appearance.isReverseMode()
                && "rounded".equals(appearance.getReverseCutoutShape())) {
            float radius = Math.min(rect.width(), rect.height()) * .22f;
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            canvas.drawRoundRect(rect, 10, 10, paint);
        }
    }

    private void drawLabel(
            Canvas canvas, RectF rect, String label, CensorAppearance appearance) {
        text.setColor(Color.WHITE);
        text.setTextSize(Math.max(10, Math.min(36, rect.height() * .18f)));
        text.setShadowLayer(3, 0, 1, Color.BLACK);
        float baseline = rect.centerY() - (text.ascent() + text.descent()) / 2f;
        drawFittedText(canvas, label, rect.centerX(), baseline, rect.width() - 12);
        text.clearShadowLayer();
    }

    private void drawFittedText(
            Canvas canvas, String value, float x, float baseline, float maximumWidth) {
        String fitted = value == null ? "" : value;
        while (fitted.length() > 4 && text.measureText(fitted) > maximumWidth) {
            fitted = fitted.substring(0, fitted.length() - 2) + "…";
        }
        canvas.drawText(fitted, x, baseline, text);
    }

    private static RectF paddedRect(
            BBox box, Bitmap target, CensorAppearance appearance) {
        BBox padded = box.padded(appearance.getSizePadding(), target.getWidth(), target.getHeight());
        return new RectF(padded.getX(), padded.getY(), padded.getRight(), padded.getBottom());
    }

    private static Rect sourceRect(Bitmap source, RectF destination) {
        int left = Math.max(0, Math.min(source.getWidth() - 1, Math.round(destination.left)));
        int top = Math.max(0, Math.min(source.getHeight() - 1, Math.round(destination.top)));
        int right = Math.max(left + 1,
                Math.min(source.getWidth(), Math.round(destination.right)));
        int bottom = Math.max(top + 1,
                Math.min(source.getHeight(), Math.round(destination.bottom)));
        return new Rect(left, top, right, bottom);
    }

    private static Path shapePath(RectF rect, String shape) {
        Path path = new Path();
        if ("ellipse".equals(shape)) path.addOval(rect, Path.Direction.CW);
        else if ("rounded".equals(shape)) {
            float radius = Math.min(rect.width(), rect.height()) * .22f;
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        } else path.addRect(rect, Path.Direction.CW);
        return path;
    }

    @Override
    public void close() {
        customImages.close();
    }

    public static final class RenderResult {
        private final Bitmap bitmap;
        private final int detectionCount;

        RenderResult(Bitmap bitmap, int detectionCount) {
            this.bitmap = bitmap;
            this.detectionCount = detectionCount;
        }

        public Bitmap getBitmap() { return bitmap; }
        public int getDetectionCount() { return detectionCount; }
    }
}
