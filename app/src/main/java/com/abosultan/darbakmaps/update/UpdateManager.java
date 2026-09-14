package com.abosultan.darbakmaps.update;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();

                JSONObject manifest = new JSONObject(body.toString().trim());
                int versionCode = manifest.getInt("versionCode");
                if (versionCode <= BuildConfig.VERSION_CODE) {
                    callback.onStatus("لديك أحدث نسخة من دربك");
                    return;
                }

                String packageName = manifest.optString("packageName", BuildConfig.APPLICATION_ID);
                int minSdk = manifest.optInt("minSdk", 24);
                String apkUrl = manifest.optString("apkUrl", "");
                String expectedSha = manifest.optString("sha256", "").toLowerCase(Locale.US);
                if (!BuildConfig.APPLICATION_ID.equals(packageName)
                        || minSdk > Build.VERSION.SDK_INT
                        || apkUrl.isEmpty()
                        || expectedSha.length() != 64) {
                    callback.onStatus("بيانات التحديث غير مكتملة أو غير متوافقة");
                    return;
                }
                secureUrl(apkUrl);

                callback.onUpdate(new UpdateInfo(
                        versionCode,
                        manifest.getString("versionName"),
                        apkUrl,
                        expectedSha,
                        packageName,
                        minSdk
                ));
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
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            callback.onStatus("فعّل السماح بالتثبيت ثم أعد طلب التحديث");
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            File apk = null;
            try {
                File base = activity.getExternalCacheDir();
                if (base == null) base = activity.getCacheDir();
                File directory = new File(base, "updates");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Update directory unavailable");
                }
                apk = new File(directory, "DarbakMaps-" + update.versionCode + ".apk");
                if (apk.exists() && !apk.delete()) throw new IllegalStateException("Old update file unavailable");

                connection = (HttpURLConnection) secureUrl(update.apkUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                if(new android.os.StatFs(directory.getAbsolutePath()).getAvailableBytes()<48L*1024*1024)throw new IllegalStateException("المساحة غير كافية");
                try(BufferedInputStream input=new BufferedInputStream(connection.getInputStream());FileOutputStream output=new FileOutputStream(apk)){
                    byte[] buffer=new byte[64*1024];int read;long total=0;
                    while((read=input.read(buffer))!=-1){total+=read;if(total>40L*1024*1024)throw new IllegalStateException("حجم تحديث غير متوقع");output.write(buffer,0,read);}
                    output.getFD().sync();
                }

                String verificationError = verifyDownloadedApk(activity, apk, update);
                if (verificationError != null) {
                    apk.delete();
                    callback.onStatus(verificationError);
                    return;
                }
                File finalApk = apk;
                activity.runOnUiThread(() -> {
                    if(activity.isFinishing()||activity.isDestroyed()){callback.onStatus("اكتمل التنزيل؛ افتح التحديث مجددًا للتثبيت");return;}
                    try{install(activity,finalApk);}catch(RuntimeException e){callback.onStatus("تعذر فتح مثبت الحزم؛ تحقق من إعدادات تثبيت التطبيقات");}
                });
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
        if (!apk.isFile() || apk.length() <= 0) return "ملف التحديث فارغ أو غير صالح";
        if (!sha256(apk).equalsIgnoreCase(update.sha256)) return "فشل التحقق من سلامة ملف التحديث (SHA-256)";

        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) return "تعذر قراءة هوية ملف التحديث";
        if (!update.packageName.equals(archive.packageName) || !activity.getPackageName().equals(archive.packageName)) {
            return "حزمة ملف التحديث لا تطابق تطبيق دربك Maps";
        }

        long archiveVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode()
                : archive.versionCode;
        if (archiveVersion != update.versionCode || archiveVersion <= BuildConfig.VERSION_CODE) {
            return "رقم إصدار ملف التحديث غير صحيح";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && archive.applicationInfo != null
                && archive.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) {
            return "ملف التحديث غير متوافق مع نسخة أندرويد في الشاشة";
        }

        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (!signatureDigests(installed).equals(signatureDigests(archive)) || signatureDigests(archive).isEmpty()) {
            return "توقيع ملف التحديث مختلف. يلزم تثبيت نسخة Production المعتمدة يدويًا مرة واحدة";
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signatureDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> values = new HashSet<>();
        if (signatures == null) return values;
        for (Signature signature : signatures) {
            values.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        }
        return values;
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
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        } finally {
            input.close();
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

