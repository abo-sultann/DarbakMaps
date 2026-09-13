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
