package com.abosultan.darbakmaps.core;

import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import java.util.List;

/**
 * Lightweight reverse breadcrumb cursor for one continuous recorded segment.
 * It snaps to the nearest part of the recorded line and then walks points backward.
 */
public final class BacktrackNavigator {
    private static final double TARGET_REACHED_METERS = 30d;
    private final List<LocationSnapshot> points;
    private int cursor = -1;

    public BacktrackNavigator(List<LocationSnapshot> points) {
        this.points = points;
    }

    public boolean usable() { return points != null && points.size() >= 2; }
    public int targetIndex() { return cursor; }

    public LocationSnapshot update(double latitude, double longitude) {
        if (!usable()) return null;
        if (cursor < 0) cursor = nearestReverseStart(latitude, longitude);

        while (cursor > 0 && distance(latitude, longitude,
                points.get(cursor).latitude, points.get(cursor).longitude) <= TARGET_REACHED_METERS) {
            cursor--;
        }
        return points.get(cursor);
    }

    public boolean finished(double latitude, double longitude) {
        if (!usable() || cursor > 0) return false;
        LocationSnapshot first = points.get(0);
        return distance(latitude, longitude, first.latitude, first.longitude) <= TARGET_REACHED_METERS;
    }

    public double offTrackMeters(double latitude, double longitude) {
        if (!usable()) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            LocationSnapshot p = points.get(i);
            best = Math.min(best, distance(latitude, longitude, p.latitude, p.longitude));
            if (i > 0) best = Math.min(best, segmentDistance(latitude, longitude, points.get(i - 1), p));
        }
        return best;
    }

    private int nearestReverseStart(double latitude, double longitude) {
        double best = Double.MAX_VALUE;
        int bestIndex = points.size() - 2;
        for (int i = 0; i < points.size() - 1; i++) {
            double d = segmentDistance(latitude, longitude, points.get(i), points.get(i + 1));
            if (d <= best) {
                best = d;
                bestIndex = i;
            }
        }
        return Math.max(0, bestIndex);
    }

    private static double segmentDistance(double latitude, double longitude,
                                          LocationSnapshot first, LocationSnapshot second) {
        double scale = Math.cos(Math.toRadians(latitude));
        double ax = (first.longitude - longitude) * 111320d * scale;
        double ay = (first.latitude - latitude) * 111320d;
        double bx = (second.longitude - longitude) * 111320d * scale;
        double by = (second.latitude - latitude) * 111320d;
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0d ? 0d
                : Math.max(0d, Math.min(1d, -(ax * dx + ay * dy) / lengthSquared));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        return PlaceMath.distanceMeters(lat1, lon1, lat2, lon2);
    }
}
