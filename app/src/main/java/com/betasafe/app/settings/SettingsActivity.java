package com.betasafe.app.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.R;
import com.betasafe.app.appmode.AppModeActivity;
import com.betasafe.app.capture.CustomImagesActivity;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.commitment.CommitmentManager;
import com.betasafe.app.databinding.ActivitySettingsBinding;
import com.betasafe.app.detection.DetectionPreset;
import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.diagnostics.DiagnosticsActivity;
import com.betasafe.app.overlay.CensorPhrases;
import com.betasafe.app.pack.LockedSettings;
import com.betasafe.app.security.ControllerPinGate;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.pack.PackManager;
import com.betasafe.app.pack.PacksActivity;
import com.betasafe.app.profiles.ProfilesActivity;
import com.betasafe.app.popup.PopupStormActivity;
import com.betasafe.app.stats.StatsRepository;

import java.util.LinkedHashSet;
import java.util.Set;

/** Styled, source-native editor for live detector and censor preferences. */
public final class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private SettingsRepository repository;
    private StatsRepository stats;
    private boolean bindingValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.getRoot().setFocusableInTouchMode(true);
        binding.getRoot().requestFocus();
        repository = new SettingsRepository(this);
        stats = new StatsRepository(this);
        new PackManager(this);
        bindValues();
        attachListeners();
        applyLockState();
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonEditLock.setOnClickListener(view -> toggleEditSession());
    }

    private void toggleEditSession() {
        if (ControllerPinManager.isSessionUnlocked()) {
            ControllerPinManager.lockNow();
            applyLockState();
        } else {
            ControllerPinGate.require(this, this::applyLockState, false);
        }
    }

    private void bindValues() {
        bindingValues = true;
        CensorAppearance appearance = repository.loadAppearance();
        setCheckedStyle(radioFor(appearance.getType()));
        binding.intensitySeek.setProgress(appearance.getIntensity());
        binding.intensityValue.setText(percent(appearance.getIntensity()));
        int padding = Math.round(appearance.getSizePadding() * 100);
        binding.paddingSeek.setProgress(padding);
        binding.paddingValue.setText(percent(padding));
        binding.switchBorder.setChecked(appearance.isShowBorder());
        binding.switchText.setChecked(appearance.isShowText());
        binding.switchAnimateBorder.setChecked(appearance.isAnimateBorder());
        binding.borderEffectGroup.check(radioFor(appearance.getBorderEffect()));
        binding.switchReverse.setChecked(appearance.isReverseMode());
        binding.reverseStrengthSeek.setProgress(appearance.getReverseStrength());
        binding.reverseStrengthValue.setText(percent(appearance.getReverseStrength()));

        DetectionPreset preset = repository.loadDetectionPreset();
        binding.presetGroup.check(radioFor(preset));
        DetectorConfig detector = repository.loadDetectorConfig();
        int confidence = Math.round(detector.getConfidenceThreshold() * 100);
        binding.confidenceSeek.setProgress(confidence);
        binding.confidenceValue.setText(percent(confidence));
        Set<String> categories = detector.getEnabledCategories();
        binding.switchGenitalsFemale.setChecked(categories.contains("genitals_female"));
        binding.switchGenitalsMale.setChecked(categories.contains("genitals_male"));
        binding.switchBreasts.setChecked(categories.contains("breasts"));
        binding.switchButtocks.setChecked(categories.contains("buttocks"));
        binding.switchAnus.setChecked(categories.contains("anus"));
        binding.switchFaces.setChecked(categories.contains("face"));
        binding.switchMaleChest.setChecked(categories.contains("male_chest"));
        binding.switchBelly.setChecked(categories.contains("belly"));
        binding.switchFeet.setChecked(categories.contains("feet"));
        binding.switchArmpits.setChecked(categories.contains("armpits"));
        binding.switchCovered.setChecked(containsCoveredCategory(categories));

        Set<String> phraseCategories = repository.preferences().getStringSet(
                SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES,
                CensorPhrases.DEFAULT_ENABLED);
        binding.switchPhraseShort.setChecked(phraseCategories.contains("short"));
        binding.switchPhraseDenial.setChecked(phraseCategories.contains("denial"));
        binding.switchPhraseHumiliation.setChecked(phraseCategories.contains("humiliation"));
        binding.switchPhraseEdge.setChecked(phraseCategories.contains("edge"));
        binding.switchPhraseFindom.setChecked(phraseCategories.contains("findom"));
        binding.switchPhraseNtr.setChecked(phraseCategories.contains("ntr"));
        binding.switchPhraseGooner.setChecked(phraseCategories.contains("gooner"));
        Set<String> customPhrases = repository.preferences().getStringSet(
                SettingsRepository.KEY_CUSTOM_PHRASES, new LinkedHashSet<>());
        binding.customPhrases.setText(joinLines(customPhrases));
        bindingValues = false;
    }

    private void attachListeners() {
        for (int id : styleRadioIds()) {
            RadioButton radio = findViewById(id);
            radio.setOnCheckedChangeListener((button, checked) -> {
                if (bindingValues || !checked) return;
                bindingValues = true;
                for (int otherId : styleRadioIds()) {
                    if (otherId != button.getId()) ((RadioButton) findViewById(otherId)).setChecked(false);
                }
                bindingValues = false;
                saveAll();
            });
        }
        binding.borderEffectGroup.setOnCheckedChangeListener((group, checkedId) -> saveAll());
        binding.presetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (bindingValues) return;
            DetectionPreset preset = presetFor(checkedId);
            repository.saveDetectionPreset(preset);
            bindingValues = true;
            int confidence = Math.round(preset.getConfidence() * 100);
            binding.confidenceSeek.setProgress(confidence);
            binding.confidenceValue.setText(percent(confidence));
            bindingValues = false;
            saveAll();
        });
        binding.intensitySeek.setOnSeekBarChangeListener(new SavingSeekListener() {
            @Override public void update(int progress, boolean fromUser) {
                binding.intensityValue.setText(percent(progress));
                if (fromUser) saveAll();
            }
        });
        binding.paddingSeek.setOnSeekBarChangeListener(new SavingSeekListener() {
            @Override public void update(int progress, boolean fromUser) {
                binding.paddingValue.setText(percent(progress));
                if (fromUser) saveAll();
            }
        });
        binding.confidenceSeek.setOnSeekBarChangeListener(new SavingSeekListener() {
            @Override public void update(int progress, boolean fromUser) {
                binding.confidenceValue.setText(percent(progress));
                if (fromUser) saveAll();
            }
        });
        binding.reverseStrengthSeek.setOnSeekBarChangeListener(new SavingSeekListener() {
            @Override public void update(int progress, boolean fromUser) {
                binding.reverseStrengthValue.setText(percent(progress));
                if (fromUser) saveAll();
            }
        });
        CompoundButton.OnCheckedChangeListener changed = (button, checked) -> saveAll();
        binding.switchBorder.setOnCheckedChangeListener(changed);
        binding.switchText.setOnCheckedChangeListener(changed);
        binding.switchAnimateBorder.setOnCheckedChangeListener(changed);
        binding.switchReverse.setOnCheckedChangeListener(changed);
        binding.switchGenitalsFemale.setOnCheckedChangeListener(changed);
        binding.switchGenitalsMale.setOnCheckedChangeListener(changed);
        binding.switchBreasts.setOnCheckedChangeListener(changed);
        binding.switchButtocks.setOnCheckedChangeListener(changed);
        binding.switchAnus.setOnCheckedChangeListener(changed);
        binding.switchFaces.setOnCheckedChangeListener(changed);
        binding.switchMaleChest.setOnCheckedChangeListener(changed);
        binding.switchBelly.setOnCheckedChangeListener(changed);
        binding.switchFeet.setOnCheckedChangeListener(changed);
        binding.switchArmpits.setOnCheckedChangeListener(changed);
        binding.switchCovered.setOnCheckedChangeListener(changed);
        binding.switchPhraseShort.setOnCheckedChangeListener(changed);
        binding.switchPhraseDenial.setOnCheckedChangeListener(changed);
        binding.switchPhraseHumiliation.setOnCheckedChangeListener(changed);
        binding.switchPhraseEdge.setOnCheckedChangeListener(changed);
        binding.switchPhraseFindom.setOnCheckedChangeListener(changed);
        binding.switchPhraseNtr.setOnCheckedChangeListener(changed);
        binding.switchPhraseGooner.setOnCheckedChangeListener(changed);
        binding.buttonSavePhrases.setOnClickListener(view -> {
            saveCustomPhrases();
            saveAll();
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        binding.buttonCustomImages.setOnClickListener(view ->
                startActivity(new Intent(this, CustomImagesActivity.class)));
        binding.buttonProfiles.setOnClickListener(view ->
                startActivity(new Intent(this, ProfilesActivity.class)));
        binding.buttonPacks.setOnClickListener(view ->
                startActivity(new Intent(this, PacksActivity.class)));
        binding.buttonPopupStorm.setOnClickListener(view ->
                startActivity(new Intent(this, PopupStormActivity.class)));
        binding.buttonDiagnostics.setOnClickListener(view ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        binding.buttonCommitment.setOnClickListener(view ->
                startActivity(new Intent(this, CommitmentActivity.class)));
        binding.buttonAppMode.setOnClickListener(view ->
                startActivity(new Intent(this, AppModeActivity.class)));
    }

    private void applyLockState() {
        boolean editing = ControllerPinManager.isSessionUnlocked();
        ControllerEditMode.renderButton(this, binding.buttonEditLock);
        setEnabledRecursive(binding.styleGroup,
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_TYPE));
        binding.intensitySeek.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_INTENSITY));
        binding.paddingSeek.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_SIZE_PADDING));
        binding.switchBorder.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_SHOW_BORDER));
        binding.switchText.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_SHOW_TEXT));
        binding.switchAnimateBorder.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_ANIMATE_BORDER));
        setEnabledRecursive(binding.borderEffectGroup,
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_BORDER_EFFECT));
        binding.switchReverse.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_REVERSE_MODE));
        binding.reverseStrengthSeek.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_REVERSE_STRENGTH));
        binding.buttonCustomImages.setEnabled(
                editing && !LockedSettings.isLocked(com.betasafe.app.capture.CustomImageManager.PREFS_KEY));
        setEnabledRecursive(binding.presetGroup,
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_DETECTION_PRESET));
        binding.confidenceSeek.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CONFIDENCE));
        boolean categoriesEnabled =
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_ENABLED_CATEGORIES);
        CompoundButton[] categories = {
                binding.switchGenitalsFemale, binding.switchGenitalsMale, binding.switchBreasts,
                binding.switchButtocks, binding.switchAnus, binding.switchFaces,
                binding.switchMaleChest, binding.switchBelly, binding.switchFeet,
                binding.switchArmpits, binding.switchCovered};
        for (CompoundButton category : categories) category.setEnabled(categoriesEnabled);
        boolean phrasesEnabled =
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES);
        CompoundButton[] phraseCategories = {
                binding.switchPhraseShort, binding.switchPhraseDenial,
                binding.switchPhraseHumiliation, binding.switchPhraseEdge,
                binding.switchPhraseFindom, binding.switchPhraseNtr, binding.switchPhraseGooner};
        for (CompoundButton category : phraseCategories) category.setEnabled(phrasesEnabled);
        boolean customPhrasesEnabled =
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CUSTOM_PHRASES);
        binding.customPhrases.setEnabled(customPhrasesEnabled);
        binding.buttonSavePhrases.setEnabled(customPhrasesEnabled && phrasesEnabled);
        int lockCount = LockedSettings.snapshot().size();
        binding.packLockStatus.setVisibility(lockCount > 0 ? View.VISIBLE : View.GONE);
        binding.packLockStatus.setText(getString(R.string.pack_lock_status, lockCount));
    }

    private static void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setEnabledRecursive(group.getChildAt(index), enabled);
        }
    }

    private void saveAll() {
        if (bindingValues) return;
        String previousStyle = repository.preferences().getString(
                SettingsRepository.KEY_CENSOR_TYPE, "box");
        String selectedStyle = typeFor(checkedStyleId())
                .getPreferenceValue();
        String selectedBorder = borderFor(binding.borderEffectGroup.getCheckedRadioButtonId())
                .preferenceValue();
        repository.saveAppearance(
                typeFor(checkedStyleId()),
                binding.intensitySeek.getProgress(),
                binding.switchBorder.isChecked(),
                binding.switchText.isChecked());
        repository.preferences().edit()
                .putFloat(SettingsRepository.KEY_CENSOR_SIZE_PADDING,
                        binding.paddingSeek.getProgress() / 100f)
                .putBoolean(SettingsRepository.KEY_ANIMATE_BORDER,
                        binding.switchAnimateBorder.isChecked())
                .putString(SettingsRepository.KEY_BORDER_EFFECT,
                        borderFor(binding.borderEffectGroup.getCheckedRadioButtonId()).preferenceValue())
                .putBoolean(SettingsRepository.KEY_REVERSE_MODE, binding.switchReverse.isChecked())
                .putFloat(SettingsRepository.KEY_REVERSE_STRENGTH,
                        binding.reverseStrengthSeek.getProgress() / 100f)
                .putStringSet(SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES,
                        selectedPhraseCategories())
                .apply();

        Set<String> categories = new LinkedHashSet<>();
        if (binding.switchGenitalsFemale.isChecked()) categories.add("genitals_female");
        if (binding.switchGenitalsMale.isChecked()) categories.add("genitals_male");
        if (binding.switchBreasts.isChecked()) categories.add("breasts");
        if (binding.switchButtocks.isChecked()) categories.add("buttocks");
        if (binding.switchAnus.isChecked()) categories.add("anus");
        if (binding.switchFaces.isChecked()) categories.add("face");
        if (binding.switchMaleChest.isChecked()) categories.add("male_chest");
        if (binding.switchBelly.isChecked()) categories.add("belly");
        if (binding.switchFeet.isChecked()) categories.add("feet");
        if (binding.switchArmpits.isChecked()) categories.add("armpits");
        if (binding.switchCovered.isChecked()) {
            categories.add("genitals_covered");
            categories.add("breasts_covered");
            categories.add("buttocks_covered");
            categories.add("anus_covered");
            categories.add("belly_covered");
            categories.add("feet_covered");
            categories.add("armpits_covered");
        }
        repository.saveDetection(binding.confidenceSeek.getProgress(), categories);
        stats.recordCensorStyleTried(selectedStyle);
        stats.recordBorderEffectTried(selectedBorder);
        if (!selectedStyle.equals(previousStyle)) stats.incrementCensorStyleChanges();
    }

    private void saveCustomPhrases() {
        Set<String> values = new LinkedHashSet<>();
        for (String line : binding.customPhrases.getText().toString().split("\\R")) {
            String phrase = line.trim();
            if (!phrase.isEmpty()) values.add(phrase.length() > 80 ? phrase.substring(0, 80) : phrase);
        }
        repository.preferences().edit()
                .putStringSet(SettingsRepository.KEY_CUSTOM_PHRASES, values)
                .apply();
        stats.setCustomPhrasesCount(values.size());
    }

    private Set<String> selectedPhraseCategories() {
        Set<String> values = new LinkedHashSet<>();
        if (binding.switchPhraseShort.isChecked()) values.add("short");
        if (binding.switchPhraseDenial.isChecked()) values.add("denial");
        if (binding.switchPhraseHumiliation.isChecked()) values.add("humiliation");
        if (binding.switchPhraseEdge.isChecked()) values.add("edge");
        if (binding.switchPhraseFindom.isChecked()) values.add("findom");
        if (binding.switchPhraseNtr.isChecked()) values.add("ntr");
        if (binding.switchPhraseGooner.isChecked()) values.add("gooner");
        return values;
    }

    private int radioFor(CensorAppearance.Type type) {
        switch (type) {
            case PIXELATE: return R.id.radio_pixelate;
            case BLUR: return R.id.radio_blur;
            case BAR: return R.id.radio_bar;
            case CUSTOM: return R.id.radio_custom;
            case STATIC: return R.id.radio_static;
            case GLITCH: return R.id.radio_glitch;
            case TAPE: return R.id.radio_tape;
            case ERROR_POPUP: return R.id.radio_error;
            default: return R.id.radio_box;
        }
    }

    private void setCheckedStyle(int checkedId) {
        for (int id : styleRadioIds()) {
            ((RadioButton) findViewById(id)).setChecked(id == checkedId);
        }
    }

    private int checkedStyleId() {
        for (int id : styleRadioIds()) {
            if (((RadioButton) findViewById(id)).isChecked()) return id;
        }
        return R.id.radio_box;
    }

    private static int[] styleRadioIds() {
        return new int[]{R.id.radio_box, R.id.radio_pixelate, R.id.radio_blur,
                R.id.radio_bar, R.id.radio_custom, R.id.radio_static, R.id.radio_glitch,
                R.id.radio_tape, R.id.radio_error};
    }

    private CensorAppearance.Type typeFor(int id) {
        if (id == R.id.radio_pixelate) return CensorAppearance.Type.PIXELATE;
        if (id == R.id.radio_blur) return CensorAppearance.Type.BLUR;
        if (id == R.id.radio_bar) return CensorAppearance.Type.BAR;
        if (id == R.id.radio_custom) return CensorAppearance.Type.CUSTOM;
        if (id == R.id.radio_static) return CensorAppearance.Type.STATIC;
        if (id == R.id.radio_glitch) return CensorAppearance.Type.GLITCH;
        if (id == R.id.radio_tape) return CensorAppearance.Type.TAPE;
        if (id == R.id.radio_error) return CensorAppearance.Type.ERROR_POPUP;
        return CensorAppearance.Type.BOX;
    }

    private int radioFor(CensorAppearance.BorderEffect effect) {
        switch (effect) {
            case GLOW: return R.id.radio_border_glow;
            case GRADIENT: return R.id.radio_border_gradient;
            case RAINBOW: return R.id.radio_border_rainbow;
            default: return R.id.radio_border_classic;
        }
    }

    private CensorAppearance.BorderEffect borderFor(int id) {
        if (id == R.id.radio_border_glow) return CensorAppearance.BorderEffect.GLOW;
        if (id == R.id.radio_border_gradient) return CensorAppearance.BorderEffect.GRADIENT;
        if (id == R.id.radio_border_rainbow) return CensorAppearance.BorderEffect.RAINBOW;
        return CensorAppearance.BorderEffect.CLASSIC;
    }

    private int radioFor(DetectionPreset preset) {
        switch (preset) {
            case LOW: return R.id.radio_preset_low;
            case HIGH: return R.id.radio_preset_high;
            case ULTRA: return R.id.radio_preset_ultra;
            default: return R.id.radio_preset_medium;
        }
    }

    private DetectionPreset presetFor(int id) {
        if (id == R.id.radio_preset_low) return DetectionPreset.LOW;
        if (id == R.id.radio_preset_high) return DetectionPreset.HIGH;
        if (id == R.id.radio_preset_ultra) return DetectionPreset.ULTRA;
        return DetectionPreset.MEDIUM;
    }

    private static boolean containsCoveredCategory(Set<String> categories) {
        for (String value : categories) if (value.endsWith("_covered")) return true;
        return false;
    }

    private static String joinLines(Set<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append('\n');
            result.append(value);
        }
        return result.toString();
    }

    private String percent(int value) { return value + "%"; }

    @Override
    protected void onResume() {
        super.onResume();
        if (CommitmentManager.isActive(this)) {
            startActivity(new Intent(this, CommitmentActivity.class));
            finish();
            return;
        }
        if (binding != null) {
            new PackManager(this);
            bindValues();
            applyLockState();
        }
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private abstract static class SavingSeekListener implements SeekBar.OnSeekBarChangeListener {
        abstract void update(int progress, boolean fromUser);
        @Override public final void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            update(progress, fromUser);
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
