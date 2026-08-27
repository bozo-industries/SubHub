package com.subhub.app.pack;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Stable, namespace-based locks owned by the currently active SubHub pack. */
public final class SubHubPackLocks {
    private static final String PREFS = "subhub_pack_state_v1";
    private static final String KEY_GROUPS = "active_lock_groups";

    private SubHubPackLocks() {}

    public static boolean isLocked(Context context, String group) {
        return groups(context).contains(group);
    }

    public static Set<String> groups(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> stored = preferences.getStringSet(KEY_GROUPS, Set.of());
        return Collections.unmodifiableSet(new LinkedHashSet<>(stored == null ? Set.of() : stored));
    }

    static void set(Context context, Set<String> groups) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_GROUPS, new LinkedHashSet<>(groups == null ? Set.of() : groups))
                .commit();
    }

    static void clear(Context context) { set(context, Set.of()); }
}
