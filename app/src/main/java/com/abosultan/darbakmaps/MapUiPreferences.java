package com.abosultan.darbakmaps;

import android.content.Context;
import android.content.SharedPreferences;

public final class MapUiPreferences {
    public static final int ORIENTATION_NORTH = 0;
    public static final int ORIENTATION_HEADING = 1;
    public static final int ORIENTATION_FREE = 2;

    private static final String PREFS = "darbak_map_ui";
    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_AUTO_HIDE = "auto_hide_tools";

    private MapUiPreferences() {}

    public static int orientation(Context context) {
        int value = prefs(context).getInt(KEY_ORIENTATION, ORIENTATION_NORTH);
        return value < ORIENTATION_NORTH || value > ORIENTATION_FREE ? ORIENTATION_NORTH : value;
    }

    public static void setOrientation(Context context, int value) {
        prefs(context).edit().putInt(KEY_ORIENTATION, value).apply();
    }

    public static boolean autoHideTools(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_HIDE, true);
    }

    public static void setAutoHideTools(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_HIDE, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
