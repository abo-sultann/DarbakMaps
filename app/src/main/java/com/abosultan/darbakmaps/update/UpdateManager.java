package com.abosultan.darbakmaps.update;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateManager {
    public interface Callback {
        void onStatus(String message);

        void onUpdate(UpdateInfo update);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateManager() {
    }

    public static void check(Callback callback) {
        if (BuildConfig.UPDATE_MANIFEST_URL.isEmpty()) {
            callback.onStatus("خادم التحديث لم يُربط بعد");
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = secureUrl(BuildConfig.UPDATE_MANIFEST_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                reader.close();
                JSONObject manifest = new JSONObject(body.toString());
                int versionCode = manifest.getInt("versionCode");
                if (versionCode <= BuildConfig.VERSION_CODE) {
                    callback.onStatus("لديك أحدث نسخة من دربك");
                    return;
                }
                callback.onUpdate(new UpdateInfo(
                        versionCode,
                        manifest.getString("versionName"),
                        manifest.getString("apkUrl"),
                        manifest.getString("sha256").toLowerCase(Locale.US)
                ));
            } catch (Exception error) {
                callback.onStatus("تعذر التحقق من التحديث الآن");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static void downloadAndInstall(Activity activity, UpdateInfo update, Callback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            callback.onStatus("فعّل السماح بالتثبيت ثم أعد طلب التحديث");
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                File base = activity.getExternalCacheDir();
                if (base == null) {
                    base = activity.getCacheDir();
                }
                File directory = new File(base, "updates");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Update directory unavailable");
                }
                File apk = new File(directory, "DarbakMaps-" + update.versionName + ".apk");
                connection = (HttpURLConnection) secureUrl(update.apkUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                FileOutputStream output = new FileOutputStream(apk);
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
                output.close();
                input.close();

                if (!sha256(apk).equals(update.sha256)) {
                    apk.delete();
                    callback.onStatus("فشل التحقق من سلامة ملف التحديث");
                    return;
                }
                File finalApk = apk;
                activity.runOnUiThread(() -> install(activity, finalApk));
            } catch (Exception error) {
                callback.onStatus("تعذر تنزيل التحديث");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static void install(Activity activity, File apk) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static URL secureUrl(String value) throws Exception {
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("HTTPS required");
        }
        return url;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        StringBuilder value = new StringBuilder();
        for (byte item : digest.digest()) {
            value.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return value.toString();
    }

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String apkUrl;
        public final String sha256;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String sha256) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }
}
