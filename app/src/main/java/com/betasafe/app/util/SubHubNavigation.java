package com.betasafe.app.util;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.ImageViewCompat;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;
import com.betasafe.app.appmode.AppModeActivity;
import com.betasafe.app.penance.PenanceActivity;

/** Fixed three-destination product navigation for SubHub's primary surfaces. */
public final class SubHubNavigation {
    public enum Screen { CENSOR, LIMITS, MONEY }

    private SubHubNavigation() {}

    public static void bind(Activity activity, View root, Screen active) {
        bindTab(activity, root.findViewById(R.id.nav_censor),
                root.findViewById(R.id.nav_censor_icon), root.findViewById(R.id.nav_censor_label),
                active, Screen.CENSOR, MainActivity.class);
        bindTab(activity, root.findViewById(R.id.nav_limits),
                root.findViewById(R.id.nav_limits_icon), root.findViewById(R.id.nav_limits_label),
                active, Screen.LIMITS, AppModeActivity.class);
        bindTab(activity, root.findViewById(R.id.nav_money),
                root.findViewById(R.id.nav_money_icon), root.findViewById(R.id.nav_money_label),
                active, Screen.MONEY, PenanceActivity.class);
    }

    private static void bindTab(Activity activity, View tab, ImageView icon, TextView label,
            Screen active, Screen destination, Class<? extends Activity> target) {
        if (tab == null) return;
        boolean selected = destination == active;
        tab.setVisibility(View.VISIBLE);
        tab.setAlpha(selected ? 1f : 0.82f);
        if (label != null) label.setTextColor(activity.getColor(R.color.text_primary));
        if (icon != null) ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(
                activity.getColor(selected ? R.color.accent : R.color.text_primary)));
        tab.setBackgroundResource(selected
                ? R.drawable.bg_bottom_tab_active : android.R.color.transparent);
        tab.setOnClickListener(view -> {
            if (selected) return;
            activity.startActivity(new Intent(activity, target)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            activity.overridePendingTransition(0, 0);
        });
    }
}
