package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.BacktrackNavigator;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.GpxTrackExporter;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.SessionStore;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder.TrackSegment;
import com.abosultan.darbakmaps.core.TrackRecordingService;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Small track control/status sheet designed for the low-memory 1024x600 head unit. */
final class TracksDialog {
    private static final int STATS_POINT_LIMIT = 10000;
    private static final double OFF_TRACK_WARNING_METERS = 180d;

    static void show(Context c, OfflineMapView map) {
        SessionStore session = new SessionStore(c);
        boolean recording = session.shouldResumeTrackRecording();

        int segments = 0;
        int points = 0;
        double retainedMeters = 0d;
        TrackSegment latestUsable = null;
        SqliteTrackRecorder reader = new SqliteTrackRecorder(c);
        try {
            List<TrackSegment> recent = reader.recentSegments(STATS_POINT_LIMIT);
            segments = recent.size();
            for (TrackSegment segment : recent) {
                points += segment.points.size();
                if (segment.points.size() >= 2) latestUsable = segment;
            }
            retainedMeters = reader.retainedDistanceMeters();
        } finally {
            reader.close();
        }

        String status = recording ? "يعمل" : "متوقف مؤقتًا";
        StringBuilder message = new StringBuilder();
        message.append("التسجيل: ").append(status)
                .append("\nالمسافة المحفوظة: ").append(formatDistance(retainedMeters)).append(" / 1000 كم")
                .append("\nالمقاطع المحفوظة: ").append(segments)
                .append("\nالنقاط المحفوظة: ").append(points)
                .append(points >= STATS_POINT_LIMIT ? "+" : "")
                .append("\nالرجوع على المسار: ").append(map.isBacktrackActive() ? "مفعّل" : "متوقف");

        if (map.isBacktrackActive() && latestUsable != null) {
            LocationSnapshot fix = LiveLocationStore.latest();
            if (fix.valid) {
                BacktrackNavigator navigator = new BacktrackNavigator(latestUsable.points);
                double offTrack = navigator.offTrackMeters(fix.latitude, fix.longitude);
                message.append("\nالبعد عن المسار: ").append(formatDistance(offTrack));
                if (offTrack > OFF_TRACK_WARNING_METERS) message.append(" ⚠");
            } else {
                message.append("\nالبعد عن المسار: بانتظار GPS");
            }
        }

        String control = recording ? "إيقاف مؤقت" : "استئناف التسجيل";
        new AlertDialog.Builder(c)
                .setTitle("المسارات")
                .setMessage(message.toString())
                .setPositiveButton(control, (d, w) -> setRecording(c, !recording))
                .setNeutralButton("إجراءات المسار", (d, w) -> showTrackActions(c, map))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private static void showTrackActions(Context c, OfflineMapView map) {
        String backtrack = map.isBacktrackActive() ? "إلغاء الرجوع على المسار" : "رجوع على آخر مسار";
        String[] actions = new String[]{backtrack, "حفظ نسخة GPX"};
        new AlertDialog.Builder(c)
                .setTitle("إجراءات المسار")
                .setItems(actions, (d, which) -> {
                    if (which == 0) toggleBacktrack(c, map);
                    else exportGpx(c);
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private static void setRecording(Context c, boolean enabled) {
        SessionStore session = new SessionStore(c);
        session.setTrackRecording(enabled);

        Intent service = new Intent(c, TrackRecordingService.class);
        service.setAction(enabled ? TrackRecordingService.ACTION_RESUME : TrackRecordingService.ACTION_PAUSE);
        c.startService(service);

        Toast.makeText(c, enabled ? "تم استئناف تسجيل المسار" : "تم إيقاف تسجيل المسار مؤقتًا", Toast.LENGTH_SHORT).show();
    }

    private static void toggleBacktrack(Context c, OfflineMapView map) {
        if (map.isBacktrackActive()) {
            map.stopBacktrack();
            Toast.makeText(c, "تم إلغاء الرجوع على المسار", Toast.LENGTH_SHORT).show();
        } else if (!map.startBacktrack()) {
            Toast.makeText(c, "لا يوجد مسار محفوظ كافٍ للرجوع", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(c, "بدأ الرجوع على آخر مسار", Toast.LENGTH_SHORT).show();
        }
    }

    private static void exportGpx(Context c) {
        try {
            File file = GpxTrackExporter.exportAll(c);
            Toast.makeText(c, "تم حفظ GPX: " + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(c, "تعذر حفظ GPX", Toast.LENGTH_LONG).show();
        }
    }

    private static String formatDistance(double meters) {
        if (Double.isInfinite(meters) || Double.isNaN(meters) || meters == Double.MAX_VALUE) return "غير متاح";
        return meters < 1000d ? Math.round(meters) + " م" : String.format(Locale.US, "%.1f كم", meters / 1000d);
    }

    private TracksDialog() {}
}
