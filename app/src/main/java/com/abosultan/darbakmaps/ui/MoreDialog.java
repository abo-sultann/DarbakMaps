package com.abosultan.darbakmaps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
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
        String message = "الإصدار: " + versionName(c)
                + "\nالخريطة: " + (mapName == null ? "غير جاهزة" : mapName)
                + "\nGPS: " + (fix.valid ? "متصل" : "بانتظار الإشارة")
                + "\nتسجيل المسار: " + (session.shouldResumeTrackRecording() ? "يعمل" : "متوقف مؤقتًا");
        String[] actions = {
                "فحص التحديث",
                "حول دربك للخرائط",
                autoLaunch ? "إيقاف التشغيل التلقائي" : "تفعيل التشغيل التلقائي",
                "تمركز على السيارة"
        };
        new AlertDialog.Builder(c)
                .setTitle("المزيد")
                .setMessage(message)
                .setItems(actions, (d, which) -> {
                    if (which == 0) checkUpdate(c);
                    else if (which == 1) showAbout(c);
                    else if (which == 2) {
                        session.setAutoLaunch(!autoLaunch);
                        Toast.makeText(c, !autoLaunch ? "تم تفعيل التشغيل التلقائي" : "تم إيقاف التشغيل التلقائي", Toast.LENGTH_SHORT).show();
                    } else if (!map.recenterOnGps()) {
                        Toast.makeText(c, map.hasMap() ? "بانتظار إشارة GPS" : "الخريطة غير جاهزة", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    static void showDiagnostics(Context c, OfflineMapView map) {
        LocationSnapshot fix = LiveLocationStore.latest();
        File db = c.getDatabasePath("darbak_tracks.db");
        SqliteTrackRecorder reader = new SqliteTrackRecorder(c);
        double retained;
        try { retained = reader.retainedDistanceMeters(); }
        finally { reader.close(); }
        long age = fix.valid ? Math.max(0L, System.currentTimeMillis() - fix.timestampMs) : -1L;
        String text = "الحزمة: " + c.getPackageName()
                + "\nالإصدار: " + versionName(c)
                + "\nالخريطة: " + (map.activeMapName() == null ? "غير جاهزة" : map.activeMapName())
                + "\nGPS age: " + (age < 0 ? "—" : age + " ms")
                + "\nالمسار الدائري: " + Math.round(retained / 1000d) + " كم"
                + "\nقاعدة المسار: " + (db != null && db.isFile() ? db.length() / 1024 + " KB" : "—")
                + "\nAndroid API: " + android.os.Build.VERSION.SDK_INT;
        new AlertDialog.Builder(c).setTitle("تشخيص دربك").setMessage(text).setPositiveButton("إغلاق", null).show();
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
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setTitle("تحديث متاح — " + update.versionName)
                        .setMessage("سيتم تنزيل النسخة والتحقق من سلامتها وتوقيعها قبل فتح مثبت النظام.")
                        .setNegativeButton("لاحقًا", null)
                        .setPositiveButton("تنزيل وتثبيت", (d, w) -> UpdateManager.downloadAndInstall(activity, update, this))
                        .show());
            }
        });
    }

    private static void showAbout(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(DarbakUi.dp(c, 16), DarbakUi.dp(c, 8), DarbakUi.dp(c, 16), DarbakUi.dp(c, 8));
        TextView text = new TextView(c);
        text.setText("دربك للخرائط\nالإصدار " + versionName(c) + "\nخرائط بر أوفلاين — Android 7.1 / 1024×600");
        text.setTextColor(DarbakUi.TEXT);
        text.setTextSize(17f);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setGravity(android.view.Gravity.CENTER);
        box.addView(text, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView signature = new ImageView(c);
        signature.setImageResource(R.drawable.darbak_owner_signature);
        signature.setAdjustViewBounds(true);
        signature.setScaleType(ImageView.ScaleType.FIT_CENTER);
        signature.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DarbakUi.dp(c, 170));
        ip.topMargin = DarbakUi.dp(c, 10);
        box.addView(signature, ip);

        new AlertDialog.Builder(c).setTitle("حول").setView(box).setPositiveButton("إغلاق", null).show();
    }

    private static String versionName(Context c) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return info.versionName == null ? "—" : info.versionName;
        } catch (Exception ignored) { return "—"; }
    }

    private MoreDialog() {}
}
