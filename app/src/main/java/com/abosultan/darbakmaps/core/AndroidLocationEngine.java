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
    private static final long MAX_LAST_KNOWN_AGE_MS = 120000L;
    private static final long MAX_DERIVED_COURSE_GAP_MS = 10000L;
    private static final float MIN_DERIVED_COURSE_DISTANCE_METERS = 2f;
    private static final float UNKNOWN_BEARING = -1f;

    private final Context context;
    private final LocationManager manager;
    private volatile LocationSnapshot latest = invalid();
    private boolean started;
    private Location lastFix;
    private float lastCourse = UNKNOWN_BEARING;

    public AndroidLocationEngine(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override public LocationSnapshot latest() {
        return latest;
    }

    @Override public void start() {
        if (started || manager == null) return;
        if (!hasPermission()) {
            setLatest(invalid());
            return;
        }
        try {
            Location last = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (isRecentLastKnown(last)) update(last);
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
            started = true;
        } catch (SecurityException error) {
            started = false;
            setLatest(invalid());
        }
    }

    @Override public void stop() {
        if (manager != null && started) {
            try {
                manager.removeUpdates(this);
            } catch (SecurityException ignored) {
                // Permission may have been revoked while the service was alive.
            }
        }
        started = false;
    }

    @Override public void onLocationChanged(Location location) {
        update(location);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}

    @Override public void onProviderDisabled(String provider) {
        setLatest(invalid());
        lastFix = null;
        lastCourse = UNKNOWN_BEARING;
    }

    private boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isRecentLastKnown(Location location) {
        if (location == null || location.getTime() <= 0L) return false;
        long age = System.currentTimeMillis() - location.getTime();
        return age >= 0L && age <= MAX_LAST_KNOWN_AGE_MS;
    }

    private void update(Location location) {
        if (location == null) return;

        float speed = location.hasSpeed() ? Math.max(0f, location.getSpeed() * 3.6f) : 0f;
        float bearing = resolveBearing(location);

        setLatest(new LocationSnapshot(
                location.getLatitude(),
                location.getLongitude(),
                bearing,
                speed,
                location.getTime(),
                true,
                location.hasAltitude() ? location.getAltitude() : 0d,
                location.hasAltitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f,
                location.hasAccuracy()));

        lastFix = new Location(location);
    }

    private float resolveBearing(Location location) {
        if (location.hasBearing()) {
            float bearing = normalizeBearing(location.getBearing());
            lastCourse = bearing;
            return bearing;
        }

        if (lastFix != null) {
            long elapsed = location.getTime() - lastFix.getTime();
            if (elapsed > 0L && elapsed <= MAX_DERIVED_COURSE_GAP_MS) {
                float distance = lastFix.distanceTo(location);
                if (distance >= MIN_DERIVED_COURSE_DISTANCE_METERS) {
                    float derived = normalizeBearing(lastFix.bearingTo(location));
                    lastCourse = derived;
                    return derived;
                }
            }
        }

        return lastCourse;
    }

    private static float normalizeBearing(float bearing) {
        float normalized = bearing % 360f;
        if (normalized < 0f) normalized += 360f;
        return normalized;
    }

    private void setLatest(LocationSnapshot snapshot) {
        latest = snapshot;
        LiveLocationStore.publish(snapshot);
    }

    private static LocationSnapshot invalid() {
        return new LocationSnapshot(0d, 0d, UNKNOWN_BEARING, 0f, 0L, false,
                0d, false, 0f, false);
    }
}
