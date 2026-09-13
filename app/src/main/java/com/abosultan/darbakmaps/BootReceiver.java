package com.abosultan.darbakmaps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Opens Darbak after the normal launcher has completed its own boot startup. */
public final class BootReceiver extends BroadcastReceiver {
    private static final long START_DELAY_MILLIS = 6000L;
    private static final long RETRY_DELAY_MILLIS = 1800L;

    @Override
    public void onReceive(Context context, Intent receivedIntent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(receivedIntent.getAction())
                || !StartupPreferences.isEnabled(context)) {
            return;
        }

        PendingResult pending = goAsync();
        Context appContext = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> launchWithFallback(appContext, handler, pending), START_DELAY_MILLIS);
    }

    private void launchWithFallback(Context context, Handler handler, PendingResult pending) {
        if (launch(context)) {
            pending.finish();
            return;
        }

        // Some Android head units reject the first foreground launch while their OEM launcher
        // is still settling. Retry once within BroadcastReceiver's short async window.
        handler.postDelayed(() -> {
            launch(context);
            pending.finish();
        }, RETRY_DELAY_MILLIS);
    }

    private boolean launch(Context context) {
        try {
            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
