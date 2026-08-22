package com.betasafe.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;
import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.util.AppShortcuts;
import com.betasafe.app.util.LocaleHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public final class HelpAndLocaleTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        preferences.edit().remove("has_seen_onboarding")
                .putString(LocaleHelper.KEY_LANGUAGE, "system").commit();
        LocaleHelper.applySaved(context);
    }

    @After public void tearDown() {
        preferences.edit().remove("has_seen_onboarding")
                .putString(LocaleHelper.KEY_LANGUAGE, "system").commit();
        LocaleHelper.applySaved(context);
    }

    @Test public void recoveredLanguageSetIsAvailableAndPersisted() {
        assertEquals(Arrays.asList("system", "en", "fr", "es", "pt", "de", "ja",
                "zh-CN", "zh-TW", "ko", "ru"), LocaleHelper.SUPPORTED);
        preferences.edit().putString(LocaleHelper.KEY_LANGUAGE, "de").commit();
        assertEquals("de", LocaleHelper.getLanguage(context));
        preferences.edit().putString(LocaleHelper.KEY_LANGUAGE, "not-a-locale").commit();
        assertEquals("system", LocaleHelper.getLanguage(context));
    }

    @Test public void helpScreenExposesRepairLanguageAndTenGuides() {
        try (ActivityScenario<HelpActivity> scenario =
                     ActivityScenario.launch(HelpActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.help_title),
                        ((TextView) activity.findViewById(R.id.help_header_title))
                                .getText().toString());
                assertNotNull(activity.findViewById(R.id.button_fix_permissions));
                assertNotNull(activity.findViewById(R.id.button_accessibility));
                assertNotNull(activity.findViewById(R.id.button_language));
                LinearLayout sections = activity.findViewById(R.id.help_sections);
                assertEquals(10, sections.getChildCount());
            });
        }
    }

    @Test public void onboardingIsInformativeAndDismissible() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View card = activity.findViewById(R.id.onboarding_card);
                assertEquals(View.VISIBLE, card.getVisibility());
                activity.findViewById(R.id.onboarding_dismiss).performClick();
                assertEquals(View.GONE, card.getVisibility());
                assertTrue(preferences.getBoolean("has_seen_onboarding", false));
            });
        }
    }

    @Test public void launcherShortcutsRouteToProtectionAndBrowser() {
        AppShortcuts.install(context);
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        assertNotNull(manager);
        List<String> ids = manager.getDynamicShortcuts().stream()
                .map(ShortcutInfo::getId).collect(Collectors.toList());
        assertTrue(ids.contains("start_protection"));
        assertTrue(ids.contains("open_browser"));
        assertEquals(AppShortcuts.ACTION_START_PROTECTION,
                manager.getDynamicShortcuts().stream()
                        .filter(item -> "start_protection".equals(item.getId()))
                        .findFirst().orElseThrow().getIntent().getAction());
    }
}
