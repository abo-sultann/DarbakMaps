package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/** Shared Darbak desert identity: charcoal surfaces, warm gold accents, high-contrast text. */
public final class DarbakUi {
    public static final int BG = Color.rgb(11, 15, 18);
    public static final int SURFACE = Color.rgb(18, 25, 31);
    public static final int CARD = Color.rgb(23, 32, 39);
    public static final int ACTIVE = Color.rgb(46, 38, 24);
    public static final int BORDER = Color.rgb(92, 75, 42);
    public static final int ACCENT = Color.rgb(222, 174, 78);
    public static final int TEXT = Color.rgb(247, 244, 236);
    public static final int TEXT_SECONDARY = Color.rgb(205, 197, 179); // build repair candidate

    private static final int IMMERSIVE_FLAGS = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

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

    static void showImmersive(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }
}
