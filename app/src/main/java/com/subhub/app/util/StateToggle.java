package com.subhub.app.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.subhub.app.R;

/** A native, accessible switch with an explicit status pill instead of a sliding thumb. */
public final class StateToggle extends SwitchMaterial {
    private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF statusBounds = new RectF();

    public StateToggle(Context context) { this(context, null); }

    public StateToggle(Context context, AttributeSet attributes) {
        super(context, attributes);
        setThumbDrawable(null);
        setTrackDrawable(null);
        setShowText(false);
        setTextOn(context.getString(R.string.control_state_on));
        setTextOff(context.getString(R.string.control_state_off));
        setSwitchPadding(dp(12));
        setMinHeight(dp(48));
    }

    @Override public void onMeasure(int widthSpec, int heightSpec) {
        statusPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        int statusWidth = Math.max(dp(64), Math.round(Math.max(
                statusPaint.measureText(getTextOn().toString()),
                statusPaint.measureText(getTextOff().toString()))) + dp(24));
        setSwitchMinWidth(statusWidth);
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getSwitchMinWidth();
        int height = Math.max(dp(32), Math.round(statusPaint.getTextSize()) + dp(14));
        float left = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? getPaddingLeft() : getWidth() - getPaddingRight() - width;
        float top = (getHeight() - height) / 2f;
        statusBounds.set(left, top, left + width, top + height);
        statusPaint.setStyle(Paint.Style.FILL);
        statusPaint.setColor(getContext().getColor(isChecked()
                ? R.color.accent_glow : R.color.surface_card_raised));
        statusPaint.setAlpha(isEnabled() ? 255 : 110);
        canvas.drawRoundRect(statusBounds, dp(10), dp(10), statusPaint);
        statusPaint.setStyle(Paint.Style.STROKE);
        statusPaint.setStrokeWidth(dp(1));
        statusPaint.setColor(getContext().getColor(isChecked()
                ? R.color.accent : R.color.text_muted));
        statusPaint.setAlpha(isEnabled() ? 255 : 110);
        canvas.drawRoundRect(statusBounds, dp(10), dp(10), statusPaint);
        statusPaint.setStyle(Paint.Style.FILL);
        statusPaint.setTextAlign(Paint.Align.CENTER);
        statusPaint.setColor(getContext().getColor(isChecked()
                ? R.color.text_primary : R.color.text_secondary));
        statusPaint.setAlpha(isEnabled() ? 255 : 110);
        Paint.FontMetrics metrics = statusPaint.getFontMetrics();
        float baseline = statusBounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText((isChecked() ? getTextOn() : getTextOff()).toString(),
                statusBounds.centerX(), baseline, statusPaint);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
