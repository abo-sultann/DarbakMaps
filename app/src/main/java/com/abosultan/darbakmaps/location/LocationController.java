package com.abosultan.darbakmaps.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

public final class LocationController implements LocationListener {
    public interface Callback {
        void onLocation(Location location);

        void onProviderState(boolean enabled);
    }

    private final Context context;
    private final LocationManager locationManager;
    private final Callback callback;
    private Location lastLocation;

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
        if (!hasPermission() || locationManager == null) {
            callback.onProviderState(false);
            return;
        }
        try {
            boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            callback.onProviderState(enabled);
            if (enabled) {
                Location cached = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (cached != null) {
                    onLocationChanged(cached);
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
            }
        } catch (SecurityException ignored) {
            callback.onProviderState(false);
        }
    }

    public void stop() {
        if (locationManager == null) {
            return;
        }
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException ignored) {
            // Permission may have been revoked while the app was running.
        }
    }

    public Location getLastLocation() {
        return lastLocation == null ? null : new Location(lastLocation);
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = new Location(location);
        callback.onLocation(new Location(location));
    }

    @Override
    public void onProviderEnabled(String provider) {
        callback.onProviderState(true);
    }

    @Override
    public void onProviderDisabled(String provider) {
        callback.onProviderState(false);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Required on Android 7.x.
    }
}

