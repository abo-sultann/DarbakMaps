package com.abosultan.darbakmaps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens Darbak Maps after device boot only when the owner enabled startup. */
public final class StartupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!StartupPreferences.isEnabled(context)) return;

        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(launch);
        } catch (RuntimeException ignored) {
            // Vendor ROMs can temporarily reject launches during early boot.
        }
    }
}