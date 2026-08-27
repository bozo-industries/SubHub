package com.subhub.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.detection.text.TextSmutConfig;
import com.subhub.app.settings.SettingsRepository;
import com.subhub.app.settings.GlobalSettingsActivity;

import java.util.Arrays;
import java.util.LinkedHashSet;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class HomeSettingsStructureContractTest {
    @Test public void helpLivesOnHomeOutsideDomOnlyContent() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View help = activity.findViewById(R.id.button_help);
                View domContent = activity.findViewById(R.id.dom_content);
                assertNotNull(help);
                assertSame(domContent.getParent(), help.getParent());
            });
        }
    }

    @Test public void filterCardSummarizesOnlyImageAndTextState() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                TextView summary = activity.findViewById(R.id.sub_censor_summary);
                String value = summary.getText().toString();
                assertTrue(value.startsWith("Image filter "));
                assertTrue(value.contains(" · Text filter "));
                assertFalse(value.contains("Box"));
                assertFalse(value.contains("Assigned app"));
            });
        }
    }

    @Test public void filterArrangementUsesCanonicalDomLabels() {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsRepository repository = new SettingsRepository(context);
        repository.preferences().edit()
                .putString(SettingsRepository.KEY_CENSOR_TYPE, "box")
                .putBoolean(SettingsRepository.KEY_SHOW_BORDER, true)
                .putString(SettingsRepository.KEY_BORDER_EFFECT, "classic")
                .putString(SettingsRepository.KEY_DETECTION_PRESET, "ultra")
                .putBoolean(SettingsRepository.KEY_SHOW_TEXT, true)
                .putStringSet(SettingsRepository.KEY_ENABLED_PHRASE_CATEGORIES,
                        new LinkedHashSet<>(Arrays.asList(
                                "short", "denial", "humiliation", "findom")))
                .putBoolean(SettingsRepository.KEY_TEXT_SMUT_ENABLED, true)
                .putInt(SettingsRepository.KEY_TEXT_SMUT_SENSITIVITY,
                        TextSmutConfig.SENSITIVITY_BALANCED)
                .putStringSet(SettingsRepository.KEY_TEXT_SMUT_CATEGORIES,
                        new LinkedHashSet<>(Arrays.asList(
                                TextSmutConfig.CATEGORY_EXPLICIT,
                                TextSmutConfig.CATEGORY_FETISH,
                                TextSmutConfig.CATEGORY_SOLICITATION)))
                .commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String details = activity.censorArrangementDetails();
                assertTrue(details.contains("BLACKOUT · Classic border"));
                assertTrue(details.contains("ULTRA · MAXIMUM COVERAGE"));
                assertTrue(details.contains(
                        "CONTEXT · Sexual words, Kink / fetish talk, Sexual invitations"));
                assertTrue(details.contains("Beta / cuck, Denial, Findom, Plain"));
                assertFalse(details.contains("Balanced"));
                assertFalse(details.contains("Explicit language"));
                assertFalse(details.contains("Humiliation"));
                assertFalse(details.contains("Short"));
            });
        }
    }

    @Test public void paypalIsSecondLastSettingsSectionAndHelpIsAbsent() {
        try (ActivityScenario<GlobalSettingsActivity> scenario =
                     ActivityScenario.launch(GlobalSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                ViewGroup sections = activity.findViewById(R.id.settings_sections);
                View paypal = activity.findViewById(R.id.paypal_card);
                View appSettings = activity.findViewById(R.id.app_settings_card);
                assertNotNull(paypal);
                assertEquals(sections.getChildCount() - 2, sections.indexOfChild(paypal));
                assertEquals(sections.getChildCount() - 1, sections.indexOfChild(appSettings));
                assertNull(activity.findViewById(R.id.button_help));
            });
        }
    }
}
