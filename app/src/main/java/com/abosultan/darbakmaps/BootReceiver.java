package com.abosultan.darbakmaps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Opens Darbak after the normal launcher has completed its own boot startup. */
public final class BootReceiver extends BroadcastReceiver {
    private static final long START_DELAY_MILLIS = 6000L;

    @Override
    public void onReceive(Context context, Intent receivedIntent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(receivedIntent.getAction())
                || !StartupPreferences.isEnabled(context)) {
            return;
        }
        PendingResult pending = goAsync();
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent launch = new Intent(appContext, MainActivity.class);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appContext.startActivity(launch);
            } catch (RuntimeException ignored) {
                // A vendor ROM can briefly reject activity starts while its launcher is loading.
            } finally {
                pending.finish();
            }
        }, START_DELAY_MILLIS);
    }
}
