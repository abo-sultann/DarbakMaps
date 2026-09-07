package com.abosultan.darbakmaps.map;

import android.content.Context;
import android.os.StatFs;

import com.abosultan.darbakmaps.BuildConfig;

import org.mapsforge.map.reader.MapFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Downloads and atomically installs the official Mapsforge GCC vector map. */
public final class RecommendedMapDownloader {
    public interface Listener {
        void onProgress(int percent, String message);

        boolean isCancelled();
    }

    public static final long EXPECTED_BYTES = 322_177_740L;
    public static final String DISPLAY_SIZE = "نحو 307 م.ب";

    private static final String DOWNLOAD_URL =
            "https://download.mapsforge.org/maps/v5/asia/gcc-states.map";
    private static final String EXPECTED_SHA256 =
            "635f330be605b8a77a7df6d9d4ddddf7935c8f800f98d046e323eccc96ea2ff4";
    private static final long FREE_SPACE_MARGIN_BYTES = 48L * 1024L * 1024L;
    private static final int BUFFER_BYTES = 64 * 1024;

    private RecommendedMapDownloader() {
    }

    public static File download(Context context, Listener listener) throws IOException {
        File directory = MapStorage.mapDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("تعذر إنشاء مجلد الخرائط");
        }

        File pending = new File(directory, "gcc-states.map.download");
        if (pending.length() > EXPECTED_BYTES) {
            pending.delete();
        }
        long remainingBytes = Math.max(0L, EXPECTED_BYTES - pending.length());
        long availableBytes = new StatFs(directory.getAbsolutePath()).getAvailableBytes();
        if (availableBytes < remainingBytes + FREE_SPACE_MARGIN_BYTES) {
            throw new IOException("المساحة غير كافية؛ وفر قرابة 360 م.ب ثم أعد المحاولة");
        }

        if (pending.length() < EXPECTED_BYTES) {
            downloadRemaining(pending, listener);
        }
        if (listener.isCancelled()) {
            throw new CancelledException();
        }
        if (pending.length() != EXPECTED_BYTES) {
            throw new IOException("تنزيل الخريطة غير مكتمل ويمكن استكماله لاحقًا");
        }

        listener.onProgress(99, "جارٍ التحقق من سلامة الخريطة…");
        if (!EXPECTED_SHA256.equalsIgnoreCase(sha256(pending))) {
            pending.delete();
            throw new IOException("فشل التحقق من ملف الخريطة؛ أعد التنزيل");
        }
        validateMap(pending);

        File target = MapStorage.activeMap(context);
        File backup = new File(directory, "saudi-active.map.backup");
        backup.delete();
        if (target.exists() && !target.renameTo(backup)) {
            throw new IOException("تعذر تجهيز الخريطة الحالية للاستبدال");
        }

        boolean activated = pending.renameTo(target);
        if (!activated) {
            File installing = new File(directory, "saudi-active.map.installing");
            installing.delete();
            try {
                copyFile(pending, installing);
                activated = installing.renameTo(target);
            } finally {
                installing.delete();
            }
        }

        if (!activated) {
            if (backup.exists()) {
                backup.renameTo(target);
            }
            throw new IOException("تعذر تفعيل الخريطة بعد تنزيلها");
        }
        pending.delete();
        backup.delete();
        listener.onProgress(100, "تم تجهيز خريطة الخليج للعمل أوفلاين");
        return target;
    }

    private static void downloadRemaining(File pending, Listener listener) throws IOException {
        long existing = pending.length();
        HttpURLConnection connection = (HttpURLConnection) new URL(DOWNLOAD_URL).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", "DarbakMaps/" + BuildConfig.VERSION_NAME);
        if (existing > 0L) {
            connection.setRequestProperty("Range", "bytes=" + existing + "-");
        }

        try {
            int response = connection.getResponseCode();
            boolean append = existing > 0L && response == HttpURLConnection.HTTP_PARTIAL;
            if (response != HttpURLConnection.HTTP_OK && response != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("تعذر تنزيل الخريطة (HTTP " + response + ")");
            }
            if (!append) {
                existing = 0L;
            }

            BufferedInputStream input = new BufferedInputStream(connection.getInputStream(), BUFFER_BYTES);
            FileOutputStream output = new FileOutputStream(pending, append);
            try {
                byte[] buffer = new byte[BUFFER_BYTES];
                long downloaded = existing;
                int lastProgress = -1;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (listener.isCancelled()) {
                        throw new CancelledException();
                    }
                    output.write(buffer, 0, read);
                    downloaded += read;
                    int progress = (int) Math.min(98L, (downloaded * 100L) / EXPECTED_BYTES);
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        listener.onProgress(progress, "تنزيل خريطة الخليج " + progress + "%");
                    }
                }
                output.getFD().sync();
            } finally {
                output.close();
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void validateMap(File file) throws IOException {
        MapFile validator = null;
        try {
            validator = new MapFile(file, "ar");
            if (validator.boundingBox() == null) {
                throw new IOException("ملف الخريطة لا يحتوي حدودًا صالحة");
            }
        } catch (RuntimeException error) {
            throw new IOException("ملف الخريطة غير صالح", error);
        } finally {
            if (validator != null) {
                validator.close();
            }
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } finally {
            output.close();
            input.close();
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream input = new FileInputStream(file);
            try {
                byte[] buffer = new byte[BUFFER_BYTES];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest.digest()) {
                value.append(String.format(Locale.US, "%02x", item & 0xff));
            }
            return value.toString();
        } catch (Exception error) {
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("تعذر التحقق من الخريطة", error);
        }
    }

    public static final class CancelledException extends IOException {
        CancelledException() {
            super("تم إيقاف التنزيل ويمكن استكماله لاحقًا");
        }
    }
}
