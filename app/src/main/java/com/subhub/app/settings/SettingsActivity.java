package com.subhub.app.settings;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.subhub.app.R;
import com.subhub.app.capture.CustomImagesActivity;
import com.subhub.app.databinding.ActivitySettingsBinding;
import com.subhub.app.detection.DetectionPreset;
import com.subhub.app.detection.DetectorConfig;
import com.subhub.app.detection.text.TextSmutConfig;
import com.subhub.app.overlay.CensorPhrases;
import com.subhub.app.pack.LockedSettings;
import com.subhub.app.pack.SubHubPackLocks;
import com.subhub.app.pack.SubHubPackSchema;
import com.subhub.app.security.ControllerPinGate;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.security.ControllerEditMode;
import com.subhub.app.pack.PackManager;
import com.subhub.app.studio.StudioActivity;
import com.subhub.app.stats.StatsRepository;
import com.subhub.app.util.PrimaryHeader;
import com.subhub.app.util.SubHubNavigation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntConsumer;

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
        PrimaryHeader.bind(binding.getRoot(), R.drawable.ic_nav_censor,
                R.string.censor_header_title, R.string.settings_subtitle);
        binding.getRoot().setFocusableInTouchMode(true);
        binding.getRoot().requestFocus();
        repository = new SettingsRepository(this);
        stats = new StatsRepository(this);
        new PackManager(this);
        bindValues();
        attachListeners();
        applyLockState();
        PrimaryHeader.backButton(binding.getRoot()).setOnClickListener(view -> finish());
        PrimaryHeader.editLockButton(binding.getRoot())
                .setOnClickListener(view -> toggleEditSession());
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.CENSOR);
    }

    private void toggleEditSession() {
        if (ControllerPinManager.isSessionUnlocked()) {
            ControllerEditMode.enterSubMode(this);
        } else {
            ControllerPinGate.require(this, this::applyLockState, false);
        }
    }

    private void bindValues() {
        bindingValues = true;
        CensorAppearance appearance = repository.loadAppearance();
        binding.captureMethodGroup.check(repository.loadCaptureMethod() == CaptureMethod.APP_MODE
                ? R.id.radio_capture_app_mode : R.id.radio_capture_recording);
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
        renderPaletteControls();
        renderColorButton(binding.borderColor, appearance.getBorderColor());
        binding.borderPreview.setAppearance(appearance);

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

        TextSmutConfig textSmut = repository.loadTextSmutConfig();
        binding.switchSmutText.setChecked(textSmut.isEnabled());
        binding.smutSensitivityGroup.check(radioFor(textSmut.getSensitivity()));
        Set<String> smutCategories = textSmut.getEnabledCategories();
        binding.switchSmutExplicit.setChecked(
                smutCategories.contains(TextSmutConfig.CATEGORY_EXPLICIT));
        binding.switchSmutFetish.setChecked(
                smutCategories.contains(TextSmutConfig.CATEGORY_FETISH));
        binding.switchSmutSolicitation.setChecked(
                smutCategories.contains(TextSmutConfig.CATEGORY_SOLICITATION));

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
        binding.captureMethodGroup.setOnCheckedChangeListener((group, checkedId) -> saveAll());
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
                renderPaletteControls();
            });
        }
        binding.borderEffectGroup.setOnCheckedChangeListener((group, checkedId) -> saveAll());
        binding.smutSensitivityGroup.setOnCheckedChangeListener((group, checkedId) -> saveAll());
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
        binding.switchBorder.setOnCheckedChangeListener((button, checked) -> {
            saveAll();
            syncBorderControlState();
        });
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
        binding.switchSmutText.setOnCheckedChangeListener((button, checked) -> {
            saveAll();
            applyLockState();
        });
        binding.switchSmutExplicit.setOnCheckedChangeListener(changed);
        binding.switchSmutFetish.setOnCheckedChangeListener(changed);
        binding.switchSmutSolicitation.setOnCheckedChangeListener(changed);
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
        binding.buttonPacks.setOnClickListener(view ->
                startActivity(new Intent(this, StudioActivity.class)));
        binding.paletteColorOne.setOnClickListener(view -> pickEffectColor(1));
        binding.paletteColorTwo.setOnClickListener(view -> pickEffectColor(2));
        binding.paletteColorThree.setOnClickListener(view -> pickEffectColor(3));
        binding.borderColor.setOnClickListener(view -> showColorDialog(
                getString(R.string.border_color_label), repository.loadAppearance().getBorderColor(),
                color -> {
                    repository.preferences().edit().putString(
                            SettingsRepository.KEY_BORDER_COLOR,
                            SettingsRepository.colorString(color)).apply();
                    renderColorButton(binding.borderColor, color);
                    stats.setBorderColorChanged();
                    refreshBorderPreview();
                }));
    }

    private void applyLockState() {
        boolean editing = ControllerPinManager.isSessionUnlocked()
                && !SubHubPackLocks.isLocked(this, SubHubPackSchema.CENSOR);
        ControllerEditMode.renderButton(this, PrimaryHeader.editLockButton(binding.getRoot()));
        setEnabledRecursive(binding.styleGroup,
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_TYPE));
        setEnabledRecursive(binding.captureMethodGroup,
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CAPTURE_METHOD));
        binding.intensitySeek.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_INTENSITY));
        binding.paddingSeek.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_SIZE_PADDING));
        binding.switchBorder.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_SHOW_BORDER));
        boolean paletteEnabled = editing
                && !LockedSettings.isLocked(SettingsRepository.KEY_CENSOR_TYPE);
        setEnabledRecursive(binding.effectPaletteGroup, paletteEnabled);
        binding.switchText.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_SHOW_TEXT));
        syncBorderControlState();
        binding.switchReverse.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_REVERSE_MODE));
        binding.reverseStrengthSeek.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_REVERSE_STRENGTH));
        binding.buttonCustomImages.setEnabled(
                editing && !LockedSettings.isLocked(com.subhub.app.capture.CustomImageManager.PREFS_KEY));
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
        binding.switchSmutText.setEnabled(
                editing && !LockedSettings.isLocked(SettingsRepository.KEY_TEXT_SMUT_ENABLED));
        boolean smutDetailsEnabled = editing && binding.switchSmutText.isChecked();
        setEnabledRecursive(binding.smutSensitivityGroup, smutDetailsEnabled
                && !LockedSettings.isLocked(SettingsRepository.KEY_TEXT_SMUT_SENSITIVITY));
        boolean smutCategoriesEnabled = smutDetailsEnabled
                && !LockedSettings.isLocked(SettingsRepository.KEY_TEXT_SMUT_CATEGORIES);
        binding.switchSmutExplicit.setEnabled(smutCategoriesEnabled);
        binding.switchSmutFetish.setEnabled(smutCategoriesEnabled);
        binding.switchSmutSolicitation.setEnabled(smutCategoriesEnabled);
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

    private void syncBorderControlState() {
        boolean editing = ControllerPinManager.isSessionUnlocked()
                && !SubHubPackLocks.isLocked(this, SubHubPackSchema.CENSOR);
        boolean active = binding.switchBorder.isChecked();
        binding.borderPreview.setAlpha(active ? 1f : .55f);
        binding.borderColor.setEnabled(active && editing
                && !LockedSettings.isLocked(SettingsRepository.KEY_BORDER_COLOR));
        binding.borderColorField.setAlpha(active ? 1f : .5f);
        binding.switchAnimateBorder.setEnabled(active && editing
                && !LockedSettings.isLocked(SettingsRepository.KEY_ANIMATE_BORDER));
        binding.switchAnimateBorder.setAlpha(active ? 1f : .5f);
        setEnabledRecursive(binding.borderEffectGroup, active && editing
                && !LockedSettings.isLocked(SettingsRepository.KEY_BORDER_EFFECT));
        binding.borderEffectGroup.setAlpha(active ? 1f : .5f);
    }

    private void renderPaletteControls() {
        CensorAppearance.Type type = typeFor(checkedStyleId());
        int count = paletteSlotCount(type);
        binding.effectPaletteGroup.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        binding.paletteColorOneField.setVisibility(count >= 1 ? View.VISIBLE : View.GONE);
        binding.paletteColorTwoField.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
        binding.paletteColorThreeField.setVisibility(count >= 3 ? View.VISIBLE : View.GONE);
        if (count == 0) return;
        int[] labels = paletteLabels(type);
        binding.paletteColorOneLabel.setText(labels[0]);
        if (count >= 2) binding.paletteColorTwoLabel.setText(labels[1]);
        if (count >= 3) binding.paletteColorThreeLabel.setText(labels[2]);
        EffectPalette palette = repository.loadEffectPalette(type);
        renderColorButton(binding.paletteColorOne, palette.first());
        renderColorButton(binding.paletteColorTwo, palette.second());
        renderColorButton(binding.paletteColorThree, palette.third());
    }

    private void pickEffectColor(int slot) {
        CensorAppearance.Type type = typeFor(checkedStyleId());
        EffectPalette current = repository.loadEffectPalette(type);
        int selected = slot == 1 ? current.first() : slot == 2 ? current.second() : current.third();
        int[] labels = paletteLabels(type);
        showColorDialog(getString(labels[Math.max(0, Math.min(2, slot - 1))]), selected, color -> {
            EffectPalette updated = new EffectPalette(
                    slot == 1 ? color : current.first(),
                    slot == 2 ? color : current.second(),
                    slot == 3 ? color : current.third());
            repository.saveEffectPalette(type, updated);
            renderPaletteControls();
        });
    }

    private void showColorDialog(String title, int current, IntConsumer accepted) {
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, 0, padding, 0);

        TextView fieldLabel = new TextView(this);
        fieldLabel.setText(R.string.color_hex_label);
        fieldLabel.setTextColor(getColor(R.color.text_primary));
        fieldLabel.setTextSize(12f);
        content.addView(fieldLabel);

        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setText(SettingsRepository.colorString(current).substring(3));
        hex.setSelection(hex.length());
        hex.setTextColor(getColor(R.color.text_primary));
        hex.setHintTextColor(getColor(R.color.text_muted));
        content.addView(hex, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        TextView presetsLabel = new TextView(this);
        presetsLabel.setText(R.string.color_picker_presets);
        presetsLabel.setTextColor(getColor(R.color.text_primary));
        presetsLabel.setTextSize(12f);
        presetsLabel.setPadding(0, dp(8), 0, dp(4));
        content.addView(presetsLabel);

        GridLayout presets = new GridLayout(this);
        presets.setColumnCount(4);
        int[] colors = {Color.BLACK, Color.WHITE, Color.rgb(255, 0, 128),
                Color.rgb(169, 76, 255), Color.rgb(215, 38, 48),
                Color.rgb(243, 211, 59), Color.rgb(0, 180, 255), Color.rgb(18, 18, 22)};
        for (int index = 0; index < colors.length; index++) {
            int color = colors[index];
            TextView swatch = new TextView(this);
            swatch.setContentDescription(SettingsRepository.colorString(color));
            swatch.setBackground(swatchBackground(color));
            swatch.setOnClickListener(view -> {
                hex.setText(SettingsRepository.colorString(color).substring(3));
                hex.setSelection(hex.length());
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 4), GridLayout.spec(index % 4, 1f));
            params.width = 0;
            params.height = dp(42);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            presets.addView(swatch, params);
        }
        content.addView(presets, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        String raw = hex.getText().toString().trim();
                        if (!raw.startsWith("#")) raw = "#" + raw;
                        int color = Color.parseColor(raw);
                        accepted.accept(Color.argb(255, Color.red(color),
                                Color.green(color), Color.blue(color)));
                        dialog.dismiss();
                    } catch (IllegalArgumentException error) {
                        hex.setError(getString(R.string.color_invalid));
                    }
                }));
        dialog.show();
    }

    private void renderColorButton(TextView button, int color) {
        button.setText(SettingsRepository.colorString(color).substring(3));
        button.setTextColor(isLight(color) ? Color.BLACK : Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(swatchBackground(color));
    }

    private void refreshBorderPreview() {
        if (binding != null) binding.borderPreview.setAppearance(repository.loadAppearance());
    }

    private GradientDrawable swatchBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), getColor(R.color.accent));
        return background;
    }

    private static boolean isLight(int color) {
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114
                >= 160_000;
    }

    private int paletteSlotCount(CensorAppearance.Type type) {
        switch (type) {
            case BOX: return 1;
            case STATIC: return 2;
            case GLITCH:
            case TAPE:
            case ERROR_POPUP: return 3;
            default: return 0;
        }
    }

    private int[] paletteLabels(CensorAppearance.Type type) {
        switch (type) {
            case STATIC:
                return new int[]{R.string.effect_color_dark, R.string.effect_color_light,
                        R.string.effect_color_light};
            case GLITCH:
                return new int[]{R.string.effect_color_left_shift,
                        R.string.effect_color_right_shift, R.string.effect_color_flash};
            case TAPE:
                return new int[]{R.string.effect_color_base,
                        R.string.effect_color_stripe_one, R.string.effect_color_stripe_two};
            case ERROR_POPUP:
                return new int[]{R.string.effect_color_panel,
                        R.string.effect_color_alert, R.string.effect_color_action};
            default:
                return new int[]{R.string.effect_color_fill,
                        R.string.effect_color_light, R.string.effect_color_flash};
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        repository.saveCaptureMethod(binding.radioCaptureAppMode.isChecked()
                ? CaptureMethod.APP_MODE : CaptureMethod.SCREEN_RECORDING);
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
        Set<String> smutCategories = new LinkedHashSet<>();
        if (binding.switchSmutExplicit.isChecked()) {
            smutCategories.add(TextSmutConfig.CATEGORY_EXPLICIT);
        }
        if (binding.switchSmutFetish.isChecked()) {
            smutCategories.add(TextSmutConfig.CATEGORY_FETISH);
        }
        if (binding.switchSmutSolicitation.isChecked()) {
            smutCategories.add(TextSmutConfig.CATEGORY_SOLICITATION);
        }
        repository.saveTextSmutConfig(new TextSmutConfig(
                binding.switchSmutText.isChecked(),
                textSensitivityFor(binding.smutSensitivityGroup.getCheckedRadioButtonId()),
                smutCategories));
        stats.recordCensorStyleTried(selectedStyle);
        stats.recordBorderEffectTried(selectedBorder);
        if (!selectedStyle.equals(previousStyle)) stats.incrementCensorStyleChanges();
        refreshBorderPreview();
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
                R.id.radio_custom, R.id.radio_static, R.id.radio_glitch,
                R.id.radio_tape, R.id.radio_error};
    }

    private CensorAppearance.Type typeFor(int id) {
        if (id == R.id.radio_pixelate) return CensorAppearance.Type.PIXELATE;
        if (id == R.id.radio_blur) return CensorAppearance.Type.BLUR;
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

    private int radioFor(int textSensitivity) {
        if (textSensitivity == TextSmutConfig.SENSITIVITY_STRICT) return R.id.radio_smut_strict;
        if (textSensitivity == TextSmutConfig.SENSITIVITY_BROAD) return R.id.radio_smut_broad;
        return R.id.radio_smut_balanced;
    }

    private int textSensitivityFor(int id) {
        if (id == R.id.radio_smut_strict) return TextSmutConfig.SENSITIVITY_STRICT;
        if (id == R.id.radio_smut_broad) return TextSmutConfig.SENSITIVITY_BROAD;
        return TextSmutConfig.SENSITIVITY_BALANCED;
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
        if (ControllerPinManager.isDomModeActive()
                && SubHubNavigation.redirectIfDisabled(this, SubHubNavigation.Screen.CENSOR)) {
            return;
        }
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.CENSOR);
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
