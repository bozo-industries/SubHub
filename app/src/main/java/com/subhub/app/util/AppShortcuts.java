package com.subhub.app.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

import com.subhub.app.MainActivity;
import com.subhub.app.R;

import java.util.Arrays;

/** Dynamic launcher shortcuts routed through the exported main activity. */
public final class AppShortcuts {
    public static final String ACTION_START_PROTECTION = "com.subhub.app.action.SHORTCUT_PROTECT";
    public static final String ACTION_OPEN_BROWSER = "com.subhub.app.action.SHORTCUT_BROWSER";

    private AppShortcuts() {}

    public static void install(Context context) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        Intent protection = new Intent(context, MainActivity.class)
                .setAction(ACTION_START_PROTECTION);
        Intent browser = new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN_BROWSER);
        ShortcutInfo start = new ShortcutInfo.Builder(context, "start_protection")
                .setShortLabel(context.getString(R.string.shortcut_start_protection))
                .setLongLabel(context.getString(R.string.shortcut_start_protection_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_monochrome))
                .setIntent(protection)
                .build();
        ShortcutInfo openBrowser = new ShortcutInfo.Builder(context, "open_browser")
                .setShortLabel(context.getString(R.string.shortcut_open_browser))
                .setLongLabel(context.getString(R.string.shortcut_open_browser_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_monochrome))
                .setIntent(browser)
                .build();
        manager.setDynamicShortcuts(Arrays.asList(start, openBrowser));
    }

    public static void reportUsed(Context context, String id) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager != null) manager.reportShortcutUsed(id);
    }
}
