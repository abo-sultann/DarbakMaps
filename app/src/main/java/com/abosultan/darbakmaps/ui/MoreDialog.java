package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.abosultan.darbakmaps.BuildConfig;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.SessionStore;

/** Small real settings/status surface; avoids a heavy settings Activity on the 1 GB head unit. */
final class MoreDialog {
    static void show(Context c, OfflineMapView map) {
        SessionStore session = new SessionStore(c);
        boolean autoLaunch = session.shouldAutoLaunch();
        boolean recording = session.shouldResumeTrackRecording();
        LocationSnapshot fix = LiveLocationStore.latest();
        String mapName = map.activeMapName();

        String message = "الإصدار: " + BuildConfig.VERSION_NAME
                + "\nالخريطة: " + (mapName == null ? "غير جاهزة" : mapName)
                + "\nGPS: " + (fix.valid ? "متصل" : "بانتظار الإشارة")
                + "\nتسجيل المسار: " + (recording ? "يعمل" : "متوقف مؤقتًا")
                + "\nالتشغيل التلقائي: " + (autoLaunch ? "مفعّل" : "متوقف");

        new AlertDialog.Builder(c)
                .setTitle("المزيد")
                .setMessage(message)
                .setPositiveButton(autoLaunch ? "إيقاف التشغيل التلقائي" : "تفعيل التشغيل التلقائي", (d, w) -> {
                    session.setAutoLaunch(!autoLaunch);
                    Toast.makeText(c,
                            !autoLaunch ? "سيظهر دربك للخرائط تلقائيًا بعد إقلاع الشاشة" : "تم إيقاف التشغيل التلقائي",
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("تمركز على السيارة", (d, w) -> {
                    if (!map.recenterOnGps()) {
                        Toast.makeText(c, "بانتظار إشارة GPS", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private MoreDialog() {}
}
