from pathlib import Path
import re


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f"{label}: source text not found")
    return text.replace(old, new, 1)

# MapUiPreferences
prefs_path = Path('app/src/main/java/com/abosultan/darbakmaps/MapUiPreferences.java')
p = prefs_path.read_text()
p = must_replace(p,
    '    private static final String KEY_KEEP_SCREEN = "keep_screen_on";\n',
    '    private static final String KEY_KEEP_SCREEN = "keep_screen_on";\n    private static final String KEY_BACKGROUND_TRACK = "background_track";\n',
    'prefs key')
p = must_replace(p,
    '    public static void setKeepScreenOn(Context context, boolean value) {\n        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN, value).apply();\n    }\n\n',
    '    public static void setKeepScreenOn(Context context, boolean value) {\n        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN, value).apply();\n    }\n\n    public static boolean backgroundTrackEnabled(Context context) {\n        return prefs(context).getBoolean(KEY_BACKGROUND_TRACK, false);\n    }\n\n    public static void setBackgroundTrackEnabled(Context context, boolean value) {\n        prefs(context).edit().putBoolean(KEY_BACKGROUND_TRACK, value).apply();\n    }\n\n',
    'prefs methods')
prefs_path.write_text(p)

# Persistent active-track storage
Path('app/src/main/java/com/abosultan/darbakmaps/data/BackgroundTrackStore.java').write_text(r'''package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.location.Location;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Incremental crash-safe storage for the currently recording track. */
public final class BackgroundTrackStore {
    private static final int MAX_POINTS = 50_000;
    private static final String ACTIVE_FILE = "active-track.csv";

    private BackgroundTrackStore() {}

    public static synchronized void append(Context context, Location location) throws IOException {
        if (location == null) return;
        File file = activeFile(context);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create tracks directory");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(Double.toString(location.getLatitude()));
            writer.write(',');
            writer.write(Double.toString(location.getLongitude()));
            writer.write(',');
            writer.write(Long.toString(location.getTime()));
            writer.newLine();
        }
    }

    public static synchronized List<GeoPoint> loadActive(Context context) {
        List<GeoPoint> points = new ArrayList<>();
        File file = activeFile(context);
        if (!file.isFile()) return points;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null && points.size() < MAX_POINTS) {
                String[] parts = line.split(",");
                if (parts.length != 3) continue;
                try {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    long time = Long.parseLong(parts[2]);
                    if (lat >= -90d && lat <= 90d && lon >= -180d && lon <= 180d) {
                        points.add(new GeoPoint(lat, lon, time));
                    }
                } catch (RuntimeException ignored) {
                    // Keep the rest of the active track usable if one line is malformed.
                }
            }
        } catch (IOException ignored) {
            // UI can continue even if the active track cannot be restored.
        }
        return points;
    }

    public static synchronized File finalizeActive(Context context) throws IOException {
        List<GeoPoint> points = loadActive(context);
        File active = activeFile(context);
        if (points.size() < 2) {
            if (active.exists()) active.delete();
            return null;
        }
        String name = "مسار تلقائي " + new SimpleDateFormat("dd-MM-yyyy HH-mm", Locale.US).format(new Date());
        File saved = TrackStorage.save(context, name, points);
        if (active.exists() && !active.delete()) {
            active.deleteOnExit();
        }
        return saved;
    }

    public static File activeFile(Context context) {
        return new File(new File(context.getFilesDir(), "tracks"), ACTIVE_FILE);
    }
}
''')

