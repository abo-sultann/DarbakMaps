package com.abosultan.darbakmaps;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.widget.TextView;

import java.util.Locale;

/** Small persistent stats state for the active background track. */
public final class TrackSessionState {
    private static final String PREFS = "darbak_track_state";
    private static final String START = "start";
    private static final String DIST = "distance";
    private static final String MAX = "max_speed";
    private static final String LAST_LAT = "last_lat";
    private static final String LAST_LON = "last_lon";
    private static final String HAS_LAST = "has_last";
    private static final String PAUSED = "paused";
    private static final String PAUSE_STARTED = "pause_started";
    private static final String PAUSED_TOTAL = "paused_total";
    private static final String NEW_SEGMENT = "new_segment";
    private static final String REVISION = "revision";

    private TrackSessionState() {}

    public static synchronized void beginIfNeeded(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getLong(START, 0L) == 0L) {
            p.edit()
                    .putLong(START, System.currentTimeMillis())
                    .putBoolean(PAUSED, false)
                    .putLong(PAUSE_STARTED, 0L)
                    .putLong(PAUSED_TOTAL, 0L)
                    .putBoolean(NEW_SEGMENT, false)
                    .putLong(REVISION, 1L)
                    .commit();
        }
    }

    public static synchronized void reset(Context context) {
        prefs(context).edit().clear().commit();
    }

    public static boolean isPaused(Context context) {
        return prefs(context).getBoolean(PAUSED, false);
    }

    public static long revision(Context context) {
        return prefs(context).getLong(REVISION, 1L);
    }

    public static synchronized boolean togglePaused(Context context) {
        SharedPreferences p = prefs(context);
        boolean currentlyPaused = p.getBoolean(PAUSED, false);
        long now = System.currentTimeMillis();
        long revision = p.getLong(REVISION, 1L) + 1L;
        SharedPreferences.Editor editor = p.edit().putLong(REVISION, revision);
        if (currentlyPaused) {
            long started = p.getLong(PAUSE_STARTED, 0L);
            long total = p.getLong(PAUSED_TOTAL, 0L);
            if (started > 0L && now > started) total += now - started;
            editor.putBoolean(PAUSED, false)
                    .putLong(PAUSE_STARTED, 0L)
                    .putLong(PAUSED_TOTAL, total)
                    .putBoolean(HAS_LAST, false)
                    .putBoolean(NEW_SEGMENT, true);
            TrackRuntimeState.setState(context, TrackRuntimeState.RUNNING);
        } else {
            editor.putBoolean(PAUSED, true)
                    .putLong(PAUSE_STARTED, now)
                    .putBoolean(HAS_LAST, false);
            TrackRuntimeState.setState(context, TrackRuntimeState.PAUSED);
        }
        editor.commit();
        return !currentlyPaused;
    }

    public static boolean needsNewSegment(Context context) {
        return prefs(context).getBoolean(NEW_SEGMENT, false);
    }

    public static synchronized void markSegmentWritten(Context context) {
        prefs(context).edit().putBoolean(NEW_SEGMENT, false).commit();
    }

    /** Call only after the point was successfully written to the journal. */
    public static synchronized void onCommittedFix(Context context, Location location) {
        if (location == null || isPaused(context)) return;
        beginIfNeeded(context);
        SharedPreferences p = prefs(context);
        float total = p.getFloat(DIST, 0f);
        float max = p.getFloat(MAX, 0f);
        if (p.getBoolean(HAS_LAST, false)) {
            Location prev = new Location("darbak");
            prev.setLatitude(Double.longBitsToDouble(p.getLong(LAST_LAT, Double.doubleToLongBits(location.getLatitude()))));
            prev.setLongitude(Double.longBitsToDouble(p.getLong(LAST_LON, Double.doubleToLongBits(location.getLongitude()))));
            float d = prev.distanceTo(location);
            if (d >= 2f && d < 1000f) total += d;
        }
        if (location.hasSpeed()) max = Math.max(max, location.getSpeed() * 3.6f);
        p.edit()
                .putFloat(DIST, total)
                .putFloat(MAX, max)
                .putLong(LAST_LAT, Double.doubleToLongBits(location.getLatitude()))
                .putLong(LAST_LON, Double.doubleToLongBits(location.getLongitude()))
                .putBoolean(HAS_LAST, true)
                .commit();
    }

    public static String summary(Context context) {
        SharedPreferences p = prefs(context);
        float km = p.getFloat(DIST, 0f) / 1000f;
        long now = System.currentTimeMillis();
        long start = p.getLong(START, now);
        long paused = p.getLong(PAUSED_TOTAL, 0L);
        if (p.getBoolean(PAUSED, false)) {
            long pauseStarted = p.getLong(PAUSE_STARTED, 0L);
            if (pauseStarted > 0L && now > pauseStarted) paused += now - pauseStarted;
        }
        long elapsed = Math.max(1000L, now - start - paused);
        float hours = elapsed / 3600000f;
        float avg = hours > 0f ? km / hours : 0f;
        long totalMinutes = elapsed / 60000L;
        return String.format(Locale.US, "%.1f كم • %02d:%02d • متوسط %.0f • أعلى %.0f كم/س",
                km, totalMinutes / 60L, totalMinutes % 60L, avg, p.getFloat(MAX, 0f));
    }

    public static void updateActionLabel(TextView view, Context context) {
        if (view == null) return;
        if (TrackRuntimeState.isFinalizing(context)) {
            view.setText("جارٍ الحفظ…");
        } else if (!MapUiPreferences.backgroundTrackEnabled(context)) {
            view.setText("تسجيل مسار");
        } else if (isPaused(context)) {
            view.setText("متابعة المسار");
        } else {
            view.setText("إيقاف المسار");
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
