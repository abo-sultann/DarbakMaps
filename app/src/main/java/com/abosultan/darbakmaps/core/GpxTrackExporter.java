package com.abosultan.darbakmaps.core;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Streams the recorded SQLite breadcrumbs to GPX without loading the full track into RAM. */
public final class GpxTrackExporter {
    private static final String DB_NAME = "darbak_tracks.db";
    private static final String GPX_NS = "http://www.topografix.com/GPX/1/1";

    private GpxTrackExporter() {}

    public static File exportAll(Context context) throws IOException {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (dbFile == null || !dbFile.isFile()) throw new IOException("لا يوجد مسار مسجل");

        File directory = context.getExternalFilesDir("tracks");
        if (directory == null) directory = new File(context.getFilesDir(), "tracks");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("تعذر إنشاء مجلد المسارات");

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File target = new File(directory, "DarbakTrack-" + stamp + ".gpx");
        File pending = new File(target.getAbsolutePath() + ".pending");

        SQLiteDatabase db = null;
        Cursor cursor = null;
        FileOutputStream output = null;
        boolean wrotePoint = false;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.query("track_points",
                    new String[]{"segment_id", "lat", "lon", "time_ms"},
                    null, null, null, null, "segment_id ASC, time_ms ASC");

            output = new FileOutputStream(pending);
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(output, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializer.setPrefix("", GPX_NS);
            serializer.startTag(GPX_NS, "gpx");
            serializer.attribute(null, "version", "1.1");
            serializer.attribute(null, "creator", "DarbakMaps");
            serializer.startTag(GPX_NS, "trk");
            serializer.startTag(GPX_NS, "name");
            serializer.text("مسار دربك " + stamp);
            serializer.endTag(GPX_NS, "name");

            long openSegment = Long.MIN_VALUE;
            SimpleDateFormat timeFormat = utcFormat();
            while (cursor.moveToNext()) {
                long segment = cursor.getLong(0);
                double lat = cursor.getDouble(1);
                double lon = cursor.getDouble(2);
                long time = cursor.getLong(3);
                if (lat < -90d || lat > 90d || lon < -180d || lon > 180d) continue;

                if (openSegment != segment) {
                    if (openSegment != Long.MIN_VALUE) serializer.endTag(GPX_NS, "trkseg");
                    serializer.startTag(GPX_NS, "trkseg");
                    openSegment = segment;
                }

                serializer.startTag(GPX_NS, "trkpt");
                serializer.attribute(null, "lat", Double.toString(lat));
                serializer.attribute(null, "lon", Double.toString(lon));
                if (time > 0L) {
                    serializer.startTag(GPX_NS, "time");
                    serializer.text(timeFormat.format(new Date(time)));
                    serializer.endTag(GPX_NS, "time");
                }
                serializer.endTag(GPX_NS, "trkpt");
                wrotePoint = true;
            }

            if (openSegment != Long.MIN_VALUE) serializer.endTag(GPX_NS, "trkseg");
            serializer.endTag(GPX_NS, "trk");
            serializer.endTag(GPX_NS, "gpx");
            serializer.endDocument();
            serializer.flush();
            output.getFD().sync();
        } catch (Exception error) {
            pending.delete();
            throw new IOException("تعذر حفظ GPX", error);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
            if (output != null) try { output.close(); } catch (IOException ignored) {}
        }

        if (!wrotePoint) {
            pending.delete();
            throw new IOException("لا توجد نقاط مسار للحفظ");
        }
        if (target.exists() || !pending.renameTo(target)) {
            pending.delete();
            throw new IOException("تعذر تثبيت ملف GPX");
        }
        return target;
    }

    private static SimpleDateFormat utcFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }
}
