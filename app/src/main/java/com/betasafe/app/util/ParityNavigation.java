package com.betasafe.app.util;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;
import com.betasafe.app.browser.BrowserActivity;
import com.betasafe.app.capture.ExportActivity;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.commitment.CommitmentManager;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.settings.SettingsActivity;

/** Keeps the recovered five-tab shell visually and behaviorally consistent across activities. */
public final class ParityNavigation {
    public enum Screen { HOME, SETTINGS, BROWSER, HELP, EXPORT }

    private ParityNavigation() {}

    public static void bind(Activity activity, View root, Screen active) {
        TextView home = root.findViewById(R.id.tab_home);
        TextView settings = root.findViewById(R.id.tab_settings);
        TextView browser = root.findViewById(R.id.tab_browser);
        TextView help = root.findViewById(R.id.tab_help);
        TextView export = root.findViewById(R.id.tab_export);
        TextView[] tabs = {home, settings, browser, help, export};
        Screen[] screens = {Screen.HOME, Screen.SETTINGS, Screen.BROWSER, Screen.HELP, Screen.EXPORT};
        for (int index = 0; index < tabs.length; index++) {
            TextView tab = tabs[index];
            if (tab == null) continue;
            boolean selected = screens[index] == active;
            int color = activity.getColor(selected ? R.color.text_primary : R.color.text_muted);
            tab.setTextColor(color);
            tab.setCompoundDrawableTintList(ColorStateList.valueOf(
                    activity.getColor(selected ? R.color.accent : R.color.text_muted)));
            tab.setBackgroundResource(selected
                    ? R.drawable.bg_tab_active : android.R.color.transparent);
            Screen destination = screens[index];
            tab.setOnClickListener(view -> open(activity, active, destination));
        }
    }

    private static void open(Activity activity, Screen current, Screen destination) {
        if (current == destination) return;
        Class<? extends Activity> target;
        switch (destination) {
            case SETTINGS:
                target = CommitmentManager.isActive(activity)
                        ? CommitmentActivity.class : SettingsActivity.class;
                break;
            case BROWSER: target = BrowserActivity.class; break;
            case HELP: target = HelpActivity.class; break;
            case EXPORT: target = ExportActivity.class; break;
            case HOME:
            default: target = MainActivity.class; break;
        }
        activity.startActivity(new Intent(activity, target)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        activity.overridePendingTransition(0, 0);
    }
}
