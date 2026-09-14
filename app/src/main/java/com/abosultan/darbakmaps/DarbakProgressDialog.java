package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Branded progress panel for map imports/downloads and data recovery. */
final class DarbakProgressDialog {
    private static final int DEEP = Color.rgb(8, 39, 31);
    private static final int SURFACE = Color.rgb(16, 52, 42);
    private static final int GOLD = Color.rgb(216, 180, 91);
    private static final int TEXT = Color.rgb(247, 242, 231);
    private static final int MUTED = Color.rgb(185, 179, 165);

    private final Activity activity;
    private final Dialog dialog;
    private final ProgressBar bar;
    private final TextView message;
    private final TextView percent;

    DarbakProgressDialog(Activity activity, String title, boolean canCancel, Runnable cancelAction) {
        this.activity = activity;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(24), dp(20), dp(24), dp(18));
        root.setBackground(round(DEEP, dp(24), Color.argb(160, 216, 180, 91)));

        TextView heading = text(title, TEXT, 21f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(40)));

        message = text("جارٍ التنفيذ…", MUTED, 13.5f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(message, new LinearLayout.LayoutParams(-1, dp(46)));

        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        if (bar.getProgressDrawable() != null) bar.getProgressDrawable().setColorFilter(GOLD, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(18));
        bp.topMargin = dp(6);
        root.addView(bar, bp);

        percent = text("0%", GOLD, 13f, Gravity.CENTER);
        root.addView(percent, new LinearLayout.LayoutParams(-1, dp(34)));

        if (canCancel) {
            TextView cancel = text("إيقاف", TEXT, 14f, Gravity.CENTER);
            cancel.setBackground(round(SURFACE, dp(14), Color.argb(55, 216, 180, 91)));
            cancel.setOnClickListener(v -> {
                if (cancelAction != null) cancelAction.run();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(150), dp(42));
            cp.gravity = Gravity.CENTER;
            cp.topMargin = dp(8);
            root.addView(cancel, cp);
        }

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(dp(620), WindowManager.LayoutParams.WRAP_CONTENT);
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(immersiveFlags());
            }
        });
    }

    void show() {
        if (!activity.isFinishing()) dialog.show();
    }

    void setProgress(int value, String detail) {
        int safe = Math.max(0, Math.min(100, value));
        bar.setProgress(safe);
        percent.setText(safe + "%");
        if (detail != null && !detail.trim().isEmpty()) message.setText(detail);
    }

    void setMessage(String detail) {
        if (detail != null) message.setText(detail);
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }

    private TextView text(String value, int color, float size, int gravity) {
        TextView view = new TextView(activity);
        view.setText(value == null ? "" : value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(gravity);
        return view;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(radius);
        if (Color.alpha(stroke) > 0) background.setStroke(1, stroke);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
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
}
