package com.abosultan.darbakmaps.data;
public final class GeoPoint {
    public final double latitude, longitude;
    public final long timeMillis;
    public final boolean segmentStart;
    public GeoPoint(double lat, double lon, long time) { this(lat, lon, time, false); }
    public GeoPoint(double lat, double lon, long time, boolean segmentStart) {
        this.latitude=lat; this.longitude=lon; this.timeMillis=time; this.segmentStart=segmentStart;
    }
}
