package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public final class DarbakUi {
    public static final int BG = Color.rgb(10, 22, 51);
    public static final int SURFACE = Color.rgb(16, 32, 64);
    public static final int CARD = Color.rgb(16, 43, 92);
    public static final int ACTIVE = Color.rgb(23, 59, 108);
    public static final int BORDER = Color.rgb(45, 85, 115);
    public static final int ACCENT = Color.rgb(25, 181, 255);
    public static final int TEXT = Color.rgb(243, 248, 255);
    public static final int TEXT_SECONDARY = Color.rgb(208, 226, 241);

    private DarbakUi() {}

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int fill, int stroke, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static TextView action(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(17);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c, 54));
        v.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        v.setBackground(rounded(SURFACE, BORDER, 17, c));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    public static void setSelected(View view, boolean selected, Context c) {
        view.setBackground(rounded(selected ? ACTIVE : SURFACE, selected ? ACCENT : BORDER, 17, c));
    }
}
