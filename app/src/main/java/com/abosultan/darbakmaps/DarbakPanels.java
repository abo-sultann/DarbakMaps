package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.map.MapStorage;
import com.abosultan.darbakmaps.map.RecommendedMapDownloader;
import com.abosultan.darbakmaps.update.UpdateManager;

import java.io.File;
import java.util.Locale;

/** Unified lightweight Darbak panels for the car screen. */
final class DarbakPanels {
    private static final int NIGHT = Color.rgb(7, 17, 29);
    private static final int SURFACE = Color.rgb(17, 29, 43);
    private static final int SURFACE_ALT = Color.rgb(16, 30, 44);
    private static final int PRIMARY = Color.rgb(57, 169, 255);
    private static final int GOLD = Color.rgb(215, 173, 85);
    private static final int TEXT = Color.rgb(244, 247, 250);
    private static final int MUTED = Color.rgb(159, 176, 194);
    private static final int MAP_FILE_REQUEST = 702;

    private DarbakPanels() {}

    static void showMore(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 24);
        root.addView(title(activity, "دربك للبر"));
        root.addView(subtitle(activity, "واجهة دربك للبر — أوفلاين ومهيأة لشاشة السيارة"));

        LinearLayout row1 = row(activity);
        row1.addView(card(activity, "الإعدادات", "التوجيه والعلامات واتجاه الخريطة والعرض", () -> {
            dialog.dismiss();
            showSettings(activity);
        }), weightedCard());
        row1.addView(card(activity, "الخرائط", mapStatus(activity), () -> {
            dialog.dismiss();
            showMaps(activity);
        }), weightedCard());
        root.addView(row1);

        LinearLayout row2 = row(activity);
        row2.addView(card(activity, "الإحداثيات", "موقعك الحالي", () -> {
            dialog.dismiss();
            showCoordinates(activity);
        }), weightedCard());
        row2.addView(card(activity, "التحديث", "البحث عن نسخة جديدة", () -> {
            dialog.dismiss();
            checkForUpdates(activity);
        }), weightedCard());
        root.addView(row2);

        TextView about = card(activity, "حول دربك", "الإصدار والهوية والتشخيص", () -> {
            dialog.dismiss();
            showAbout(activity);
        });
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(-1, dp(activity, 82));
        aboutParams.setMargins(dp(activity, 6), dp(activity, 6), dp(activity, 6), 0);
        root.addView(about, aboutParams);

        TextView hint = text(activity, "لمسة على الخريطة تُظهر أو تُخفي الأدوات • السرعة تبقى ظاهرة دائمًا", MUTED, 12f, Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(activity, 34));
        hintParams.topMargin = dp(activity, 6);
        root.addView(hint, hintParams);

