package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.location.Location;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Journal is authoritative; display sampling must never truncate the GPX export. */
public final class BackgroundTrackStore {
    private BackgroundTrackStore() {}

    public static synchronized void append(Context context, Location location) throws IOException {
        if (location == null) return;
        TrackJournal.append(activeFile(context), location.getLatitude(),
                location.getLongitude(), location.getTime(), false);
    }

    public static synchronized List<GeoPoint> loadActive(Context context) {
        try {
            return TrackJournal.preview(activeFile(context), 5000);
        } catch (IOException error) {
            // Display failure cannot modify the authoritative journal.
            return Collections.emptyList();
        }
    }

    public static synchronized File finalizeActive(Context context) throws IOException {
        File source = activeFile(context);
        if (!source.exists()) return null;
        if (!source.isFile() || source.length() == 0) {
            throw new IOException("تعذر قراءة التسجيل؛ تم الاحتفاظ بالملف الأصلي");
        }
        File saved = new File(source.getParentFile(),
                "مسار-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".gpx");
        TrackJournal.export(source, saved, "مسار دربك");
        if (!source.delete()) {
            throw new IOException("تم حفظ GPX لكن تعذر إغلاق التسجيل الأصلي؛ لم تحذف بياناتك");
        }
        return saved;
    }

    public static File activeFile(Context context) {
        return new File(new File(context.getFilesDir(), "tracks"), "active-track.csv");
    }
}
