package com.abosultan.darbakmaps.core;

/** Parses decimal latitude/longitude without network services or heavy dependencies. */
public final class CoordinateParser {
    private CoordinateParser() {}

    public static double[] parse(String raw) {
        if (raw == null) return null;
        String value = normalizeDigits(raw.trim());
        if (value.length() == 0) return null;
        if (value.startsWith("geo:")) value = value.substring(4);

        value = value.replace('،', ',').replace(';', ',');
        String[] parts = value.split("[,\\s]+", -1);
        if (parts.length < 2) return null;

        try {
            double lat = Double.parseDouble(clean(parts[0]));
            double lon = Double.parseDouble(clean(parts[1]));
            if (!Double.isFinite(lat) || !Double.isFinite(lon)) return null;
            if (lat < -90d || lat > 90d || lon < -180d || lon > 180d) return null;
            return new double[]{lat, lon};
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        String v = value.trim().toUpperCase();
        boolean southOrWest = v.endsWith("S") || v.endsWith("W");
        if (v.endsWith("N") || v.endsWith("S") || v.endsWith("E") || v.endsWith("W")) {
            v = v.substring(0, v.length() - 1).trim();
        }
        double number = Double.parseDouble(v);
        if (southOrWest && number > 0d) number = -number;
        return Double.toString(number);
    }

    private static String normalizeDigits(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '٠' && c <= '٩') out.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') out.append((char) ('0' + (c - '۰')));
            else if (c == '٫') out.append('.');
            else out.append(c);
        }
        return out.toString();
    }
}
