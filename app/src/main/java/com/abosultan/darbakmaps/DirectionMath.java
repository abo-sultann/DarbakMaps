package com.abosultan.darbakmaps;

/** Pure direction helpers shared by the saved-place list and tests. */
public final class DirectionMath {
    private DirectionMath() {}

    public static float bearing(double fromLat, double fromLon, double toLat, double toLon) {
        double phi1 = Math.toRadians(fromLat);
        double phi2 = Math.toRadians(toLat);
        double lambda = Math.toRadians(toLon - fromLon);
        double y = Math.sin(lambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda);
        return normalize((float) Math.toDegrees(Math.atan2(y, x)));
    }

    public static float relative(float targetBearing, float vehicleBearing) {
        float value = normalize(targetBearing) - normalize(vehicleBearing);
        if (value > 180f) value -= 360f;
        if (value <= -180f) value += 360f;
        return value;
    }

    /** Circular low-pass filter: alpha 0 keeps previous, alpha 1 uses current. */
    public static float smoothAngle(float previous, float current, float alpha) {
        if (Float.isNaN(previous)) return normalizeSigned(current);
        float delta = relative(normalize(current), normalize(previous));
        return normalizeSigned(previous + Math.max(0f, Math.min(1f, alpha)) * delta);
    }

    public static String cardinalArabic(float bearing) {
        String[] names = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        int index = Math.round(normalize(bearing) / 45f) % 8;
        return names[index];
    }

    public static float normalize(float value) {
        float out = value % 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    public static float normalizeSigned(float value) {
        float out = normalize(value);
        return out > 180f ? out - 360f : out;
    }
}
