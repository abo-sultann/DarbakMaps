package com.abosultan.darbakmaps.update;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.abosultan.darbakmaps.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Signed in-app update channel. No update is installed before SHA, package and signer verification. */
public final class UpdateManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;
    private static final long SPACE_MARGIN_BYTES = 24L * 1024L * 1024L;

    public interface Callback {
        void onStatus(String message);
        void onUpdate(UpdateInfo update);
    }

    private UpdateManager() {}

    public static void check(Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = secureUrl(BuildConfig.UPDATE_MANIFEST_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(true);
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (body.length() > 128 * 1024) throw new IllegalStateException("manifest too large");
                        body.append(line);
                    }
                }
                JSONObject manifest = new JSONObject(body.toString().trim());
                int versionCode = manifest.getInt("versionCode");
                if (versionCode <= BuildConfig.VERSION_CODE) {
                    callback.onStatus("لديك أحدث إصدار");
                    return;
                }
                String packageName = manifest.optString("packageName", BuildConfig.APPLICATION_ID);
                int minSdk = manifest.optInt("minSdk", 25);
                String apkUrl = manifest.optString("apkUrl", "");
                String sha256 = manifest.optString("sha256", "").toLowerCase(Locale.US);
                String versionName = manifest.optString("versionName", Integer.toString(versionCode));
                if (!BuildConfig.APPLICATION_ID.equals(packageName) || minSdk > Build.VERSION.SDK_INT
                        || apkUrl.isEmpty() || sha256.length() != 64) {
                    callback.onStatus("بيانات التحديث غير متوافقة");
                    return;
                }
                secureUrl(apkUrl);
                callback.onUpdate(new UpdateInfo(versionCode, versionName, apkUrl, sha256, packageName, minSdk));
            } catch (Exception error) {
                callback.onStatus("تعذر التحقق من التحديث الآن");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static void downloadAndInstall(Activity activity, UpdateInfo update, Callback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
                callback.onStatus("فعّل السماح بالتثبيت ثم أعد طلب التحديث");
            } catch (RuntimeException error) {
                callback.onStatus("تعذر فتح إعداد السماح بالتثبيت");
            }
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            File apk = null;
            try {
                File base = activity.getExternalCacheDir();
                if (base == null) base = activity.getCacheDir();
                File directory = new File(base, "updates");
                if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("cache unavailable");
                if (new StatFs(directory.getAbsolutePath()).getAvailableBytes() < MAX_APK_BYTES + SPACE_MARGIN_BYTES) {
                    callback.onStatus("المساحة غير كافية للتحديث");
                    return;
                }
                apk = new File(directory, "DarbakMaps-" + update.versionCode + ".apk");
                if (apk.exists()) apk.delete();
                connection = (HttpURLConnection) secureUrl(update.apkUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                int response = connection.getResponseCode();
                if (response < 200 || response >= 300) throw new IllegalStateException("HTTP " + response);
                long announced = connection.getContentLengthLong();
                if (announced > MAX_APK_BYTES) throw new IllegalStateException("APK too large");
                long total = 0L;
                try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_APK_BYTES) throw new IllegalStateException("APK too large");
                        output.write(buffer, 0, read);
                    }
                    output.getFD().sync();
                }
                String verificationError = verifyDownloadedApk(activity, apk, update);
                if (verificationError != null) {
                    apk.delete();
                    callback.onStatus(verificationError);
                    return;
                }
                File finalApk = apk;
                activity.runOnUiThread(() -> install(activity, finalApk, callback));
            } catch (Exception error) {
                if (apk != null) apk.delete();
                callback.onStatus("تعذر تنزيل أو التحقق من التحديث");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static String verifyDownloadedApk(Activity activity, File apk, UpdateInfo update) throws Exception {
        if (!apk.isFile() || apk.length() <= 0 || apk.length() > MAX_APK_BYTES) return "ملف التحديث غير صالح";
        if (!sha256(apk).equalsIgnoreCase(update.sha256)) return "فشل التحقق من سلامة التحديث";
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null || !update.packageName.equals(archive.packageName)) return "حزمة التحديث لا تطابق دربك";
        long archiveVersion = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        if (archiveVersion != update.versionCode || archiveVersion <= BuildConfig.VERSION_CODE) return "رقم إصدار التحديث غير صحيح";
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (!signatureDigests(installed).equals(signatureDigests(archive))) return "توقيع التحديث مختلف";
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signatureDigests(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                ? info.signingInfo.getApkContentsSigners() : info.signatures;
        Set<String> values = new HashSet<>();
        if (signatures != null) for (Signature signature : signatures) {
            values.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        }
        return values;
    }

    private static void install(Activity activity, File apk, Callback callback) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                callback.onStatus("لا يوجد مثبت APK متاح");
                return;
            }
            activity.startActivity(intent);
        } catch (RuntimeException error) {
            callback.onStatus("تعذر فتح مثبت التحديث");
        }
    }

    private static URL secureUrl(String value) throws Exception {
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("HTTPS required");
        return url;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) value.append(String.format(Locale.US, "%02x", item & 0xff));
        return value.toString();
    }

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String apkUrl;
        public final String sha256;
        public final String packageName;
        public final int minSdk;
        UpdateInfo(int versionCode, String versionName, String apkUrl, String sha256, String packageName, int minSdk) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.packageName = packageName;
            this.minSdk = minSdk;
        }
    }
}
