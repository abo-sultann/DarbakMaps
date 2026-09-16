package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.content.SharedPreferences;

final class MapUiSettings {
    private static final String PREFS = "darbak_map_ui";
    static final String SPEED = "show_speed";
    static final String MAP_TOOLS = "show_map_tools";
    static final String BOTTOM_DOCK = "show_bottom_dock";
    static final String TAP_HIDE = "tap_hide";
    static final String AUTO_HIDE = "auto_hide";

    private final SharedPreferences p;
    MapUiSettings(Context c) { p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    boolean get(String key) { return p.getBoolean(key, true); }
    void set(String key, boolean value) { p.edit().putBoolean(key, value).apply(); }
}
