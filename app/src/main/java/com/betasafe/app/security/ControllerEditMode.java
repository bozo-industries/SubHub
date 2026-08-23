package com.betasafe.app.security;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.betasafe.app.R;

/** Shared, explicit controller edit toggle for every settings-changing surface. */
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
        button.setOnClickListener(view -> toggle());
        refresh();
    }

    public static ControllerEditMode bind(
            Activity activity, TextView button, Listener listener) {
        return new ControllerEditMode(activity, button, listener);
    }

    public static void renderButton(Activity activity, TextView button) {
        boolean editing = ControllerPinManager.isSessionUnlocked();
        button.setText(editing
                ? R.string.controller_edit_unlocked : R.string.controller_edit_locked);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        button.setContentDescription(activity.getString(editing
                ? R.string.controller_edit_unlocked_description
                : R.string.controller_edit_locked_description));
        button.setSelected(editing);
    }

    public boolean isEditing() {
        return ControllerPinManager.isSessionUnlocked();
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
            ControllerPinManager.lockNow();
            refresh();
        } else {
            ControllerPinGate.require(activity, this::refresh, false);
        }
    }
}
