package com.abosultan.darbakmaps.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

/** GPS controller that rejects stale/inaccurate fixes and expires lost signal. */
public final class LocationController implements LocationListener {
    public interface Callback {
        void onLocation(Location location);
        void onProviderState(boolean enabled);
    }

    public static final long MAX_LOCATION_AGE_MS = 15_000L;
    public static final float MAX_ACCURACY_METERS = 100f;
    private static final long WATCHDOG_MS = 5_000L;

    private final Context context;
    private final LocationManager locationManager;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Location lastLocation;
    private long lastAcceptedElapsed;
    private boolean started;

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (lastLocation != null && SystemClock.elapsedRealtime() - lastAcceptedElapsed > MAX_LOCATION_AGE_MS) {
                lastLocation = null;
                callback.onProviderState(false);
            }
            handler.postDelayed(this, WATCHDOG_MS);
        }
    };

    public LocationController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void start() {
        stop();
        started = true;
        if (!hasPermission() || locationManager == null) {
            callback.onProviderState(false);
            scheduleWatchdog();
            return;
        }
        try {
            boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            callback.onProviderState(enabled);
            if (enabled) {
                Location cached = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (isUsable(cached)) onLocationChanged(cached);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
            }
        } catch (SecurityException ignored) {
            callback.onProviderState(false);
        }
        scheduleWatchdog();
    }

    public void stop() {
        started = false;
        handler.removeCallbacks(watchdog);
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); }
            catch (SecurityException ignored) { }
        }
    }

    public Location getLastLocation() {
        if (lastLocation == null || SystemClock.elapsedRealtime() - lastAcceptedElapsed > MAX_LOCATION_AGE_MS) return null;
        return new Location(lastLocation);
    }

    public static boolean isUsable(Location location) {
        if (location == null) return false;
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lon) || lat < -90d || lat > 90d || lon < -180d || lon > 180d) return false;
        if (location.hasAccuracy() && location.getAccuracy() > MAX_ACCURACY_METERS) return false;
        long time = location.getTime();
        return time > 0L && Math.abs(System.currentTimeMillis() - time) <= MAX_LOCATION_AGE_MS;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!isUsable(location)) return;
        lastLocation = new Location(location);
        lastAcceptedElapsed = SystemClock.elapsedRealtime();
        callback.onProviderState(true);
        callback.onLocation(new Location(location));
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) callback.onProviderState(true);
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            lastLocation = null;
            callback.onProviderState(false);
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private void scheduleWatchdog() {
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, WATCHDOG_MS);
    }
}
