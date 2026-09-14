package com.abosultan.darbakmaps.location;
/** Shared freshness and quality policy, independent of Android for deterministic tests. */
public final class FixQuality {
    public static final long MAX_AGE_MS=15000;
    public static boolean usable(long ageMillis, boolean hasAccuracy, float accuracy, double lat, double lon) {
        return ageMillis>=0 && ageMillis<=MAX_AGE_MS && hasAccuracy && accuracy>=0 && accuracy<=60
            && Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180;
    }
    private FixQuality(){}
}
