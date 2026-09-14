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

public final class LocationController implements LocationListener {
    private static final long MAX_CACHED_AGE_MS = 30_000L;
    private static final long FIX_TIMEOUT_MS = 15_000L;
    // Kept permissive until the real T3 antenna is measured; freshness is enforced independently.
    private static final float MAX_ACCEPTABLE_ACCURACY_METERS = 250f;

    public interface Callback {
        void onLocation(Location location);
        void onProviderState(boolean enabled);
    }

    private final Context context;
    private final LocationManager locationManager;
    private final Callback callback;
    private final Handler handler;
    private final Runnable staleFixRunnable;
    private Location lastLocation;

    public LocationController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.staleFixRunnable = () -> {
            lastLocation = null;
            this.callback.onProviderState(false);
        };
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void start() {
        if (!hasPermission() || locationManager == null) {
            lastLocation = null;
            callback.onProviderState(false);
            return;
        }
        try {
            boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            callback.onProviderState(enabled);
            if (!enabled) {
                lastLocation = null;
                return;
            }
            Location cached = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (usable(cached, MAX_CACHED_AGE_MS)) onLocationChanged(cached);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
            scheduleStaleTimeout();
        } catch (SecurityException ignored) {
            lastLocation = null;
            callback.onProviderState(false);
        } catch (RuntimeException ignored) {
            lastLocation = null;
            callback.onProviderState(false);
        }
    }

    public void stop() {
        handler.removeCallbacks(staleFixRunnable);
        if (locationManager == null) return;
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException ignored) {
            // Permission may have been revoked while the app was running.
        } catch (RuntimeException ignored) {
            // Some vendor ROMs throw during provider teardown.
        }
    }

    public Location getLastLocation() {
        if (!usable(lastLocation, FIX_TIMEOUT_MS)) {
            lastLocation = null;
            return null;
        }
        return new Location(lastLocation);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!usable(location, MAX_CACHED_AGE_MS)) return;
        lastLocation = new Location(location);
        callback.onLocation(new Location(location));
        scheduleStaleTimeout();
    }

    @Override
    public void onProviderEnabled(String provider) {
        callback.onProviderState(true);
        scheduleStaleTimeout();
    }

    @Override
    public void onProviderDisabled(String provider) {
        handler.removeCallbacks(staleFixRunnable);
        lastLocation = null;
        callback.onProviderState(false);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Required on Android 7.x.
    }

    private void scheduleStaleTimeout() {
        handler.removeCallbacks(staleFixRunnable);
        handler.postDelayed(staleFixRunnable, FIX_TIMEOUT_MS);
    }

    private static boolean usable(Location location, long maxAgeMs) {
        if (location == null) return false;
        long age = ageMillis(location);
        return FixQuality.usable(age, maxAgeMs,
                location.hasAccuracy(), location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE,
                MAX_ACCEPTABLE_ACCURACY_METERS,
                location.getLatitude(), location.getLongitude());
    }

    private static long ageMillis(Location location) {
        long elapsedNanos = location.getElapsedRealtimeNanos();
        if (elapsedNanos > 0L) {
            long now = SystemClock.elapsedRealtimeNanos();
            if (now >= elapsedNanos) return (now - elapsedNanos) / 1_000_000L;
            return -1L;
        }
        long fixTime = location.getTime();
        if (fixTime <= 0L) return Long.MAX_VALUE;
        long age = System.currentTimeMillis() - fixTime;
        return age >= 0L ? age : -1L;
    }
}