# Foreground service: keeps GPS recording alive while Activity is closed.
Path('app/src/main/java/com/abosultan/darbakmaps/BackgroundTrackService.java').write_text(r'''package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import com.abosultan.darbakmaps.data.BackgroundTrackStore;

import java.io.IOException;

/** Persistent GPS track recorder that survives closing the map Activity. */
public final class BackgroundTrackService extends Service implements LocationListener {
    private static final String ACTION_START = "com.abosultan.darbakmaps.TRACK_START";
    private static final String ACTION_STOP = "com.abosultan.darbakmaps.TRACK_STOP";
    private static final String CHANNEL_ID = "darbak_track";
    private static final int NOTIFICATION_ID = 2306;
    private static final long MIN_TIME_MS = 1500L;
    private static final float MIN_DISTANCE_METERS = 3f;

    private LocationManager locationManager;
    private Location lastAccepted;
    private boolean listening;

    public static void setEnabled(Context context, boolean enabled) {
        MapUiPreferences.setBackgroundTrackEnabled(context, enabled);
        Intent intent = new Intent(context, BackgroundTrackService.class);
        intent.setAction(enabled ? ACTION_START : ACTION_STOP);
        startCompat(context, intent);
    }

    public static void ensureRunning(Context context) {
        if (!MapUiPreferences.backgroundTrackEnabled(context)) return;
        Intent intent = new Intent(context, BackgroundTrackService.class);
        intent.setAction(ACTION_START);
        startCompat(context, intent);
    }

    private static void startCompat(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && ACTION_START.equals(intent.getAction())) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException ignored) {
            // OEM head units can transiently reject service starts during boot; START_STICKY and
            // the next Activity/boot pass will retry without crashing the app.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        startForeground(NOTIFICATION_ID, notification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action) || !MapUiPreferences.backgroundTrackEnabled(this)) {
            stopTracking();
            try {
                BackgroundTrackStore.finalizeActive(this);
            } catch (IOException ignored) {
                // Keep shutdown safe; the partial file remains recoverable if saving fails.
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startTracking();
        return START_STICKY;
    }

    private void startTracking() {
        if (listening || locationManager == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_METERS,
                    this);
            listening = true;
        } catch (RuntimeException ignored) {
            listening = false;
        }
    }

    private void stopTracking() {
        if (!listening || locationManager == null) return;
        try {
            locationManager.removeUpdates(this);
        } catch (RuntimeException ignored) {
            // Safe shutdown on vendor ROMs.
        }
        listening = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        if (lastAccepted != null && lastAccepted.distanceTo(location) < MIN_DISTANCE_METERS) return;
        try {
            BackgroundTrackStore.append(this, location);
            lastAccepted = new Location(location);
        } catch (IOException ignored) {
            // Do not crash the long-running service because of one storage failure.
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification notification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "تسجيل مسار دربك",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("يبقي تسجيل المسار مستمرًا عند إغلاق التطبيق");
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("دربك — تسجيل المسار")
                .setContentText("تسجيل مسارك مستمر في الخلفية")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }
}
''')

# Manifest
manifest_path = Path('app/src/main/AndroidManifest.xml')
m = manifest_path.read_text()
m = must_replace(m,
    '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n',
    '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />\n',
    'manifest permissions')
m = must_replace(m,
    '        <receiver\n            android:name=".BootReceiver"',
    '        <service\n            android:name=".BackgroundTrackService"\n            android:enabled="true"\n            android:exported="false"\n            android:foregroundServiceType="location" />\n\n        <receiver\n            android:name=".BootReceiver"',
    'manifest service')
manifest_path.write_text(m)

# BootReceiver: background recording is independent of auto-opening the UI.
boot_path = Path('app/src/main/java/com/abosultan/darbakmaps/BootReceiver.java')
b = boot_path.read_text()
b = must_replace(b,
'''        if (!Intent.ACTION_BOOT_COMPLETED.equals(receivedIntent.getAction())
                || !StartupPreferences.isEnabled(context)) {
            return;
        }

        PendingResult pending = goAsync();
''',
'''        if (!Intent.ACTION_BOOT_COMPLETED.equals(receivedIntent.getAction())) {
            return;
        }
        BackgroundTrackService.ensureRunning(context);
        if (!StartupPreferences.isEnabled(context)) {
            return;
        }

        PendingResult pending = goAsync();
''',
'boot background track')
boot_path.write_text(b)

# Darbak settings: toggle + scrollable settings panel.
panels_path = Path('app/src/main/java/com/abosultan/darbakmaps/DarbakPanels.java')
d = panels_path.read_text()
d = must_replace(d,
    'import android.widget.LinearLayout;\n',
    'import android.widget.LinearLayout;\nimport android.widget.ScrollView;\n',
    'panels scroll import')
