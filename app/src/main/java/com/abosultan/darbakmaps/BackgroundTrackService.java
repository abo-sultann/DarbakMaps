package com.abosultan.darbakmaps;

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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persistent GPS track recorder that survives closing the map Activity. */
public final class BackgroundTrackService extends Service implements LocationListener {
    private static final String ACTION_START = "com.abosultan.darbakmaps.TRACK_START";
    private static final String ACTION_STOP = "com.abosultan.darbakmaps.TRACK_STOP";
    public static final String ACTION_FINALIZE_RESULT = "com.abosultan.darbakmaps.TRACK_FINALIZE_RESULT";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_FILE = "file";
    private static final String CHANNEL_ID = "darbak_track";
    private static final int NOTIFICATION_ID = 2306;
    private static final long MIN_TIME_MS = 1500L;
    private static final float MIN_DISTANCE_METERS = 3f;

    private final ExecutorService trackIo = Executors.newSingleThreadExecutor();
    private LocationManager locationManager;
    private Location lastAccepted;
    private boolean listening;
    private volatile boolean stopping;

    public static void setEnabled(Context context, boolean enabled) {
        MapUiPreferences.setBackgroundTrackEnabled(context, enabled);
        if (enabled) TrackSessionState.beginIfNeeded(context);
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
            beginFinalize();
            return START_NOT_STICKY;
        }
        if (!stopping) startTracking();
        return START_STICKY;
    }

    private void beginFinalize() {
        if (stopping) return;
        stopping = true;
        stopTracking();
        trackIo.execute(() -> {
            boolean success = false;
            String message;
            File saved = null;
            try {
                saved = BackgroundTrackStore.finalizeActive(this);
                success = true;
                message = saved == null ? "لا يوجد مسار نشط للحفظ" : "تم حفظ المسار بنجاح";
                TrackSessionState.reset(this);
            } catch (IOException error) {
                message = error.getMessage() == null
                        ? "تعذر حفظ المسار؛ احتفظ التطبيق بالتسجيل القابل للاستعادة"
                        : error.getMessage();
            } catch (RuntimeException error) {
                message = "تعذر إنهاء المسار؛ احتفظ التطبيق بالتسجيل القابل للاستعادة";
            }
            Intent result = new Intent(ACTION_FINALIZE_RESULT);
            result.setPackage(getPackageName());
            result.putExtra(EXTRA_SUCCESS, success);
            result.putExtra(EXTRA_MESSAGE, message);
            if (saved != null) result.putExtra(EXTRA_FILE, saved.getAbsolutePath());
            sendBroadcast(result);
            stopForeground(true);
            stopSelf();
        });
        trackIo.shutdown();
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
        if (location == null || stopping || TrackSessionState.isPaused(this)) return;
        final Location accepted = new Location(location);
        if (lastAccepted != null && lastAccepted.distanceTo(accepted) < MIN_DISTANCE_METERS) return;
        lastAccepted = new Location(accepted);
        trackIo.execute(() -> {
            if (stopping && TrackSessionState.isPaused(this)) return;
            boolean newSegment = TrackSessionState.needsNewSegment(this);
            try {
                BackgroundTrackStore.append(this, accepted, newSegment);
                if (newSegment) TrackSessionState.markSegmentWritten(this);
                TrackSessionState.onFix(this, accepted);
            } catch (IOException ignored) {
                // The journal remains authoritative; a later fix may succeed and finalization will
                // report any persistent storage failure to the UI.
            }
        });
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}

    @Override
    public void onDestroy() {
        stopTracking();
        if (!trackIo.isShutdown()) trackIo.shutdown();
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
