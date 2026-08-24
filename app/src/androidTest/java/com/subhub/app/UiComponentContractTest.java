package com.subhub.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.Gravity;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.penance.PenanceActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class UiComponentContractTest {
    @Test public void toolButtonsCenterTheIconAndLabelAsOneGroup() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                int[] ids = {R.id.button_censor_settings, R.id.button_browser,
                        R.id.button_export, R.id.button_help};
                for (int id : ids) {
                    TextView button = activity.findViewById(id);
                    assertEquals(Gravity.CENTER, button.getGravity());
                    assertNotNull(button.getCompoundDrawables()[1]);
                    assertEquals(button.getPaddingTop(), button.getPaddingBottom());
                    assertEquals(1, button.getMaxLines());
                }
            });
        }
    }

    @Test public void moneyInputsAndCompactActionsCannotClipOrWrap() {
        try (ActivityScenario<PenanceActivity> scenario =
                     ActivityScenario.launch(PenanceActivity.class)) {
            scenario.onActivity(activity -> {
                int[] inputIds = {R.id.rule_detection_amount, R.id.detection_batch,
                        R.id.daily_cap, R.id.weekly_cap, R.id.mercy_minutes,
                        R.id.dwell_seconds};
                for (int id : inputIds) {
                    EditText input = activity.findViewById(id);
                    assertTrue((input.getGravity() & Gravity.CENTER_VERTICAL) != 0);
                    assertEquals(1, input.getMaxLines());
                    assertTrue(!input.getIncludeFontPadding());
                }
                int[] actionIds = {R.id.button_forgive_latest, R.id.button_clear_unpaid};
                for (int id : actionIds) {
                    TextView action = activity.findViewById(id);
                    assertEquals(1, action.getMaxLines());
                    assertTrue(action.getText().length() <= 14);
                }
            });
        }
    }
}
