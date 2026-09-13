from pathlib import Path


def read(p):
    return Path(p).read_text(encoding='utf-8')

def write(p, s):
    Path(p).write_text(s, encoding='utf-8')

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing marker: {label}')
    return text.replace(old, new, 1)

base = Path('.')

write(base/'app/src/main/res/values/ids.xml', '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <item name="tools_overlay" type="id" />
    <item name="orientation_mode" type="id" />
    <item name="speed_panel" type="id" />
    <item name="nav_panel" type="id" />
    <item name="nav_arrow" type="id" />
    <item name="nav_name" type="id" />
    <item name="nav_distance" type="id" />
    <item name="nav_detail" type="id" />
    <item name="nav_stop" type="id" />
</resources>
''')

p = base/'app/build.gradle'
s = read(p)
s = s.replace("versionCode 14", "versionCode 15")
s = s.replace("versionName '0.6.1'", "versionName '0.7.0'")
write(p, s)

p = base/'app/src/main/java/com/abosultan/darbakmaps/MapUiPreferences.java'
s = read(p)
s = rep(s, '    private static final String KEY_BACKGROUND_TRACK = "background_track";\n',
'''    private static final String KEY_BACKGROUND_TRACK = "background_track";
    private static final String KEY_FOLLOW_VEHICLE = "follow_vehicle";
    private static final String KEY_SHOW_TRACK_STATS = "show_track_stats";
    private static final String KEY_OFF_ROUTE_ALERT = "off_route_alert";
''', 'prefs keys')
s = rep(s, '    /** Compatibility with older builds. Tools are now controlled only by tapping the map. */\n',
'''    public static boolean followVehicle(Context context) {
        return prefs(context).getBoolean(KEY_FOLLOW_VEHICLE, true);
    }

    public static void setFollowVehicle(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_FOLLOW_VEHICLE, value).apply();
    }

    public static boolean showTrackStats(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_TRACK_STATS, true);
    }

    public static void setShowTrackStats(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_SHOW_TRACK_STATS, value).apply();
    }

    public static boolean offRouteAlert(Context context) {
        return prefs(context).getBoolean(KEY_OFF_ROUTE_ALERT, true);
    }

    public static void setOffRouteAlert(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_OFF_ROUTE_ALERT, value).apply();
    }

    /** Compatibility with older builds. Tools are now controlled only by tapping the map. */
''', 'prefs methods')
write(p, s)

write(base/'app/src/main/java/com/abosultan/darbakmaps/NavigationGuidance.java', r'''package com.abosultan.darbakmaps;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Lightweight offline guidance HUD for saved places and direct desert navigation. */
public final class NavigationGuidance {
    private static final String PREFS = "darbak_guidance";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_NAME = "name";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static boolean arrivalShown;

    private NavigationGuidance() {}

