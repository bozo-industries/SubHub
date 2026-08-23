package com.betasafe.app.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.betasafe.app.R;
import com.betasafe.app.appmode.AppModeActivity;
import com.betasafe.app.penance.PenanceActivity;
import com.betasafe.app.settings.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies that every top-level configuration surface stays visible but read-only when locked. */
@RunWith(AndroidJUnit4.class)
public final class ControllerEditSessionTest {
    @Test public void censorSettingsAreReadOnlyWhenSessionLocks() {
        assertLocked(SettingsActivity.class, R.id.radio_box);
    }

    @Test public void limitsSettingsAreReadOnlyWhenSessionLocks() {
        assertLocked(AppModeActivity.class, R.id.armed);
    }

    @Test public void moneySettingsAreReadOnlyWhenSessionLocks() {
        assertLocked(PenanceActivity.class, R.id.ledger_enabled);
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
