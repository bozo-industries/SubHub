package com.subhub.app.detection.text;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** User-facing configuration for local explicit-text recognition. */
public final class TextSmutConfig {
    public static final String CATEGORY_EXPLICIT = "explicit_language";
    public static final String CATEGORY_FETISH = "fetish_context";
    public static final String CATEGORY_SOLICITATION = "sexual_solicitation";
    public static final int SENSITIVITY_STRICT = 0;
    public static final int SENSITIVITY_BALANCED = 1;
    public static final int SENSITIVITY_BROAD = 2;
    public static final Set<String> DEFAULT_CATEGORIES;

    static {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add(CATEGORY_EXPLICIT);
        categories.add(CATEGORY_FETISH);
        categories.add(CATEGORY_SOLICITATION);
        DEFAULT_CATEGORIES = Collections.unmodifiableSet(categories);
    }

    private final boolean enabled;
    private final int sensitivity;
    private final Set<String> enabledCategories;

    public TextSmutConfig(
            boolean enabled,
            int sensitivity,
            Set<String> enabledCategories) {
        this.enabled = enabled;
        this.sensitivity = Math.max(SENSITIVITY_STRICT, Math.min(SENSITIVITY_BROAD, sensitivity));
        Set<String> categories = enabledCategories == null
                ? DEFAULT_CATEGORIES : enabledCategories;
        this.enabledCategories = Collections.unmodifiableSet(new LinkedHashSet<>(categories));
    }

    public boolean isEnabled() { return enabled; }
    public int getSensitivity() { return sensitivity; }
    public Set<String> getEnabledCategories() { return enabledCategories; }
}
