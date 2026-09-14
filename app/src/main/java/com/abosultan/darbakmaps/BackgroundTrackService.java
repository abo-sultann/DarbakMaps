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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.BackgroundTrackStore;
import com.abosultan.darbakmaps.location.FixQuality;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persistent GPS track recorder that survives closing the map Activity. */
public final class BackgroundTrackService extends Service implements LocationListener {
    private static final String ACTION_START = "com.abosultan.darbakmaps.TRACK_START";
    private static final String ACTION_STOP = "com.abosultan.darbakmaps.TRACK_STOP";
    private static final String ACTION_RETRY_FINALIZE = "com.abosultan.darbakmaps.TRACK_RETRY_FINALIZE";
    public static final String ACTION_FINALIZE_RESULT = "com.abosultan.darbakmaps.TRACK_FINALIZE_RESULT";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_FILE = "file";
    private static final String CHANNEL_ID = "darbak_track";
    private static final int NOTIFICATION_ID = 2306;
    private static final long MIN_TIME_MS = 1500L;
    private static final float MIN_DISTANCE_METERS = 3f;
    private static final long GAP_NEW_SEGMENT_MS = 30_000L;

    private final ExecutorService trackIo = Executors.newSingleThreadExecutor();
    private LocationManager locationManager;
    private Location lastAccepted;
    private long lastAcceptedElapsedMs;
    private boolean listening;
    private volatile boolean stopping;
    private int finalizeStartId;

    public static void setEnabled(Context context, boolean enabled) {
        if (enabled && TrackRuntimeState.isFinalizing(context)) {
            MapUiPreferences.setBackgroundTrackEnabled(context, true);
            TrackRuntimeState.requestRestart(context);
            return;
        }
        MapUiPreferences.setBackgroundTrackEnabled(context, enabled);
        if (enabled) {
            TrackSessionState.beginIfNeeded(context);
            TrackRuntimeState.clearWriteError(context);
        }
        Intent intent = new Intent(context, BackgroundTrackService.class);
        intent.setAction(enabled ? ACTION_START : ACTION_STOP);
        startCompat(context, intent);
    }

