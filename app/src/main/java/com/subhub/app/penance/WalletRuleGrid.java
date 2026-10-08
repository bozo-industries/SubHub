package com.subhub.app.penance;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import java.util.IdentityHashMap;
import java.util.Map;

/** Keeps adjacent rule inputs aligned without clipping wrapped titles or help. */
public final class WalletRuleGrid extends GridLayout {
    private final Map<View, Integer> originalMinimums = new IdentityHashMap<>();

    public WalletRuleGrid(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        // Re-measure natural heights on width/font changes, including shrinking.
        for (Map.Entry<View, Integer> entry : originalMinimums.entrySet()) {
            entry.getKey().setMinimumHeight(entry.getValue());
        }
        super.onMeasure(widthSpec, heightSpec);
        boolean changed = false;
        for (int index = 0; index + 1 < getChildCount(); index += 2) {
            ViewGroup left = (ViewGroup) getChildAt(index);
            ViewGroup right = (ViewGroup) getChildAt(index + 1);
            for (int row = 0; row < 2; row++) {
                View first = left.getChildAt(row);
                View second = right.getChildAt(row);
                originalMinimums.putIfAbsent(first, first.getMinimumHeight());
                originalMinimums.putIfAbsent(second, second.getMinimumHeight());
                int height = Math.max(first.getMeasuredHeight(), second.getMeasuredHeight());
                if (first.getMeasuredHeight() != second.getMeasuredHeight()) {
                    first.setMinimumHeight(height);
                    second.setMinimumHeight(height);
                    changed = true;
                }
            }
        }
        if (changed) super.onMeasure(widthSpec, heightSpec);
    }
}
