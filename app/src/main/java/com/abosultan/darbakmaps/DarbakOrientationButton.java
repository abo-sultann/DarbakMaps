package com.abosultan.darbakmaps;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/** One-button orientation control inspired by off-road and navigation apps. */
final class DarbakOrientationButton extends TextView {
    private static final int PRIMARY = Color.rgb(57, 169, 255);

    DarbakOrientationButton(Activity activity) {
        super(activity);
        setId(R.id.orientation_mode);
        setGravity(Gravity.CENTER);
        setTextColor(PRIMARY);
        setTextSize(13f);
        setPadding(dp(8), 0, dp(8), 0);
        setBackground(round(Color.argb(247, 17, 29, 43), dp(22), Color.argb(90, 57, 169, 255)));
        refresh();
        setOnClickListener(view -> {
            int mode = MapRuntimeBridge.cycleOrientation(getContext());
            refresh();
            Toast.makeText(getContext(), description(mode), Toast.LENGTH_SHORT).show();
        });
        setOnLongClickListener(view -> {
            MapRuntimeBridge.setOrientation(getContext(), MapUiPreferences.ORIENTATION_NORTH);
            refresh();
            Toast.makeText(getContext(), "الشمال أعلى الخريطة", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void refresh() {
        int mode = MapUiPreferences.orientation(getContext());
        setText(MapRuntimeBridge.label(mode));
        setContentDescription(description(mode));
    }

    private String description(int mode) {
        if (mode == MapUiPreferences.ORIENTATION_HEADING) {
            return "الخريطة باتجاه حركة السيارة";
        }
        if (mode == MapUiPreferences.ORIENTATION_FREE) {
            return "اتجاه الخريطة حر";
        }
        return "الشمال أعلى الخريطة";
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(radius);
        background.setStroke(1, stroke);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
