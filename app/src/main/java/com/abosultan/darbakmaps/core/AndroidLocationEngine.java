package com.abosultan.darbakmaps.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import static com.abosultan.darbakmaps.core.CoreContracts.LocationEngine;
import static com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;

/** GPS-only engine: no Google Play Services and no network dependency. */
public final class AndroidLocationEngine implements LocationEngine, LocationListener {
    private final Context context;
    private final LocationManager manager;
    private volatile LocationSnapshot latest = invalid();
    private boolean started;

    public AndroidLocationEngine(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override public LocationSnapshot latest() { return latest; }

    @Override public void start() {
        if (started || manager == null) return;
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            latest = invalid();
            return;
        }
        Location last = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last != null) update(last);
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
        started = true;
    }

    @Override public void stop() {
        if (manager != null && started) manager.removeUpdates(this);
        started = false;
    }

    @Override public void onLocationChanged(Location location) { update(location); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) { latest = invalid(); }

    private void update(Location location) {
        float speed = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        float bearing = location.hasBearing() ? location.getBearing() : 0f;
        latest = new LocationSnapshot(location.getLatitude(), location.getLongitude(), bearing, speed, location.getTime(), true);
    }

    private static LocationSnapshot invalid() {
        return new LocationSnapshot(0d, 0d, 0f, 0f, 0L, false);
    }
}
