package com.abosultan.darbakmaps.core;

/** Pure offline navigation math; intentionally has no map/network dependency. */
public final class PlaceMath {
    private static final double EARTH_RADIUS_M = 6371000.0;

    private PlaceMath() {}

    public static double distanceMeters(double fromLat, double fromLon, double toLat, double toLon) {
        double lat1 = Math.toRadians(fromLat);
        double lat2 = Math.toRadians(toLat);
        double dLat = Math.toRadians(toLat - fromLat);
        double dLon = Math.toRadians(toLon - fromLon);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return EARTH_RADIUS_M * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    /** Bearing in degrees clockwise from true north, normalized to [0, 360). */
    public static float bearingDegrees(double fromLat, double fromLon, double toLat, double toLon) {
        double lat1 = Math.toRadians(fromLat);
        double lat2 = Math.toRadians(toLat);
        double dLon = Math.toRadians(toLon - fromLon);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return (float) ((degrees + 360.0) % 360.0);
    }

    /** Relative arrow angle: 0 means straight ahead. */
    public static float relativeBearing(float vehicleBearing, float targetBearing) {
        float angle = (targetBearing - vehicleBearing + 540f) % 360f - 180f;
        return angle;
    }
}
