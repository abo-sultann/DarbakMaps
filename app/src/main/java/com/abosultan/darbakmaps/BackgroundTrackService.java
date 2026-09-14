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
    private static final float MAX_PLAUSIBLE_SPEED_MPS = 80f;
    private static final float STATIONARY_SPEED_MPS = 0.7f;
    private static final float STATIONARY_DRIFT_METERS = 8f;

    private final ExecutorService trackIo = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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
        startForeground(NOTIFICATION_ID, notification(
                TrackRuntimeState.isFinalizing(this)
                        ? "جارٍ استكمال حفظ المسار بعد إعادة تشغيل الخدمة"
                        : "تسجيل آخر 1000 كم مستمر في الخلفية"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (intent == null && TrackRuntimeState.isFinalizing(this)) {
            beginFinalize(startId);
            return START_NOT_STICKY;
        }
        if (action == null) action = ACTION_START;

        if (ACTION_START.equals(action)) {
            if (!MapUiPreferences.backgroundTrackEnabled(this)) {
                stopForeground(true);
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }
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
        updateNotification("جارٍ حفظ نسخة متسقة من المسار…");

        // Single-thread executor acts as a barrier: all fixes accepted before stopTracking() are
        // committed before this snapshot task runs. Accepted fixes are never dropped merely because
        // stopping became true after they were queued.
        trackIo.execute(() -> {
            boolean success = false;
            String message;
            File saved = null;
            try {
                saved = BackgroundTrackStore.finalizeActive(this);
                success = true;
                message = saved == null ? "لا يوجد مسار نشط للحفظ" : "تم حفظ نسخة من المسار بنجاح";
                TrackRuntimeState.clearWriteError(this);
            } catch (IOException error) {
                message = error.getMessage() == null
                        ? "تعذر حفظ نسخة المسار؛ بقي السجل التلقائي محفوظًا"
                        : error.getMessage();
            } catch (RuntimeException error) {
                message = "تعذر إنهاء الحفظ؛ بقي السجل التلقائي محفوظًا";
            }

            TrackRuntimeState.recordFinalizeResult(this, success, message);
            Intent result = new Intent(ACTION_FINALIZE_RESULT);
            result.setPackage(getPackageName());
            result.putExtra(EXTRA_SUCCESS, success);
            result.putExtra(EXTRA_MESSAGE, message);
            if (saved != null) result.putExtra(EXTRA_FILE, saved.getAbsolutePath());
            sendBroadcast(result);

            final String finalMessage = message;
            mainHandler.post(() -> Toast.makeText(getApplicationContext(), finalMessage, Toast.LENGTH_LONG).show());

            boolean restart = success
                    && TrackRuntimeState.consumeRestart(this)
                    && MapUiPreferences.backgroundTrackEnabled(this);
            if (restart) {
                stopping = false;
                lastAccepted = null;
                lastAcceptedElapsedMs = 0L;
                TrackSessionState.beginIfNeeded(this);
                TrackRuntimeState.setState(this, TrackSessionState.isPaused(this)
                        ? TrackRuntimeState.PAUSED : TrackRuntimeState.RUNNING);
                updateNotification(TrackSessionState.isPaused(this)
                        ? "تسجيل المسار متوقف مؤقتًا"
                        : "تسجيل آخر 1000 كم مستمر في الخلفية");
                startTracking();
                return;
            }

            if (!success) MapUiPreferences.setBackgroundTrackEnabled(this, false);
            stopForeground(true);
            stopSelfResult(finalizeStartId);
        });
    }

    private void startTracking() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::startTracking);
            return;
        }
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
                    this,
                    Looper.getMainLooper());
            listening = true;
        } catch (RuntimeException error) {
            listening = false;
            TrackRuntimeState.setWriteError(this, "تعذر بدء استقبال GPS لتسجيل المسار");
        }
    }

    private void stopTracking() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::stopTracking);
            return;
        }
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
            // Do not check stopping here: this runnable may have been accepted before stopTracking().
            if (TrackSessionState.isPaused(this) || revision != TrackSessionState.revision(this)) return;

            boolean gap = lastAccepted == null
                    || (lastAcceptedElapsedMs > 0L
                    && receivedElapsed - lastAcceptedElapsedMs > GAP_NEW_SEGMENT_MS);
            boolean newSegment = gap || TrackSessionState.needsNewSegment(this);
            double connectedMeters = 0d;

            if (lastAccepted != null && !newSegment) {
                float distance = lastAccepted.distanceTo(accepted);
                long elapsedMs = Math.max(1L, receivedElapsed - lastAcceptedElapsedMs);
                float derivedSpeed = distance / (elapsedMs / 1000f);

                if (distance < MIN_DISTANCE_METERS) return;
                if (derivedSpeed > MAX_PLAUSIBLE_SPEED_MPS) return;
                if (accepted.hasSpeed() && accepted.getSpeed() < STATIONARY_SPEED_MPS
                        && distance < STATIONARY_DRIFT_METERS) return;
                connectedMeters = distance;
            }

            try {
                BackgroundTrackStore.append(this, accepted, newSegment, connectedMeters);
                if (newSegment) TrackSessionState.markSegmentWritten(this);
                TrackSessionState.onCommittedFix(this, accepted);
                lastAccepted = new Location(accepted);
                lastAcceptedElapsedMs = receivedElapsed;
                TrackRuntimeState.clearWriteError(this);
                TrackRuntimeState.setState(this, TrackSessionState.isPaused(this)
                        ? TrackRuntimeState.PAUSED : TrackRuntimeState.RUNNING);
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
            channel.setDescription("يسجل آخر 1000 كم ويستمر عند إغلاق واجهة التطبيق");
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("دربك — التسجيل التلقائي")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }
}
