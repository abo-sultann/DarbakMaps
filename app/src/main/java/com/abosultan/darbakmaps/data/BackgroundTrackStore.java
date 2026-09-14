package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.location.Location;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Incremental crash-safe storage for the currently recording track. */
public final class BackgroundTrackStore {
    private static final int MAX_DISPLAY_POINTS = 50_000;
    private static final String ACTIVE_FILE = "active-track.csv";

    private BackgroundTrackStore() {}

    public static synchronized void append(Context context, Location location) throws IOException {
        if (location == null) return;
        File file = activeFile(context);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create tracks directory");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(Double.toString(location.getLatitude()));
            writer.write(',');
            writer.write(Double.toString(location.getLongitude()));
            writer.write(',');
            writer.write(Long.toString(location.getTime()));
            writer.newLine();
        }
    }

    /** Lightweight restore path for drawing the active track on the map. */
    public static synchronized List<GeoPoint> loadActive(Context context) {
        return load(context, MAX_DISPLAY_POINTS);
    }

    /** Complete restore path used only when finalizing the GPX so no recorded points are lost. */
    public static synchronized List<GeoPoint> loadActiveComplete(Context context) {
        return load(context, 0);
    }

    private static List<GeoPoint> load(Context context, int maxPoints) {
        List<GeoPoint> points = new ArrayList<>();
        File file = activeFile(context);
        if (!file.isFile()) return points;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (maxPoints > 0 && points.size() >= maxPoints) break;
                String[] parts = line.split(",");
                if (parts.length != 3) continue;
                try {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    long time = Long.parseLong(parts[2]);
                    if (lat >= -90d && lat <= 90d && lon >= -180d && lon <= 180d) {
                        points.add(new GeoPoint(lat, lon, time));
                    }
                } catch (RuntimeException ignored) {
                    // Keep the rest of the active track usable if one line is malformed.
                }
            }
        } catch (IOException ignored) {
            // UI can continue even if the active track cannot be restored.
        }
        return points;
    }

    public static synchronized File finalizeActive(Context context) throws IOException {
        List<GeoPoint> points = loadActiveComplete(context);
        File active = activeFile(context);
        if (points.size() < 2) {
            if (active.exists()) active.delete();
            return null;
        }
        String name = "مسار تلقائي " + new SimpleDateFormat("dd-MM-yyyy HH-mm", Locale.US).format(new Date());
        File saved = TrackStorage.save(context, name, points);
        if (active.exists() && !active.delete()) {
            active.deleteOnExit();
        }
        return saved;
    }

    public static File activeFile(Context context) {
        return new File(new File(context.getFilesDir(), "tracks"), ACTIVE_FILE);
    }
}
