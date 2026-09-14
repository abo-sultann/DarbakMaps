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
    private static final long MAX_FIX_AGE_MS = FixQuality.MAX_AGE_MS;
    private static final float MAX_ACCEPTABLE_ACCURACY_METERS = FixQuality.MAX_ACCURACY_METERS;

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
    private boolean listening;

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
            clearUnavailable();
            return;
        }
        try {
            if (!listening) {
                // Register even while GPS is disabled so Android 7.x can deliver onProviderEnabled
                // without requiring the Activity to be restarted.
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
                listening = true;
            }
            boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            callback.onProviderState(enabled);
            if (!enabled) {
                lastLocation = null;
                handler.removeCallbacks(staleFixRunnable);
                return;
            }
            Location cached = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (usable(cached)) onLocationChanged(cached);
            else scheduleStaleTimeout(MAX_FIX_AGE_MS);
        } catch (RuntimeException ignored) {
            clearUnavailable();
        }
    }

    public void stop() {
        handler.removeCallbacks(staleFixRunnable);
        if (locationManager == null || !listening) return;
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException ignored) {
            // Permission may have been revoked while the app was running.
        } catch (RuntimeException ignored) {
            // Some vendor ROMs throw during provider teardown.
        } finally {
            listening = false;
        }
    }

    public Location getLastLocation() {
        if (!usable(lastLocation)) {
            lastLocation = null;
            return null;
        }
        return new Location(lastLocation);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!usable(location)) return;
        long age = ageMillis(location);
        lastLocation = new Location(location);
        callback.onProviderState(true);
        callback.onLocation(new Location(location));
        scheduleStaleTimeout(Math.max(1L, MAX_FIX_AGE_MS - age));
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (!LocationManager.GPS_PROVIDER.equals(provider)) return;
        callback.onProviderState(true);
        lastLocation = null;
        scheduleStaleTimeout(MAX_FIX_AGE_MS);
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (!LocationManager.GPS_PROVIDER.equals(provider)) return;
        clearUnavailable();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Required on Android 7.x.
    }

    private void scheduleStaleTimeout(long delayMs) {
        handler.removeCallbacks(staleFixRunnable);
        handler.postDelayed(staleFixRunnable, Math.max(1L, delayMs));
    }

    private void clearUnavailable() {
        handler.removeCallbacks(staleFixRunnable);
        lastLocation = null;
        callback.onProviderState(false);
    }

    private static boolean usable(Location location) {
        if (location == null) return false;
        long age = ageMillis(location);
        return FixQuality.usable(age, MAX_FIX_AGE_MS,
                location.hasAccuracy(), location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE,
                MAX_ACCEPTABLE_ACCURACY_METERS,
                location.getLatitude(), location.getLongitude());
    }

    static long ageMillis(Location location) {
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
