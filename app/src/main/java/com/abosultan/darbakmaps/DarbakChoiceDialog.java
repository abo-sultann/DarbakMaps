package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Lightweight Darbak-native chooser used instead of stock Android item dialogs. */
final class DarbakChoiceDialog {
    interface Listener { void onSelected(int index); }

    private static final int DEEP = Color.rgb(8, 39, 31);
    private static final int SURFACE = Color.rgb(16, 52, 42);
    private static final int SURFACE_ALT = Color.rgb(25, 72, 58);
    private static final int GOLD = Color.rgb(216, 180, 91);
    private static final int TEXT = Color.rgb(247, 242, 231);
    private static final int MUTED = Color.rgb(185, 179, 165);

    private DarbakChoiceDialog() {}

    static void show(Activity activity, String title, String subtitle,
                     String[] items, Listener listener) {
        show(activity, title, subtitle, items, listener, -1);
    }

    static void show(Activity activity, String title, String subtitle,
                     String[] items, Listener listener, int accentIndex) {
        if (activity == null || activity.isFinishing()) return;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 18));
        root.setBackground(round(DEEP, dp(activity, 24), Color.argb(170, 216, 180, 91)));

        TextView heading = text(activity, title == null ? "دربك" : title, TEXT, 22f,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(activity, 38)));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(activity, subtitle, MUTED, 12.5f,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(activity, 34));
            sp.bottomMargin = dp(activity, 6);
            root.addView(sub, sp);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                final int index = i;
                boolean accent = index == accentIndex;
                TextView row = text(activity, items[i], accent ? DEEP : TEXT,
                        15f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                row.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 16), dp(activity, 4));
                row.setBackground(round(accent ? GOLD : SURFACE_ALT,
                        dp(activity, 15), accent ? GOLD : Color.argb(55, 216, 180, 91)));
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (listener != null) listener.onSelected(index);
                });
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(activity, 52));
                rp.topMargin = dp(activity, 6);
                list.addView(row, rp);
            }
        }
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(scroll, scrollParams);

        TextView close = text(activity, "إغلاق", MUTED, 14f, Gravity.CENTER);
        close.setBackground(round(SURFACE, dp(activity, 14), Color.argb(50, 216, 180, 91)));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(activity, 150), dp(activity, 42));
        cp.gravity = Gravity.CENTER;
        cp.topMargin = dp(activity, 12);
        root.addView(close, cp);

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(dp(activity, 720), dp(activity, 500));
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(immersiveFlags());
            }
        });
        dialog.show();
    }

    private static TextView text(Activity activity, String value, int color, float size, int gravity) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(gravity);
        return view;
    }

    private static GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(radius);
        if (Color.alpha(stroke) > 0) background.setStroke(1, stroke);
        return background;
    }

    private static int immersiveFlags() {
        return View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
