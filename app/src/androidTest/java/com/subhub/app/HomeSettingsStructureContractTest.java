package com.subhub.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.settings.GlobalSettingsActivity;

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
