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

/** Downloads and atomically installs the approved Darbak Saudi offline map. */
public final class RecommendedMapDownloader {
    public interface Listener {
        void onProgress(int percent, String message);
        boolean isCancelled();
    }

    public static final long EXPECTED_BYTES = 189_923_374L;
    public static final String DISPLAY_SIZE = "نحو 181 م.ب";

    private static final String DOWNLOAD_URL =
            "https://github.com/abo-sultann/DarbakMaps/releases/download/darbak-saudi-map-v1/darbak-saudi.map";
    private static final String EXPECTED_SHA256 =
            "409d7ecaf2d6c610921cfadd42855fbdd3ce63ae08a00ff94965b18bb25fdd1c";
    private static final long FREE_SPACE_MARGIN_BYTES = 48L * 1024L * 1024L;
    private static final int BUFFER_BYTES = 64 * 1024;

    private RecommendedMapDownloader() {}

    /** Restores a previously-active map if the process died between backup and activation. */
    public static void recoverInterruptedInstall(Context context) throws IOException {
        File directory = MapStorage.mapDirectory(context);
        if (!directory.exists()) return;
        File target = MapStorage.activeMap(context);
        File backup = new File(directory, "saudi-active.map.backup");
        File installing = new File(directory, "saudi-active.map.installing");

        if (!backup.exists() && !installing.exists()) return;

        boolean targetApproved = isApprovedRecommendedMap(target);
        if (targetApproved) {
            // Only now is it safe to discard the previous map: replacement identity, length,
            // Mapsforge structure and SHA-256 have all been verified.
            if (installing.exists()) installing.delete();
            if (backup.exists() && !backup.delete()) {
                throw new IOException("الخريطة الجديدة سليمة لكن تعذر حذف نسخة الأمان القديمة");
            }
            return;
        }

        // A non-empty target is not proof of success. Preserve it only as a failed candidate and
        // restore the last known valid backup when available.
        if (backup.isFile() && isValidMap(backup)) {
            File failed = new File(directory, "saudi-active.map.failed-install");
            if (failed.exists()) failed.delete();
            if (target.exists() && !target.renameTo(failed)) {
                if (!target.delete()) throw new IOException("تعذر عزل الخريطة غير المكتملة");
            }
            if (!backup.renameTo(target)) {
                copyFile(backup, target);
                if (!isValidMap(target)) throw new IOException("تعذر استعادة الخريطة السابقة");
            }
            if (installing.exists()) installing.delete();
            if (failed.exists()) failed.delete();
            return;
        }

        // If no usable backup exists, an installing file may be a complete approved replacement.
        if (isApprovedRecommendedMap(installing)) {
            if (target.exists() && !target.delete()) throw new IOException("تعذر إزالة ملف خريطة غير صالح");
            if (!installing.renameTo(target)) {
                copyFile(installing, target);
                if (!isApprovedRecommendedMap(target)) throw new IOException("تعذر تفعيل الخريطة المستعادة");
                installing.delete();
            }
            if (backup.exists()) backup.delete();
            return;
        }

        throw new IOException("تعذر التحقق من استبدال خريطة سابق؛ احتفظ التطبيق بالملفات للفحص");
    }

    public static File download(Context context, Listener listener) throws IOException {
        File directory = MapStorage.mapDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("تعذر إنشاء مجلد الخرائط");
        }
        recoverInterruptedInstall(context);

        File pending = new File(directory, "darbak-saudi.map.download");
        if (pending.length() > EXPECTED_BYTES) pending.delete();
        long remainingBytes = Math.max(0L, EXPECTED_BYTES - pending.length());
        long availableBytes = new StatFs(directory.getAbsolutePath()).getAvailableBytes();
        if (availableBytes < remainingBytes + FREE_SPACE_MARGIN_BYTES) {
            throw new IOException("المساحة غير كافية؛ وفر قرابة 230 م.ب ثم أعد المحاولة");
        }

        if (pending.length() < EXPECTED_BYTES) downloadRemaining(pending, listener);
        if (listener.isCancelled()) throw new CancelledException();
        if (pending.length() != EXPECTED_BYTES) {
            throw new IOException("تنزيل خريطة دربك السعودية غير مكتمل ويمكن استكماله لاحقًا");
        }

        listener.onProgress(99, "جارٍ التحقق من سلامة خريطة دربك…");
        if (!isApprovedRecommendedMap(pending)) {
            pending.delete();
            throw new IOException("فشل التحقق من خريطة دربك؛ لم تُستبدل الخريطة الحالية");
        }

