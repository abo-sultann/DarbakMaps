package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.SessionStore;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder.TrackSegment;
import com.abosultan.darbakmaps.core.TrackRecordingService;

import java.util.List;

/** Small track control/status sheet designed for the low-memory 1024x600 head unit. */
final class TracksDialog {
    private static final int STATS_POINT_LIMIT = 10000;

    static void show(Context c) {
        SessionStore session = new SessionStore(c);
        boolean recording = session.shouldResumeTrackRecording();

        int segments = 0;
        int points = 0;
        SqliteTrackRecorder reader = new SqliteTrackRecorder(c);
        try {
            List<TrackSegment> recent = reader.recentSegments(STATS_POINT_LIMIT);
            segments = recent.size();
            for (TrackSegment segment : recent) points += segment.points.size();
        } finally {
            reader.close();
        }

        String status = recording ? "يعمل" : "متوقف مؤقتًا";
        String message = "التسجيل: " + status
                + "\nالمقاطع المحفوظة: " + segments
                + "\nالنقاط المحفوظة: " + points
                + (points >= STATS_POINT_LIMIT ? "+" : "");

        String control = recording ? "إيقاف مؤقت" : "استئناف التسجيل";
        new AlertDialog.Builder(c)
                .setTitle("المسارات")
                .setMessage(message)
                .setPositiveButton(control, (d, w) -> setRecording(c, !recording))
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

    private TracksDialog() {}
}
