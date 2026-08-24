package com.subhub.app.penance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;

import com.subhub.app.R;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.settings.FeatureModuleManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

/** Sub mode keeps explicit settlement visible without exposing Wallet configuration. */
@RunWith(AndroidJUnit4.class)
public final class SubWalletCheckoutTest {
    @Before public void enableWalletModule() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        FeatureModuleManager current = new FeatureModuleManager(context);
        current.save(current.isCensorEnabled(), current.isLimitsEnabled(), true);
        ControllerPinManager.enterDomMode();
    }

    @Test public void subModeShowsCheckoutAndHidesWalletRules() {
        try (ActivityScenario<PenanceActivity> scenario =
                     ActivityScenario.launch(PenanceActivity.class)) {
            scenario.onActivity(activity -> {
                ControllerPinManager.enterSubMode();
                invokePresentation(activity);
                assertEquals(View.GONE,
                        activity.findViewById(R.id.button_edit_lock).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.rule_config_card).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.safety_config_card).getVisibility());
                assertEquals(View.GONE,
                        activity.findViewById(R.id.corrections_card).getVisibility());
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.checkout_card).getVisibility());
                assertTrue(activity.findViewById(R.id.button_back).isShown());
                assertFalse(activity.findViewById(R.id.bottom_navigation).isShown());
            });
        }
    }

    private static void invokePresentation(PenanceActivity activity) {
        try {
            Method method = PenanceActivity.class.getDeclaredMethod("applyEditState");
            method.setAccessible(true);
            method.invoke(activity);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
