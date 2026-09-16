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

    static void show(Context c, OfflineMapView map) { show(c, map, null); }

    private static void show(Context c, OfflineMapView map, String filter) {
        LocationSnapshot fix = LiveLocationStore.latest();
        SqlitePlaceRepository repo = new SqlitePlaceRepository(c);
        final List<Place> places;
        try { places = fix.valid ? repo.nearest(fix.latitude, fix.longitude, filter, 100) : filtered(repo.all(100), filter); }
        finally { repo.close(); }
        if (places.isEmpty()) { Toast.makeText(c, filter == null ? "لا توجد مواقع محفوظة" : "لا توجد مواقع من هذا النوع", Toast.LENGTH_SHORT).show(); if (filter != null) show(c, map, null); return; }

        boolean headingReliable = fix.valid && fix.speedKmh >= MIN_HEADING_SPEED_KMH && validBearing(fix.bearing);
        String title = filter == null ? "المواقع المحفوظة" : "المواقع — " + category(filter);
        if (fix.valid) title += headingReliable ? " — السهم حسب السيارة" : " — الأقرب أولاً";
        LinearLayout box = DarbakDialog.panel(c, title);
        LinearLayout filters = new LinearLayout(c); filters.setOrientation(LinearLayout.HORIZONTAL);
        final AlertDialog[] holder = new AlertDialog[1];
        addFilter(c, filters, "الكل", null, filter, map, holder); addFilter(c, filters, "سمان", "summan", filter, map, holder); addFilter(c, filters, "ماء", "water", filter, map, holder); addFilter(c, filters, "مخيم", "camp", filter, map, holder); addFilter(c, filters, "وقود", "fuel", filter, map, holder);
        box.addView(filters, DarbakDialog.row(c, 4));
        LinearLayout list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(c); scroll.setFillViewport(false); scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = DarbakDialog.row(c, 4); sp.height = DarbakUi.dp(c, 270); box.addView(scroll, sp);

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
            action.setOnLongClickListener(v -> { confirmDelete(c, map, holder[0], place, filter); return true; });
            LinearLayout.LayoutParams p = DarbakDialog.row(c, 8); p.height = DarbakUi.dp(c, 50); list.addView(action, p);
        }
        TextView close = DarbakUi.action(c, "إغلاق"); close.setTextColor(DarbakUi.TEXT_SECONDARY); close.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); });
        LinearLayout.LayoutParams cp = DarbakDialog.row(c, 8); cp.height = DarbakUi.dp(c, 46); box.addView(close, cp); holder[0] = DarbakDialog.show(c, box);
    }

    private static void addFilter(Context c, LinearLayout row, String label, String value, String selected, OfflineMapView map, AlertDialog[] holder) {
        TextView v = DarbakUi.action(c, label); if ((selected == null && value == null) || (selected != null && selected.equals(value))) v.setTextColor(DarbakUi.ACCENT);
        v.setOnClickListener(x -> { if (holder[0] != null) holder[0].dismiss(); show(c, map, value); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, DarbakUi.dp(c, 42), 1f); if (row.getChildCount() > 0) p.rightMargin = DarbakUi.dp(c, 5); row.addView(v, p);
    }
    private static List<Place> filtered(List<Place> all, String filter) { if (filter == null) return all; java.util.ArrayList<Place> out = new java.util.ArrayList<>(); for (Place p : all) if (filter.equals(p.category)) out.add(p); return out; }

    private static void confirmDelete(Context c, OfflineMapView map, AlertDialog parent, Place place, String filter) {
        DarbakDialog.infoActions(c, "حذف الموقع؟", category(place.category) + "\nلن يمكن التراجع عن الحذف.", new String[]{"حذف الموقع"}, which -> {
            boolean deleted; SqlitePlaceRepository repo = new SqlitePlaceRepository(c); try { deleted = repo.delete(place.id); } finally { repo.close(); }
            if (!deleted) { Toast.makeText(c, "تعذر حذف الموقع", Toast.LENGTH_SHORT).show(); return; }
            map.removeSavedPlaceMarker(place.id); if (parent != null) parent.dismiss(); Toast.makeText(c, "تم حذف الموقع", Toast.LENGTH_SHORT).show(); show(c, map, filter);
        });
    }

    private static String category(String c) { if ("summan".equals(c)) return "طير سمان"; if ("water".equals(c)) return "ماء"; if ("camp".equals(c)) return "مخيم"; if ("fuel".equals(c)) return "وقود"; return "موقع"; }
    private static String distance(double m) { return m < 1000 ? Math.round(m) + " م" : String.format(java.util.Locale.US, "%.1f كم", m / 1000d); }
    private static double bearing(double a, double o, double b, double p) { double y = Math.sin(Math.toRadians(p - o)) * Math.cos(Math.toRadians(b)); double x = Math.cos(Math.toRadians(a)) * Math.sin(Math.toRadians(b)) - Math.sin(Math.toRadians(a)) * Math.cos(Math.toRadians(b)) * Math.cos(Math.toRadians(p - o)); return (Math.toDegrees(Math.atan2(y, x)) + 360d) % 360d; }
    private static boolean validBearing(float bearing) { return !Float.isNaN(bearing) && bearing >= 0f && bearing < 360f; }
    private static double relativeBearing(double targetBearing, float vehicleBearing) { return (targetBearing - vehicleBearing + 360d) % 360d; }
    private static String cardinal(double b) { if (b < 22.5 || b >= 337.5) return "شمال"; if (b < 67.5) return "شمال شرق"; if (b < 112.5) return "شرق"; if (b < 157.5) return "جنوب شرق"; if (b < 202.5) return "جنوب"; if (b < 247.5) return "جنوب غرب"; if (b < 292.5) return "غرب"; return "شمال غرب"; }
    private static String arrow(double b) { if (b < 22.5 || b >= 337.5) return "↑"; if (b < 67.5) return "↗"; if (b < 112.5) return "→"; if (b < 157.5) return "↘"; if (b < 202.5) return "↓"; if (b < 247.5) return "↙"; if (b < 292.5) return "←"; return "↖"; }
    private SavedPlacesDialog() {}
}
