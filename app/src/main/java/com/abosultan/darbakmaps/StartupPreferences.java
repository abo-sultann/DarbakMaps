package com.abosultan.darbakmaps;

import android.content.Context;

final class StartupPreferences {
    private static final String FILE = "darbak_startup";
    private static final String KEY_ENABLED = "open_after_boot";

    private StartupPreferences() {
    }

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
