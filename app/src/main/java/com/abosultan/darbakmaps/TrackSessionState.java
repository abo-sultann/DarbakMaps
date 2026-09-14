package com.abosultan.darbakmaps;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.widget.TextView;

import java.util.Locale;

/** Persistent statistics for the active background track, excluding paused time. */
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

    private TrackSessionState() {}

    public static void beginIfNeeded(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getLong(START, 0L) == 0L) {
            p.edit()
                    .putLong(START, System.currentTimeMillis())
                    .putBoolean(PAUSED, false)
                    .putLong(PAUSE_STARTED, 0L)
                    .putLong(PAUSED_TOTAL, 0L)
                    .apply();
        }
    }

    public static void reset(Context context) { prefs(context).edit().clear().apply(); }

    public static boolean isPaused(Context context) { return prefs(context).getBoolean(PAUSED, false); }

    public static boolean togglePaused(Context context) {
        beginIfNeeded(context);
        SharedPreferences p = prefs(context);
        long now = System.currentTimeMillis();
        boolean wasPaused = p.getBoolean(PAUSED, false);
        SharedPreferences.Editor e = p.edit();
        if (!wasPaused) {
            e.putBoolean(PAUSED, true).putLong(PAUSE_STARTED, now);
        } else {
            long pauseStart = p.getLong(PAUSE_STARTED, now);
            long pausedTotal = p.getLong(PAUSED_TOTAL, 0L) + Math.max(0L, now - pauseStart);
            e.putBoolean(PAUSED, false)
                    .putLong(PAUSE_STARTED, 0L)
                    .putLong(PAUSED_TOTAL, pausedTotal)
                    .putBoolean(HAS_LAST, false);
        }
        e.apply();
        return !wasPaused;
    }

    public static void onFix(Context context, Location location) {
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
                .apply();
    }

    public static String summary(Context context) {
        SharedPreferences p = prefs(context);
        float km = p.getFloat(DIST, 0f) / 1000f;
        long now = System.currentTimeMillis();
        long start = p.getLong(START, now);
        long paused = p.getLong(PAUSED_TOTAL, 0L);
        if (p.getBoolean(PAUSED, false)) paused += Math.max(0L, now - p.getLong(PAUSE_STARTED, now));
        long elapsed = Math.max(1000L, now - start - paused);
        float hours = elapsed / 3600000f;
        float avg = hours > 0f ? km / hours : 0f;
        long totalMinutes = elapsed / 60000L;
        return String.format(Locale.US, "%.1f كم • %02d:%02d • متوسط %.0f • أعلى %.0f كم/س",
                km, totalMinutes / 60L, totalMinutes % 60L, avg, p.getFloat(MAX, 0f));
    }

    public static void updateActionLabel(TextView view, Context context) {
        if (view == null) return;
        if (!MapUiPreferences.backgroundTrackEnabled(context)) view.setText("تسجيل مسار");
        else if (isPaused(context)) view.setText("متابعة المسار");
        else view.setText("إيقاف المسار");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
