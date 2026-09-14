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
import android.widget.TextView;

/** Lightweight branded confirmation panel for the car screen. */
final class DarbakConfirmDialog {
    private static final int DEEP = Color.rgb(8, 39, 31);
    private static final int SURFACE = Color.rgb(16, 52, 42);
    private static final int GOLD = Color.rgb(216, 180, 91);
    private static final int TEXT = Color.rgb(247, 242, 231);
    private static final int MUTED = Color.rgb(185, 179, 165);
    private static final int DANGER = Color.rgb(196, 88, 77);

    private DarbakConfirmDialog() {}

    static void show(Activity activity, String title, String message,
                     String confirmText, boolean destructive, Runnable confirmAction) {
        if (activity == null || activity.isFinishing()) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 18));
        root.setBackground(round(DEEP, dp(activity, 24), Color.argb(160, 216, 180, 91)));

        TextView heading = text(activity, title, TEXT, 22f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(activity, 40)));
        TextView body = text(activity, message, MUTED, 14f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        body.setLineSpacing(3f, 1.05f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(activity, 76));
        bp.bottomMargin = dp(activity, 10);
        root.addView(body, bp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        TextView confirm = text(activity, confirmText, destructive ? TEXT : DEEP, 15f, Gravity.CENTER);
        confirm.setBackground(round(destructive ? DANGER : GOLD, dp(activity, 15), Color.TRANSPARENT));
        TextView cancel = text(activity, "إلغاء", TEXT, 15f, Gravity.CENTER);
        cancel.setBackground(round(SURFACE, dp(activity, 15), Color.argb(55, 216, 180, 91)));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(activity, 46), 1f);
        ap.setMargins(dp(activity, 5), 0, dp(activity, 5), 0);
        actions.addView(confirm, ap);
        actions.addView(cancel, ap);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(activity, 50)));

        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (confirmAction != null) confirmAction.run();
        });

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(dp(activity, 620), WindowManager.LayoutParams.WRAP_CONTENT);
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(immersiveFlags());
            }
        });
        dialog.show();
    }

    private static TextView text(Activity activity, String value, int color, float size, int gravity) {
        TextView view = new TextView(activity);
        view.setText(value == null ? "" : value);
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
