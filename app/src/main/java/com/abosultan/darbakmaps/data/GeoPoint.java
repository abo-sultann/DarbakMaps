package com.abosultan.darbakmaps.data;

public final class GeoPoint {
    public final double latitude;
    public final double longitude;
    public final long timeMillis;

    public GeoPoint(double latitude, double longitude, long timeMillis) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeMillis = timeMillis;
    }
}

