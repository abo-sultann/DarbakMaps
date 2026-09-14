package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.StatFs;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports the owner-approved backup created by tools/migration/backup_old_debug.py. */
public final class LegacyMigration {
    private static final long MAX_TOTAL_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_PREF_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_TRACK_BYTES = 100L * 1024L * 1024L;
    private static final long SPACE_MARGIN_BYTES = 32L * 1024L * 1024L;
    private static final int BUFFER = 64 * 1024;
    private static final String EXPECTED_PACKAGE = "com.abosultan.darbakmaps.debug";

    private LegacyMigration() {}

    public static Result importBackup(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) throw new IOException("ملف الانتقال غير صالح");
        File stageRoot = new File(context.getCacheDir(), "migration-stage-" + UUID.randomUUID());
        File stagedTracks = new File(stageRoot, "tracks");
        if (!stagedTracks.mkdirs()) throw new IOException("تعذر تجهيز مساحة مؤقتة للاستعادة");

        Staged staged = null;
        try {
            staged = inspectAndStage(context, uri, stagedTracks);
            verifyFreeSpace(context, staged.totalBytes);
            validateStagedTracks(staged.trackFiles);
            return applyAtomically(context, staged);
        } finally {
            deleteTree(stageRoot);
        }
    }

    /** Reads and validates the entire archive before touching live app state. */
    private static Staged inspectAndStage(Context context, Uri uri, File stagedTracks) throws IOException {
        long total = 0L;
        boolean versionOk = false;
        boolean packageOk = false;
        List<StagedPreference> prefs = new ArrayList<>();
        List<StagedTrack> tracks = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("تعذر فتح ملف الانتقال");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = safeName(entry.getName());
                    if (name == null || !seen.add(name)) {
                        throw new IOException("ملف الانتقال يحتوي اسمًا غير آمن أو مكررًا");
                    }

                    if ("migration/version.txt".equals(name)) {
                        byte[] bytes = readEntry(zip, 128);
                        total += bytes.length;
                        versionOk = "1".equals(new String(bytes, "UTF-8").trim());
                    } else if ("migration/package.txt".equals(name)) {
                        byte[] bytes = readEntry(zip, 256);
                        total += bytes.length;
                        packageOk = EXPECTED_PACKAGE.equals(new String(bytes, "UTF-8").trim());
                    } else if (name.startsWith("shared_prefs/") && name.endsWith(".xml")) {
                        byte[] bytes = readEntry(zip, MAX_PREF_BYTES);
                        total += bytes.length;
                        String prefName = name.substring("shared_prefs/".length(), name.length() - 4);
                        if (prefName.isEmpty() || prefName.contains("/")) throw new IOException("اسم إعدادات غير صالح");
                        if (!isRuntimeState(prefName)) {
                            Map<String, Object> values = parsePreferences(bytes);
                            sanitizePreferences(prefName, values);
                            prefs.add(new StagedPreference(prefName, values));
                        }
                    } else if (name.startsWith("files/tracks/")) {
                        String relative = name.substring("files/tracks/".length());
                        if (relative.isEmpty() || relative.contains("/") || relative.contains("\\")) {
                            throw new IOException("اسم ملف مسار غير صالح");
                        }
                        File stagedFile = new File(stagedTracks, UUID.randomUUID() + "-" + relative);
                        long written = copyEntry(zip, stagedFile, MAX_TRACK_BYTES);
                        total += written;
                        tracks.add(new StagedTrack(relative, stagedFile));
                    }
                    if (total > MAX_TOTAL_BYTES) throw new IOException("حجم نسخة الانتقال كبير جدًا");
                }
            }
        } catch (SecurityException error) {
            throw new IOException("لا توجد صلاحية لقراءة ملف الانتقال", error);
        }

        if (!versionOk || !packageOk) throw new IOException("ملف الانتقال ليس نسخة Darbak Maps معتمدة");
        if (prefs.isEmpty()) throw new IOException("نسخة الانتقال لا تحتوي إعدادات قابلة للاستعادة");
        return new Staged(prefs, tracks, total);
    }

    private static Result applyAtomically(Context context, Staged staged) throws IOException {
        DataStoreLock.lock();
        File tracksDir = new File(context.getFilesDir(), "tracks");
        File backupDir = new File(context.getFilesDir(), ".migration-backup-" + UUID.randomUUID());
        Map<String, Map<String, ?>> originalPrefs = new HashMap<>();
        List<AppliedTrack> appliedTracks = new ArrayList<>();
        int restoredTracks = 0;
        try {
            if (!tracksDir.exists() && !tracksDir.mkdirs()) throw new IOException("تعذر إنشاء مجلد المسارات");
            if (!backupDir.mkdirs()) throw new IOException("تعذر تجهيز نسخة تراجع للاستعادة");

            for (StagedPreference pref : staged.preferences) {
                originalPrefs.put(pref.name,
                        new HashMap<>(context.getSharedPreferences(pref.name, Context.MODE_PRIVATE).getAll()));
            }

            for (StagedTrack stagedTrack : staged.trackFiles) {
                if ("active-track.csv".equals(stagedTrack.originalName)) {
                    File converted = uniqueTrackTarget(tracksDir, "مسار-مستعاد-قديم.gpx");
                    TrackJournal.export(stagedTrack.file, converted, "مسار مستعاد من نسخة قديمة");
                    appliedTracks.add(new AppliedTrack(converted, null));
                    restoredTracks++;
                    continue;
                }

                File target = uniqueTrackTarget(tracksDir, stagedTrack.originalName);
                File pending = new File(target.getAbsolutePath() + ".migration-pending");
                copyFile(stagedTrack.file, pending);
                if (!pending.renameTo(target)) {
                    pending.delete();
                    throw new IOException("تعذر تثبيت ملف مسار أثناء الاستعادة");
                }
                appliedTracks.add(new AppliedTrack(target, null));
                restoredTracks++;
            }

            for (StagedPreference pref : staged.preferences) {
                replacePreferences(context, pref.name, pref.values);
            }

            deleteTree(backupDir);
            int places = new PlaceRepository(context).all().size();
            int gpx = TrackStorage.list(context).length;
            return new Result(staged.preferences.size(), restoredTracks, places, gpx);
        } catch (Exception error) {
            IOException rollbackError = rollback(context, originalPrefs, appliedTracks);
            if (rollbackError != null) {
                IOException combined = new IOException("فشلت الاستعادة وتعذر التراجع الكامل؛ لا تضف بيانات جديدة قبل الفحص", error);
                combined.addSuppressed(rollbackError);
                throw combined;
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("فشلت الاستعادة وتمت إعادة البيانات الأصلية", error);
        } finally {
            deleteTree(backupDir);
            DataStoreLock.unlock();
        }
    }

    private static IOException rollback(Context context, Map<String, Map<String, ?>> prefs,
                                        List<AppliedTrack> appliedTracks) {
        IOException first = null;
        for (AppliedTrack track : appliedTracks) {
            if (track.target.exists() && !track.target.delete() && first == null) {
                first = new IOException("تعذر حذف ملف استعادة جزئي");
            }
        }
        for (Map.Entry<String, Map<String, ?>> entry : prefs.entrySet()) {
            try {
                replacePreferences(context, entry.getKey(), entry.getValue());
            } catch (IOException error) {
                if (first == null) first = error;
            }
        }
        return first;
    }

    private static void validateStagedTracks(List<StagedTrack> tracks) throws IOException {
        for (StagedTrack track : tracks) {
            String lower = track.originalName.toLowerCase();
            if ("active-track.csv".equals(track.originalName)) {
                validateJournal(track.file);
            } else if (lower.endsWith(".gpx")) {
                List<GeoPoint> points = TrackStorage.load(track.file);
                if (points.isEmpty()) throw new IOException("ملف GPX في النسخة الاحتياطية لا يحتوي نقاطًا صالحة");
            } else {
                throw new IOException("نوع ملف مسار غير معتمد في النسخة: " + track.originalName);
            }
        }
    }

    private static void validateJournal(File file) throws IOException {
        boolean found = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#segment")) continue;
                if (line.trim().isEmpty()) continue;
                if (TrackJournal.parse(line, false) == null) throw new IOException("سجل مسار قديم تالف");
                found = true;
            }
        }
        if (!found) throw new IOException("سجل المسار القديم فارغ");
    }

    private static void verifyFreeSpace(Context context, long stagedBytes) throws IOException {
        File target = context.getFilesDir();
        long free = new StatFs(target.getAbsolutePath()).getAvailableBytes();
        if (free < stagedBytes + SPACE_MARGIN_BYTES) {
            throw new IOException("المساحة غير كافية للاستعادة الآمنة؛ وفر مساحة إضافية ثم أعد المحاولة");
        }
    }

    private static boolean isRuntimeState(String prefName) {
        return "darbak_track_state".equals(prefName)
                || "darbak_track_runtime".equals(prefName)
                || "darbak_rolling_track".equals(prefName);
    }

    private static void sanitizePreferences(String prefName, Map<String, Object> values) {
        if ("darbak_map_ui".equals(prefName)) {
            values.remove("background_track");
            values.remove("automatic_rolling_track_v2");
        }
    }

    private static Map<String, Object> parsePreferences(byte[] xmlBytes) throws IOException {
        Map<String, Object> values = new HashMap<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new ByteArrayInputStream(xmlBytes), "UTF-8");
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                String tag = parser.getName();
                if ("map".equals(tag)) continue;
                String key = parser.getAttributeValue(null, "name");
                if (key == null || key.isEmpty()) continue;
                String value = parser.getAttributeValue(null, "value");
                if ("string".equals(tag)) {
                    values.put(key, parser.nextText());
                } else if ("int".equals(tag)) {
                    values.put(key, Integer.parseInt(value));
                } else if ("long".equals(tag)) {
                    values.put(key, Long.parseLong(value));
                } else if ("float".equals(tag)) {
                    values.put(key, Float.parseFloat(value));
                } else if ("boolean".equals(tag)) {
                    values.put(key, Boolean.parseBoolean(value));
                } else if ("set".equals(tag)) {
                    Set<String> set = new HashSet<>();
                    int depth = parser.getDepth();
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.getEventType() == XmlPullParser.START_TAG && "string".equals(parser.getName())) {
                            set.add(parser.nextText());
                        } else if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() == depth) {
                            break;
                        }
                    }
                    values.put(key, set);
                }
            }
            return values;
        } catch (Exception error) {
            throw new IOException("تعذر قراءة إعدادات النسخة القديمة", error);
        }
    }

    private static void replacePreferences(Context context, String name, Map<String, ?> values) throws IOException {
        SharedPreferences.Editor editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Set) {
                Set<String> strings = new HashSet<>();
                for (Object item : (Set<?>) value) if (item instanceof String) strings.add((String) item);
                editor.putStringSet(entry.getKey(), strings);
            }
        }
        if (!editor.commit()) throw new IOException("تعذر تثبيت إعدادات النسخة القديمة");
    }

    private static String safeName(String raw) {
        if (raw == null || raw.startsWith("/") || raw.startsWith("\\")) return null;
        String name = raw.replace('\\', '/');
        String[] parts = name.split("/");
        for (String part : parts) if ("..".equals(part) || (part.isEmpty() && !name.endsWith("/"))) return null;
        return name;
    }

    private static byte[] readEntry(ZipInputStream zip, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER];
        long total = 0L;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            total += read;
            if (total > max) throw new IOException("أحد ملفات الانتقال تجاوز الحد الآمن");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static long copyEntry(ZipInputStream zip, File target, long max) throws IOException {
        long total = 0L;
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = zip.read(buffer)) != -1) {
                total += read;
                if (total > max) throw new IOException("ملف مسار في النسخة الاحتياطية أكبر من الحد الآمن");
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        } catch (IOException error) {
            target.delete();
            throw error;
        }
        return total;
    }

    private static void copyFile(File source, File target) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.getFD().sync();
        } catch (IOException error) {
            target.delete();
            throw error;
        }
    }

    private static File uniqueTrackTarget(File directory, String original) {
        File target = new File(directory, original);
        if (!target.exists()) return target;
        int dot = original.lastIndexOf('.');
        String base = dot > 0 ? original.substring(0, dot) : original;
        String ext = dot > 0 ? original.substring(dot) : "";
        return new File(directory, base + "-مستعاد-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ext);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static final class Staged {
        final List<StagedPreference> preferences;
        final List<StagedTrack> trackFiles;
        final long totalBytes;

        Staged(List<StagedPreference> preferences, List<StagedTrack> trackFiles, long totalBytes) {
            this.preferences = preferences;
            this.trackFiles = trackFiles;
            this.totalBytes = totalBytes;
        }
    }

    private static final class StagedPreference {
        final String name;
        final Map<String, Object> values;

        StagedPreference(String name, Map<String, Object> values) {
            this.name = name;
            this.values = values;
        }
    }

    private static final class StagedTrack {
        final String originalName;
        final File file;

        StagedTrack(String originalName, File file) {
            this.originalName = originalName;
            this.file = file;
        }
    }

    private static final class AppliedTrack {
        final File target;
        final File originalBackup;

        AppliedTrack(File target, File originalBackup) {
            this.target = target;
            this.originalBackup = originalBackup;
        }
    }

    public static final class Result {
        public final int preferenceFiles;
        public final int trackFiles;
        public final int savedPlaces;
        public final int gpxTracks;

        Result(int preferenceFiles, int trackFiles, int savedPlaces, int gpxTracks) {
            this.preferenceFiles = preferenceFiles;
            this.trackFiles = trackFiles;
            this.savedPlaces = savedPlaces;
            this.gpxTracks = gpxTracks;
        }
    }
}
