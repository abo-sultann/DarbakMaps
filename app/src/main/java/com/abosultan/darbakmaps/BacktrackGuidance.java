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

/** Breadcrumb return guidance that follows the recorded track back instead of pointing straight home. */
public final class BacktrackGuidance {
    private static final String PREFS = "darbak_backtrack";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_FILE = "file";
    private static final String KEY_TARGET_INDEX = "target_index";
    private static final float LOOK_BACK_METERS = 90f;
    private static List<GeoPoint> cached = Collections.emptyList();
    private static long lastAlert;
    private static int lastTargetIndex = -1;

    private BacktrackGuidance() {}

    public static void start(Activity activity, File file, List<GeoPoint> points) {
        if (file == null || points == null || points.size() < 2) return;
        cached = points;
        lastTargetIndex = points.size() - 1;
        prefs(activity).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_FILE, file.getAbsolutePath())
                .putInt(KEY_TARGET_INDEX, lastTargetIndex)
                .apply();
        MapRuntimeBridge.showStoredTrack(points);
        GeoPoint last = points.get(lastTargetIndex);
        NavigationGuidance.start(activity, "الرجوع على المسار", last.latitude, last.longitude);
    }

    public static void restore(Activity activity) {
        if (!isActive(activity)) return;
        ensureLoaded(activity);
        if (cached.size() < 2) return;
        MapRuntimeBridge.showStoredTrack(cached);
        int saved = prefs(activity).getInt(KEY_TARGET_INDEX, cached.size() - 1);
        lastTargetIndex = Math.max(0, Math.min(saved, cached.size() - 1));
        GeoPoint target = cached.get(lastTargetIndex);
        NavigationGuidance.start(activity, "الرجوع على المسار", target.latitude, target.longitude);
    }

    public static void stop(Context context) {
        prefs(context).edit().clear().apply();
        cached = Collections.emptyList();
        lastAlert = 0L;
        lastTargetIndex = -1;
    }

    public static boolean isActive(Context context) { return prefs(context).getBoolean(KEY_ACTIVE, false); }

    public static void update(Activity activity, Location location) {
        if (!isActive(activity) || location == null) return;
        ensureLoaded(activity);
        if (cached.size() < 2) return;

        Nearest nearest = nearest(location, cached);
        int targetIndex = targetBehind(nearest.index, cached, LOOK_BACK_METERS);
        if (targetIndex != lastTargetIndex) {
            lastTargetIndex = targetIndex;
            prefs(activity).edit().putInt(KEY_TARGET_INDEX, targetIndex).apply();
            GeoPoint target = cached.get(targetIndex);
            NavigationGuidance.start(activity,
                    targetIndex == 0 ? "بداية المسار" : "الرجوع على المسار",
                    target.latitude, target.longitude);
        }

        if (MapUiPreferences.offRouteAlert(activity)) {
            TextView detail = activity.findViewById(R.id.nav_detail);
            if (nearest.meters > 120f && detail != null) {
                String base = detail.getText().toString().replaceAll(" • خارج المسار [0-9]+م", "");
                detail.setText(base + " • خارج المسار " + Math.round(nearest.meters) + "م");
            }
            long now = System.currentTimeMillis();
            if (nearest.meters > 180f && now - lastAlert > 30000L) {
                lastAlert = now;
                Toast.makeText(activity, "ابتعدت عن المسار المسجل " + Math.round(nearest.meters) + " م", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static void ensureLoaded(Context context) {
        if (!cached.isEmpty()) return;
        String path = prefs(context).getString(KEY_FILE, "");
        if (path == null || path.isEmpty()) return;
        try { cached = TrackStorage.load(new File(path)); }
        catch (Exception ignored) { cached = Collections.emptyList(); }
    }

    private static Nearest nearest(Location current, List<GeoPoint> points) {
        int step = Math.max(1, points.size() / 1200);
        int bestIndex = 0;
        float best = Float.MAX_VALUE;
        float[] out = new float[1];
        for (int i = 0; i < points.size(); i += step) {
            GeoPoint p = points.get(i);
            Location.distanceBetween(current.getLatitude(), current.getLongitude(), p.latitude, p.longitude, out);
            if (out[0] < best) { best = out[0]; bestIndex = i; }
        }
        int from = Math.max(0, bestIndex - step);
        int to = Math.min(points.size() - 1, bestIndex + step);
        for (int i = from; i <= to; i++) {
            GeoPoint p = points.get(i);
            Location.distanceBetween(current.getLatitude(), current.getLongitude(), p.latitude, p.longitude, out);
            if (out[0] < best) { best = out[0]; bestIndex = i; }
        }
        return new Nearest(bestIndex, best);
    }

    private static int targetBehind(int nearestIndex, List<GeoPoint> points, float meters) {
        if (nearestIndex <= 0) return 0;
        float accumulated = 0f;
        float[] out = new float[1];
        int index = nearestIndex;
        while (index > 0 && accumulated < meters) {
            GeoPoint a = points.get(index);
            GeoPoint b = points.get(index - 1);
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out);
            accumulated += out[0];
            index--;
        }
        return index;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static final class Nearest {
        final int index;
        final float meters;
        Nearest(int index, float meters) { this.index = index; this.meters = meters; }
    }
}
