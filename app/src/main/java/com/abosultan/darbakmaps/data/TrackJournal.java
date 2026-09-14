package com.abosultan.darbakmaps.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Append-only source of truth for the automatic track.
 *
 * Display limits never limit exported data. Rolling retention is applied only after a new point
 * has been fsync'ed, using pending/backup files so an interrupted trim can be recovered safely.
 */
public final class TrackJournal {
    private static final int BUFFER = 64 * 1024;

    private TrackJournal() {}

    public static void append(File file, double lat, double lon, long time, boolean newSegment) throws IOException {
        recoverInterruptedTrim(file);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("تعذر إنشاء مجلد المسارات");
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            String row = (newSegment ? "#segment\n" : "") + lat + "," + lon + "," + time + "\n";
            out.write(row.getBytes("UTF-8"));
            out.getFD().sync();
        }
    }

    public static GeoPoint parse(String line, boolean segmentStart) {
        try {
            String[] v = line.split(",");
            if (v.length != 3) return null;
            double lat = Double.parseDouble(v[0]);
            double lon = Double.parseDouble(v[1]);
            long t = Long.parseLong(v[2]);
            if (!Double.isFinite(lat) || !Double.isFinite(lon)
                    || lat < -90 || lat > 90 || lon < -180 || lon > 180 || t < 0) return null;
            return new GeoPoint(lat, lon, t, segmentStart);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static List<GeoPoint> preview(File file, int limit) throws IOException {
        recoverInterruptedTrim(file);
        List<GeoPoint> points = new ArrayList<>();
        if (!file.isFile()) return points;
        long count = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String s;
            while ((s = in.readLine()) != null) if (parse(s, false) != null) count++;
        }
        long step = Math.max(1, (count + Math.max(2, limit) - 1) / Math.max(2, limit));
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String s;
            long index = 0;
            boolean segment = true;
            while ((s = in.readLine()) != null) {
                if (s.startsWith("#segment")) {
                    segment = true;
                    continue;
                }
                GeoPoint p = parse(s, segment);
                if (p == null) continue;
                if (index % step == 0 || index == count - 1) {
                    points.add(new GeoPoint(p.latitude, p.longitude, p.timeMillis, segment));
                    segment = false;
                }
                index++;
            }
        }
        return points;
    }

    /** Returns connected-track distance only; segment gaps never contribute distance. */
    public static double distanceMeters(File file) throws IOException {
        recoverInterruptedTrim(file);
        if (!file.isFile()) return 0d;
        return scanDistance(file).distanceMeters;
    }

    /**
     * Keeps the newest connected distance at or below maxMeters (within one retained edge).
     * The source is replaced only after the pending file is fsync'ed and validated.
     */
    public static double trimToDistance(File file, double maxMeters) throws IOException {
        if (maxMeters <= 0d) throw new IllegalArgumentException("maxMeters");
        recoverInterruptedTrim(file);
        if (!file.isFile()) return 0d;

        Scan total = scanDistance(file);
        if (total.validPoints < 2 || total.distanceMeters <= maxMeters) return total.distanceMeters;

        double removeTarget = total.distanceMeters - maxMeters;
        long skipPoints = findSkipPointCount(file, removeTarget);
        if (skipPoints <= 0L) return total.distanceMeters;

        File pending = pendingFile(file);
        File backup = backupFile(file);
        if (pending.exists() && !pending.delete()) throw new IOException("تعذر تجهيز ملف تقليم المسار");

        writeRetained(file, pending, skipPoints);
        Scan retained = scanDistance(pending);
        if (retained.validPoints == 0) {
            pending.delete();
            throw new IOException("فشل تقليم المسار؛ تم الاحتفاظ بالأصل");
        }
        // Allow one GPS edge above the exact boundary; next trim will remove it.
        if (retained.distanceMeters > maxMeters + 1500d) {
            pending.delete();
            throw new IOException("فشل التحقق من حد المسافة؛ تم الاحتفاظ بالأصل");
        }

        if (backup.exists() && !backup.delete()) {
            pending.delete();
            throw new IOException("تعذر تجهيز نسخة أمان للمسار");
        }
        if (!file.renameTo(backup)) {
            pending.delete();
            throw new IOException("تعذر حماية المسار قبل التقليم");
        }

        boolean activated = false;
        try {
            if (!pending.renameTo(file)) throw new IOException("تعذر تفعيل المسار المقلم");
            Scan installed = scanDistance(file);
            if (installed.validPoints == 0 || installed.distanceMeters > maxMeters + 1500d) {
                throw new IOException("فشل التحقق من المسار بعد التقليم");
            }
            activated = true;
            return installed.distanceMeters;
        } finally {
            if (!activated) {
                if (file.exists()) file.delete();
                if (backup.isFile()) backup.renameTo(file);
            } else if (backup.exists()) {
                backup.delete();
            }
            if (pending.exists()) pending.delete();
        }
    }

    /** Repairs a power-loss window around trim swap without discarding the last known good copy. */
    public static void recoverInterruptedTrim(File file) throws IOException {
        File pending = pendingFile(file);
        File backup = backupFile(file);
        if (!pending.exists() && !backup.exists()) return;

        if (isValidJournal(file)) {
            if (pending.exists()) pending.delete();
            if (backup.exists()) backup.delete();
            return;
        }

        if (isValidJournal(pending)) {
            if (file.exists()) file.delete();
            if (!pending.renameTo(file)) throw new IOException("تعذر استعادة المسار المقلم");
            if (!isValidJournal(file)) throw new IOException("المسار المستعاد غير صالح");
            if (backup.exists()) backup.delete();
            return;
        }

        if (isValidJournal(backup)) {
            if (file.exists()) file.delete();
            if (!backup.renameTo(file)) throw new IOException("تعذر استعادة نسخة أمان المسار");
            if (pending.exists()) pending.delete();
            return;
        }

        throw new IOException("تعذر استعادة سجل المسار بعد انقطاع سابق");
    }

    public static long export(File source, File target, String name) throws IOException {
        recoverInterruptedTrim(source);
        File pending = new File(target.getAbsolutePath() + ".pending");
        long count = 0;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        try (FileOutputStream bytes = new FileOutputStream(pending);
             Writer out = new BufferedWriter(new OutputStreamWriter(bytes, "UTF-8"));
             BufferedReader in = new BufferedReader(new FileReader(source))) {
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"DarbakMaps\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>" + escape(name) + "</name><trkseg>");
            String line;
            boolean segment = false;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#segment")) {
                    segment = count > 0;
                    continue;
                }
                GeoPoint p = parse(line, false);
                if (p == null) continue;
                if (segment) {
                    out.write("</trkseg><trkseg>");
                    segment = false;
                }
                out.write("<trkpt lat=\"" + p.latitude + "\" lon=\"" + p.longitude + "\"><time>" + fmt.format(new Date(p.timeMillis)) + "</time></trkpt>");
                count++;
            }
            out.write("</trkseg></trk></gpx>");
            out.flush();
            bytes.getFD().sync();
        } catch (IOException e) {
            pending.delete();
            throw e;
        }
        if (count == 0) {
            pending.delete();
            throw new IOException("لا توجد نقاط صالحة لحفظ المسار؛ تم الاحتفاظ بالملف الأصلي");
        }
        if (target.exists() || !pending.renameTo(target)) {
            pending.delete();
            throw new IOException("تعذر حفظ المسار؛ تم الاحتفاظ بالتسجيل الأصلي");
        }
        return count;
    }

    private static long findSkipPointCount(File file, double removeTarget) throws IOException {
        long validIndex = 0L;
        double removed = 0d;
        GeoPoint previous = null;
        boolean segmentStart = true;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#segment")) {
                    segmentStart = true;
                    previous = null;
                    continue;
                }
                GeoPoint point = parse(line, segmentStart);
                if (point == null) continue;
                if (previous != null && !segmentStart) removed += distance(previous, point);
                validIndex++;
                previous = point;
                segmentStart = false;
                if (removed >= removeTarget) return validIndex;
            }
        }
        return 0L;
    }

    private static void writeRetained(File source, File target, long skipPoints) throws IOException {
        try (FileOutputStream bytes = new FileOutputStream(target);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(bytes, "UTF-8"));
             BufferedReader in = new BufferedReader(new FileReader(source))) {
            String line;
            long validIndex = 0L;
            boolean started = false;
            boolean pendingSegment = true;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#segment")) {
                    pendingSegment = true;
                    continue;
                }
                GeoPoint p = parse(line, pendingSegment);
                if (p == null) continue;
                validIndex++;
                if (validIndex <= skipPoints) continue;
                if (!started || pendingSegment) out.write("#segment\n");
                out.write(p.latitude + "," + p.longitude + "," + p.timeMillis + "\n");
                started = true;
                pendingSegment = false;
            }
            out.flush();
            bytes.getFD().sync();
        } catch (IOException error) {
            target.delete();
            throw error;
        }
    }

    private static Scan scanDistance(File file) throws IOException {
        double total = 0d;
        long count = 0L;
        GeoPoint previous = null;
        boolean segmentStart = true;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#segment")) {
                    segmentStart = true;
                    previous = null;
                    continue;
                }
                GeoPoint point = parse(line, segmentStart);
                if (point == null) continue;
                if (previous != null && !segmentStart) total += distance(previous, point);
                count++;
                previous = point;
                segmentStart = false;
            }
        }
        return new Scan(total, count);
    }

    private static boolean isValidJournal(File file) {
        if (file == null || !file.isFile() || file.length() == 0L) return false;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            boolean found = false;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#segment")) continue;
                if (line.trim().isEmpty()) continue;
                if (parse(line, false) == null) return false;
                found = true;
            }
            return found;
        } catch (IOException error) {
            return false;
        }
    }

    private static double distance(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.latitude - a.latitude);
        double dLon = Math.toRadians(b.longitude - a.longitude);
        double h = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        return 6371000d * 2d * Math.asin(Math.sqrt(Math.max(0d, Math.min(1d, h))));
    }

    private static File pendingFile(File file) {
        return new File(file.getAbsolutePath() + ".trim-pending");
    }

    private static File backupFile(File file) {
        return new File(file.getAbsolutePath() + ".trim-backup");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final class Scan {
        final double distanceMeters;
        final long validPoints;

        Scan(double distanceMeters, long validPoints) {
            this.distanceMeters = distanceMeters;
            this.validPoints = validPoints;
        }
    }
}