        File target = MapStorage.activeMap(context);
        File backup = new File(directory, "saudi-active.map.backup");
        File installing = new File(directory, "saudi-active.map.installing");
        boolean hadTarget = target.isFile() && isValidMap(target);
        if (backup.exists()) {
            throw new IOException("توجد عملية استبدال سابقة غير مغلقة؛ أعد فتح مدير الخرائط للاستعادة أولًا");
        }
        if (installing.exists() && !installing.delete()) {
            throw new IOException("تعذر تجهيز ملف تثبيت الخريطة");
        }
        if (hadTarget && !target.renameTo(backup)) {
            throw new IOException("تعذر تجهيز الخريطة الحالية للاستبدال");
        }

        boolean activated = false;
        try {
            if (pending.renameTo(target)) {
                activated = true;
            } else {
                copyFile(pending, installing);
                if (!isApprovedRecommendedMap(installing)) {
                    throw new IOException("نسخة تثبيت الخريطة غير مكتملة");
                }
                if (!installing.renameTo(target)) {
                    throw new IOException("تعذر تفعيل ملف الخريطة الجديد");
                }
                activated = true;
            }
            if (!isApprovedRecommendedMap(target)) {
                throw new IOException("فشل التحقق بعد تفعيل الخريطة الجديدة");
            }
        } catch (IOException | RuntimeException error) {
            if (target.exists()) target.delete();
            if (hadTarget && backup.isFile()) {
                if (!backup.renameTo(target)) {
                    try {
                        copyFile(backup, target);
                        if (!isValidMap(target)) throw new IOException("الخريطة المستعادة غير صالحة");
                    } catch (IOException restoreError) {
                        IOException combined = new IOException("فشل تفعيل الخريطة وتعذر استعادة السابقة", error);
                        combined.addSuppressed(restoreError);
                        throw combined;
                    }
                }
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("تعذر تفعيل الخريطة؛ تمت محاولة استعادة السابقة", error);
        } finally {
            if (installing.exists() && activated) installing.delete();
        }

        if (!activated) throw new IOException("تعذر تفعيل الخريطة");
        pending.delete();
        // Delete backup only after the active target has passed full validation.
        if (backup.exists() && !backup.delete()) {
            throw new IOException("تم تثبيت الخريطة لكن بقيت نسخة الأمان؛ يمكن حذفها بعد التحقق");
        }
        listener.onProgress(100, "تم تجهيز خريطة دربك السعودية للعمل أوفلاين");
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
        if (existing > 0L) connection.setRequestProperty("Range", "bytes=" + existing + "-");

        try {
            int response = connection.getResponseCode();
            boolean append = existing > 0L && response == HttpURLConnection.HTTP_PARTIAL;
            if (response != HttpURLConnection.HTTP_OK && response != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("تعذر تنزيل الخريطة (HTTP " + response + ")");
            }
            if (!append) existing = 0L;

            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream(), BUFFER_BYTES);
                 FileOutputStream output = new FileOutputStream(pending, append)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                long downloaded = existing;
                int lastProgress = -1;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (listener.isCancelled()) throw new CancelledException();
                    output.write(buffer, 0, read);
                    downloaded += read;
                    if (downloaded > EXPECTED_BYTES) {
                        throw new IOException("حجم تنزيل الخريطة تجاوز الحجم المعتمد");
                    }
                    int progress = (int) Math.min(98L, (downloaded * 100L) / EXPECTED_BYTES);
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        listener.onProgress(progress, "تنزيل خريطة دربك السعودية " + progress + "%");
                    }
                }
                output.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean isApprovedRecommendedMap(File file) {
        if (file == null || !file.isFile() || file.length() != EXPECTED_BYTES) return false;
        try {
            validateMap(file);
            return EXPECTED_SHA256.equalsIgnoreCase(sha256(file));
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean isValidMap(File file) {
        if (file == null || !file.isFile() || file.length() <= 0L) return false;
        try {
            validateMap(file);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private static void validateMap(File file) throws IOException {
        MapFile validator = null;
        try {
            validator = new MapFile(file, "ar");
            if (validator.boundingBox() == null) throw new IOException("ملف الخريطة لا يحتوي حدودًا صالحة");
        } catch (RuntimeException error) {
            throw new IOException("ملف الخريطة غير صالح", error);
        } finally {
            if (validator != null) validator.close();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.getFD().sync();
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest.digest()) value.append(String.format(Locale.US, "%02x", item & 0xff));
            return value.toString();
        } catch (Exception error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("تعذر التحقق من الخريطة", error);
        }
    }

    public static final class CancelledException extends IOException {
        CancelledException() {
            super("تم إيقاف التنزيل ويمكن استكماله لاحقًا");
        }
    }
}
