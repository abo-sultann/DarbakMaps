package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class TrackStorage {
    private static final int MAX_LOADED_POINTS = 12000;
    private static final String GPX_NS = "http://www.topografix.com/GPX/1/1";

    private TrackStorage() {}

    public static File save(Context context, String displayName, List<GeoPoint> points) throws IOException {
        File directory = new File(context.getFilesDir(), "tracks");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("تعذر إنشاء مجلد المسارات");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File target = new File(directory, sanitize(displayName) + "-" + stamp + "-" + System.nanoTime() + ".gpx");
        File pending = new File(target.getAbsolutePath() + ".pending");
        FileOutputStream output = new FileOutputStream(pending);
        try {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(output, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializer.setPrefix("", GPX_NS);
            serializer.startTag(GPX_NS, "gpx");
            serializer.attribute(null, "version", "1.1");
            serializer.attribute(null, "creator", "DarbakMaps");
            serializer.startTag(GPX_NS, "trk");
            serializer.startTag(GPX_NS, "name");
            serializer.text(displayName == null ? "مسار دربك" : displayName);
            serializer.endTag(GPX_NS, "name");
            serializer.startTag(GPX_NS, "trkseg");
            SimpleDateFormat timeFormat = utcFormat();
            boolean first = true;
            for (GeoPoint point : points) {
                if (point == null) continue;
                if (point.segmentStart && !first) {
                    serializer.endTag(GPX_NS, "trkseg");
                    serializer.startTag(GPX_NS, "trkseg");
                }
                first = false;
                serializer.startTag(GPX_NS, "trkpt");
                serializer.attribute(null, "lat", Double.toString(point.latitude));
                serializer.attribute(null, "lon", Double.toString(point.longitude));
                if (point.timeMillis > 0L) {
                    serializer.startTag(GPX_NS, "time");
                    serializer.text(timeFormat.format(new Date(point.timeMillis)));
                    serializer.endTag(GPX_NS, "time");
                }
                serializer.endTag(GPX_NS, "trkpt");
            }
            serializer.endTag(GPX_NS, "trkseg");
            serializer.endTag(GPX_NS, "trk");
            serializer.endTag(GPX_NS, "gpx");
            serializer.endDocument();
            serializer.flush();
            output.getFD().sync();
        } catch (IOException error) {
            pending.delete();
            throw error;
        } finally {
            output.close();
        }
        if (target.exists() || !pending.renameTo(target)) {
            pending.delete();
            throw new IOException("تعذر تثبيت ملف المسار");
        }
        return target;
    }

    public static File[] list(Context context) {
        File directory = new File(context.getFilesDir(), "tracks");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".gpx"));
        if (files == null) return new File[0];
        Arrays.sort(files, (first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        return files;
    }

    /**
     * Loads a bounded navigation/display representation. The source GPX is never rewritten.
     * Missing timestamps remain unknown (0), and reduction preserves segment starts/endpoints and
     * geometrically important turns rather than blindly dropping every second point.
     */
    public static List<GeoPoint> load(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("ملف المسار غير موجود");
        List<GeoPoint> points = new ArrayList<>();
        boolean segmentStart = true;
        boolean inPoint = false;
        boolean validPoint = false;
        double latitude = 0d;
        double longitude = 0d;
        long time = 0L;

        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(input, "UTF-8");
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                String name = parser.getName();
                if (event == XmlPullParser.START_TAG) {
                    if ("trkseg".equalsIgnoreCase(name)) {
                        segmentStart = true;
                    } else if ("trkpt".equalsIgnoreCase(name) || "rtept".equalsIgnoreCase(name)) {
                        inPoint = true;
                        validPoint = false;
                        time = 0L;
                        try {
                            latitude = Double.parseDouble(parser.getAttributeValue(null, "lat"));
                            longitude = Double.parseDouble(parser.getAttributeValue(null, "lon"));
                            validPoint = Double.isFinite(latitude) && Double.isFinite(longitude)
                                    && latitude >= -90d && latitude <= 90d
                                    && longitude >= -180d && longitude <= 180d;
                        } catch (RuntimeException ignored) {
                            validPoint = false;
                        }
                    } else if (inPoint && validPoint && "time".equalsIgnoreCase(name)) {
                        try {
                            time = parseGpxTime(parser.nextText());
                        } catch (Exception ignored) {
                            time = 0L;
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && inPoint
                        && ("trkpt".equalsIgnoreCase(name) || "rtept".equalsIgnoreCase(name))) {
                    if (validPoint) {
                        points.add(new GeoPoint(latitude, longitude, time, segmentStart));
                        segmentStart = false;
                        if (points.size() > MAX_LOADED_POINTS * 2) {
                            points = reducePreservingGeometry(points, MAX_LOADED_POINTS);
                        }
                    }
                    inPoint = false;
                }
            }
        } catch (Exception error) {
            throw new IOException("تعذر قراءة GPX", error);
        }
        if (points.size() > MAX_LOADED_POINTS) points = reducePreservingGeometry(points, MAX_LOADED_POINTS);
        if (points.size() < 2) throw new IOException("المسار لا يحتوي نقاطًا كافية");
        return points;
    }

    static List<GeoPoint> reducePreservingGeometry(List<GeoPoint> input, int limit) {
        if (input == null || input.size() <= limit) return input == null ? new ArrayList<>() : new ArrayList<>(input);
        int stride = Math.max(2, (int) Math.ceil(input.size() / (double) Math.max(2, limit - 2)));
        List<GeoPoint> out = new ArrayList<>(Math.min(limit + 64, input.size()));
        for (int i = 0; i < input.size(); i++) {
            GeoPoint p = input.get(i);
            boolean endpoint = i == 0 || i == input.size() - 1;
            boolean boundary = p.segmentStart || (i + 1 < input.size() && input.get(i + 1).segmentStart);
            boolean sharpTurn = i > 0 && i + 1 < input.size()
                    && !p.segmentStart && !input.get(i + 1).segmentStart
                    && turnAngleDegrees(input.get(i - 1), p, input.get(i + 1)) >= 25d;
            if (endpoint || boundary || sharpTurn || i % stride == 0) out.add(p);
        }
        // If an extremely twisty route still exceeds the cap, increase stride but never discard
        // segment boundaries/endpoints. Sharp turns are retained while space allows.
        if (out.size() > limit) {
            List<GeoPoint> bounded = new ArrayList<>(limit);
            int secondaryStride = Math.max(2, (int) Math.ceil(out.size() / (double) limit));
            for (int i = 0; i < out.size(); i++) {
                GeoPoint p = out.get(i);
                boolean mandatory = i == 0 || i == out.size() - 1 || p.segmentStart
                        || (i + 1 < out.size() && out.get(i + 1).segmentStart);
                if (mandatory || i % secondaryStride == 0) bounded.add(p);
            }
            out = bounded;
        }
        return out;
    }

    static double turnAngleDegrees(GeoPoint a, GeoPoint b, GeoPoint c) {
        double h1 = Math.atan2(b.longitude - a.longitude, b.latitude - a.latitude);
        double h2 = Math.atan2(c.longitude - b.longitude, c.latitude - b.latitude);
        double diff = Math.abs(Math.toDegrees(h2 - h1)) % 360d;
        return diff > 180d ? 360d - diff : diff;
    }

    static long parseGpxTime(String raw) throws ParseException {
        if (raw == null || raw.trim().isEmpty()) return 0L;
        String value = raw.trim();
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        ParseException last = null;
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                if (pattern.endsWith("'Z'")) format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = format.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException error) {
                last = error;
            }
        }
        throw last == null ? new ParseException(value, 0) : last;
    }

    private static SimpleDateFormat utcFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

    private static String sanitize(String value) {
        if (value == null) value = "مسار";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length() && result.length() < 40; i++) {
            char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) result.append(character);
            else if (result.length() > 0 && result.charAt(result.length() - 1) != '_') result.append('_');
        }
        return result.length() == 0 ? "مسار" : result.toString();
    }
}
