package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports the owner-approved backup created by tools/migration/backup_old_debug.py. */
public final class LegacyMigration {
    private static final long MAX_TOTAL_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_PREF_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_TRACK_BYTES = 100L * 1024L * 1024L;
    private static final int BUFFER = 64 * 1024;

    private LegacyMigration() {}

    public static Result importBackup(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) throw new IOException("ملف الانتقال غير صالح");
        long total = 0L;
        int prefFiles = 0;
        int trackFiles = 0;
        boolean versionOk = false;
        boolean packageOk = false;
        File tracksDir = new File(context.getFilesDir(), "tracks");
        if (!tracksDir.exists() && !tracksDir.mkdirs()) throw new IOException("تعذر إنشاء مجلد المسارات");

        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("تعذر فتح ملف الانتقال");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = safeName(entry.getName());
                    if (name == null) throw new IOException("ملف الانتقال يحتوي مسارًا غير آمن");

                    if ("migration/version.txt".equals(name)) {
                        byte[] bytes = readEntry(zip, 128);
                        total += bytes.length;
                        versionOk = "1".equals(new String(bytes, "UTF-8").trim());
                    } else if ("migration/package.txt".equals(name)) {
                        byte[] bytes = readEntry(zip, 256);
                        total += bytes.length;
                        packageOk = "com.abosultan.darbakmaps.debug".equals(new String(bytes, "UTF-8").trim());
                    } else if (name.startsWith("shared_prefs/") && name.endsWith(".xml")) {
                        byte[] bytes = readEntry(zip, MAX_PREF_BYTES);
                        total += bytes.length;
                        if (total > MAX_TOTAL_BYTES) throw new IOException("حجم نسخة الانتقال كبير جدًا");
                        String file = name.substring("shared_prefs/".length(), name.length() - 4);
                        if (file.isEmpty() || file.contains("/")) throw new IOException("اسم إعدادات غير صالح");
                        importPreferences(context, file, bytes);
                        prefFiles++;
                    } else if (name.startsWith("files/tracks/")) {
                        String relative = name.substring("files/tracks/".length());
                        if (relative.isEmpty() || relative.contains("/") || relative.contains("\\")) {
                            throw new IOException("اسم ملف مسار غير صالح");
                        }
                        File target = new File(tracksDir, relative);
                        String root = tracksDir.getCanonicalPath() + File.separator;
                        if (!target.getCanonicalPath().startsWith(root)) throw new IOException("مسار استعادة غير آمن");
                        long written = copyEntry(zip, target, MAX_TRACK_BYTES);
                        total += written;
                        if (total > MAX_TOTAL_BYTES) {
                            target.delete();
                            throw new IOException("حجم نسخة الانتقال كبير جدًا");
                        }
                        trackFiles++;
                    }
                }
            }
        } catch (SecurityException error) {
            throw new IOException("لا توجد صلاحية لقراءة ملف الانتقال", error);
        }

        if (!versionOk || !packageOk) throw new IOException("ملف الانتقال ليس نسخة Darbak Maps معتمدة");
        if (prefFiles == 0) throw new IOException("نسخة الانتقال لا تحتوي الإعدادات والمحفوظات");
        int places = new PlaceRepository(context).all().size();
        int gpx = TrackStorage.list(context).length;
        return new Result(prefFiles, trackFiles, places, gpx);
    }

    private static String safeName(String raw) {
        if (raw == null || raw.startsWith("/") || raw.startsWith("\\")) return null;
        String name = raw.replace('\\', '/');
        String[] parts = name.split("/");
        for (String part : parts) if ("..".equals(part) || part.isEmpty() && !name.endsWith("/")) return null;
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
        File pending = new File(target.getAbsolutePath() + ".migration");
        if (pending.exists()) pending.delete();
        long total = 0L;
        try (FileOutputStream out = new FileOutputStream(pending)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = zip.read(buffer)) != -1) {
                total += read;
                if (total > max) throw new IOException("ملف مسار في النسخة الاحتياطية أكبر من الحد الآمن");
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        } catch (IOException error) {
            pending.delete();
            throw error;
        }
        if (target.exists() && !target.delete()) {
            pending.delete();
            throw new IOException("تعذر استبدال ملف مسار أثناء الاستعادة");
        }
        if (!pending.renameTo(target)) {
            pending.delete();
            throw new IOException("تعذر تثبيت ملف مسار أثناء الاستعادة");
        }
        return total;
    }

    private static void importPreferences(Context context, String prefsName, byte[] xmlBytes) throws IOException {
        SharedPreferences.Editor editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit();
        editor.clear();
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
                    editor.putString(key, parser.nextText());
                } else if ("int".equals(tag)) {
                    editor.putInt(key, Integer.parseInt(value));
                } else if ("long".equals(tag)) {
                    editor.putLong(key, Long.parseLong(value));
                } else if ("float".equals(tag)) {
                    editor.putFloat(key, Float.parseFloat(value));
                } else if ("boolean".equals(tag)) {
                    editor.putBoolean(key, Boolean.parseBoolean(value));
                } else if ("set".equals(tag)) {
                    Set<String> values = new HashSet<>();
                    int depth = parser.getDepth();
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.getEventType() == XmlPullParser.START_TAG && "string".equals(parser.getName())) {
                            values.add(parser.nextText());
                        } else if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() == depth) {
                            break;
                        }
                    }
                    editor.putStringSet(key, values);
                }
            }
        } catch (Exception error) {
            throw new IOException("تعذر قراءة إعدادات النسخة القديمة", error);
        }
        if (!editor.commit()) throw new IOException("تعذر تثبيت إعدادات النسخة القديمة");
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
