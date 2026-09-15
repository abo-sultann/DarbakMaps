package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.NearbyPoiSearch;
import com.abosultan.darbakmaps.core.OfflineMapLocator;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Nearby POI browser backed entirely by the installed Mapsforge file. */
final class NearbyPoiDialog {
    private static final double SEARCH_RADIUS_METERS = 20000d;
    private static final int RESULT_LIMIT = 60;

    static void show(Context c, OfflineMapView map) {
        LocationSnapshot fix = LiveLocationStore.latest();
        if (!fix.valid) {
            Toast.makeText(c, "بانتظار إشارة GPS لعرض القريب", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] labels = {
                NearbyPoiSearch.ALL,
                NearbyPoiSearch.WADIS,
                NearbyPoiSearch.MOUNTAINS,
                NearbyPoiSearch.LANDMARKS,
                NearbyPoiSearch.VILLAGES,
                NearbyPoiSearch.WATER,
                NearbyPoiSearch.SERVICES
        };
        new AlertDialog.Builder(c)
                .setTitle("القريب مني — حتى 20 كم")
                .setItems(labels, (d, which) -> runSearch(c, map, fix, labels[which]))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static void runSearch(Context c, OfflineMapView map, LocationSnapshot fix, String category) {
        List<File> maps = OfflineMapLocator.find(c);
        if (maps.isEmpty()) {
            Toast.makeText(c, "لم يتم العثور على ملف الخريطة", Toast.LENGTH_SHORT).show();
            return;
        }
        File activeMap = maps.get(0);
        AlertDialog progress = new AlertDialog.Builder(c)
                .setTitle("القريب مني")
                .setMessage("جارٍ قراءة المعالم من الخريطة الأوفلاين…")
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            List<NearbyPoiSearch.Result> results = NearbyPoiSearch.search(
                    activeMap, fix.latitude, fix.longitude,
                    SEARCH_RADIUS_METERS, category, RESULT_LIMIT);
            map.post(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (!map.isAttachedToWindow()) return;
                showResults(c, map, fix, category, results);
            });
        }, "darbak-nearby-poi").start();
    }

    private static void showResults(Context c, OfflineMapView map, LocationSnapshot fix,
                                    String category, List<NearbyPoiSearch.Result> results) {
        if (results == null || results.isEmpty()) {
            Toast.makeText(c, "لا توجد نتائج قريبة ضمن هذا التصنيف", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] rows = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            NearbyPoiSearch.Result r = results.get(i);
            double bearing = bearing(fix.latitude, fix.longitude, r.latitude, r.longitude);
            rows[i] = r.name + " • " + r.source + " • " + distance(r.distanceMeters) + " " + arrow(bearing);
        }
        new AlertDialog.Builder(c)
                .setTitle("القريب — " + category)
                .setItems(rows, (d, which) -> {
                    NearbyPoiSearch.Result result = results.get(which);
                    map.focusPlace(result.latitude, result.longitude);
                    Toast.makeText(c, result.name + " — " + distance(result.distanceMeters), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private static String distance(double meters) {
        return meters < 1000d ? Math.round(meters) + " م"
                : String.format(Locale.US, "%.1f كم", meters / 1000d);
    }

    private static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double y = Math.sin(Math.toRadians(lon2 - lon1)) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lon2 - lon1));
        return (Math.toDegrees(Math.atan2(y, x)) + 360d) % 360d;
    }

    private static String arrow(double b) {
        if (b < 22.5 || b >= 337.5) return "↑";
        if (b < 67.5) return "↗";
        if (b < 112.5) return "→";
        if (b < 157.5) return "↘";
        if (b < 202.5) return "↓";
        if (b < 247.5) return "↙";
        if (b < 292.5) return "←";
        return "↖";
    }

    private NearbyPoiDialog() {}
}
