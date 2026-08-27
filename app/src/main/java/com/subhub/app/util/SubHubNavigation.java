package com.subhub.app.util;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.ImageViewCompat;

import com.subhub.app.MainActivity;
import com.subhub.app.R;
import com.subhub.app.appmode.AppModeActivity;
import com.subhub.app.penance.PenanceActivity;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.settings.GlobalSettingsActivity;
import com.subhub.app.settings.SettingsActivity;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.studio.StudioActivity;

/** Feature-aware product navigation with Studio available in both Dom and Sub spaces. */
public final class SubHubNavigation {
    public enum Screen { HOME, CENSOR, LIMITS, MONEY, STUDIO, SETTINGS }

    private SubHubNavigation() {}

    public static void bind(Activity activity, View root, Screen active) {
        View navigation = root.findViewById(R.id.bottom_navigation);
        boolean domMode = ControllerPinManager.isDomModeActive();
        setVisible(navigation, true);
        FeatureModuleManager modules = new FeatureModuleManager(activity);
        setVisible(root.findViewById(R.id.nav_home), true);
        setVisible(root.findViewById(R.id.nav_censor), domMode && modules.isCensorEnabled());
        setVisible(root.findViewById(R.id.nav_limits), domMode && modules.isLimitsEnabled());
        setVisible(root.findViewById(R.id.nav_money), domMode && modules.isWalletEnabled());
        setVisible(root.findViewById(R.id.nav_studio), true);
        setVisible(root.findViewById(R.id.nav_settings), domMode);
        bindTab(activity, root.findViewById(R.id.nav_home),
                root.findViewById(R.id.nav_home_icon), root.findViewById(R.id.nav_home_label),
                active, Screen.HOME, MainActivity.class);
        bindTab(activity, root.findViewById(R.id.nav_censor),
                root.findViewById(R.id.nav_censor_icon), root.findViewById(R.id.nav_censor_label),
                active, Screen.CENSOR, SettingsActivity.class);
        bindTab(activity, root.findViewById(R.id.nav_limits),
                root.findViewById(R.id.nav_limits_icon), root.findViewById(R.id.nav_limits_label),
                active, Screen.LIMITS, AppModeActivity.class);
        bindTab(activity, root.findViewById(R.id.nav_money),
                root.findViewById(R.id.nav_money_icon), root.findViewById(R.id.nav_money_label),
                active, Screen.MONEY, PenanceActivity.class);
        bindTab(activity, root.findViewById(R.id.nav_studio),
                root.findViewById(R.id.nav_studio_icon), root.findViewById(R.id.nav_studio_label),
                active, Screen.STUDIO, StudioActivity.class);
        bindTab(activity, root.findViewById(R.id.nav_settings),
                root.findViewById(R.id.nav_settings_icon), root.findViewById(R.id.nav_settings_label),
                active, Screen.SETTINGS, GlobalSettingsActivity.class);
    }

    public static boolean redirectIfDisabled(Activity activity, Screen current) {
        FeatureModuleManager modules = new FeatureModuleManager(activity);
        boolean enabled = current == Screen.HOME || current == Screen.STUDIO
                || current == Screen.SETTINGS
                || current == Screen.CENSOR && modules.isCensorEnabled()
                || current == Screen.LIMITS && modules.isLimitsEnabled()
                || current == Screen.MONEY && modules.isWalletEnabled();
        if (enabled) return false;
        Class<? extends Activity> target = MainActivity.class;
        activity.startActivity(new Intent(activity, target)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        activity.finish();
        activity.overridePendingTransition(R.anim.subhub_page_pop_enter,
                R.anim.subhub_page_pop_exit);
        return true;
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private static void bindTab(Activity activity, View tab, ImageView icon, TextView label,
            Screen active, Screen destination, Class<? extends Activity> target) {
        if (tab == null) return;
        if (tab.getVisibility() != View.VISIBLE) return;
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
            boolean movingForward = destination.ordinal() > active.ordinal();
            activity.overridePendingTransition(
                    movingForward ? R.anim.subhub_page_enter : R.anim.subhub_page_pop_enter,
                    movingForward ? R.anim.subhub_page_exit : R.anim.subhub_page_pop_exit);
        });
    }
}
