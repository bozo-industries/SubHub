package com.betasafe.app.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.R;
import com.betasafe.app.appmode.AppModeActivity;
import com.betasafe.app.browser.BrowserActivity;
import com.betasafe.app.capture.CustomImagesActivity;
import com.betasafe.app.capture.ExportActivity;
import com.betasafe.app.commitment.CommitmentActivity;
import com.betasafe.app.diagnostics.DiagnosticsActivity;
import com.betasafe.app.help.HelpActivity;
import com.betasafe.app.pack.PacksActivity;
import com.betasafe.app.penance.PenanceActivity;
import com.betasafe.app.popup.PopupStormActivity;
import com.betasafe.app.profiles.ProfilesActivity;
import com.betasafe.app.settings.SettingsActivity;
import com.betasafe.app.settings.GlobalSettingsActivity;
import com.betasafe.app.settings.FeatureModuleManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies that every top-level configuration surface stays visible but read-only when locked. */
@RunWith(AndroidJUnit4.class)
public final class ControllerEditSessionTest {
    @Before public void setUpControllerPin() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        if (!ControllerPinManager.isConfigured(context)) {
            assertTrue(ControllerPinManager.setPin(context, "2468"));
        }
        ControllerPinManager.lockNow();
        new FeatureModuleManager(context).save(true, true, true);
    }

    @Test public void censorSettingsAreReadOnlyWhenSessionLocks() {
        assertLocked(SettingsActivity.class, R.id.radio_box);
        assertLocked(SettingsActivity.class, R.id.switch_smut_text);
    }

    @Test public void limitsSettingsAreReadOnlyWhenSessionLocks() {
        assertLocked(AppModeActivity.class, R.id.armed);
    }

    @Test public void moneySettingsAreReadOnlyWhenSessionLocks() {
        assertLocked(PenanceActivity.class, R.id.ledger_enabled);
    }

    @Test public void globalFeatureSettingsAreReadOnlyWhenSessionLocks() {
        assertLocked(GlobalSettingsActivity.class, R.id.switch_module_censor);
    }

    @Test public void secondaryConfigurationSurfacesAreReadOnlyWhenSessionLocks() {
        assertLocked(CustomImagesActivity.class, R.id.button_add);
        assertLocked(ProfilesActivity.class, R.id.profile_name);
        assertLocked(PacksActivity.class, R.id.button_import_pack);
        assertLocked(PopupStormActivity.class, R.id.switch_enabled);
        assertLocked(DiagnosticsActivity.class, R.id.switch_diagnostics_overlay);
        assertLocked(CommitmentActivity.class, R.id.duration_group);
        assertLocked(HelpActivity.class, R.id.button_language);
        assertLocked(ExportActivity.class, R.id.switch_delete_originals);
    }

    @Test public void browserShowsTheSameExplicitEditControl() {
        try (ActivityScenario<BrowserActivity> scenario =
                     ActivityScenario.launch(BrowserActivity.class)) {
            scenario.onActivity(activity -> {
                View unlock = activity.findViewById(R.id.button_edit_lock);
                assertTrue(unlock.isEnabled());
                assertTrue(unlock.isClickable());
            });
        }
    }

    private static void assertLocked(Class<? extends Activity> type, int editableId) {
        try (ActivityScenario<? extends Activity> scenario = ActivityScenario.launch(type)) {
            scenario.onActivity(activity -> ControllerPinManager.lockNow());
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                View editable = activity.findViewById(editableId);
                View unlock = activity.findViewById(R.id.button_edit_lock);
                assertFalse(editable.isEnabled());
                assertTrue(unlock.isEnabled());
                assertTrue(unlock.isClickable());
            });
        }
    }
}
