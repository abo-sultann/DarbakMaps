package com.abosultan.darbakmaps.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight crash/restart-safe state for the car screen. */
public final class SessionStore {
    private static final String PREFS = "darbak_map_session";
    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveViewport(double latitude, double longitude, int zoom) {
        prefs.edit()
                .putLong("lat", Double.doubleToRawLongBits(latitude))
                .putLong("lon", Double.doubleToRawLongBits(longitude))
                .putInt("zoom", zoom)
                .putBoolean("has_viewport", true)
                .apply();
    }

    public Viewport restoreViewport() {
        if (!prefs.getBoolean("has_viewport", false)) return null;
        return new Viewport(
                Double.longBitsToDouble(prefs.getLong("lat", 0L)),
                Double.longBitsToDouble(prefs.getLong("lon", 0L)),
                prefs.getInt("zoom", 14));
    }

    public void setTrackRecording(boolean recording) {
        prefs.edit().putBoolean("track_recording", recording).apply();
    }

    public boolean shouldResumeTrackRecording() {
        return prefs.getBoolean("track_recording", true);
    }

    public void setAutoLaunch(boolean enabled) {
        prefs.edit().putBoolean("auto_launch", enabled).apply();
    }

    /** Disabled by default so installing a development build never steals the launcher unexpectedly. */
    public boolean shouldAutoLaunch() {
        return prefs.getBoolean("auto_launch", false);
    }

    public static final class Viewport {
        public final double latitude;
        public final double longitude;
        public final int zoom;

        public Viewport(double latitude, double longitude, int zoom) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.zoom = zoom;
        }
    }
}
