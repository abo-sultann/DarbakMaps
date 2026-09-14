package com.abosultan.darbakmaps.location;

/** Pure GPS freshness/quality policy used by Android code and JVM tests. */
public final class FixQuality {
    private FixQuality() {}

    public static boolean usable(long ageMillis,
                                 long maxAgeMillis,
                                 boolean hasAccuracy,
                                 float accuracyMeters,
                                 float maxAccuracyMeters,
                                 double latitude,
                                 double longitude) {
        return ageMillis >= 0L
                && ageMillis <= maxAgeMillis
                && hasAccuracy
                && accuracyMeters >= 0f
                && accuracyMeters <= maxAccuracyMeters
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }
}
