package com.abosultan.darbakmaps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.R;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.SessionStore;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder;
import com.abosultan.darbakmaps.update.UpdateManager;

import java.io.File;

final class MoreDialog {
    static void show(Context c, OfflineMapView map) {
        SessionStore session = new SessionStore(c);
        boolean autoLaunch = session.shouldAutoLaunch();
        LocationSnapshot fix = LiveLocationStore.latest();
        String mapName = map.activeMapName();
        String status = "الإصدار  " + versionName(c)
                + "\nالخريطة  " + (mapName == null ? "غير جاهزة" : mapName)
                + "\nGPS  " + (fix.valid ? "متصل" : "بانتظار الإشارة")
                + "\nتسجيل المسار  " + (session.shouldResumeTrackRecording() ? "يعمل" : "متوقف مؤقتًا");

        LinearLayout box = panel(c, "المزيد");
        TextView statusView = bodyText(c, status, 16, false);
        statusView.setBackground(DarbakUi.rounded(DarbakUi.CARD, DarbakUi.BORDER, 14, c));
        statusView.setPadding(DarbakUi.dp(c, 18), DarbakUi.dp(c, 12), DarbakUi.dp(c, 18), DarbakUi.dp(c, 12));
        box.addView(statusView, matchWrap(c, 0));

        final AlertDialog[] holder = new AlertDialog[1];
        addAction(c, box, "فحص التحديث", v -> {
            if (holder[0] != null) holder[0].dismiss();
            checkUpdate(c);
        });
        addAction(c, box, "حول دربك للخرائط", v -> {
            if (holder[0] != null) holder[0].dismiss();
            showAbout(c);
        });
        addAction(c, box, autoLaunch ? "إيقاف التشغيل التلقائي" : "تفعيل التشغيل التلقائي", v -> {
            session.setAutoLaunch(!autoLaunch);
            Toast.makeText(c, !autoLaunch ? "تم تفعيل التشغيل التلقائي" : "تم إيقاف التشغيل التلقائي", Toast.LENGTH_SHORT).show();
            if (holder[0] != null) holder[0].dismiss();
        });
        addAction(c, box, "تمركز على السيارة", v -> {
            if (!map.recenterOnGps()) Toast.makeText(c, map.hasMap() ? "بانتظار إشارة GPS" : "الخريطة غير جاهزة", Toast.LENGTH_SHORT).show();
            if (holder[0] != null) holder[0].dismiss();
        });
        addSecondary(c, box, "إغلاق", v -> { if (holder[0] != null) holder[0].dismiss(); });

        holder[0] = showPanel(c, box);
    }

    static void showDiagnostics(Context c, OfflineMapView map) {
        LocationSnapshot fix = LiveLocationStore.latest();
        File db = c.getDatabasePath("darbak_tracks.db");
        SqliteTrackRecorder reader = new SqliteTrackRecorder(c);
        double retained;
        try { retained = reader.retainedDistanceMeters(); }
        finally { reader.close(); }
        long age = fix.valid ? Math.max(0L, System.currentTimeMillis() - fix.timestampMs) : -1L;
        String text = "الحزمة  " + c.getPackageName()
                + "\nالإصدار  " + versionName(c)
                + "\nالخريطة  " + (map.activeMapName() == null ? "غير جاهزة" : map.activeMapName())
                + "\nGPS age  " + (age < 0 ? "—" : age + " ms")
                + "\nالمسار الدائري  " + Math.round(retained / 1000d) + " كم"
                + "\nقاعدة المسار  " + (db != null && db.isFile() ? db.length() / 1024 + " KB" : "—")
                + "\nAndroid API  " + android.os.Build.VERSION.SDK_INT;

        LinearLayout box = panel(c, "تشخيص دربك");
        box.addView(bodyText(c, text, 16, false), matchWrap(c, 0));
        final AlertDialog[] holder = new AlertDialog[1];
        addSecondary(c, box, "إغلاق", v -> { if (holder[0] != null) holder[0].dismiss(); });
        holder[0] = showPanel(c, box);
    }

