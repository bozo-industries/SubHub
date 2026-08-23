package com.betasafe.app.security;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;

/** Shared Dom/Sub role toggle for settings-changing surfaces. */
public final class ControllerEditMode {
    public interface Listener {
        void onEditStateChanged(boolean editing);
    }

    private final Activity activity;
    private final TextView button;
    private final Listener listener;

    private ControllerEditMode(Activity activity, TextView button, Listener listener) {
        this.activity = activity;
        this.button = button;
        this.listener = listener;
        if (!ControllerPinManager.isDomModeActive() && !(activity instanceof MainActivity)) {
            button.setVisibility(View.GONE);
            listener.onEditStateChanged(false);
            enterSubMode(activity);
            return;
        }
        button.setOnClickListener(view -> toggle());
        refresh();
    }

    public static ControllerEditMode bind(
            Activity activity, TextView button, Listener listener) {
        return new ControllerEditMode(activity, button, listener);
    }

    public static void renderButton(Activity activity, TextView button) {
        boolean editing = ControllerPinManager.isDomModeActive();
        button.setText(editing
                ? R.string.controller_edit_unlocked : R.string.controller_edit_locked);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        button.setContentDescription(activity.getString(editing
                ? R.string.controller_edit_unlocked_description
                : R.string.controller_edit_locked_description));
        button.setSelected(editing);
    }

    public boolean isEditing() {
        return ControllerPinManager.isDomModeActive();
    }

    public void refresh() {
        renderButton(activity, button);
        listener.onEditStateChanged(isEditing());
    }

    public void setEditable(View... views) {
        boolean editing = isEditing();
        for (View view : views) {
            if (view != null) view.setEnabled(editing);
        }
    }

    private void toggle() {
        if (isEditing()) {
            enterSubMode(activity);
        } else {
            ControllerPinGate.require(activity, this::refresh, false);
        }
    }

    /** Leaves all configuration surfaces and returns to the single Sub dashboard. */
    public static void enterSubMode(Activity activity) {
        ControllerPinManager.enterSubMode();
        if (activity instanceof MainActivity) return;
        activity.startActivity(new Intent(activity, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        activity.finish();
        activity.overridePendingTransition(0, 0);
    }
}
