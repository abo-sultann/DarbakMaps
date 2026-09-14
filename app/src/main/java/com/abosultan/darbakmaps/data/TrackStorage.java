package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
     * Loads a navigation/display-safe representation of a GPX file. Original GPX bytes are never
     * rewritten. Long tracks are reduced in memory while retaining segment boundaries and the end.
     */
    public static List<GeoPoint> load(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("ملف المسار غير موجود");
        List<GeoPoint> points = new ArrayList<>();
        SimpleDateFormat timeFormat = utcFormat();
        GeoPoint lastPoint = null;
        boolean pendingSegment = true;
        long sampleStep = 1L;
        long seen = 0L;

        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(input, "UTF-8");

            boolean inPoint = false;
            boolean segmentStart = true;
            boolean validPoint = false;
            double latitude = 0d;
            double longitude = 0d;
            long time = 0L;

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
                            Date parsed = timeFormat.parse(parser.nextText());
                            if (parsed != null) time = parsed.getTime();
                        } catch (Exception ignored) {
                            time = 0L;
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && inPoint
                        && ("trkpt".equalsIgnoreCase(name) || "rtept".equalsIgnoreCase(name))) {
                    if (validPoint) {
                        pendingSegment |= segmentStart;
                        long resolvedTime = time > 0L ? time : file.lastModified();
                        GeoPoint point = new GeoPoint(latitude, longitude, resolvedTime, pendingSegment);
                        lastPoint = point;
                        if (seen % sampleStep == 0L) {
                            points.add(point);
                            pendingSegment = false;
                        }
                        seen++;
                        segmentStart = false;
                        if (points.size() > MAX_LOADED_POINTS) {
                            List<GeoPoint> reduced = new ArrayList<>(points.size() / 2 + 1);
                            boolean carrySegment = false;
                            for (int index = 0; index < points.size(); index++) {
                                GeoPoint candidate = points.get(index);
                                carrySegment |= candidate.segmentStart;
                                if (index % 2 == 0) {
                                    reduced.add(new GeoPoint(candidate.latitude, candidate.longitude,
                                            candidate.timeMillis, carrySegment));
                                    carrySegment = false;
                                }
                            }
                            pendingSegment |= carrySegment;
                            points = reduced;
                            sampleStep *= 2L;
                        }
                    }
                    inPoint = false;
                }
            }
        } catch (Exception error) {
            throw new IOException("تعذر قراءة GPX", error);
        }

        if (lastPoint != null) {
            GeoPoint currentLast = points.isEmpty() ? null : points.get(points.size() - 1);
            if (currentLast == null || currentLast.latitude != lastPoint.latitude
                    || currentLast.longitude != lastPoint.longitude || currentLast.timeMillis != lastPoint.timeMillis) {
                points.add(new GeoPoint(lastPoint.latitude, lastPoint.longitude,
                        lastPoint.timeMillis, pendingSegment || lastPoint.segmentStart));
            }
        }
        if (points.size() < 2) throw new IOException("المسار لا يحتوي نقاطًا كافية");
        return points;
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
