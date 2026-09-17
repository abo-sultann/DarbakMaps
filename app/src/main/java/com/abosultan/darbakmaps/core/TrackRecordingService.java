package com.abosultan.darbakmaps.core;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.abosultan.darbakmaps.MainActivity;

import static com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;

/**
 * Lightweight started service for the Android 7.1 head unit.
 * API 25 permits a started background service; no Play Services dependency.
 */
public final class TrackRecordingService extends Service {
    public static final String ACTION_PAUSE = "com.abosultan.darbakmaps.action.PAUSE_TRACK";
    public static final String ACTION_RESUME = "com.abosultan.darbakmaps.action.RESUME_TRACK";
    public static final String ACTION_REFRESH = "com.abosultan.darbakmaps.action.REFRESH_SERVICE";
    public static final String ACTION_UI_ACTIVE = "com.abosultan.darbakmaps.action.UI_ACTIVE";
    public static final String ACTION_UI_INACTIVE = "com.abosultan.darbakmaps.action.UI_INACTIVE";

    private AndroidLocationEngine location;
    private SqliteTrackRecorder recorder;
    private Handler handler;
    private BroadcastReceiver screenReceiver;
    private boolean uiActive;

    private final Runnable pump = new Runnable() {
        @Override public void run() {
            SessionStore session = new SessionStore(TrackRecordingService.this);
            if (session.shouldResumeTrackRecording() && location != null && recorder != null) {
                LocationSnapshot point = location.latest();
                recorder.append(point);
                if (handler != null) handler.postDelayed(this, 2000L);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        location = new AndroidLocationEngine(this);
        recorder = new SqliteTrackRecorder(this);
        recorder.restoreAutomaticState();
        registerScreenWakeReceiver();
        refreshWorkState();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_UI_ACTIVE.equals(action)) uiActive = true;
        else if (ACTION_UI_INACTIVE.equals(action)) uiActive = false;

        if (recorder != null) {
            if (ACTION_PAUSE.equals(action)) {
                recorder.pause();
            } else if (ACTION_RESUME.equals(action)) {
                recorder.ensureAutomaticRecording();
            }
            // Refresh/UI actions and normal starts keep persisted recording choice unchanged.
        }

        refreshWorkState();
        SessionStore session = new SessionStore(this);
        boolean recording = session.shouldResumeTrackRecording();
        boolean autoLaunch = session.shouldAutoLaunch();
        if (!uiActive && !recording && !autoLaunch) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        return recording || autoLaunch ? START_STICKY : START_NOT_STICKY;
    }

    private void refreshWorkState() {
        if (handler == null || location == null) return;
        handler.removeCallbacks(pump);
        boolean recording = new SessionStore(this).shouldResumeTrackRecording();
        if (uiActive || recording) {
            location.start();
            if (recording) handler.post(pump);
        } else {
            location.stop();
            LiveLocationStore.invalidate();
        }
    }

    private void registerScreenWakeReceiver() {
        if (screenReceiver != null) return;
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || !Intent.ACTION_SCREEN_ON.equals(intent.getAction())) return;
                if (!new SessionStore(context).shouldAutoLaunch()) return;
                try {
                    Intent launch = new Intent(context, MainActivity.class);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(launch);
                } catch (RuntimeException ignored) {
                    // Vendor launchers may briefly reject foreground launches during wake-up.
                }
            }
        };
        try {
            registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        } catch (RuntimeException error) {
            screenReceiver = null;
        }
    }

    @Override public void onDestroy() {
        if (handler != null) handler.removeCallbacks(pump);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (RuntimeException ignored) {
                // Receiver may already have been detached by the system.
            }
            screenReceiver = null;
        }
        if (location != null) location.stop();
        LiveLocationStore.invalidate();
        if (recorder != null) recorder.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