    public static void retryFinalize(Context context) {
        MapUiPreferences.setBackgroundTrackEnabled(context, false);
        Intent intent = new Intent(context, BackgroundTrackService.class);
        intent.setAction(ACTION_RETRY_FINALIZE);
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
            TrackRuntimeState.setWriteError(context, "تعذر تشغيل خدمة تسجيل المسار الآن");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        startForeground(NOTIFICATION_ID, notification("تسجيل مسارك مستمر في الخلفية"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_START.equals(action)) {
            if (stopping) {
                TrackRuntimeState.requestRestart(this);
                return START_STICKY;
            }
            TrackRuntimeState.setState(this, TrackSessionState.isPaused(this)
                    ? TrackRuntimeState.PAUSED : TrackRuntimeState.RUNNING);
            startTracking();
            return START_STICKY;
        }
        if (ACTION_STOP.equals(action) || ACTION_RETRY_FINALIZE.equals(action)
                || !MapUiPreferences.backgroundTrackEnabled(this)) {
            beginFinalize(startId);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void beginFinalize(int startId) {
        if (stopping) return;
        stopping = true;
        finalizeStartId = startId;
        TrackRuntimeState.setState(this, TrackRuntimeState.FINALIZING);
        stopTracking();
        updateNotification("جارٍ إنهاء وحفظ المسار…");
        trackIo.execute(() -> {
            boolean success = false;
            String message;
            File saved = null;
            try {
                saved = BackgroundTrackStore.finalizeActive(this);
                success = true;
                message = saved == null ? "لا يوجد مسار نشط للحفظ" : "تم حفظ المسار بنجاح";
                TrackSessionState.reset(this);
                TrackRuntimeState.clearWriteError(this);
            } catch (IOException error) {
                message = error.getMessage() == null
                        ? "تعذر حفظ المسار؛ احتفظ التطبيق بالتسجيل القابل للاستعادة"
                        : error.getMessage();
            } catch (RuntimeException error) {
                message = "تعذر إنهاء المسار؛ احتفظ التطبيق بالتسجيل القابل للاستعادة";
            }

            TrackRuntimeState.recordFinalizeResult(this, success, message);
            Intent result = new Intent(ACTION_FINALIZE_RESULT);
            result.setPackage(getPackageName());
            result.putExtra(EXTRA_SUCCESS, success);
            result.putExtra(EXTRA_MESSAGE, message);
            if (saved != null) result.putExtra(EXTRA_FILE, saved.getAbsolutePath());
            sendBroadcast(result);

            final String finalMessage = message;
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(), finalMessage, Toast.LENGTH_LONG).show());

            boolean restart = success
                    && TrackRuntimeState.consumeRestart(this)
                    && MapUiPreferences.backgroundTrackEnabled(this);
            if (restart) {
                stopping = false;
                lastAccepted = null;
                lastAcceptedElapsedMs = 0L;
                TrackSessionState.beginIfNeeded(this);
                TrackRuntimeState.setState(this, TrackRuntimeState.RUNNING);
                updateNotification("تسجيل مسارك مستمر في الخلفية");
                startTracking();
                return;
            }

            if (!success) MapUiPreferences.setBackgroundTrackEnabled(this, false);
            stopForeground(true);
            stopSelfResult(finalizeStartId);
        });
    }

    private void startTracking() {
        if (listening || locationManager == null || stopping) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            TrackRuntimeState.setWriteError(this, "صلاحية الموقع غير متاحة لتسجيل المسار");
            return;
        }
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_METERS,
                    this);
            listening = true;
        } catch (RuntimeException error) {
            listening = false;
            TrackRuntimeState.setWriteError(this, "تعذر بدء استقبال GPS لتسجيل المسار");
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
        final long age = ageMillis(accepted);
        if (!FixQuality.usable(age, FixQuality.MAX_AGE_MS,
                accepted.hasAccuracy(), accepted.hasAccuracy() ? accepted.getAccuracy() : Float.MAX_VALUE,
                FixQuality.MAX_ACCURACY_METERS, accepted.getLatitude(), accepted.getLongitude())) {
            return;
        }
        final long revision = TrackSessionState.revision(this);
        final long receivedElapsed = SystemClock.elapsedRealtime();
        trackIo.execute(() -> {
            if (stopping || TrackSessionState.isPaused(this) || revision != TrackSessionState.revision(this)) return;
            if (lastAccepted != null) {
                if (lastAccepted.distanceTo(accepted) < MIN_DISTANCE_METERS) return;
            }
            boolean gap = lastAcceptedElapsedMs > 0L
                    && receivedElapsed - lastAcceptedElapsedMs > GAP_NEW_SEGMENT_MS;
            boolean newSegment = gap || TrackSessionState.needsNewSegment(this);
            try {
                BackgroundTrackStore.append(this, accepted, newSegment);
                if (newSegment) TrackSessionState.markSegmentWritten(this);
                TrackSessionState.onCommittedFix(this, accepted);
                lastAccepted = new Location(accepted);
                lastAcceptedElapsedMs = receivedElapsed;
                TrackRuntimeState.clearWriteError(this);
                TrackRuntimeState.setState(this, TrackRuntimeState.RUNNING);
            } catch (IOException error) {
                TrackRuntimeState.setWriteError(this,
                        error.getMessage() == null ? "تعذر كتابة نقطة في المسار" : error.getMessage());
            }
        });
    }

    private static long ageMillis(Location location) {
        long elapsedNanos = location.getElapsedRealtimeNanos();
        if (elapsedNanos > 0L) {
            long now = SystemClock.elapsedRealtimeNanos();
            if (now >= elapsedNanos) return (now - elapsedNanos) / 1_000_000L;
            return -1L;
        }
        long fixTime = location.getTime();
        if (fixTime <= 0L) return Long.MAX_VALUE;
        long age = System.currentTimeMillis() - fixTime;
        return age >= 0L ? age : -1L;
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}

    @Override
    public void onDestroy() {
        stopTracking();
        trackIo.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.notify(NOTIFICATION_ID, notification(text));
    }

    private Notification notification(String text) {
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
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }
}