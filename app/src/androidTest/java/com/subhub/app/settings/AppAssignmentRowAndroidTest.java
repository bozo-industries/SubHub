package com.subhub.app.settings;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.subhub.app.R;
import com.subhub.app.appmode.AppModeManager;
import com.subhub.app.appmode.AppModePolicy;
import com.subhub.app.security.ControllerPinManager;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Native geometry and assignment contracts; fixture preferences are restored, no screenshots. */
@RunWith(AndroidJUnit4.class)
public final class AppAssignmentRowAndroidTest {
    @Test public void appAssignmentsPersistIndependentlyOnReopenAndDenySubModeEditing() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AppModeManager manager = new AppModeManager(context);
        Set<String> censorBefore = manager.getSelectedPackages();
        Set<String> limitBefore = manager.getTimerPackages();
        Set<String> subliminalBefore = manager.getSubliminalPackages();
        boolean armed = manager.isArmed();
        boolean wasDom = ControllerPinManager.isDomModeActive();
        AppModePolicy.Mode mode = manager.getMode();
        ControllerPinManager.enterDomMode();
        AtomicReference<String> selectedPackage = new AtomicReference<>();
        try {
            try (ActivityScenario<GlobalSettingsActivity> scenario = ActivityScenario.launch(GlobalSettingsActivity.class)) {
                awaitRows(scenario);
                scenario.onActivity(activity -> {
                    LinearLayout list = activity.findViewById(R.id.app_list);
                    AppAssignmentRow row = (AppAssignmentRow) list.getChildAt(1);
                    String tag = row.choice(0).getTag().toString();
                    String packageName = tag.substring("assignment:".length(), tag.lastIndexOf(':'));
                    selectedPackage.set(packageName);
                    row.choice(0).setChecked(true);
                    row.choice(1).setChecked(false);
                    row.choice(2).setChecked(true);
                });
                assertTrue(manager.getSelectedPackages().contains(selectedPackage.get()));
                assertFalse(manager.getTimerPackages().contains(selectedPackage.get()));
                assertTrue(manager.getSubliminalPackages().contains(selectedPackage.get()));
                assertEquals(armed, manager.isArmed());
                assertEquals(mode, manager.getMode());
                scenario.recreate();
                awaitRows(scenario);
                scenario.onActivity(activity -> {
                    CheckBox censor = activity.findViewById(R.id.app_list)
                            .findViewWithTag("assignment:" + selectedPackage.get() + ":0");
                    CheckBox limit = activity.findViewById(R.id.app_list)
                            .findViewWithTag("assignment:" + selectedPackage.get() + ":1");
                    CheckBox subliminal = activity.findViewById(R.id.app_list)
                            .findViewWithTag("assignment:" + selectedPackage.get() + ":2");
                    assertTrue(censor.isChecked());
                    assertFalse(limit.isChecked());
                    assertTrue(subliminal.isChecked());
                });
                ControllerPinManager.enterSubMode();
                scenario.recreate();
                awaitRows(scenario);
                scenario.onActivity(activity -> {
                    CheckBox censor = activity.findViewById(R.id.app_list)
                            .findViewWithTag("assignment:" + selectedPackage.get() + ":0");
                    assertFalse(censor.isEnabled());
                });
                assertTrue(manager.getSelectedPackages().contains(selectedPackage.get()));
            }
        } finally {
            manager.saveAppSelections(censorBefore, limitBefore, subliminalBefore);
            manager.save(armed, mode, censorBefore);
            if (wasDom) ControllerPinManager.enterDomMode();
            else ControllerPinManager.enterSubMode();
        }
    }

    private static void awaitRows(ActivityScenario<GlobalSettingsActivity> scenario) throws Exception {
        AtomicBoolean loaded = new AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> loaded.set(
                    ((LinearLayout) activity.findViewById(R.id.app_list)).getChildCount() > 1));
            if (loaded.get()) return;
            Thread.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("Launcher assignments did not load within the bounded fixture deadline");
    }

    @Test public void narrowLargeTextAndRtlKeepIndependentTargetsInsideRow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int screenWidth : new int[]{320, 360, 411}) {
                for (float font : new float[]{1f, 1.3f, 2f}) {
                    for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
                        AppAssignmentRow row = row(font);
                        row.setLayoutDirection(direction);
                        int width = dp(row.getContext(), screenWidth - 40);
                        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        row.layout(0, 0, width, row.getMeasuredHeight());
                        Rect previous = null;
                        for (int index = 0; index < 3; index++) {
                            CheckBox check = row.choice(index);
                            assertTrue(check.getWidth() >= dp(row.getContext(), 48));
                            assertTrue(check.getHeight() >= dp(row.getContext(), 48));
                            assertTrue(check.isFocusable());
                            Rect bounds = new Rect(0, 0, check.getWidth(), check.getHeight());
                            row.offsetDescendantRectToMyCoords(check, bounds);
                            assertTrue(bounds.left >= 0 && bounds.right <= width);
                            assertTrue(bounds.top >= 0 && bounds.bottom <= row.getHeight());
                            if (previous != null) assertFalse(Rect.intersects(previous, bounds));
                            previous = bounds;
                            assertTrue(check.getContentDescription().toString().startsWith("A very long"));
                        }
                        assertEquals(font > 1.4f, row.isStacked());
                        if (font == 1f) {
                            assertEquals("Compact rows are 56dp including padding", dp(row.getContext(), 56),
                                    row.getHeight());
                        }
                        android.util.Log.i("AppAssignmentGeometry", "screen=" + screenWidth
                                + " font=" + font + " direction=" + direction
                                + " rowHeightDp=" + row.getHeight()
                                / row.getResources().getDisplayMetrics().density);
                    }
                }
            }
        });
    }

    @Test public void independentChecksExposeAppAndModuleAndDoNotChangeOtherChoices() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AppAssignmentRow row = row(1);
            assertTrue(row.choice(0).isChecked());
            assertFalse(row.choice(1).isChecked());
            assertTrue(row.choice(2).isChecked());
            row.choice(1).performClick();
            assertTrue(row.choice(0).isChecked());
            assertTrue(row.choice(1).isChecked());
            assertTrue(row.choice(2).isChecked());
            assertTrue(row.choice(0).getContentDescription().toString().endsWith("Censor"));
            assertTrue(row.choice(1).getContentDescription().toString().endsWith("Limit"));
            assertTrue(row.choice(2).getContentDescription().toString().endsWith("Subliminal"));
            assertEquals("assignment:example.app:1", row.choice(1).getTag());
            assertNotSame(row.choice(0).getBackground(), row.choice(1).getBackground());
        });
    }

    private static AppAssignmentRow row(float font) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = font;
        Context themed = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_SubHub);
        return new AppAssignmentRow(themed, "A very long application name", "example.app",
                themed.getDrawable(R.drawable.ic_launcher_foreground), new boolean[]{true, false, true});
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
