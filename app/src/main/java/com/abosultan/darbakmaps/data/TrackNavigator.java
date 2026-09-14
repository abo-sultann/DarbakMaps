package com.abosultan.darbakmaps.data;

import java.util.List;

/** Reverse breadcrumb cursor. Matches the nearest valid segment, then walks vertices in reverse. */
public final class TrackNavigator {
    private final List<GeoPoint> points;
    private int cursor = -1;
    private boolean stoppedAtGap;

    public TrackNavigator(List<GeoPoint> points) {
        this.points = points;
    }

    public int targetIndex() {
        return cursor;
    }

    public boolean stoppedAtGap() {
        return stoppedAtGap;
    }

    public GeoPoint update(double latitude, double longitude) {
        if (stoppedAtGap || points == null || points.size() < 2) return null;
        if (cursor < 0) {
            double best = Double.MAX_VALUE;
            int bestIndex = points.size() - 2;
            for (int index = 0; index < points.size() - 1; index++) {
                if (points.get(index + 1).segmentStart) continue;
                double distance = segmentDistance(latitude, longitude, points.get(index), points.get(index + 1));
                if (distance <= best) {
                    best = distance;
                    bestIndex = index;
                }
            }
            cursor = bestIndex;
        }

        while (cursor > 0
                && distance(latitude, longitude,
                points.get(cursor).latitude, points.get(cursor).longitude) < 30d) {
            // Reaching the first point of a recorded segment is a hard stop. Never direct the
            // driver across the missing GPS/pause gap to the previous segment.
            if (points.get(cursor).segmentStart) {
                stoppedAtGap = true;
                return null;
            }
            cursor--;
        }
        return points.get(cursor);
    }

    public double offTrack(double latitude, double longitude) {
        if (points == null || points.isEmpty()) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (int index = 0; index < points.size(); index++) {
            GeoPoint point = points.get(index);
            best = Math.min(best, distance(latitude, longitude, point.latitude, point.longitude));
            if (index > 0 && !point.segmentStart) {
                best = Math.min(best, segmentDistance(latitude, longitude, points.get(index - 1), point));
            }
        }
        return best;
    }

    public boolean crossesGap() {
        return cursor >= 0 && cursor + 1 < points.size() && points.get(cursor + 1).segmentStart;
    }

    public static double segmentDistance(double latitude, double longitude, GeoPoint first, GeoPoint second) {
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

    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double hav = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        return 6371000d * 2d * Math.asin(Math.sqrt(Math.max(0d, Math.min(1d, hav))));
    }
}
