package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.CoreContracts.Place;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.PlaceMath;
import com.abosultan.darbakmaps.core.SqlitePlaceRepository;

import java.util.List;

/** Lightweight nearest-first saved-place browser for the 1024x600 head unit. */
final class SavedPlacesDialog {
    static void show(Context c, OfflineMapView map) {
        LocationSnapshot fix = LiveLocationStore.latest();
        SqlitePlaceRepository repo = new SqlitePlaceRepository(c);
        final List<Place> places;
        try {
            places = fix.valid
                    ? repo.nearest(fix.latitude, fix.longitude, null, 100)
                    : repo.all(100);
        } finally {
            repo.close();
        }
        if (places.isEmpty()) {
            Toast.makeText(c, "لا توجد مواقع محفوظة", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] rows = new String[places.size()];
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            if (fix.valid) {
                double meters = PlaceMath.distanceMeters(fix.latitude, fix.longitude, p.latitude, p.longitude);
                double bearing = bearing(fix.latitude, fix.longitude, p.latitude, p.longitude);
                rows[i] = category(p.category) + "   " + distance(meters) + "   " + arrow(bearing);
            } else {
                rows[i] = category(p.category) + "   —   GPS غير متاح";
            }
        }

        String title = fix.valid ? "المواقع المحفوظة — الأقرب أولاً" : "المواقع المحفوظة";
        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(title)
                .setItems(rows, (d, which) -> map.showPlaceActions(places.get(which)))
                .setNegativeButton("إغلاق", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            if (dialog.getListView() == null) return;
            dialog.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                confirmDelete(c, map, dialog, places.get(position));
                return true;
            });
        });
        dialog.show();
    }

    private static void confirmDelete(Context c, OfflineMapView map, AlertDialog parent, Place place) {
        new AlertDialog.Builder(c)
                .setTitle("حذف الموقع؟")
                .setMessage(category(place.category) + "\nلن يمكن التراجع عن الحذف.")
                .setPositiveButton("حذف", (d, w) -> {
                    boolean deleted;
                    SqlitePlaceRepository repo = new SqlitePlaceRepository(c);
                    try {
                        deleted = repo.delete(place.id);
                    } finally {
                        repo.close();
                    }
                    if (!deleted) {
                        Toast.makeText(c, "تعذر حذف الموقع", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    map.removeSavedPlaceMarker(place.id);
                    parent.dismiss();
                    Toast.makeText(c, "تم حذف الموقع", Toast.LENGTH_SHORT).show();
                    show(c, map);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static String category(String c) {
        if ("summan".equals(c)) return "طير سمان";
        if ("water".equals(c)) return "ماء";
        if ("camp".equals(c)) return "مخيم";
        if ("fuel".equals(c)) return "وقود";
        return "موقع";
    }

    private static String distance(double m) {
        return m < 1000 ? Math.round(m) + " م" : String.format(java.util.Locale.US, "%.1f كم", m / 1000d);
    }

    private static double bearing(double a, double o, double b, double p) {
        double y = Math.sin(Math.toRadians(p - o)) * Math.cos(Math.toRadians(b));
        double x = Math.cos(Math.toRadians(a)) * Math.sin(Math.toRadians(b))
                - Math.sin(Math.toRadians(a)) * Math.cos(Math.toRadians(b)) * Math.cos(Math.toRadians(p - o));
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

    private SavedPlacesDialog() {}
}
