package com.abosultan.darbakmaps;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.GeoPoint;
import com.abosultan.darbakmaps.data.TrackNavigator;
import com.abosultan.darbakmaps.data.TrackStorage;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** Reverse breadcrumb guidance that respects turns and recording gaps. */
public final class BacktrackGuidance {
    private static final String PREFS = "darbak_backtrack";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_FILE = "file";
    private static List<GeoPoint> cached = Collections.emptyList();
    private static TrackNavigator navigator;
    private static long lastAlert;
    private static long lastDistanceCheck;
    private static double nearestMeters;

    private BacktrackGuidance() {}

    public static void start(Activity activity, File file) {
        try {
            start(activity, file, TrackStorage.readForNavigation(file));
        } catch (Exception error) {
            Toast.makeText(activity, "تعذر قراءة مسار الرجوع", Toast.LENGTH_LONG).show();
        }
    }

    public static void start(Activity activity, File file, List<GeoPoint> points) {
        if (file == null || points == null || points.size() < 2) return;
        cached = points;
        navigator = new TrackNavigator(points);
        prefs(activity).edit().putBoolean(KEY_ACTIVE, true).putString(KEY_FILE, file.getAbsolutePath()).apply();
        MapRuntimeBridge.showStoredTrack(points);
        MapRuntimeBridge.resumeFollow();
        Toast.makeText(activity, "الرجوع باتباع نقاط المسار بالتتابع", Toast.LENGTH_SHORT).show();
    }

    public static void restore(Activity activity) {
        if (!isActive(activity)) return;
        if (!cached.isEmpty()) {
            if (navigator == null) navigator = new TrackNavigator(cached);
            MapRuntimeBridge.showStoredTrack(cached);
            MapRuntimeBridge.resumeFollow();
            return;
        }
        final String path = prefs(activity).getString(KEY_FILE, "");
        if (path == null || path.isEmpty()) {
            stop(activity);
            return;
        }
        new Thread(() -> {
            try {
                List<GeoPoint> points = TrackStorage.load(new File(path));
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !isActive(activity)
                            || !path.equals(prefs(activity).getString(KEY_FILE, ""))) return;
                    cached = points;
                    navigator = new TrackNavigator(points);
                    MapRuntimeBridge.showStoredTrack(points);
                    MapRuntimeBridge.resumeFollow();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    stop(activity);
                    NavigationGuidance.stop(activity);
                    Toast.makeText(activity, "تعذر استعادة مسار الرجوع", Toast.LENGTH_LONG).show();
                });
            }
        }, "darbak-backtrack-restore").start();
    }

    public static void stop(Context context) {
        prefs(context).edit().clear().apply();
        cached = Collections.emptyList();
        navigator = null;
        lastAlert = 0L;
        lastDistanceCheck = 0L;
        nearestMeters = 0d;
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static void update(Activity activity, Location location) {
        if (!isActive(activity) || location == null) return;
        if (navigator == null) {
            restore(activity);
            return;
        }
        GeoPoint target = navigator.update(location.getLatitude(), location.getLongitude());
        if (target == null) {
            if (navigator.stoppedAtGap()) {
                stop(activity);
                NavigationGuidance.stop(activity);
                View panel = activity.findViewById(R.id.nav_panel);
                if (panel != null) panel.setVisibility(View.GONE);
                Toast.makeText(activity,
                        "وصلت إلى بداية هذا المقطع. يوجد فاصل تسجيل؛ لم يتم توجيهك عبره.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        NavigationGuidance.guideTo(activity, location,
                "رجوع على المسار • نقطة " + (navigator.targetIndex() + 1),
                target.latitude, target.longitude, false);

        long now = System.currentTimeMillis();
        if (now - lastDistanceCheck >= 5000L) {
            nearestMeters = navigator.offTrack(location.getLatitude(), location.getLongitude());
            lastDistanceCheck = now;
        }
        TextView detail = activity.findViewById(R.id.nav_detail);
        if (detail != null && nearestMeters > 120d) {
            detail.append(" • خارج المسار " + Math.round(nearestMeters) + "م");
        }
        if (MapUiPreferences.offRouteAlert(activity)
                && nearestMeters > 180d && now - lastAlert > 30000L) {
            lastAlert = now;
            Toast.makeText(activity,
                    "ابتعدت عن المسار المسجل " + Math.round(nearestMeters) + " م",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