    public static void start(Activity activity, String name, double lat, double lon) {
        prefs(activity).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_NAME, name == null ? "موقع محفوظ" : name)
                .putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon))
                .apply();
        arrivalShown = false;
        MapRuntimeBridge.navigateTo(activity, lat, lon);
        restore(activity);
    }

    public static void restore(Activity activity) {
        if (!isActive(activity)) {
            hide(activity);
            return;
        }
        View panel = activity.findViewById(R.id.nav_panel);
        if (panel != null) panel.setVisibility(View.VISIBLE);
        TextView name = activity.findViewById(R.id.nav_name);
        if (name != null) name.setText(targetName(activity));
        MapRuntimeBridge.navigateTo(activity, targetLat(activity), targetLon(activity));
    }

    public static void stop(Activity activity) {
        prefs(activity).edit().clear().apply();
        arrivalShown = false;
        MapRuntimeBridge.clearNavigation();
        hide(activity);
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static void update(Activity activity, Location location) {
        if (!isActive(activity) || location == null) return;
        double lat = targetLat(activity);
        double lon = targetLon(activity);
        float[] result = new float[3];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), lat, lon, result);
        float meters = Math.max(0f, result[0]);
        float targetBearing = normalize(result[1]);
        float vehicleBearing = location.hasBearing() ? normalize(location.getBearing()) : 0f;
        float relative = normalizeSigned(targetBearing - vehicleBearing);

        View panel = activity.findViewById(R.id.nav_panel);
        TextView arrow = activity.findViewById(R.id.nav_arrow);
        TextView name = activity.findViewById(R.id.nav_name);
        TextView distance = activity.findViewById(R.id.nav_distance);
        TextView detail = activity.findViewById(R.id.nav_detail);
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (arrow != null) arrow.setRotation(relative);
        if (name != null) name.setText(targetName(activity));
        if (distance != null) distance.setText(formatDistance(meters));

        String eta = "";
        if (location.hasSpeed() && location.getSpeed() > 2f) {
            long minutes = Math.max(1L, Math.round((meters / location.getSpeed()) / 60f));
            eta = " • تقريبًا " + minutes + " د";
        }
        String mode = MapRuntimeBridge.routingLabel(MapUiPreferences.routingMode(activity));
        String warning = MapUiPreferences.offRouteAlert(activity) && Math.abs(relative) > 70f ? " • عدّل اتجاهك" : "";
        if (detail != null) {
            detail.setText(String.format(Locale.US, "%s • اتجاه %03d°%s%s",
                    mode, Math.round(targetBearing), eta, warning));
        }

        if (meters <= 40f && !arrivalShown) {
            arrivalShown = true;
            Toast.makeText(activity, "وصلت إلى " + targetName(activity), Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatDistance(float meters) {
        if (meters >= 1000f) return String.format(Locale.US, "باقي %.1f كم", meters / 1000f);
        return "باقي " + Math.round(meters) + " م";
    }

    private static String targetName(Context context) {
        return prefs(context).getString(KEY_NAME, "موقع محفوظ");
    }

    private static double targetLat(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong(KEY_LAT, Double.doubleToLongBits(0d)));
    }

    private static double targetLon(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong(KEY_LON, Double.doubleToLongBits(0d)));
    }

    private static void hide(Activity activity) {
        View panel = activity.findViewById(R.id.nav_panel);
        if (panel != null) panel.setVisibility(View.GONE);
    }

    private static float normalize(float value) {
        float v = value % 360f;
        if (v < 0f) v += 360f;
        return v;
    }

    private static float normalizeSigned(float value) {
        float v = normalize(value);
        return v > 180f ? v - 360f : v;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
''')

write(base/'app/src/main/java/com/abosultan/darbakmaps/TrackSessionState.java', r'''package com.abosultan.darbakmaps;

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

    private TrackSessionState() {}

    public static void beginIfNeeded(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getLong(START, 0L) == 0L) {
            p.edit().putLong(START, System.currentTimeMillis()).putBoolean(PAUSED, false).apply();
        }
    }

    public static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static boolean isPaused(Context context) {
        return prefs(context).getBoolean(PAUSED, false);
    }

    public static boolean togglePaused(Context context) {
        boolean next = !isPaused(context);
        prefs(context).edit().putBoolean(PAUSED, next).apply();
        return next;
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
        long start = p.getLong(START, System.currentTimeMillis());
        long elapsed = Math.max(1000L, System.currentTimeMillis() - start);
        float hours = elapsed / 3600000f;
        float avg = hours > 0f ? km / hours : 0f;
        long totalMinutes = elapsed / 60000L;
        return String.format(Locale.US, "%.1f كم • %02d:%02d • متوسط %.0f • أعلى %.0f كم/س",
                km, totalMinutes / 60L, totalMinutes % 60L, avg, p.getFloat(MAX, 0f));
    }

    public static void updateActionLabel(TextView view, Context context) {
        if (view == null) return;
        if (!MapUiPreferences.backgroundTrackEnabled(context)) {
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
''')

write(base/'app/src/main/java/com/abosultan/darbakmaps/QuickPointDialog.java', r'''package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.location.Location;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

/** One-tap waypoint creation for driving/off-road use. */
public final class QuickPointDialog {
    private QuickPointDialog() {}

    public static void show(Activity activity, PlaceRepository repository, Location location) {
        if (location == null) {
            Toast.makeText(activity, "بانتظار إشارة GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = {"مخيم", "بيت", "موقع سمان", "ماء / بئر", "شجرة مميزة", "سيارة", "مدخل", "خطر", "تجمع", "نقطة رجوع"};
        final String[] icons = {
                PlaceRepository.ICON_CAMP, PlaceRepository.ICON_HOME, PlaceRepository.ICON_QUAIL,
                PlaceRepository.ICON_WATER, PlaceRepository.ICON_TREE, PlaceRepository.ICON_CAR,
                PlaceRepository.ICON_GATE, PlaceRepository.ICON_STAR, PlaceRepository.ICON_STAR,
                PlaceRepository.ICON_STAR
        };
        new AlertDialog.Builder(activity)
                .setTitle("حفظ سريع للموقع الحالي")
                .setItems(names, (dialog, which) -> {
                    repository.addDetailed(names[which], location.getLatitude(), location.getLongitude(),
                            icons[which], names[which], "حفظ سريع أثناء القيادة");
                    MapRuntimeBridge.refreshSavedPlaces(activity);
                    Toast.makeText(activity, "تم حفظ " + names[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }
}
''')

p = base/'app/src/main/java/com/abosultan/darbakmaps/BackgroundTrackService.java'
s = read(p)
s = rep(s, '        MapUiPreferences.setBackgroundTrackEnabled(context, enabled);\n',
'''        MapUiPreferences.setBackgroundTrackEnabled(context, enabled);
        if (enabled) TrackSessionState.beginIfNeeded(context);
''', 'service begin stats')
s = rep(s, '        if (location == null) return;\n',
'''        if (location == null || TrackSessionState.isPaused(this)) return;
''', 'service paused')
s = rep(s, '            BackgroundTrackStore.append(this, location);\n            lastAccepted = new Location(location);\n',
'''            BackgroundTrackStore.append(this, location);
            TrackSessionState.onFix(this, location);
            lastAccepted = new Location(location);
''', 'service stats fix')
s = rep(s, '            stopForeground(true);\n            stopSelf();\n',
'''            TrackSessionState.reset(this);
            stopForeground(true);
            stopSelf();
''', 'service reset')
write(p, s)

p = base/'app/src/main/java/com/abosultan/darbakmaps/CarScreenLayout.java'
s = read(p)
marker = '        tools.addView(gps, frame(-2, dp(activity, 36), Gravity.TOP | Gravity.RIGHT, dp(activity, 14), dp(activity, 78), 0, 0));\n'
hud = marker + r'''

        LinearLayout navPanel = new LinearLayout(activity);
        navPanel.setId(R.id.nav_panel);
        navPanel.setOrientation(LinearLayout.HORIZONTAL);
        navPanel.setGravity(Gravity.CENTER_VERTICAL);
        navPanel.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 6));
        navPanel.setBackground(round(Color.argb(246, 7, 17, 29), dp(activity, 20), Color.argb(160, 215, 173, 85)));
        TextView navArrow = label(activity, "➤", GOLD, 34f, Gravity.CENTER);
        navArrow.setId(R.id.nav_arrow);
        navPanel.addView(navArrow, new LinearLayout.LayoutParams(dp(activity, 58), -1));
        LinearLayout navText = new LinearLayout(activity);
        navText.setOrientation(LinearLayout.VERTICAL);
        navText.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        TextView navName = label(activity, "", TEXT, 16f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        navName.setId(R.id.nav_name);
        TextView navDistance = label(activity, "", GOLD, 20f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        navDistance.setId(R.id.nav_distance);
        TextView navDetail = label(activity, "", MUTED, 11f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        navDetail.setId(R.id.nav_detail);
        navText.addView(navName, new LinearLayout.LayoutParams(-1, dp(activity, 24)));
        navText.addView(navDistance, new LinearLayout.LayoutParams(-1, dp(activity, 29)));
        navText.addView(navDetail, new LinearLayout.LayoutParams(-1, dp(activity, 20)));
        navPanel.addView(navText, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView navStop = label(activity, "×", TEXT, 25f, Gravity.CENTER);
        navStop.setId(R.id.nav_stop);
        navStop.setBackground(round(SURFACE_ALT, dp(activity, 14), Color.argb(85, 215, 173, 85)));
        navPanel.addView(navStop, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));
        navPanel.setVisibility(View.GONE);
        tools.addView(navPanel, frame(dp(activity, 470), dp(activity, 88), Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                0, dp(activity, 74), 0, 0));
'''
s = rep(s, marker, hud, 'layout hud')
write(p, s)

p = base/'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java'
s = read(p)
s = rep(s, '        bindActions();\n        startupPhase = "تجهيز شاشة الخريطة";\n',
'''        bindActions();
        NavigationGuidance.restore(this);
        startupPhase = "تجهيز شاشة الخريطة";
''', 'restore guidance')
s = rep(s, '                MapRuntimeBridge.refreshSavedPlaces(this);\n                restoreActiveTrack();\n',
'''                MapRuntimeBridge.refreshSavedPlaces(this);
                NavigationGuidance.restore(this);
                restoreActiveTrack();
''', 'restore guidance after map')
s = rep(s, '        findViewById(R.id.action_save).setOnClickListener(view -> saveCurrentPlace());\n        actionRecord.setOnClickListener(view -> toggleTrackRecording());\n',
'''        findViewById(R.id.action_save).setOnClickListener(view -> QuickPointDialog.show(this, placeRepository,
                locationController == null ? null : locationController.getLastLocation()));
        actionRecord.setOnClickListener(view -> toggleTrackRecording());
        actionRecord.setOnLongClickListener(view -> {
            if (!MapUiPreferences.backgroundTrackEnabled(this)) return false;
            boolean paused = TrackSessionState.togglePaused(this);
            TrackSessionState.updateActionLabel(actionRecord, this);
            toast(paused ? "تم إيقاف تسجيل المسار مؤقتًا" : "تمت متابعة تسجيل المسار");
            return true;
        });
        View navStop = findViewById(R.id.nav_stop);
        if (navStop != null) navStop.setOnClickListener(view -> NavigationGuidance.stop(this));
''', 'bind quick/nav')
s = rep(s, '                        MapRuntimeBridge.navigateTo(this, selected.latitude, selected.longitude);\n',
'''                        NavigationGuidance.start(this, selected.name, selected.latitude, selected.longitude);
''', 'saved guidance')
s = rep(s, '        actionRecord.setText(MapUiPreferences.backgroundTrackEnabled(this)\n                ? "إيقاف المسار"\n                : getString(R.string.record_track));\n',
'''        TrackSessionState.updateActionLabel(actionRecord, this);
        if (MapUiPreferences.backgroundTrackEnabled(this) && MapUiPreferences.showTrackStats(this)) {
            actionRecord.setContentDescription(TrackSessionState.summary(this));
        }
''', 'sync track label')
s = rep(s, '            if (MapUiPreferences.backgroundTrackEnabled(this) && mapController != null) {\n                mapController.addTrackPoint(location.getLatitude(), location.getLongitude());\n            }\n',
'''            if (MapUiPreferences.backgroundTrackEnabled(this) && !TrackSessionState.isPaused(this) && mapController != null) {
                mapController.addTrackPoint(location.getLatitude(), location.getLongitude());
            }
            NavigationGuidance.update(this, location);
            TrackSessionState.updateActionLabel(actionRecord, this);
''', 'location guidance')
s = rep(s, '                "المواقع المحفوظة (" + placeRepository.all().size() + ")",\n                "المسارات السابقة (" + TrackStorage.list(this).length + ")"\n',
'''                "المواقع المحفوظة (" + placeRepository.all().size() + ")",
                "الأقرب إلى موقعي",
                "المسارات السابقة (" + TrackStorage.list(this).length + ")"
''', 'saved hub items')
s = rep(s, '                    if (which == 0) {\n                        showPlaces(placeRepository.all(), "المواقع المحفوظة");\n                    } else {\n                        showTracks();\n                    }\n',
'''                    if (which == 0) {
                        showPlaces(placeRepository.all(), "المواقع المحفوظة");
                    } else if (which == 1) {
                        showNearbyPlaces();
                    } else {
                        showTracks();
                    }
''', 'saved hub actions')
s = rep(s, '                .setItems(labels, (dialog, which) -> loadStoredTrack(tracks[which]))\n',
'''                .setItems(labels, (dialog, which) -> showTrackActions(tracks[which]))
''', 'track actions menu')
anchor = '    private void showMore() {\n'
helpers = r'''    private void showNearbyPlaces() {
        Location current = locationController == null ? null : locationController.getLastLocation();
        if (current == null) {
            toast("بانتظار إشارة GPS");
            return;
        }
        List<PlaceRepository.Place> places = new java.util.ArrayList<>(placeRepository.all());
        java.util.Collections.sort(places, (a, b) -> Float.compare(distanceTo(current, a), distanceTo(current, b)));
        if (places.size() > 30) places = new java.util.ArrayList<>(places.subList(0, 30));
        showPlaces(places, "أقرب المواقع إليك");
    }

    private float distanceTo(Location current, PlaceRepository.Place place) {
        float[] out = new float[1];
        Location.distanceBetween(current.getLatitude(), current.getLongitude(), place.latitude, place.longitude, out);
        return out[0];
    }

    private void showTrackActions(File track) {
        String[] actions = {"عرض المسار على الخريطة", "الرجوع على نفس الطريق"};
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(track.getName().replace(".gpx", "").replace('_', ' '))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) loadStoredTrack(track);
                    else startBacktrack(track);
                })
                .setNegativeButton("إلغاء", null)
                .create());
    }

    private void startBacktrack(File track) {
        if (mapController == null) {
            toast("أضف حزمة خريطة لعرض المسار");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                List<GeoPoint> points = TrackStorage.load(track);
                if (points.size() < 2) throw new IllegalStateException("المسار قصير");
                GeoPoint start = points.get(0);
                runOnUiThread(() -> {
                    mapController.showStoredTrack(points);
                    NavigationGuidance.start(this, "بداية المسار", start.latitude, start.longitude);
                    toast("اتبع الخط الظاهر للرجوع على نفس الطريق");
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("تعذر بدء الرجوع على المسار"));
            }
        });
    }

'''+anchor
s = rep(s, anchor, helpers, 'main helpers')
write(p, s)

p = base/'app/src/main/java/com/abosultan/darbakmaps/DarbakPanels.java'
s = read(p)
marker = '''        root.addView(toggleCard(activity,
                "رسم وتسجيل المسار بالخلفية",
                "يسجل خط سيرك ويستمر حتى عند إغلاق التطبيق",
                MapUiPreferences.backgroundTrackEnabled(activity),
                checked -> {
                    BackgroundTrackService.setEnabled(activity, checked);
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).onBackgroundTrackSettingChanged(checked);
                    }
                }), compact(activity));
'''
addition = marker + '''
        root.addView(toggleCard(activity,
                "متابعة السيارة أثناء القيادة",
                "يبقي الخريطة متمركزة على سيارتك أثناء الحركة",
                MapUiPreferences.followVehicle(activity),
                checked -> MapUiPreferences.setFollowVehicle(activity, checked)), compact(activity));

        root.addView(toggleCard(activity,
                "إحصاءات المسار",
                "المسافة والمدة والمتوسط وأعلى سرعة أثناء التسجيل",
                MapUiPreferences.showTrackStats(activity),
                checked -> MapUiPreferences.setShowTrackStats(activity, checked)), compact(activity));

        root.addView(toggleCard(activity,
                "تنبيه الابتعاد عن الاتجاه",
                "يظهر تنبيه واضح عندما يصبح اتجاه السيارة بعيدًا عن الهدف",
                MapUiPreferences.offRouteAlert(activity),
                checked -> MapUiPreferences.setOffRouteAlert(activity, checked)), compact(activity));
'''
s = rep(s, marker, addition, 'settings expedition')
write(p, s)

p = base/'app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java'
s = read(p)
s = rep(s, '        updateNavigationLine();\n\n        if (!centeredOnFirstFix) {\n',
'''        updateNavigationLine();

        if (MapUiPreferences.followVehicle(mapView.getContext()) && centeredOnFirstFix) {
            mapView.getModel().mapViewPosition.setCenter(lastLocation);
        }
        if (!centeredOnFirstFix) {
''', 'follow vehicle')
write(p, s)

print('expedition upgrade applied')
