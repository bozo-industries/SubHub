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

import java.util.ArrayList;
import java.util.List;

/** Full-screen, touch-through renderer for tracked censor regions. */
final class CensorOverlayView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawRect = new RectF();
    private List<TrackedObject> tracks = new ArrayList<>();
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
            canvas.drawRoundRect(drawRect, radius, radius, fill);
            canvas.drawRoundRect(drawRect, radius, radius, border);
            if (drawRect.height() >= dp(28) && drawRect.width() >= dp(64)) {
                float baseline = drawRect.centerY() - (label.ascent() + label.descent()) / 2f;
                canvas.drawText(
                        getContext().getString(R.string.blocked_label),
                        drawRect.centerX(), baseline, label);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
