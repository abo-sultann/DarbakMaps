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

/** Small branded information panel; replaces stock message dialogs. */
final class DarbakInfoDialog {
    private static final int DEEP = Color.rgb(8, 39, 31);
    private static final int SURFACE = Color.rgb(16, 52, 42);
    private static final int GOLD = Color.rgb(216, 180, 91);
    private static final int TEXT = Color.rgb(247, 242, 231);
    private static final int MUTED = Color.rgb(185, 179, 165);

    private DarbakInfoDialog() {}

    static void show(Activity activity, String title, String message) {
        if (activity == null || activity.isFinishing()) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 18));
        root.setBackground(round(DEEP, dp(activity, 24), Color.argb(160, 216, 180, 91)));

        TextView heading = text(activity, title, TEXT, 21f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        TextView body = text(activity, message, MUTED, 14f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        body.setLineSpacing(3f, 1.05f);
        root.addView(body, new LinearLayout.LayoutParams(-1, dp(activity, 86)));

        TextView close = text(activity, "تم", DEEP, 15f, Gravity.CENTER);
        close.setBackground(round(GOLD, dp(activity, 15), Color.TRANSPARENT));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(activity, 150), dp(activity, 44));
        cp.gravity = Gravity.CENTER;
        cp.topMargin = dp(activity, 8);
        root.addView(close, cp);

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
