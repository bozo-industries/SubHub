package com.subhub.app.atmosphere;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import com.subhub.app.R;
import com.subhub.app.appmode.AppModeManager;
import com.subhub.app.databinding.ActivityAtmosphereBinding;
import com.subhub.app.popup.IntensityPresets;
import com.subhub.app.popup.PopupStormActivity;
import com.subhub.app.popup.PopupStormManager;
import com.subhub.app.popup.PopupStormSettings;
import com.subhub.app.security.ControllerEditMode;
import com.subhub.app.security.ControllerPinGate;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.settings.GlobalSettingsActivity;
import com.subhub.app.subliminal.SubliminalSettings;
import com.subhub.app.subliminal.SubliminalSettingsActivity;
import com.subhub.app.subliminal.SubliminalSettingsRepository;

import java.util.Locale;

/** Shared, discoverable home for optional scene effects without adding navigation tabs. */
public final class AtmosphereActivity extends AppCompatActivity {
    private ActivityAtmosphereBinding binding;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAtmosphereBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonEditLock.setOnClickListener(view -> toggleSpace());
        binding.whispersCard.setOnClickListener(view -> openWhispers());
        binding.buttonWhispers.setOnClickListener(view -> openWhispers());
        binding.popupStormCard.setOnClickListener(view -> openPopupStorm());
        binding.buttonPopupStorm.setOnClickListener(view -> openPopupStorm());
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void toggleSpace() {
        if (ControllerPinManager.isDomModeActive()) {
            ControllerPinManager.enterSubMode();
            render();
        } else {
            ControllerPinGate.require(this, this::render, false);
        }
    }

    private void openWhispers() {
        requireDom(() -> {
            boolean enabled = new FeatureModuleManager(this).isSubliminalEnabled();
            startActivity(new Intent(this, enabled
                    ? SubliminalSettingsActivity.class : GlobalSettingsActivity.class));
        });
    }

    private void openPopupStorm() {
        requireDom(() -> startActivity(new Intent(this, PopupStormActivity.class)));
    }

    private void requireDom(Runnable action) {
        if (ControllerPinManager.isDomModeActive()) action.run();
        else ControllerPinGate.require(this, () -> {
            render();
            action.run();
        }, false);
    }

    private void render() {
        boolean dom = ControllerPinManager.isDomModeActive();
        ControllerEditMode.renderButton(this, binding.buttonEditLock);
        binding.atmosphereSubtitle.setText(dom
                ? R.string.atmosphere_subtitle_dom : R.string.atmosphere_subtitle_sub);
        binding.buttonWhispers.setText(dom
                ? (new FeatureModuleManager(this).isSubliminalEnabled()
                        ? R.string.atmosphere_shape_whispers
                        : R.string.atmosphere_enable_whispers)
                : R.string.atmosphere_unlock_to_edit);
        binding.buttonPopupStorm.setText(dom
                ? R.string.atmosphere_open_popup_storm : R.string.atmosphere_unlock_to_edit);
        renderWhispers();
        renderPopupStorm();
    }

    private void renderWhispers() {
        FeatureModuleManager modules = new FeatureModuleManager(this);
        if (!modules.isSubliminalEnabled()) {
            binding.whispersStatus.setText(R.string.atmosphere_state_disabled);
            binding.whispersSummary.setText(R.string.sub_subliminal_idle);
            return;
        }
        long now = System.currentTimeMillis();
        AppModeManager appMode = new AppModeManager(this);
        int state = !appMode.isAccessibilityEnabled()
                ? R.string.atmosphere_state_setup
                : appMode.isEffectivelyArmed(now)
                ? R.string.atmosphere_state_active : R.string.atmosphere_state_ready;
        binding.whispersStatus.setText(state);
        SubliminalSettings settings = new SubliminalSettingsRepository(this).load();
        binding.whispersSummary.setText(getString(R.string.atmosphere_whispers_summary,
                friendly(settings.getPreset().name()), appMode.getSubliminalPackages().size()));
    }

    private void renderPopupStorm() {
        PopupStormSettings settings = PopupStormSettings.load(this);
        int state = PopupStormManager.get().isRunning()
                ? R.string.popup_status_running
                : !settings.isEnabled()
                ? R.string.popup_status_off
                : !settings.isAcknowledged()
                ? R.string.popup_status_ack
                : !Settings.canDrawOverlays(this)
                ? R.string.popup_status_permission : R.string.popup_status_ready;
        binding.popupStormStatus.setText(state);
        String presetName = PopupStormSettings.preferences(this).getString(
                PopupStormSettings.K_PRESET, IntensityPresets.MEDIUM.name());
        String preset;
        try {
            preset = IntensityPresets.valueOf(presetName).getDisplayName();
        } catch (IllegalArgumentException ignored) {
            preset = IntensityPresets.MEDIUM.getDisplayName();
        }
        int sources = settings.getFolders().size()
                + (settings.getPackImageDir().isEmpty() ? 0 : 1);
        binding.popupStormSummary.setText(getResources().getQuantityString(
                R.plurals.atmosphere_popup_summary, sources, preset, sources));
    }

    private static String friendly(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower
                : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
