package com.subhub.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.subhub.app.util.CompactChoiceGroup;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CompactChoiceGroupAndroidTest {
    @Test public void choicesWrapWithoutClippingAtNarrowWideAndLargeTextSizes() {
        Context base = ApplicationProvider.getApplicationContext();
        for (int width : new int[]{320, 480}) for (float scale : new float[]{1f, 1.7f}) {
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.fontScale = scale;
            config.screenWidthDp = width;
            Context themed = new android.view.ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_SubHub);
            for (int layout : new int[]{R.layout.activity_settings, R.layout.activity_global_settings}) {
                View page = LayoutInflater.from(themed).inflate(layout, null, false);
                int pixels = Math.round(width * themed.getResources().getDisplayMetrics().density);
                page.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY));
                page.layout(0, 0, pixels, 1280);
                assertTrue(check(page) > 0);
            }
        }
    }

    private static int check(View view) {
        int count = 0;
        if (view instanceof CompactChoiceGroup) {
            CompactChoiceGroup group = (CompactChoiceGroup) view;
            if (group.getVisibility() == View.GONE) return 0;
            count++;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView choice = (TextView) group.getChildAt(i);
                if (choice.getVisibility() == View.GONE) continue;
                assertTrue(choice.getWidth() > 0);
                assertNotNull(choice.getLayout());
                assertTrue(choice.getLayout().getHeight() <= choice.getHeight()
                        - choice.getCompoundPaddingTop() - choice.getCompoundPaddingBottom());
                for (int line = 0; line < choice.getLineCount(); line++) {
                    assertEquals(0, choice.getLayout().getEllipsisCount(line));
                }
                for (int j = i + 1; j < group.getChildCount(); j++) {
                    View other = group.getChildAt(j);
                    if (other.getVisibility() == View.GONE) continue;
                    assertFalse(android.graphics.Rect.intersects(new android.graphics.Rect(choice.getLeft(), choice.getTop(), choice.getRight(), choice.getBottom()),
                            new android.graphics.Rect(other.getLeft(), other.getTop(), other.getRight(), other.getBottom())));
                }
            }
            if (group.getChildCount() > 1) {
                group.check(group.getChildAt(0).getId());
                group.check(group.getChildAt(1).getId());
                assertFalse(((RadioButton) group.getChildAt(0)).isChecked());
                assertTrue(((RadioButton) group.getChildAt(1)).isChecked());
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += check(group.getChildAt(i));
        }
        return count;
    }
}
