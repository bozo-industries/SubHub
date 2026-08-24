package com.subhub.app.detection;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Class order and category mapping expected by the bundled 18-class NudeNet model. */
public final class NudeNetClassCatalog {
    public static final int CLASS_COUNT = 18;

    public static final List<String> CLASS_NAMES = Collections.unmodifiableList(Arrays.asList(
            "FEMALE_GENITALIA_COVERED",
            "FACE_FEMALE",
            "BUTTOCKS_EXPOSED",
            "FEMALE_BREAST_EXPOSED",
            "FEMALE_GENITALIA_EXPOSED",
            "MALE_BREAST_EXPOSED",
            "ANUS_EXPOSED",
            "FEET_EXPOSED",
            "BELLY_COVERED",
            "FEET_COVERED",
            "ARMPITS_COVERED",
            "ARMPITS_EXPOSED",
            "FACE_MALE",
            "BELLY_EXPOSED",
            "MALE_GENITALIA_EXPOSED",
            "ANUS_COVERED",
            "FEMALE_BREAST_COVERED",
            "BUTTOCKS_COVERED"));

    public static final Set<String> DEFAULT_ENABLED = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "genitals_female", "genitals_male", "breasts", "buttocks", "anus")));

    private static final Map<String, ClassInfo> CLASSES;

    static {
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        add(classes, "FEMALE_GENITALIA_EXPOSED", true, true, "genitals_female");
        add(classes, "MALE_GENITALIA_EXPOSED", true, true, "genitals_male");
        add(classes, "FEMALE_BREAST_EXPOSED", true, true, "breasts");
        add(classes, "BUTTOCKS_EXPOSED", true, true, "buttocks");
        add(classes, "ANUS_EXPOSED", true, true, "anus");
        add(classes, "FEMALE_GENITALIA_COVERED", true, false, "genitals_covered");
        add(classes, "FEMALE_BREAST_COVERED", true, false, "breasts_covered");
        add(classes, "BUTTOCKS_COVERED", true, false, "buttocks_covered");
        add(classes, "ANUS_COVERED", true, false, "anus_covered");
        add(classes, "MALE_BREAST_EXPOSED", false, true, "male_chest");
        add(classes, "BELLY_EXPOSED", false, true, "belly");
        add(classes, "BELLY_COVERED", false, false, "belly_covered");
        add(classes, "FEET_EXPOSED", false, true, "feet");
        add(classes, "FEET_COVERED", false, false, "feet_covered");
        add(classes, "ARMPITS_EXPOSED", false, true, "armpits");
        add(classes, "ARMPITS_COVERED", false, false, "armpits_covered");
        add(classes, "FACE_FEMALE", false, true, "face", "face_female");
        add(classes, "FACE_MALE", false, true, "face", "face_male");
        CLASSES = Collections.unmodifiableMap(classes);

        if (CLASS_NAMES.size() != CLASS_COUNT || !CLASSES.keySet().containsAll(CLASS_NAMES)) {
            throw new IllegalStateException("NudeNet class mapping does not match model output order");
        }
    }

    private NudeNetClassCatalog() {}

    private static void add(
            Map<String, ClassInfo> target,
            String name,
            boolean nsfw,
            boolean exposed,
            String... categories) {
        target.put(name, new ClassInfo(Arrays.asList(categories), nsfw, exposed));
    }

    public static ClassInfo byIndex(int index) {
        if (index < 0 || index >= CLASS_NAMES.size()) return null;
        return CLASSES.get(CLASS_NAMES.get(index));
    }

    public static String nameByIndex(int index) {
        return index >= 0 && index < CLASS_NAMES.size() ? CLASS_NAMES.get(index) : null;
    }

    public static final class ClassInfo {
        private final List<String> categories;
        private final boolean nsfw;
        private final boolean exposed;

        private ClassInfo(List<String> categories, boolean nsfw, boolean exposed) {
            this.categories = Collections.unmodifiableList(categories);
            this.nsfw = nsfw;
            this.exposed = exposed;
        }

        public List<String> getCategories() { return categories; }
        public boolean isNsfw() { return nsfw; }
        public boolean isExposed() { return exposed; }
    }
}
