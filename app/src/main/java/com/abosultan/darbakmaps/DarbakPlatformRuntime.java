package com.abosultan.darbakmaps;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.StatFs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Extremely small Darbak Platform runtime. It deliberately does not touch Mapsforge, GPS,
 * storage packs, network, or licensing during process startup.
 */
public final class DarbakPlatformRuntime {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static Context app;
    private static boolean installed;

    private DarbakPlatformRuntime() {}

    public static synchronized void install(Context context) {
        if (installed) return;
        app = context.getApplicationContext();
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { writeCrash(thread, error); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    public static String healthReport() {
        if (!installed || app == null) return "Darbak Platform runtime غير مهيأ";
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        StatFs stat = new StatFs(app.getFilesDir().getAbsolutePath());
        return "دربك خرائط — Darbak Platform\n"
                + "الإصدار: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "Android: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
                + "الجهاز: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "RAM: " + (mem.availMem / 1048576L) + "MB متاح من " + (mem.totalMem / 1048576L) + "MB\n"
                + "التخزين الداخلي المتاح: " + (stat.getAvailableBytes() / 1048576L) + "MB\n"
                + "آخر Crash: " + (lastCrash() == null ? "لا يوجد" : "مسجل") + "\n"
                + "الهوية: دربك • تصميم وتطوير • أبوسلطان";
    }

    public static String lastCrash() {
        if (!installed || app == null) return null;
        File file = crashFile();
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), UTF8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void clearCrash() {
        if (installed && app != null) {
            try { crashFile().delete(); } catch (Throwable ignored) { }
        }
    }

    private static File crashFile() {
        File dir = new File(app.getFilesDir(), "darbak");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "last_crash.txt");
    }

    private static void writeCrash(Thread thread, Throwable error) throws Exception {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String report = "Darbak Maps Crash Report\n"
                + "Time: " + stamp + "\n"
                + "Version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "Android: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Thread: " + thread.getName() + "\n\n" + sw;
        try (FileOutputStream out = new FileOutputStream(crashFile(), false)) {
            out.write(report.getBytes(UTF8));
            out.flush();
        }
    }
}
