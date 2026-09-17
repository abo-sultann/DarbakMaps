package com.abosultan.darbakmaps.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.abosultan.darbakmaps.MainActivity;

/** Restores Darbak Maps background/foreground behavior after the Android 7.1 head unit boots. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SessionStore session = new SessionStore(context);
        boolean recording = session.shouldResumeTrackRecording();
        boolean autoLaunch = session.shouldAutoLaunch();

        // The same lightweight service owns GPS recording and the SCREEN_ON receiver. Keep it
        // alive when either feature needs it; pausing track recording must not disable auto-launch.
        if (recording || autoLaunch) {
            try {
                context.startService(new Intent(context, TrackRecordingService.class));
            } catch (RuntimeException ignored) {
                // Never crash the system boot path; opening Darbak Maps will retry safely.
            }
        }

        if (autoLaunch) {
            try {
                Intent launch = new Intent(context, MainActivity.class);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
            } catch (RuntimeException ignored) {
                // Some vendor launchers can temporarily block activity starts during boot.
            }
        }
    }
}
