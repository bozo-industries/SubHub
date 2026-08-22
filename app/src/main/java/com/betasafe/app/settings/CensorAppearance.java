package com.betasafe.app.settings;

import android.graphics.Color;

/** Immutable visual configuration for live censor overlays. */
public final class CensorAppearance {
    public enum Type {
        BOX("box"),
        PIXELATE("pixelate"),
        BLUR("blur"),
        BAR("bar");

        private final String preferenceValue;

        Type(String preferenceValue) { this.preferenceValue = preferenceValue; }
        public String getPreferenceValue() { return preferenceValue; }

        public static Type fromPreference(String value) {
            if (value != null) {
                for (Type type : values()) {
                    if (type.preferenceValue.equalsIgnoreCase(value)) return type;
                }
            }
            return BOX;
        }
    }

    private final Type type;
    private final int intensity;
    private final boolean showBorder;
    private final boolean showText;
    private final int borderColor;

    public CensorAppearance(
            Type type,
            int intensity,
            boolean showBorder,
            boolean showText,
            int borderColor) {
        this.type = type;
        this.intensity = Math.max(0, Math.min(100, intensity));
        this.showBorder = showBorder;
        this.showText = showText;
        this.borderColor = borderColor;
    }

    public static CensorAppearance defaults() {
        return new CensorAppearance(Type.BOX, 50, true, true, Color.rgb(255, 0, 128));
    }

    public Type getType() { return type; }
    public int getIntensity() { return intensity; }
    public boolean isShowBorder() { return showBorder; }
    public boolean isShowText() { return showText; }
    public int getBorderColor() { return borderColor; }
}
