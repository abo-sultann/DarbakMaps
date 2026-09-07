package com.abosultan.darbakmaps.data;

import android.location.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrackRecorder {
    private static final int MAX_IN_MEMORY_POINTS = 50000;
    private static final float MIN_DISTANCE_METERS = 3f;

    private final List<GeoPoint> points = new ArrayList<>();
    private Location lastAccepted;
    private boolean recording;

    public synchronized void start() {
        points.clear();
        lastAccepted = null;
        recording = true;
    }

    public synchronized boolean add(Location location) {
        if (!recording || location == null || points.size() >= MAX_IN_MEMORY_POINTS) {
            return false;
        }
        if (lastAccepted != null && lastAccepted.distanceTo(location) < MIN_DISTANCE_METERS) {
            return false;
        }
        points.add(new GeoPoint(location.getLatitude(), location.getLongitude(), location.getTime()));
        lastAccepted = new Location(location);
        return true;
    }

    public synchronized List<GeoPoint> stop() {
        recording = false;
        return Collections.unmodifiableList(new ArrayList<>(points));
    }

    public synchronized boolean isRecording() {
        return recording;
    }
}

