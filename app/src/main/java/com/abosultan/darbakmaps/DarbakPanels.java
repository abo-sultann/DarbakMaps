package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.update.UpdateManager;

/** Lightweight Darbak UI V1 panels for the car screen. */
final class DarbakPanels {
    private static final int NIGHT = Color.rgb(7, 17, 29);
    private static final int SURFACE = Color.rgb(17, 29, 43);
    private static final int SURFACE_ALT = Color.rgb(16, 30, 44);
    private static final int PRIMARY = Color.rgb(57, 169, 255);
    private static final int GOLD = Color.rgb(215, 173, 85);
    private static final int TEXT = Color.rgb(244, 247, 250);
    private static final int MUTED = Color.rgb(159, 176, 194);
    private static final int SUCCESS = Color.rgb(76, 217, 137);

    private DarbakPanels() {}

    static void showMore(Activity activity, View.OnClickListener legacyListener, View source) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 26);
        root.addView(title(activity, "دربك خرائط"));
        root.addView(subtitle(activity, "الإعدادات والأدوات"));

        LinearLayout row1 = row(activity);
        row1.addView(card(activity, "الإعدادات", "التشغيل مع الشاشة", () -> {
            dialog.dismiss();
            showSettings(activity);
        }), weightedCard());
        row1.addView(card(activity, "حول", "الإصدار والهوية", () -> {
            dialog.dismiss();
            showAbout(activity);
        }), weightedCard());
        root.addView(row1);

        LinearLayout row2 = row(activity);
        row2.addView(card(activity, "التحديث", "البحث عن نسخة جديدة", () -> {
            dialog.dismiss();
            checkForUpdates(activity);
        }), weightedCard());
        row2.addView(card(activity, "أدوات الخريطة", "الخرائط والإحداثيات والترخيص", () -> {
            dialog.dismiss();
            if (legacyListener != null) legacyListener.onClick(source);
        }), weightedCard());
        root.addView(row2);

        TextView hint = text(activity, "اضغط مطولًا على رقم الإصدار في «حول» لفتح التشخيص", MUTED, 12f, Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(activity, 34));
        hintParams.topMargin = dp(activity, 8);
        root.addView(hint, hintParams);

        dialog.setContentView(root);
        show(dialog, activity, 760);
    }

    static void showSettings(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 28);
        root.addView(title(activity, "الإعدادات"));
        root.addView(subtitle(activity, "إعدادات أساسية فقط — بدون تعقيد"));

        LinearLayout settingCard = new LinearLayout(activity);
        settingCard.setOrientation(LinearLayout.HORIZONTAL);
        settingCard.setGravity(Gravity.CENTER_VERTICAL);
        settingCard.setPadding(dp(activity, 22), dp(activity, 14), dp(activity, 22), dp(activity, 14));
        settingCard.setBackground(round(SURFACE_ALT, dp(activity, 22), Color.argb(90, 57, 169, 255)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.RIGHT);
        labels.addView(text(activity, "التشغيل مع الشاشة", TEXT, 18f, Gravity.RIGHT));
        labels.addView(text(activity, "يفتح دربك خرائط تلقائيًا عند تشغيل الشاشة", MUTED, 13f, Gravity.RIGHT));
        settingCard.addView(labels, new LinearLayout.LayoutParams(0, dp(activity, 72), 1f));

        Switch toggle = new Switch(activity);
        toggle.setChecked(StartupPreferences.isEnabled(activity));
        toggle.setOnCheckedChangeListener((buttonView, checked) -> StartupPreferences.setEnabled(activity, checked));
        settingCard.addView(toggle, new LinearLayout.LayoutParams(dp(activity, 86), dp(activity, 72)));
        root.addView(settingCard, new LinearLayout.LayoutParams(-1, dp(activity, 100)));

        TextView done = action(activity, "تم", PRIMARY, NIGHT);
        done.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(activity, 180), dp(activity, 52));
        doneParams.gravity = Gravity.CENTER;
        doneParams.topMargin = dp(activity, 18);
        root.addView(done, doneParams);

        dialog.setContentView(root);
        show(dialog, activity, 700);
    }

    static void showAbout(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView brand = new ImageView(activity);
        brand.setImageResource(R.drawable.darbak_brand);
        brand.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(brand, new LinearLayout.LayoutParams(dp(activity, 110), dp(activity, 92)));

        root.addView(text(activity, "دربك خرائط", TEXT, 25f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 44)));
        root.addView(text(activity, "خرائط أوفلاين للبر والمدن", MUTED, 14f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 30)));

        TextView version = text(activity,
                "الإصدار " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                PRIMARY, 15f, Gravity.CENTER);
        version.setBackground(round(Color.argb(38, 57, 169, 255), dp(activity, 16), Color.argb(90, 57, 169, 255)));
        version.setOnLongClickListener(view -> {
            showDiagnostics(activity);
            return true;
        });
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(dp(activity, 260), dp(activity, 42));
        versionParams.topMargin = dp(activity, 8);
        root.addView(version, versionParams);

        TextView divider = new TextView(activity);
        divider.setBackgroundColor(GOLD);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(activity, 260), dp(activity, 2));
        dividerParams.topMargin = dp(activity, 18);
        dividerParams.bottomMargin = dp(activity, 14);
        root.addView(divider, dividerParams);

        root.addView(text(activity, "تصميم وتطوير", GOLD, 14f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 28)));
        root.addView(text(activity, "أبوسلطان", GOLD, 24f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        root.addView(text(activity, "جميع الحقوق محفوظة", MUTED, 12f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 28)));

        LinearLayout buttons = row(activity);
        TextView update = action(activity, "البحث عن تحديث", PRIMARY, NIGHT);
        update.setOnClickListener(view -> {
            dialog.dismiss();
            checkForUpdates(activity);
        });
        TextView close = action(activity, "إغلاق", SURFACE_ALT, TEXT);
        close.setOnClickListener(view -> dialog.dismiss());
        buttons.addView(update, weightedAction());
        buttons.addView(close, weightedAction());
        root.addView(buttons);

        dialog.setContentView(root);
        show(dialog, activity, 680);
    }

    private static void showDiagnostics(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 26);
        root.addView(title(activity, "تشخيص دربك"));
        root.addView(subtitle(activity, "معلومات فنية محلية — لا يتم إرسال شيء للخارج"));

        TextView report = text(activity, DarbakPlatformRuntime.healthReport(), TEXT, 14f, Gravity.RIGHT);
        report.setTextDirection(View.TEXT_DIRECTION_RTL);
        report.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 14));
        report.setBackground(round(SURFACE_ALT, dp(activity, 20), Color.argb(80, 57, 169, 255)));
        root.addView(report, new LinearLayout.LayoutParams(-1, dp(activity, 230)));

        TextView clear = action(activity, "مسح آخر Crash", SURFACE_ALT, TEXT);
        clear.setOnClickListener(view -> {
            DarbakPlatformRuntime.clearCrash();
            Toast.makeText(activity, "تم مسح سجل الانهيار", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(activity, 210), dp(activity, 48));
        clearParams.gravity = Gravity.CENTER;
        clearParams.topMargin = dp(activity, 16);
        root.addView(clear, clearParams);

        dialog.setContentView(root);
        show(dialog, activity, 720);
    }

    private static void checkForUpdates(Activity activity) {
        Toast.makeText(activity, "جارٍ التحقق من التحديث…", Toast.LENGTH_SHORT).show();
        UpdateManager.check(new UpdateManager.Callback() {
            @Override
            public void onStatus(String message) {
                activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onUpdate(UpdateManager.UpdateInfo update) {
                activity.runOnUiThread(() -> {
                    AlertDialog updateDialog = new AlertDialog.Builder(activity)
                            .setTitle("تحديث " + update.versionName)
                            .setMessage("نسخة جديدة من دربك خرائط جاهزة للتثبيت")
                            .setNegativeButton("لاحقًا", null)
                            .setPositiveButton("تنزيل وتثبيت", (dialog, which) ->
                                    UpdateManager.downloadAndInstall(activity, update, this))
                            .create();
                    showSystemDialog(updateDialog, activity);
                });
            }
        });
    }

    private static Dialog baseDialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private static LinearLayout panel(Activity activity, int padding) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.RIGHT);
        root.setPadding(dp(activity, padding), dp(activity, 22), dp(activity, padding), dp(activity, 22));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(round(NIGHT, dp(activity, 28), Color.argb(110, 57, 169, 255)));
        return root;
    }

    private static TextView title(Activity activity, String value) {
        return text(activity, value, TEXT, 26f, Gravity.RIGHT);
    }

    private static TextView subtitle(Activity activity, String value) {
        TextView view = text(activity, value, MUTED, 13f, Gravity.RIGHT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 36));
        params.bottomMargin = dp(activity, 10);
        view.setLayoutParams(params);
        return view;
    }

    private static LinearLayout row(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
        return row;
    }

    private static TextView card(Activity activity, String label, String detail, Runnable action) {
        TextView view = text(activity, label + "\n" + detail, TEXT, 17f, Gravity.CENTER);
        view.setLineSpacing(3f, 1f);
        view.setBackground(round(SURFACE, dp(activity, 22), Color.argb(80, 57, 169, 255)));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private static LinearLayout.LayoutParams weightedCard() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 108, 1f);
        params.setMargins(6, 0, 6, 0);
        return params;
    }

    private static LinearLayout.LayoutParams weightedAction() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 52, 1f);
        params.setMargins(7, 0, 7, 0);
        return params;
    }

    private static TextView action(Activity activity, String value, int fill, int color) {
        TextView view = text(activity, value, color, 15f, Gravity.CENTER);
        view.setBackground(round(fill, dp(activity, 18), Color.TRANSPARENT));
        return view;
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

    private static void show(Dialog dialog, Activity activity, int widthDp) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        dialog.setOnShowListener(ignored -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                shown.setLayout(dp(activity, widthDp), WindowManager.LayoutParams.WRAP_CONTENT);
                shown.getDecorView().setSystemUiVisibility(immersiveFlags());
                shown.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            }
        });
        dialog.show();
    }

    private static void showSystemDialog(Dialog dialog, Activity activity) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.getDecorView().setSystemUiVisibility(immersiveFlags());
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
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
