package com.abosultan.darbakmaps.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts automatic breadcrumb recording after the Android 7.1 head unit boots. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SessionStore session = new SessionStore(context);
        if (!session.shouldResumeTrackRecording()) return;
        try {
            context.startService(new Intent(context, TrackRecordingService.class));
        } catch (RuntimeException ignored) {
            // Never crash the system boot path; opening Darbak Maps will retry safely.
        }
    }
}
