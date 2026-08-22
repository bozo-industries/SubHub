package com.betasafe.app.pack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Process-local view of settings controlled by the currently active configuration pack. */
public final class LockedSettings {
    private static volatile Set<String> locked = Collections.emptySet();

    private LockedSettings() {}

    public static boolean isLocked(String key) { return locked.contains(key); }
    public static Set<String> snapshot() { return locked; }

    static void set(Set<String> values) {
        locked = Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    static void clear() { locked = Collections.emptySet(); }
}
