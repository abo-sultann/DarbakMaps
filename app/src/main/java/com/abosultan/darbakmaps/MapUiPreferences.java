package com.abosultan.darbakmaps;

import android.content.Context;
import android.content.SharedPreferences;

public final class MapUiPreferences {
    public static final int ORIENTATION_NORTH = 0;
    public static final int ORIENTATION_HEADING = 1;
    public static final int ORIENTATION_FREE = 2;

    public static final int ROUTING_DIRECT = 0;
    public static final int ROUTING_ROADS = 1;

    private static final String PREFS = "darbak_map_ui";
    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_ROUTING = "routing_mode";
    private static final String KEY_SHOW_SAVED = "show_saved_places";
    private static final String KEY_SHOW_SAVED_LABELS = "show_saved_labels";
    private static final String KEY_SHOW_SPEED = "show_speed";
    private static final String KEY_KEEP_SCREEN = "keep_screen_on";

    private MapUiPreferences() {}

    public static int orientation(Context context) {
        int value = prefs(context).getInt(KEY_ORIENTATION, ORIENTATION_NORTH);
        return value < ORIENTATION_NORTH || value > ORIENTATION_FREE ? ORIENTATION_NORTH : value;
    }

    public static void setOrientation(Context context, int value) {
        prefs(context).edit().putInt(KEY_ORIENTATION, value).apply();
    }

    public static int routingMode(Context context) {
        int value = prefs(context).getInt(KEY_ROUTING, ROUTING_DIRECT);
        return value == ROUTING_ROADS ? ROUTING_ROADS : ROUTING_DIRECT;
    }

    public static void setRoutingMode(Context context, int value) {
        prefs(context).edit().putInt(KEY_ROUTING, value == ROUTING_ROADS ? ROUTING_ROADS : ROUTING_DIRECT).apply();
    }

    public static boolean showSavedPlaces(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_SAVED, true);
    }

    public static void setShowSavedPlaces(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_SHOW_SAVED, value).apply();
    }

    public static boolean showSavedLabels(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_SAVED_LABELS, true);
    }

    public static void setShowSavedLabels(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_SHOW_SAVED_LABELS, value).apply();
    }

    public static boolean showSpeed(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_SPEED, true);
    }

    public static void setShowSpeed(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_SHOW_SPEED, value).apply();
    }

    public static boolean keepScreenOn(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_SCREEN, true);
    }

    public static void setKeepScreenOn(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN, value).apply();
    }

    /** Compatibility with older builds. Tools are now controlled only by tapping the map. */
    public static boolean autoHideTools(Context context) {
        return false;
    }

    public static void setAutoHideTools(Context context, boolean enabled) {
        // Intentionally ignored.
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
