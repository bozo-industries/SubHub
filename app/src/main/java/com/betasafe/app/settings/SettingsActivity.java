package com.betasafe.app.settings;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivitySettingsBinding;
import com.betasafe.app.detection.DetectorConfig;

import java.util.LinkedHashSet;
import java.util.Set;

/** Styled, source-native editor for live detector and censor preferences. */
public final class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private SettingsRepository repository;
    private boolean bindingValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new SettingsRepository(this);
        bindValues();
        attachListeners();
        binding.buttonBack.setOnClickListener(view -> finish());
    }

    private void bindValues() {
        bindingValues = true;
        CensorAppearance appearance = repository.loadAppearance();
        binding.styleGroup.check(radioFor(appearance.getType()));
        binding.intensitySeek.setProgress(appearance.getIntensity());
        binding.intensityValue.setText(percent(appearance.getIntensity()));
        binding.switchBorder.setChecked(appearance.isShowBorder());
        binding.switchText.setChecked(appearance.isShowText());

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
        bindingValues = false;
    }

    private void attachListeners() {
        binding.styleGroup.setOnCheckedChangeListener((group, checkedId) -> saveAll());
        binding.intensitySeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.intensityValue.setText(percent(progress));
                if (fromUser) saveAll();
            }
        });
        binding.confidenceSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.confidenceValue.setText(percent(progress));
                if (fromUser) saveAll();
            }
        });
        CompoundButton.OnCheckedChangeListener changed = (button, checked) -> saveAll();
        binding.switchBorder.setOnCheckedChangeListener(changed);
        binding.switchText.setOnCheckedChangeListener(changed);
        binding.switchGenitalsFemale.setOnCheckedChangeListener(changed);
        binding.switchGenitalsMale.setOnCheckedChangeListener(changed);
        binding.switchBreasts.setOnCheckedChangeListener(changed);
        binding.switchButtocks.setOnCheckedChangeListener(changed);
        binding.switchAnus.setOnCheckedChangeListener(changed);
    }

    private void saveAll() {
        if (bindingValues) return;
        repository.saveAppearance(
                typeFor(binding.styleGroup.getCheckedRadioButtonId()),
                binding.intensitySeek.getProgress(),
                binding.switchBorder.isChecked(),
                binding.switchText.isChecked());
        Set<String> categories = new LinkedHashSet<>();
        if (binding.switchGenitalsFemale.isChecked()) categories.add("genitals_female");
        if (binding.switchGenitalsMale.isChecked()) categories.add("genitals_male");
        if (binding.switchBreasts.isChecked()) categories.add("breasts");
        if (binding.switchButtocks.isChecked()) categories.add("buttocks");
        if (binding.switchAnus.isChecked()) categories.add("anus");
        repository.saveDetection(binding.confidenceSeek.getProgress(), categories);
    }

    private int radioFor(CensorAppearance.Type type) {
        switch (type) {
            case PIXELATE: return R.id.radio_pixelate;
            case BLUR: return R.id.radio_blur;
            case BAR: return R.id.radio_bar;
            default: return R.id.radio_box;
        }
    }

    private CensorAppearance.Type typeFor(int radioId) {
        if (radioId == R.id.radio_pixelate) return CensorAppearance.Type.PIXELATE;
        if (radioId == R.id.radio_blur) return CensorAppearance.Type.BLUR;
        if (radioId == R.id.radio_bar) return CensorAppearance.Type.BAR;
        return CensorAppearance.Type.BOX;
    }

    private String percent(int value) { return value + "%"; }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
