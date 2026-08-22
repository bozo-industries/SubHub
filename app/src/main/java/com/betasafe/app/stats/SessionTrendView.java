package com.betasafe.app.stats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.betasafe.app.R;

import java.util.ArrayList;
import java.util.List;

/** Compact dual-metric chart for the recovered 30-session history. */
public final class SessionTrendView extends View {
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<StatsRepository.SessionEntry> entries = new ArrayList<>();

    public SessionTrendView(Context context, AttributeSet attributes) {
        super(context, attributes);
        blockPaint.setColor(context.getColor(R.color.accent));
        timePaint.setColor(context.getColor(R.color.accent_dim));
        timePaint.setAlpha(110);
        axisPaint.setColor(context.getColor(R.color.surface_high));
        axisPaint.setStrokeWidth(dp(1));
        setContentDescription("Recent session blocks and duration chart");
    }

    public void setEntries(List<StatsRepository.SessionEntry> values) {
        entries = new ArrayList<>(values);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(8), top = dp(8), right = getWidth() - dp(8), bottom = getHeight() - dp(12);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
        if (entries.isEmpty()) return;
        int start = Math.max(0, entries.size() - 30);
        long maxDuration = 1;
        int maxBlocks = 1;
        for (int index = start; index < entries.size(); index++) {
            StatsRepository.SessionEntry entry = entries.get(index);
            maxDuration = Math.max(maxDuration, entry.getDurationSeconds());
            maxBlocks = Math.max(maxBlocks, entry.getBlocks());
        }
        int count = entries.size() - start;
        float slot = (right - left) / count;
        float barWidth = Math.max(dp(2), slot * 0.35f);
        float available = bottom - top;
        for (int offset = 0; offset < count; offset++) {
            StatsRepository.SessionEntry entry = entries.get(start + offset);
            float center = left + slot * (offset + 0.5f);
            float timeHeight = available * entry.getDurationSeconds() / maxDuration;
            float blockHeight = available * entry.getBlocks() / maxBlocks;
            canvas.drawRoundRect(new RectF(center - barWidth, bottom - timeHeight,
                    center, bottom), dp(2), dp(2), timePaint);
            canvas.drawRoundRect(new RectF(center, bottom - blockHeight,
                    center + barWidth, bottom), dp(2), dp(2), blockPaint);
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
