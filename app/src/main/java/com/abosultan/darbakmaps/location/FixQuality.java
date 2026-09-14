package com.abosultan.darbakmaps.location;

/** Pure GPS freshness/quality policy used by Android code and JVM tests. */
public final class FixQuality {
    public static final long MAX_AGE_MS = 15_000L;
    // Intentionally permissive until the real T3 antenna is measured in the field.
    public static final float MAX_ACCURACY_METERS = 250f;

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
