package com.abosultan.darbakmaps;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

/**
 * Keeps MainActivity untouched while replacing only the visible "more" entry point.
 * The original listener is retained as a safe fallback for legacy map/license tools.
 */
final class DarbakMoreButton extends TextView {
    private final Activity activity;
    private View.OnClickListener legacyListener;

    DarbakMoreButton(Activity activity) {
        super(activity);
        this.activity = activity;
        super.setOnClickListener(view -> DarbakPanels.showMore(activity, legacyListener, view));
    }

    @Override
    public void setOnClickListener(View.OnClickListener listener) {
        legacyListener = listener;
    }
}