    private static void checkUpdate(Context c) {
        if (!(c instanceof Activity)) {
            Toast.makeText(c, "تعذر فتح التحديث", Toast.LENGTH_SHORT).show();
            return;
        }
        Activity activity = (Activity) c;
        Toast.makeText(c, "جارٍ فحص التحديث…", Toast.LENGTH_SHORT).show();
        UpdateManager.check(new UpdateManager.Callback() {
            @Override public void onStatus(String message) {
                activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
            }
            @Override public void onUpdate(UpdateManager.UpdateInfo update) {
                activity.runOnUiThread(() -> showUpdateAvailable(activity, update, this));
            }
        });
    }

    private static void showUpdateAvailable(Activity activity, UpdateManager.UpdateInfo update, UpdateManager.Callback callback) {
        LinearLayout box = panel(activity, "تحديث متاح — " + update.versionName);
        box.addView(bodyText(activity, "سيتم تنزيل النسخة والتحقق من سلامتها وتوقيعها قبل فتح مثبت النظام.", 16, false), matchWrap(activity, 0));
        final AlertDialog[] holder = new AlertDialog[1];
        addAction(activity, box, "تنزيل وتثبيت", v -> {
            if (holder[0] != null) holder[0].dismiss();
            UpdateManager.downloadAndInstall(activity, update, callback);
        });
        addSecondary(activity, box, "لاحقًا", v -> { if (holder[0] != null) holder[0].dismiss(); });
        holder[0] = showPanel(activity, box);
    }

    private static void showAbout(Context c) {
        LinearLayout box = panel(c, "حول دربك للخرائط");
        TextView text = bodyText(c, "دربك للخرائط\nالإصدار " + versionName(c) + "\nخرائط بر أوفلاين — Android 7.1 / 1024×600", 17, true);
        text.setGravity(Gravity.CENTER);
        box.addView(text, matchWrap(c, 0));

        ImageView signature = new ImageView(c);
        signature.setImageResource(R.drawable.darbak_owner_signature);
        signature.setAdjustViewBounds(true);
        signature.setScaleType(ImageView.ScaleType.FIT_CENTER);
        signature.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DarbakUi.dp(c, 130));
        ip.topMargin = DarbakUi.dp(c, 8);
        box.addView(signature, ip);

        final AlertDialog[] holder = new AlertDialog[1];
        addSecondary(c, box, "إغلاق", v -> { if (holder[0] != null) holder[0].dismiss(); });
        holder[0] = showPanel(c, box);
    }

    private static LinearLayout panel(Context c, String titleText) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = DarbakUi.dp(c, 18);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(DarbakUi.rounded(DarbakUi.BG, DarbakUi.ACCENT, 20, c));
        TextView title = bodyText(c, titleText, 21, true);
        title.setTextColor(DarbakUi.ACCENT);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams tp = matchWrap(c, 0);
        tp.bottomMargin = DarbakUi.dp(c, 12);
        box.addView(title, tp);
        return box;
    }

    private static TextView bodyText(Context c, String value, int sp, boolean bold) {
        TextView text = new TextView(c);
        text.setText(value);
        text.setTextColor(DarbakUi.TEXT);
        text.setTextSize(sp);
        text.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        text.setLineSpacing(0f, 1.12f);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private static void addAction(Context c, LinearLayout box, String label, View.OnClickListener listener) {
        TextView action = DarbakUi.action(c, label);
        action.setOnClickListener(listener);
        LinearLayout.LayoutParams p = matchWrap(c, 8);
        p.height = DarbakUi.dp(c, 50);
        box.addView(action, p);
    }

    private static void addSecondary(Context c, LinearLayout box, String label, View.OnClickListener listener) {
        TextView action = DarbakUi.action(c, label);
        action.setTextColor(DarbakUi.TEXT_SECONDARY);
        action.setBackground(DarbakUi.rounded(DarbakUi.CARD, DarbakUi.BORDER, 14, c));
        action.setOnClickListener(listener);
        LinearLayout.LayoutParams p = matchWrap(c, 8);
        p.height = DarbakUi.dp(c, 46);
        box.addView(action, p);
    }

    private static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = DarbakUi.dp(c, topMarginDp);
        return p;
    }

    private static AlertDialog showPanel(Context c, LinearLayout box) {
        AlertDialog dialog = new AlertDialog.Builder(c).setView(box).create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.55f);
            }
        });
        dialog.show();
        return dialog;
    }

    private static String versionName(Context c) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return info.versionName == null ? "—" : info.versionName;
        } catch (Exception ignored) { return "—"; }
    }

    private MoreDialog() {}
}
