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

    private AndroidLocationEngine location;
    private SqliteTrackRecorder recorder;
    private Handler handler;
    private BroadcastReceiver screenReceiver;

    private final Runnable pump = new Runnable() {
        @Override public void run() {
            if (location != null && recorder != null) {
                LocationSnapshot point = location.latest();
                recorder.append(point);
                handler.postDelayed(this, 2000L);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        location = new AndroidLocationEngine(this);
        recorder = new SqliteTrackRecorder(this);
        recorder.restoreAutomaticState();
        location.start();
        registerScreenWakeReceiver();
        handler.post(pump);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (location != null) location.start();

        String action = intent == null ? null : intent.getAction();
        if (recorder != null) {
            if (ACTION_PAUSE.equals(action)) {
                recorder.pause();
            } else if (ACTION_RESUME.equals(action)) {
                recorder.ensureAutomaticRecording();
            }
            // For a normal start, keep the persisted choice restored in onCreate.
            // This prevents simply reopening the app from silently cancelling a user pause.
        }
        return START_STICKY;
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
        handler.removeCallbacks(pump);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (RuntimeException ignored) {
                // Receiver may already have been detached by the system.
            }
            screenReceiver = null;
        }
        if (location != null) location.stop();
        if (recorder != null) recorder.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
