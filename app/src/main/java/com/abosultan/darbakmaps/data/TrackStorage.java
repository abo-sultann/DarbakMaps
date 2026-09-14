package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;
import org.xmlpull.v1.XmlPullParser;

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
    private TrackStorage() {
    }

    public static File save(Context context, String displayName, List<GeoPoint> points) throws IOException {
        File directory = new File(context.getFilesDir(), "tracks");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create tracks directory");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String safeName = sanitize(displayName);
        File file = new File(directory, safeName + "-" + stamp + ".gpx");

        FileOutputStream output = new FileOutputStream(file);
        try {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(output, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializer.setPrefix("", "http://www.topografix.com/GPX/1/1");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "gpx");
            serializer.attribute(null, "version", "1.1");
            serializer.attribute(null, "creator", "DarbakMaps");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "trk");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "name");
            serializer.text(displayName);
            serializer.endTag("http://www.topografix.com/GPX/1/1", "name");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "trkseg");
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (GeoPoint point : points) {
                serializer.startTag("http://www.topografix.com/GPX/1/1", "trkpt");
                serializer.attribute(null, "lat", Double.toString(point.latitude));
                serializer.attribute(null, "lon", Double.toString(point.longitude));
                serializer.startTag("http://www.topografix.com/GPX/1/1", "time");
                serializer.text(timeFormat.format(new Date(point.timeMillis)));
                serializer.endTag("http://www.topografix.com/GPX/1/1", "time");
                serializer.endTag("http://www.topografix.com/GPX/1/1", "trkpt");
            }
            serializer.endTag("http://www.topografix.com/GPX/1/1", "trkseg");
            serializer.endTag("http://www.topografix.com/GPX/1/1", "trk");
            serializer.endTag("http://www.topografix.com/GPX/1/1", "gpx");
            serializer.endDocument();
            serializer.flush();
        } finally {
            output.close();
        }
        return file;
    }

    public static File[] list(Context context) {
        File directory = new File(context.getFilesDir(), "tracks");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".gpx"));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        return files;
    }

    public static List<GeoPoint> load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("ملف المسار غير موجود");
        }
        List<GeoPoint> points = new ArrayList<>();
        FileInputStream input = new FileInputStream(file);
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG
                        && ("trkpt".equalsIgnoreCase(parser.getName())
                        || "rtept".equalsIgnoreCase(parser.getName()))) {
                    String rawLatitude = parser.getAttributeValue(null, "lat");
                    String rawLongitude = parser.getAttributeValue(null, "lon");
                    try {
                        double latitude = Double.parseDouble(rawLatitude);
                        double longitude = Double.parseDouble(rawLongitude);
                        if (latitude >= -90d && latitude <= 90d
                                && longitude >= -180d && longitude <= 180d) {
                            points.add(new GeoPoint(latitude, longitude,
                                    file.lastModified() + points.size()));
                        }
                    } catch (RuntimeException ignored) {
                        // Skip malformed points while keeping the rest of the GPX usable.
                    }
                }
                event = parser.next();
            }
        } catch (Exception error) {
            throw new IOException("ملف GPX غير صالح", error);
        } finally {
            input.close();
        }
        if (points.size() < 2) {
            throw new IOException("المسار لا يحتوي نقاطًا كافية");
        }
        return points;
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length() && result.length() < 40; i++) {
            char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            } else if (result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                result.append('_');
            }
        }
        return result.length() == 0 ? "مسار" : result.toString();
    }
}
