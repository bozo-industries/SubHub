package com.betasafe.app.popup;

import android.content.Context;
import android.content.SharedPreferences;

/** Recovered Popup Storm intensity presets. */
public enum IntensityPresets {
    GENTLE("Gentle", .5f, 1.5f, 3, false, 30f, 4f),
    MEDIUM("Medium", 2f, 1f, 8, false, 30f, 4f),
    INTENSE("Intense", 4f, .7f, 12, true, 25f, 4f),
    OVERLOAD("Overload", 7f, .5f, 15, true, 15f, 5f);

    private final String displayName;
    private final float spawnRate;
    private final float duration;
    private final int maximum;
    private final boolean burst;
    private final float burstFrequency;
    private final float burstDuration;

    IntensityPresets(String displayName, float spawnRate, float duration, int maximum,
            boolean burst, float burstFrequency, float burstDuration) {
        this.displayName = displayName;
        this.spawnRate = spawnRate;
        this.duration = duration;
        this.maximum = maximum;
        this.burst = burst;
        this.burstFrequency = burstFrequency;
        this.burstDuration = burstDuration;
    }

    public String getDisplayName() { return displayName; }

    public void apply(Context context) {
        SharedPreferences.Editor edit = PopupStormSettings.preferences(context).edit();
        edit.putFloat(PopupStormSettings.K_SPAWN_RATE, spawnRate);
        edit.putFloat(PopupStormSettings.K_DISPLAY_DURATION, duration);
        edit.putInt(PopupStormSettings.K_MAX_SIMULTANEOUS, maximum);
        edit.putBoolean(PopupStormSettings.K_BURST_ENABLED, burst);
        edit.putFloat(PopupStormSettings.K_BURST_FREQUENCY, burstFrequency);
        edit.putFloat(PopupStormSettings.K_BURST_DURATION, burstDuration);
        edit.apply();
    }
}
