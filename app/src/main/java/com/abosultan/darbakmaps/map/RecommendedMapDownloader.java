package com.abosultan.darbakmaps.map;

import android.content.Context;

import java.io.File;
import java.io.IOException;

/**
 * The old public GCC downloader is intentionally disabled.
 * Darbak Maps must use the approved Saudi/off-road map imported through the normal map picker.
 */
public final class RecommendedMapDownloader {
    public interface Listener {
        void onProgress(int percent, String message);
        boolean isCancelled();
    }

    public static final long EXPECTED_BYTES = 0L;
    public static final String DISPLAY_SIZE = "خريطة دربك المعتمدة";

    private RecommendedMapDownloader() { }

    public static File download(Context context, Listener listener) throws IOException {
        if (listener != null) listener.onProgress(0, "استخدم إضافة خريطة دربك من USB أو الذاكرة");
        throw new IOException("تنزيل الخرائط العامة معطل لحماية خريطة دربك المعتمدة. استخدم إضافة خريطة دربك من USB أو الذاكرة.");
    }

    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("تم إيقاف العملية");
        }
    }
}
