package com.abosultan.darbakmaps.ui;

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

final class TracksDialog {
    private static final int STATS_POINT_LIMIT = 10000;
    private static final double OFF_TRACK_WARNING_METERS = 180d;

    static void show(Context c, OfflineMapView map) {
        SessionStore session = new SessionStore(c); boolean recording = session.shouldResumeTrackRecording();
        int segments = 0, points = 0; double retainedMeters; TrackSegment latestUsable = null;
        SqliteTrackRecorder reader = new SqliteTrackRecorder(c);
        try {
            List<TrackSegment> recent = reader.recentSegments(STATS_POINT_LIMIT); segments = recent.size();
            for (TrackSegment segment : recent) { points += segment.points.size(); if (segment.points.size() >= 2) latestUsable = segment; }
            retainedMeters = reader.retainedDistanceMeters();
        } finally { reader.close(); }
        StringBuilder message = new StringBuilder();
        message.append("التسجيل  ").append(recording ? "يعمل" : "متوقف مؤقتًا")
                .append("\nالمسافة الدائرية  ").append(formatDistance(retainedMeters)).append(" / 1000 كم")
                .append("\nالمقاطع  ").append(segments).append("\nالنقاط  ").append(points).append(points >= STATS_POINT_LIMIT ? "+" : "")
                .append("\nالمسارات المحفوظة  ").append(GpxTrackExporter.savedTracks(c).length)
                .append("\nالرجوع على المسار  ").append(map.isBacktrackActive() ? "مفعّل" : "متوقف");
        if (map.isBacktrackActive() && latestUsable != null) {
            LocationSnapshot fix = LiveLocationStore.latest();
            if (fix.valid) {
                double offTrack = new BacktrackNavigator(latestUsable.points).offTrackMeters(fix.latitude, fix.longitude);
                message.append("\nالبعد عن المسار  ").append(formatDistance(offTrack)); if (offTrack > OFF_TRACK_WARNING_METERS) message.append(" ⚠");
            } else message.append("\nالبعد عن المسار  بانتظار GPS");
        }
        String control = recording ? "إيقاف مؤقت" : "استئناف التسجيل";
        DarbakDialog.infoActions(c, "المسارات", message.toString(), new String[]{control, "إجراءات المسار"}, which -> {
            if (which == 0) setRecording(c, !recording); else showTrackActions(c, map);
        });
    }

    private static void showTrackActions(Context c, OfflineMapView map) {
        String backtrack = map.isBacktrackActive() ? "إلغاء الرجوع على المسار" : "رجوع على آخر مسار";
        String[] actions = {backtrack, "حفظ المسار الحالي GPX", "المسارات المحفوظة"};
        DarbakDialog.menu(c, "إجراءات المسار", actions, which -> {
            if (which == 0) toggleBacktrack(c, map); else if (which == 1) exportGpx(c); else showSavedTracks(c);
        });
    }

    private static void showSavedTracks(Context c) {
        File[] files = GpxTrackExporter.savedTracks(c);
        if (files.length == 0) { Toast.makeText(c, "لا توجد مسارات محفوظة", Toast.LENGTH_SHORT).show(); return; }
        String[] rows = new String[files.length];
        for (int i = 0; i < files.length; i++) rows[i] = files[i].getName() + "   •   " + Math.max(1, files[i].length() / 1024) + " KB";
        DarbakDialog.menu(c, "المسارات المحفوظة — " + files.length, rows, which -> { });
    }

    private static void setRecording(Context c, boolean enabled) {
        new SessionStore(c).setTrackRecording(enabled);
        Intent service = new Intent(c, TrackRecordingService.class); service.setAction(enabled ? TrackRecordingService.ACTION_RESUME : TrackRecordingService.ACTION_PAUSE); c.startService(service);
        Toast.makeText(c, enabled ? "تم استئناف تسجيل المسار" : "تم إيقاف تسجيل المسار مؤقتًا", Toast.LENGTH_SHORT).show();
    }
    private static void toggleBacktrack(Context c, OfflineMapView map) {
        if (map.isBacktrackActive()) { map.stopBacktrack(); Toast.makeText(c, "تم إلغاء الرجوع على المسار", Toast.LENGTH_SHORT).show(); }
        else if (!map.startBacktrack()) Toast.makeText(c, "لا يوجد مسار محفوظ كافٍ للرجوع", Toast.LENGTH_SHORT).show();
        else Toast.makeText(c, "بدأ الرجوع على آخر مسار", Toast.LENGTH_SHORT).show();
    }
    private static void exportGpx(Context c) { try { File f = GpxTrackExporter.exportAll(c); Toast.makeText(c, "تم حفظ المسار: " + f.getName(), Toast.LENGTH_LONG).show(); } catch (Exception e) { Toast.makeText(c, "تعذر حفظ GPX", Toast.LENGTH_LONG).show(); } }
    private static String formatDistance(double meters) { if (Double.isInfinite(meters) || Double.isNaN(meters) || meters == Double.MAX_VALUE) return "غير متاح"; return meters < 1000d ? Math.round(meters) + " م" : String.format(Locale.US, "%.1f كم", meters / 1000d); }
    private TracksDialog() {}
}
