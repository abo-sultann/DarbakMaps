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
        if (!fix.valid) {
            Toast.makeText(c, "بانتظار إشارة GPS لترتيب المواقع حسب الأقرب", Toast.LENGTH_SHORT).show();
            return;
        }
        SqlitePlaceRepository repo = new SqlitePlaceRepository(c);
        final List<Place> places;
        try {
            places = repo.nearest(fix.latitude, fix.longitude, null, 100);
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
            double meters = PlaceMath.distanceMeters(fix.latitude, fix.longitude, p.latitude, p.longitude);
            double bearing = bearing(fix.latitude, fix.longitude, p.latitude, p.longitude);
            rows[i] = category(p.category) + "   " + distance(meters) + "   " + arrow(bearing);
        }
        new AlertDialog.Builder(c)
                .setTitle("المواقع المحفوظة — الأقرب أولاً")
                .setItems(rows, (d, which) -> map.showPlaceActions(places.get(which)))
                .setNegativeButton("إغلاق", null)
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
