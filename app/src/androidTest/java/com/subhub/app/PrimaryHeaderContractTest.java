package com.subhub.app;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.appmode.AppModeActivity;
import com.subhub.app.atmosphere.AtmosphereActivity;
import com.subhub.app.penance.PenanceActivity;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.settings.FeatureModuleManager;
import com.subhub.app.settings.GlobalSettingsActivity;
import com.subhub.app.settings.SettingsActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PrimaryHeaderContractTest {
    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ControllerPinManager.enterDomMode();
        new FeatureModuleManager(context).save(true, true, true);
    }

    @Test public void everyPrimaryDestinationUsesTheSameHeaderGeometry() {
        assertHeader(MainActivity.class, R.string.app_name);
        assertHeader(SettingsActivity.class, R.string.censor_header_title);
        assertHeader(AppModeActivity.class, R.string.app_mode_title);
        assertHeader(PenanceActivity.class, R.string.penance_title);
        assertHeader(AtmosphereActivity.class, R.string.atmosphere_title);
        assertHeader(GlobalSettingsActivity.class, R.string.global_settings_title);
    }

    private void assertHeader(Class<? extends Activity> activityClass, int titleRes) {
        Intent intent = new Intent(context, activityClass);
        if (activityClass == MainActivity.class) {
            intent.setAction(Intent.ACTION_MAIN)
                    .putExtra(MainActivity.EXTRA_SUPPRESS_PERMISSION_READINESS, true);
        }
        try (ActivityScenario<? extends Activity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                View header = activity.findViewById(R.id.primary_header);
                ImageView icon = activity.findViewById(R.id.primary_header_icon);
                TextView title = activity.findViewById(R.id.primary_header_title);
                TextView subtitle = activity.findViewById(R.id.primary_header_subtitle);
                assertEquals(dp(activity, 10), header.getTop());
                assertEquals(dp(activity, 92), header.getHeight());
                assertEquals(activity.getResources().getDisplayMetrics().widthPixels
                        - dp(activity, 32), header.getWidth());
                assertEquals(dp(activity, 44), icon.getWidth());
                assertEquals(activity.getString(titleRes), title.getText().toString());
                assertEquals(20f, title.getTextSize()
                        / activity.getResources().getDisplayMetrics().scaledDensity, 0.3f);
                assertEquals(12f, subtitle.getTextSize()
                        / activity.getResources().getDisplayMetrics().scaledDensity, 0.3f);
            });
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
