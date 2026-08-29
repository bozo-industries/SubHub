package com.subhub.app.util;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.subhub.app.R;

/** Configures the single visual contract used by every primary destination header. */
public final class PrimaryHeader {
    private PrimaryHeader() {}

    public static void bind(View root, @DrawableRes int iconRes, @StringRes int titleRes,
            @StringRes int subtitleRes) {
        ImageView icon = root.findViewById(R.id.primary_header_icon);
        TextView title = root.findViewById(R.id.primary_header_title);
        TextView subtitle = root.findViewById(R.id.primary_header_subtitle);
        icon.setImageResource(iconRes);
        icon.setContentDescription(root.getContext().getString(titleRes));
        title.setText(titleRes);
        subtitle.setText(subtitleRes);
    }

    public static View view(View root) {
        return root.findViewById(R.id.primary_header);
    }

    public static TextView backButton(View root) {
        return root.findViewById(R.id.button_back);
    }

    public static TextView editLockButton(View root) {
        return root.findViewById(R.id.button_edit_lock);
    }

    public static TextView subtitle(View root) {
        return root.findViewById(R.id.primary_header_subtitle);
    }
}
