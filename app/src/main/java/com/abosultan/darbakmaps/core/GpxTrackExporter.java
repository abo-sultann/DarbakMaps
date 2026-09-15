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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Streams the rolling SQLite breadcrumbs to independent GPX files that are never pruned by the 1000 km ring. */
public final class GpxTrackExporter {
    private static final String DB_NAME = "darbak_tracks.db";
    private static final String GPX_NS = "http://www.topografix.com/GPX/1/1";
    private GpxTrackExporter() {}

    public static File exportAll(Context context) throws IOException {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (dbFile == null || !dbFile.isFile()) throw new IOException("لا يوجد مسار مسجل");
        File directory = trackDirectory(context);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File target = new File(directory, "DarbakTrack-" + stamp + ".gpx");
        File pending = new File(target.getAbsolutePath() + ".pending");
        SQLiteDatabase db = null; Cursor cursor = null; FileOutputStream output = null; boolean wrotePoint = false;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.query("track_points", new String[]{"segment_id", "lat", "lon", "time_ms"}, null, null, null, null, "segment_id ASC, time_ms ASC");
            output = new FileOutputStream(pending);
            XmlSerializer s = Xml.newSerializer(); s.setOutput(output, "UTF-8"); s.startDocument("UTF-8", true); s.setPrefix("", GPX_NS);
            s.startTag(GPX_NS, "gpx"); s.attribute(null, "version", "1.1"); s.attribute(null, "creator", "DarbakMaps"); s.startTag(GPX_NS, "trk");
            s.startTag(GPX_NS, "name"); s.text("مسار دربك " + stamp); s.endTag(GPX_NS, "name");
            long openSegment = Long.MIN_VALUE; SimpleDateFormat timeFormat = utcFormat();
            while (cursor.moveToNext()) {
                long segment = cursor.getLong(0); double lat = cursor.getDouble(1); double lon = cursor.getDouble(2); long time = cursor.getLong(3);
                if (lat < -90d || lat > 90d || lon < -180d || lon > 180d) continue;
                if (openSegment != segment) { if (openSegment != Long.MIN_VALUE) s.endTag(GPX_NS, "trkseg"); s.startTag(GPX_NS, "trkseg"); openSegment = segment; }
                s.startTag(GPX_NS, "trkpt"); s.attribute(null, "lat", Double.toString(lat)); s.attribute(null, "lon", Double.toString(lon));
                if (time > 0L) { s.startTag(GPX_NS, "time"); s.text(timeFormat.format(new Date(time))); s.endTag(GPX_NS, "time"); }
                s.endTag(GPX_NS, "trkpt"); wrotePoint = true;
            }
            if (openSegment != Long.MIN_VALUE) s.endTag(GPX_NS, "trkseg"); s.endTag(GPX_NS, "trk"); s.endTag(GPX_NS, "gpx"); s.endDocument(); s.flush(); output.getFD().sync();
        } catch (Exception error) { pending.delete(); throw new IOException("تعذر حفظ GPX", error); }
        finally { if (cursor != null) cursor.close(); if (db != null) db.close(); if (output != null) try { output.close(); } catch (IOException ignored) {} }
        if (!wrotePoint) { pending.delete(); throw new IOException("لا توجد نقاط مسار للحفظ"); }
        if (target.exists() || !pending.renameTo(target)) { pending.delete(); throw new IOException("تعذر تثبيت ملف GPX"); }
        return target;
    }

    public static File[] savedTracks(Context context) {
        File directory;
        try { directory = trackDirectory(context); } catch (IOException error) { return new File[0]; }
        File[] files = directory.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".gpx"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    private static File trackDirectory(Context context) throws IOException {
        File directory = context.getExternalFilesDir("tracks");
        if (directory == null) directory = new File(context.getFilesDir(), "tracks");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("تعذر إنشاء مجلد المسارات");
        return directory;
    }

    private static SimpleDateFormat utcFormat() { SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC")); return f; }
}
