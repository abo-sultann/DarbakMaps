package com.abosultan.darbakmaps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the automatic recorder after boot without opening the map UI. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent receivedIntent) {
        if (receivedIntent == null || !Intent.ACTION_BOOT_COMPLETED.equals(receivedIntent.getAction())) {
            return;
        }
        // Android may still prevent work after force-stop; we intentionally do not promise otherwise.
        // When the app is allowed to receive BOOT_COMPLETED, only the recorder is resumed.
        BackgroundTrackService.ensureRunning(context.getApplicationContext());
    }
}
