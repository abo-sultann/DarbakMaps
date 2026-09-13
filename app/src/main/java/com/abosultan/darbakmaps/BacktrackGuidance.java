package com.abosultan.darbakmaps;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.GeoPoint;
import com.abosultan.darbakmaps.data.TrackStorage;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** Breadcrumb return helper with lightweight off-track detection. */
public final class BacktrackGuidance {
    private static final String PREFS = "darbak_backtrack";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_FILE = "file";
    private static List<GeoPoint> cached = Collections.emptyList();
    private static long lastAlert;

    private BacktrackGuidance() {}

    public static void start(Activity activity, File file, List<GeoPoint> points) {
        if (file == null || points == null || points.size() < 2) return;
        cached = points;
        prefs(activity).edit().putBoolean(KEY_ACTIVE, true).putString(KEY_FILE, file.getAbsolutePath()).apply();
        GeoPoint first = points.get(0);
        MapRuntimeBridge.showStoredTrack(points);
        NavigationGuidance.start(activity, "بداية المسار", first.latitude, first.longitude);
    }

    public static void restore(Activity activity) {
        if (!isActive(activity)) return;
        ensureLoaded(activity);
        if (cached.size() >= 2) MapRuntimeBridge.showStoredTrack(cached);
    }

    public static void stop(Context context) {
        prefs(context).edit().clear().apply();
        cached = Collections.emptyList();
        lastAlert = 0L;
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static void update(Activity activity, Location location) {
        if (!isActive(activity) || location == null || !MapUiPreferences.offRouteAlert(activity)) return;
        ensureLoaded(activity);
        if (cached.size() < 2) return;
        float nearest = nearestMeters(location, cached);
        TextView detail = activity.findViewById(R.id.nav_detail);
        if (nearest > 120f && detail != null) {
            detail.setText(detail.getText() + " • خارج المسار " + Math.round(nearest) + "م");
        }
        long now = System.currentTimeMillis();
        if (nearest > 180f && now - lastAlert > 30000L) {
            lastAlert = now;
            Toast.makeText(activity, "ابتعدت عن المسار المسجل " + Math.round(nearest) + " م", Toast.LENGTH_SHORT).show();
        }
    }

    private static void ensureLoaded(Context context) {
        if (!cached.isEmpty()) return;
        String path = prefs(context).getString(KEY_FILE, "");
        if (path == null || path.isEmpty()) return;
        try { cached = TrackStorage.load(new File(path)); }
        catch (Exception ignored) { cached = Collections.emptyList(); }
    }

    private static float nearestMeters(Location current, List<GeoPoint> points) {
        int step = Math.max(1, points.size() / 1200);
        float best = Float.MAX_VALUE;
        float[] out = new float[1];
        for (int i = 0; i < points.size(); i += step) {
            GeoPoint p = points.get(i);
            Location.distanceBetween(current.getLatitude(), current.getLongitude(), p.latitude, p.longitude, out);
            if (out[0] < best) best = out[0];
        }
        GeoPoint last = points.get(points.size()-1);
        Location.distanceBetween(current.getLatitude(), current.getLongitude(), last.latitude, last.longitude, out);
        return Math.min(best, out[0]);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