        dialog.setContentView(root);
        show(dialog, activity, 760);
    }

    static void showSettings(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 18);
        root.addView(title(activity, "إعدادات دربك"));
        root.addView(subtitle(activity, "التحكم بالخريطة والتوجيه والعلامات وشاشة السيارة"));

        root.addView(toggleCard(activity,
                "التشغيل مع الشاشة",
                "يفتح دربك تلقائيًا بعد تشغيل الشاشة",
                StartupPreferences.isEnabled(activity),
                checked -> StartupPreferences.setEnabled(activity, checked)));

        TextView routing = text(activity,
                "نمط التوجيه: " + MapRuntimeBridge.routingLabel(MapUiPreferences.routingMode(activity)) + "  •  اضغط للتغيير",
                TEXT, 15f, Gravity.CENTER);
        routing.setBackground(round(SURFACE_ALT, dp(activity, 20), Color.argb(90, 215, 173, 85)));
        routing.setOnClickListener(view -> {
            int next = MapUiPreferences.routingMode(activity) == MapUiPreferences.ROUTING_DIRECT
                    ? MapUiPreferences.ROUTING_ROADS : MapUiPreferences.ROUTING_DIRECT;
            MapUiPreferences.setRoutingMode(activity, next);
            String suffix = next == MapUiPreferences.ROUTING_ROADS ? " (تجريبي)" : "";
            routing.setText("نمط التوجيه: " + MapRuntimeBridge.routingLabel(next) + suffix + "  •  اضغط للتغيير");
        });
        LinearLayout.LayoutParams routingParams = new LinearLayout.LayoutParams(-1, dp(activity, 60));
        routingParams.topMargin = dp(activity, 7);
        root.addView(routing, routingParams);

        root.addView(toggleCard(activity,
                "علامات المواقع المحفوظة",
                "إظهار الأيقونات على الخريطة دائمًا",
                MapUiPreferences.showSavedPlaces(activity),
                checked -> { MapUiPreferences.setShowSavedPlaces(activity, checked); MapRuntimeBridge.refreshSavedPlaces(activity); }), compact(activity));

        root.addView(toggleCard(activity,
                "أسماء المواقع المحفوظة",
                "إظهار الاسم بجوار أيقونة المخيم أو البيت وغيرها",
                MapUiPreferences.showSavedLabels(activity),
                checked -> { MapUiPreferences.setShowSavedLabels(activity, checked); MapRuntimeBridge.refreshSavedPlaces(activity); }), compact(activity));

        root.addView(toggleCard(activity,
                "إظهار السرعة",
                "إظهار أو إخفاء قراءة كم/س على الخريطة",
                MapUiPreferences.showSpeed(activity),
                checked -> {
                    MapUiPreferences.setShowSpeed(activity, checked);
                    View speed = activity.findViewById(R.id.speed_panel);
                    if (speed != null) speed.setVisibility(checked ? View.VISIBLE : View.GONE);
                }), compact(activity));

        root.addView(toggleCard(activity,
                "إبقاء الشاشة مضاءة",
                "مناسب للقيادة والبر أثناء عرض الخريطة",
                MapUiPreferences.keepScreenOn(activity),
                checked -> {
                    MapUiPreferences.setKeepScreenOn(activity, checked);
                    if (checked) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }), compact(activity));

        root.addView(toggleCard(activity,
                "رسم وتسجيل المسار بالخلفية",
                "يسجل خط سيرك ويستمر حتى عند إغلاق التطبيق",
                MapUiPreferences.backgroundTrackEnabled(activity),
                checked -> {
                    BackgroundTrackService.setEnabled(activity, checked);
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).onBackgroundTrackSettingChanged(checked);
                    }
                }), compact(activity));

        root.addView(toggleCard(activity,
                "متابعة السيارة أثناء القيادة",
                "يبقي الخريطة متمركزة على سيارتك أثناء الحركة",
                MapUiPreferences.followVehicle(activity),
                checked -> MapUiPreferences.setFollowVehicle(activity, checked)), compact(activity));

        root.addView(toggleCard(activity,
                "إحصاءات المسار",
                "المسافة والمدة والمتوسط وأعلى سرعة أثناء التسجيل",
                MapUiPreferences.showTrackStats(activity),
                checked -> MapUiPreferences.setShowTrackStats(activity, checked)), compact(activity));

        root.addView(toggleCard(activity,
                "تنبيه الابتعاد عن الاتجاه",
                "يظهر تنبيه واضح عندما يصبح اتجاه السيارة بعيدًا عن الهدف",
                MapUiPreferences.offRouteAlert(activity),
                checked -> MapUiPreferences.setOffRouteAlert(activity, checked)), compact(activity));

        TextView orientation = text(activity,
                "اتجاه الخريطة: " + MapRuntimeBridge.label(MapUiPreferences.orientation(activity)) + "  •  اضغط للتغيير",
                TEXT, 15f, Gravity.CENTER);
        orientation.setBackground(round(SURFACE_ALT, dp(activity, 20), Color.argb(80, 57, 169, 255)));
        orientation.setOnClickListener(view -> {
            int mode = MapRuntimeBridge.cycleOrientation(activity);
            orientation.setText("اتجاه الخريطة: " + MapRuntimeBridge.label(mode) + "  •  اضغط للتغيير");
        });
        LinearLayout.LayoutParams orientationParams = new LinearLayout.LayoutParams(-1, dp(activity, 58));
        orientationParams.topMargin = dp(activity, 7);
        root.addView(orientation, orientationParams);

        TextView behavior = text(activity,
                "الأدوات تظهر وتختفي بلمسة على الخريطة فقط — بدون مؤقت",
                GOLD, 12.5f, Gravity.CENTER);
        LinearLayout.LayoutParams behaviorParams = new LinearLayout.LayoutParams(-1, dp(activity, 38));
        behaviorParams.topMargin = dp(activity, 6);
        root.addView(behavior, behaviorParams);

        TextView done = action(activity, "تم", PRIMARY, NIGHT);
        done.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(activity, 160), dp(activity, 44));
        doneParams.gravity = Gravity.CENTER;
        doneParams.topMargin = dp(activity, 6);
        root.addView(done, doneParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(root);
        dialog.setContentView(scroll);
        show(dialog, activity, 820);
    }

    private static LinearLayout.LayoutParams compact(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 72));
        params.topMargin = dp(activity, 6);
        return params;
    }

    private interface ToggleAction {
        void onChanged(boolean checked);
    }

    private static LinearLayout toggleCard(Activity activity, String title, String detail,
                                           boolean checked, ToggleAction action) {
        LinearLayout settingCard = new LinearLayout(activity);
        settingCard.setOrientation(LinearLayout.HORIZONTAL);
        settingCard.setGravity(Gravity.CENTER_VERTICAL);
        settingCard.setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10));
        settingCard.setBackground(round(SURFACE_ALT, dp(activity, 22), Color.argb(80, 57, 169, 255)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.RIGHT);
        labels.addView(text(activity, title, TEXT, 17f, Gravity.RIGHT));
        labels.addView(text(activity, detail, MUTED, 12f, Gravity.RIGHT));
        settingCard.addView(labels, new LinearLayout.LayoutParams(0, dp(activity, 68), 1f));

        Switch toggle = new Switch(activity);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((buttonView, value) -> action.onChanged(value));
        settingCard.addView(toggle, new LinearLayout.LayoutParams(dp(activity, 86), dp(activity, 68)));
        return settingCard;
    }

    private static void showMaps(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 26);
        root.addView(title(activity, "الخرائط الأوفلاين"));
        root.addView(subtitle(activity, mapStatus(activity)));

        TextView download = action(activity, "خريطة دربك السعودية المعتمدة", PRIMARY, NIGHT);
        download.setOnClickListener(view -> {
            dialog.dismiss();
            downloadRecommendedMap(activity);
        });
        root.addView(download, new LinearLayout.LayoutParams(-1, dp(activity, 56)));

        TextView importMap = action(activity, "إضافة خريطة من USB أو الذاكرة", SURFACE_ALT, TEXT);
        importMap.setOnClickListener(view -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, MAP_FILE_REQUEST);
        });
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(-1, dp(activity, 56));
        importParams.topMargin = dp(activity, 10);
        root.addView(importMap, importParams);

        TextView close = action(activity, "إغلاق", SURFACE, TEXT);
        close.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(activity, 180), dp(activity, 48));
        closeParams.gravity = Gravity.CENTER;
        closeParams.topMargin = dp(activity, 16);
        root.addView(close, closeParams);

        dialog.setContentView(root);
        show(dialog, activity, 660);
    }

    private static String mapStatus(Activity activity) {
        File file = MapStorage.activeMap(activity);
        if (!file.isFile() || file.length() <= 0) {
            return "لا توجد خريطة محمّلة";
        }
        long mb = Math.max(1, file.length() / (1024L * 1024L));
        return "جاهزة أوفلاين • " + mb + " م.ب";
    }

    private static void downloadRecommendedMap(Activity activity) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("خرائط دربك");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setMessage("بدء التنزيل…");
        progress.setCancelable(false);
        showSystemDialog(progress, activity);

        new Thread(() -> {
            try {
                RecommendedMapDownloader.download(activity, new RecommendedMapDownloader.Listener() {
                    @Override
                    public void onProgress(int percent, String message) {
                        activity.runOnUiThread(() -> {
                            progress.setProgress(percent);
                            progress.setMessage(message);
                        });
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                });
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(activity, "الخريطة جاهزة أوفلاين", Toast.LENGTH_SHORT).show();
                    activity.recreate();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(activity,
                            error.getMessage() == null ? "تعذر تنزيل الخريطة" : error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }, "darbak-map-download").start();
    }

    private static void showCoordinates(Activity activity) {
        String message = "بانتظار إشارة GPS";
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationManager manager = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
                Location location = manager == null ? null : manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    message = String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude());
                }
            } catch (RuntimeException ignored) {
                // Keep the safe fallback message on vendor ROMs.
            }
        }
        AlertDialog coordinateDialog = new AlertDialog.Builder(activity)
                .setTitle("الإحداثيات الحالية")
                .setMessage(message)
                .setPositiveButton("تم", null)
                .create();
        showSystemDialog(coordinateDialog, activity);
    }

    static void showAbout(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 26);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView brand = new ImageView(activity);
        brand.setImageResource(R.drawable.darbak_brand);
        brand.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(brand, new LinearLayout.LayoutParams(dp(activity, 105), dp(activity, 88)));

        root.addView(text(activity, "دربك للبر", TEXT, 25f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        root.addView(text(activity, "خرائط متجهية أوفلاين للبر • البحث يشمل المدن والمعالم", MUTED, 13f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 30)));

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
        dividerParams.topMargin = dp(activity, 16);
        dividerParams.bottomMargin = dp(activity, 12);
        root.addView(divider, dividerParams);

        root.addView(text(activity, "تصميم وتطوير", GOLD, 14f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 26)));
        root.addView(text(activity, "أبوسلطان", GOLD, 24f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 40)));
        root.addView(text(activity, "بيانات الخريطة © مساهمو OpenStreetMap", MUTED, 11f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 26)));

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
        LinearLayout root = panel(activity, 24);
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
        clearParams.topMargin = dp(activity, 14);
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
                            .setMessage("نسخة جديدة من دربك جاهزة للتثبيت")
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
        root.setPadding(dp(activity, padding), dp(activity, 20), dp(activity, padding), dp(activity, 20));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(round(NIGHT, dp(activity, 28), Color.argb(110, 57, 169, 255)));
        return root;
    }

    private static TextView title(Activity activity, String value) {
        return text(activity, value, TEXT, 25f, Gravity.RIGHT);
    }

    private static TextView subtitle(Activity activity, String value) {
        TextView view = text(activity, value, MUTED, 13f, Gravity.RIGHT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 36));
        params.bottomMargin = dp(activity, 8);
        view.setLayoutParams(params);
        return view;
    }

    private static LinearLayout row(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(activity, 5), 0, dp(activity, 5));
        return row;
    }

    private static TextView card(Activity activity, String label, String detail, Runnable action) {
        TextView view = text(activity, label + "\n" + detail, TEXT, 16f, Gravity.CENTER);
        view.setLineSpacing(3f, 1f);
        view.setBackground(round(SURFACE, dp(activity, 22), Color.argb(80, 57, 169, 255)));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private static LinearLayout.LayoutParams weightedCard() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 96, 1f);
        params.setMargins(6, 0, 6, 0);
        return params;
    }

    private static LinearLayout.LayoutParams weightedAction() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 50, 1f);
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