needle = '''        root.addView(toggleCard(activity,
                "إبقاء الشاشة مضاءة",
                "مناسب للقيادة والبر أثناء عرض الخريطة",
                MapUiPreferences.keepScreenOn(activity),
                checked -> {
                    MapUiPreferences.setKeepScreenOn(activity, checked);
                    if (checked) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }), compact(activity));

'''
insert = needle + '''        root.addView(toggleCard(activity,
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
d = must_replace(d, needle, insert, 'settings background track toggle')
d = must_replace(d,
    '        dialog.setContentView(root);\n        show(dialog, activity, 820);\n    }\n\n    private static LinearLayout.LayoutParams compact',
    '        ScrollView scroll = new ScrollView(activity);\n        scroll.setFillViewport(true);\n        scroll.addView(root);\n        dialog.setContentView(scroll);\n        show(dialog, activity, 820);\n    }\n\n    private static LinearLayout.LayoutParams compact',
    'settings scrolling')
panels_path.write_text(d)

# MainActivity: replace old in-memory/manual recorder with persistent setting/service.
main_path = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = main_path.read_text()
s = s.replace('import com.abosultan.darbakmaps.data.TrackRecorder;\n', '')
s = must_replace(s,
    'import com.abosultan.darbakmaps.data.TrackStorage;\n',
    'import com.abosultan.darbakmaps.data.TrackStorage;\nimport com.abosultan.darbakmaps.data.BackgroundTrackStore;\n',
    'main background store import')
s = s.replace('    private final TrackRecorder trackRecorder = new TrackRecorder();\n', '')
s = must_replace(s,
    '        startupPhase = "اكتمل";\n',
    '        BackgroundTrackService.ensureRunning(this);\n        syncBackgroundTrackUi();\n        startupPhase = "اكتمل";\n',
    'main startup service')
s = must_replace(s,
    '                MapRuntimeBridge.refreshSavedPlaces(this);\n                return;\n',
    '                MapRuntimeBridge.refreshSavedPlaces(this);\n                restoreActiveTrack();\n                return;\n',
    'restore active track')

pattern = re.compile(r'    private void toggleTrackRecording\(\) \{.*?\n    \}\n\n(?=    private void showSavedHub)', re.S)
replacement = r'''    private void toggleTrackRecording() {
        boolean enabled = !MapUiPreferences.backgroundTrackEnabled(this);
        BackgroundTrackService.setEnabled(this, enabled);
        onBackgroundTrackSettingChanged(enabled);
        toast(enabled
                ? "بدأ رسم وتسجيل المسار — سيستمر عند إغلاق التطبيق"
                : "تم إيقاف التسجيل وحفظ المسار");
    }

    void onBackgroundTrackSettingChanged(boolean enabled) {
        if (enabled) {
            if (mapController != null) {
                mapController.beginTrack();
                restoreActiveTrack();
            }
        } else if (mapController != null) {
            mapController.beginTrack();
        }
        syncBackgroundTrackUi();
    }

    private void syncBackgroundTrackUi() {
        if (actionRecord == null) return;
        actionRecord.setText(MapUiPreferences.backgroundTrackEnabled(this)
                ? "إيقاف المسار"
                : getString(R.string.record_track));
    }

    private void restoreActiveTrack() {
        if (!MapUiPreferences.backgroundTrackEnabled(this) || mapController == null) return;
        mapController.beginTrack();
        ioExecutor.execute(() -> {
            List<GeoPoint> points = BackgroundTrackStore.loadActive(this);
            runOnUiThread(() -> {
                if (!isActivityUnavailable() && mapController != null && points.size() >= 2) {
                    mapController.showStoredTrack(points);
                }
            });
        });
    }

'''
s, count = pattern.subn(lambda _: replacement, s, count=1)
if count != 1:
    raise SystemExit('toggleTrackRecording method patch failed')

s = must_replace(s,
'''            if (trackRecorder.add(location) && mapController != null) {
                mapController.addTrackPoint(location.getLatitude(), location.getLongitude());
            }
''',
'''            if (MapUiPreferences.backgroundTrackEnabled(this) && mapController != null) {
                mapController.addTrackPoint(location.getLatitude(), location.getLongitude());
            }
''',
'main live track draw')
s = must_replace(s,
'''            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationController.start();
            } else {
''',
'''            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationController.start();
                BackgroundTrackService.ensureRunning(this);
            } else {
''',
'permission service retry')
s = must_replace(s,
'''        if (initialized && locationController != null && locationController.hasPermission()) {
            locationController.start();
        }
''',
'''        if (initialized && locationController != null && locationController.hasPermission()) {
            locationController.start();
        }
        if (initialized) {
            BackgroundTrackService.ensureRunning(this);
            syncBackgroundTrackUi();
        }
''',
'onResume service')
main_path.write_text(s)

# Version bump
gradle_path = Path('app/build.gradle')
g = gradle_path.read_text()
g = g.replace('versionCode 13', 'versionCode 14').replace("versionName '0.6.0'", "versionName '0.6.1'")
gradle_path.write_text(g)

print('Background track upgrade applied')
