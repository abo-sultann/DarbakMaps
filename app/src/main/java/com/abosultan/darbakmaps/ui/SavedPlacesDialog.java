package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.CoreContracts.Place;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.PlaceMath;
import com.abosultan.darbakmaps.core.SqlitePlaceRepository;

import java.util.List;

/** Lightweight nearest-first saved-place browser for the 1024x600 head unit. */
final class SavedPlacesDialog {
    private static final float MIN_HEADING_SPEED_KMH = 4f;

    static void show(Context c, OfflineMapView map) {
        LocationSnapshot fix = LiveLocationStore.latest();
        SqlitePlaceRepository repo = new SqlitePlaceRepository(c);
        final List<Place> places;
        try { places = fix.valid ? repo.nearest(fix.latitude, fix.longitude, null, 100) : repo.all(100); }
        finally { repo.close(); }
        if (places.isEmpty()) { Toast.makeText(c, "لا توجد مواقع محفوظة", Toast.LENGTH_SHORT).show(); return; }

        boolean headingReliable = fix.valid && fix.speedKmh >= MIN_HEADING_SPEED_KMH && validBearing(fix.bearing);
        String title = fix.valid ? (headingReliable ? "المواقع المحفوظة — السهم حسب اتجاه السيارة" : "المواقع المحفوظة — الأقرب أولاً") : "المواقع المحفوظة";
        LinearLayout box = DarbakDialog.panel(c, title);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(false);
        scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = DarbakDialog.row(c, 0); sp.height = DarbakUi.dp(c, 320); box.addView(scroll, sp);

        final AlertDialog[] holder = new AlertDialog[1];
        for (Place place : places) {
            String row;
            if (fix.valid) {
                double meters = PlaceMath.distanceMeters(fix.latitude, fix.longitude, place.latitude, place.longitude);
                double targetBearing = bearing(fix.latitude, fix.longitude, place.latitude, place.longitude);
                String direction = headingReliable ? arrow(relativeBearing(targetBearing, fix.bearing)) : cardinal(targetBearing);
                row = category(place.category) + "   " + distance(meters) + "   " + direction;
            } else row = category(place.category) + "   —   GPS غير متاح";
            TextView action = DarbakUi.action(c, row);
            action.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); map.showPlaceActions(place); });
            action.setOnLongClickListener(v -> { confirmDelete(c, map, holder[0], place); return true; });
            LinearLayout.LayoutParams p = DarbakDialog.row(c, 8); p.height = DarbakUi.dp(c, 50); list.addView(action, p);
        }
        TextView close = DarbakUi.action(c, "إغلاق");
        close.setTextColor(DarbakUi.TEXT_SECONDARY);
        close.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); });
        LinearLayout.LayoutParams cp = DarbakDialog.row(c, 8); cp.height = DarbakUi.dp(c, 46); box.addView(close, cp);
        holder[0] = DarbakDialog.show(c, box);
    }

    private static void confirmDelete(Context c, OfflineMapView map, AlertDialog parent, Place place) {
        DarbakDialog.infoActions(c, "حذف الموقع؟", category(place.category) + "\nلن يمكن التراجع عن الحذف.", new String[]{"حذف الموقع"}, which -> {
            boolean deleted;
            SqlitePlaceRepository repo = new SqlitePlaceRepository(c);
            try { deleted = repo.delete(place.id); } finally { repo.close(); }
            if (!deleted) { Toast.makeText(c, "تعذر حذف الموقع", Toast.LENGTH_SHORT).show(); return; }
            map.removeSavedPlaceMarker(place.id);
            if (parent != null) parent.dismiss();
            Toast.makeText(c, "تم حذف الموقع", Toast.LENGTH_SHORT).show();
            show(c, map);
        });
    }

    private static String category(String c) {
        if ("summan".equals(c)) return "طير سمان"; if ("water".equals(c)) return "ماء"; if ("camp".equals(c)) return "مخيم"; if ("fuel".equals(c)) return "وقود"; return "موقع";
    }
    private static String distance(double m) { return m < 1000 ? Math.round(m) + " م" : String.format(java.util.Locale.US, "%.1f كم", m / 1000d); }
    private static double bearing(double a, double o, double b, double p) {
        double y = Math.sin(Math.toRadians(p - o)) * Math.cos(Math.toRadians(b));
        double x = Math.cos(Math.toRadians(a)) * Math.sin(Math.toRadians(b)) - Math.sin(Math.toRadians(a)) * Math.cos(Math.toRadians(b)) * Math.cos(Math.toRadians(p - o));
        return (Math.toDegrees(Math.atan2(y, x)) + 360d) % 360d;
    }
    private static boolean validBearing(float bearing) { return !Float.isNaN(bearing) && bearing >= 0f && bearing < 360f; }
    private static double relativeBearing(double targetBearing, float vehicleBearing) { return (targetBearing - vehicleBearing + 360d) % 360d; }
    private static String cardinal(double b) {
        if (b < 22.5 || b >= 337.5) return "شمال"; if (b < 67.5) return "شمال شرق"; if (b < 112.5) return "شرق"; if (b < 157.5) return "جنوب شرق"; if (b < 202.5) return "جنوب"; if (b < 247.5) return "جنوب غرب"; if (b < 292.5) return "غرب"; return "شمال غرب";
    }
    private static String arrow(double b) {
        if (b < 22.5 || b >= 337.5) return "↑"; if (b < 67.5) return "↗"; if (b < 112.5) return "→"; if (b < 157.5) return "↘"; if (b < 202.5) return "↓"; if (b < 247.5) return "↙"; if (b < 292.5) return "←"; return "↖";
    }
    private SavedPlacesDialog() {}
}
