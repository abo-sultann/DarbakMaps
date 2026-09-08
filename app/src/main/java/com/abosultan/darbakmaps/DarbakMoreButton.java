package com.abosultan.darbakmaps;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

/** Keeps the legacy activity listener from reopening old settings dialogs. */
final class DarbakMoreButton extends TextView {
    DarbakMoreButton(Activity activity) {
        super(activity);
        super.setOnClickListener(view -> DarbakPanels.showMore(activity));
    }

    @Override
    public void setOnClickListener(View.OnClickListener ignored) {
        // MainActivity still binds its old listener; intentionally ignore it.
    }
}
