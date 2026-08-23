package com.betasafe.app.settings;

import java.util.Locale;

/** The Android capture path used when the main protection button is pressed. */
public enum CaptureMethod {
    APP_MODE("app_mode"),
    SCREEN_RECORDING("screen_recording");

    private final String preferenceValue;

    CaptureMethod(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public String preferenceValue() {
        return preferenceValue;
    }

    public static CaptureMethod fromPreference(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return APP_MODE.preferenceValue.equals(normalized) ? APP_MODE : SCREEN_RECORDING;
    }
}
