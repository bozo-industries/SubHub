package com.betasafe.app.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.betasafe.app.R;
import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.settings.CensorAppearance;

import java.util.ArrayList;
import java.util.List;

/** Full-screen, touch-through renderer for tracked censor regions. */
final class CensorOverlayView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawRect = new RectF();
    private List<TrackedObject> tracks = new ArrayList<>();
    private CensorAppearance appearance = CensorAppearance.defaults();
    private int captureWidth = 1;
    private int captureHeight = 1;

    CensorOverlayView(Context context) {
        super(context);
        fill.setColor(Color.rgb(13, 13, 20));
        fill.setAlpha(242);
        border.setColor(context.getColor(R.color.accent));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        label.setColor(context.getColor(R.color.text_primary));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(dp(11));
        label.setFakeBoldText(true);
    }

    void setTracks(List<TrackedObject> value, int sourceWidth, int sourceHeight) {
        tracks = new ArrayList<>(value);
        captureWidth = Math.max(1, sourceWidth);
        captureHeight = Math.max(1, sourceHeight);
        invalidate();
    }

    void setAppearance(CensorAppearance value) {
        appearance = value;
        border.setColor(value.getBorderColor());
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scaleX = (float) getWidth() / captureWidth;
        float scaleY = (float) getHeight() / captureHeight;
        float radius = dp(8);
        for (TrackedObject track : tracks) {
            BBox box = track.getBox();
            drawRect.set(
                    box.getX() * scaleX,
                    box.getY() * scaleY,
                    box.getRight() * scaleX,
                    box.getBottom() * scaleY);
            drawCensor(canvas, drawRect, radius);
            if (appearance.isShowBorder()) canvas.drawRoundRect(drawRect, radius, radius, border);
            if (drawRect.height() >= dp(28) && drawRect.width() >= dp(64)) {
                if (appearance.isShowText()) {
                    float baseline = drawRect.centerY() - (label.ascent() + label.descent()) / 2f;
                    canvas.drawText(
                            getContext().getString(R.string.blocked_label),
                            drawRect.centerX(), baseline, label);
                }
            }
        }
    }

    private void drawCensor(Canvas canvas, RectF rect, float radius) {
        int intensity = appearance.getIntensity();
        switch (appearance.getType()) {
            case PIXELATE:
                fill.setAlpha(255);
                float cell = Math.max(dp(8), Math.min(rect.width(), rect.height()) / 6f);
                int row = 0;
                for (float top = rect.top; top < rect.bottom; top += cell, row++) {
                    int column = 0;
                    for (float left = rect.left; left < rect.right; left += cell, column++) {
                        fill.setColor(((row + column) & 1) == 0
                                ? Color.rgb(13, 13, 20)
                                : Color.rgb(37, 37, 53));
                        canvas.drawRect(
                                left,
                                top,
                                Math.min(rect.right, left + cell),
                                Math.min(rect.bottom, top + cell),
                                fill);
                    }
                }
                break;
            case BLUR:
                fill.setColor(Color.rgb(26, 26, 36));
                fill.setAlpha(90 + (int) (intensity * 1.4f));
                canvas.drawRoundRect(rect, radius, radius, fill);
                break;
            case BAR:
                float center = rect.centerY();
                float halfHeight = Math.max(dp(12), rect.height() * 0.22f);
                rect.top = center - halfHeight;
                rect.bottom = center + halfHeight;
                fill.setColor(Color.BLACK);
                fill.setAlpha(255);
                canvas.drawRoundRect(rect, radius, radius, fill);
                break;
            case BOX:
            default:
                fill.setColor(Color.rgb(13, 13, 20));
                fill.setAlpha(180 + (int) (intensity * 0.75f));
                canvas.drawRoundRect(rect, radius, radius, fill);
                break;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
