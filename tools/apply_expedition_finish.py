from pathlib import Path

def read(p): return Path(p).read_text(encoding='utf-8')
def write(p,s): Path(p).write_text(s,encoding='utf-8')
def rep(t,o,n,l):
    if o not in t: raise SystemExit('missing '+l)
    return t.replace(o,n,1)
base=Path('.')

# version
p=base/'app/build.gradle'; s=read(p); s=s.replace('versionCode 15','versionCode 16').replace("versionName '0.7.0'","versionName '0.7.1'"); write(p,s)

# ids
p=base/'app/src/main/res/values/ids.xml'; s=read(p); s=s.replace('    <item name="nav_stop" type="id" />\n','    <item name="nav_stop" type="id" />\n    <item name="track_stats_panel" type="id" />\n    <item name="track_stats_text" type="id" />\n'); write(p,s)

# backtrack helper
write(base/'app/src/main/java/com/abosultan/darbakmaps/BacktrackGuidance.java', r'''package com.abosultan.darbakmaps;

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
''')

# MapRuntimeBridge clear stored track
p=base/'app/src/main/java/com/abosultan/darbakmaps/MapRuntimeBridge.java'; s=read(p)
s=rep(s,'    public static synchronized boolean hasActiveMap() {\n', '''    public static synchronized void clearStoredTrack() {
        if (activeController != null) activeController.clearStoredTrack();
    }

    public static synchronized boolean hasActiveMap() {
''','bridge clear')
write(p,s)

# controller clear stored
p=base/'app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java'; s=read(p)
s=rep(s,'    public void beginTrack() {\n', '''    public void clearStoredTrack() {
        if (storedTrack != null) {
            mapView.getLayerManager().getLayers().remove(storedTrack);
            storedTrack = null;
            mapView.getLayerManager().redrawLayers();
        }
    }

    public void beginTrack() {
''','controller clear')
write(p,s)

# track stats panel in layout just after speed panel
p=base/'app/src/main/java/com/abosultan/darbakmaps/CarScreenLayout.java'; s=read(p)
marker='        root.addView(speedPill, frame(dp(activity, 112), dp(activity, 52), Gravity.TOP | Gravity.LEFT, 0, dp(activity, 12), dp(activity, 12), 0));\n'
addition=marker+r'''

        LinearLayout trackStatsPanel = new LinearLayout(activity);
        trackStatsPanel.setId(R.id.track_stats_panel);
        trackStatsPanel.setGravity(Gravity.CENTER);
        trackStatsPanel.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        trackStatsPanel.setBackground(round(Color.argb(228, 7, 17, 29), dp(activity, 16), Color.argb(95, 215, 173, 85)));
        TextView trackStats = label(activity, "", TEXT, 12f, Gravity.CENTER);
        trackStats.setId(R.id.track_stats_text);
        trackStatsPanel.addView(trackStats, new LinearLayout.LayoutParams(-1, -1));
        trackStatsPanel.setVisibility(View.GONE);
        root.addView(trackStatsPanel, frame(dp(activity, 335), dp(activity, 46), Gravity.BOTTOM | Gravity.RIGHT,
                dp(activity, 14), 0, 0, dp(activity, 14)));
'''
s=rep(s,marker,addition,'stats panel')
write(p,s)

# MainActivity: stats, backtrack, clear track
p=base/'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java'; s=read(p)
s=rep(s,'                NavigationGuidance.restore(this);\n                restoreActiveTrack();\n', '''                NavigationGuidance.restore(this);
                BacktrackGuidance.restore(this);
                restoreActiveTrack();
''','restore backtrack')
s=rep(s,'        if (navStop != null) navStop.setOnClickListener(view -> NavigationGuidance.stop(this));\n', '''        if (navStop != null) navStop.setOnClickListener(view -> {
            BacktrackGuidance.stop(this);
            NavigationGuidance.stop(this);
        });
''','nav stop backtrack')
s=rep(s,'                        NavigationGuidance.start(this, selected.name, selected.latitude, selected.longitude);\n', '''                        BacktrackGuidance.stop(this);
                        NavigationGuidance.start(this, selected.name, selected.latitude, selected.longitude);
''','saved clears backtrack')
s=rep(s,'        if (MapUiPreferences.backgroundTrackEnabled(this) && MapUiPreferences.showTrackStats(this)) {\n            actionRecord.setContentDescription(TrackSessionState.summary(this));\n        }\n', '''        View statsPanel = findViewById(R.id.track_stats_panel);
        TextView statsText = findViewById(R.id.track_stats_text);
        boolean showStats = MapUiPreferences.backgroundTrackEnabled(this) && MapUiPreferences.showTrackStats(this);
        if (statsPanel != null) statsPanel.setVisibility(showStats ? View.VISIBLE : View.GONE);
        if (statsText != null && showStats) {
            statsText.setText(TrackSessionState.summary(this) + (TrackSessionState.isPaused(this) ? " • متوقف مؤقتًا" : ""));
        }
''','visible stats')
s=rep(s,'            NavigationGuidance.update(this, location);\n            TrackSessionState.updateActionLabel(actionRecord, this);\n', '''            NavigationGuidance.update(this, location);
            BacktrackGuidance.update(this, location);
            syncBackgroundTrackUi();
''','location finish')
s=rep(s,'        String[] actions = {"عرض المسار على الخريطة", "الرجوع على نفس الطريق"};\n', '        String[] actions = {"عرض المسار على الخريطة", "الرجوع على نفس الطريق", "إخفاء المسار المعروض"};\n','track action list')
s=rep(s,'                    if (which == 0) loadStoredTrack(track);\n                    else startBacktrack(track);\n', '''                    if (which == 0) loadStoredTrack(track);
                    else if (which == 1) startBacktrack(track);
                    else {
                        BacktrackGuidance.stop(this);
                        MapRuntimeBridge.clearStoredTrack();
                        toast("تم إخفاء المسار");
                    }
''','track action cases')
s=rep(s,'                    mapController.showStoredTrack(points);\n                    NavigationGuidance.start(this, "بداية المسار", start.latitude, start.longitude);\n                    toast("اتبع الخط الظاهر للرجوع على نفس الطريق");\n', '''                    BacktrackGuidance.start(this, track, points);
                    toast("اتبع الخط الظاهر للرجوع على نفس الطريق");
''','start backtrack helper')
write(p,s)

print('finish upgrade applied')
