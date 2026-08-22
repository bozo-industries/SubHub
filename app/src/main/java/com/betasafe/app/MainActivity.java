package com.betasafe.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

/**
 * Source-level shell for the reconstruction. Feature controllers are introduced behind this
 * activity as each parity milestone becomes executable and tested.
 */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonProtection.setOnClickListener(this::showFoundationStatus);
        binding.tabHome.setOnClickListener(view -> selectTab(binding.tabHome, R.string.tab_home));
        binding.tabSettings.setOnClickListener(view -> selectTab(binding.tabSettings, R.string.tab_settings));
        binding.tabBrowser.setOnClickListener(view -> selectTab(binding.tabBrowser, R.string.tab_browser));
        binding.tabHelp.setOnClickListener(view -> selectTab(binding.tabHelp, R.string.tab_help));
    }

    private void showFoundationStatus(View view) {
        Snackbar.make(view, R.string.foundation_status, Snackbar.LENGTH_LONG).show();
    }

    private void selectTab(TextView selected, int label) {
        TextView[] tabs = {binding.tabHome, binding.tabSettings, binding.tabBrowser, binding.tabHelp};
        for (TextView tab : tabs) {
            boolean active = tab == selected;
            tab.setTextColor(getColor(active ? R.color.text_primary : R.color.text_muted));
            tab.setBackgroundResource(active ? R.drawable.bg_tab_active : android.R.color.transparent);
        }
        binding.sectionTitle.setText(label);
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
