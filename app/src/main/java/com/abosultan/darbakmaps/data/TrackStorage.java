package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
            serializer.startTag(null, "gpx");
            serializer.attribute(null, "version", "1.1");
            serializer.attribute(null, "creator", "DarbakMaps");
            serializer.startTag(null, "trk");
            serializer.startTag(null, "name");
            serializer.text(displayName);
            serializer.endTag(null, "name");
            serializer.startTag(null, "trkseg");
            for (GeoPoint point : points) {
                serializer.startTag(null, "trkpt");
                serializer.attribute(null, "lat", Double.toString(point.latitude));
                serializer.attribute(null, "lon", Double.toString(point.longitude));
                serializer.startTag(null, "time");
                serializer.text(Long.toString(point.timeMillis));
                serializer.endTag(null, "time");
                serializer.endTag(null, "trkpt");
            }
            serializer.endTag(null, "trkseg");
            serializer.endTag(null, "trk");
            serializer.endTag(null, "gpx");
            serializer.endDocument();
            serializer.flush();
        } finally {
            output.close();
        }
        return file;
    }

    public static File[] list(Context context) {
        File directory = new File(context.getFilesDir(), "tracks");
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".gpx"));
        return files == null ? new File[0] : files;
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
