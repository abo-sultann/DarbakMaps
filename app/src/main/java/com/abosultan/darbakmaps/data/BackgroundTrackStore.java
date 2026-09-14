package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Persistent automatic-track store.
 *
 * The journal is continuous across UI closes/reboots and is bounded by connected distance, not
 * by point count. Manually saved GPX files live beside it and are never part of rolling deletion.
 */
public final class BackgroundTrackStore {
    public static final double MAX_RETAINED_METERS = 1_000_000d;
    private static final double TRIM_TRIGGER_METERS = 1_001_000d;
    private static final String PREFS = "darbak_rolling_track";
    private static final String KEY_DISTANCE = "distance_m";
    private static final String KEY_LENGTH = "journal_length";

    private BackgroundTrackStore() {}

    public static synchronized void append(Context context, Location location) throws IOException {
        append(context, location, false, 0d);
    }

    public static synchronized void append(Context context, Location location, boolean newSegment) throws IOException {
        append(context, location, newSegment, 0d);
    }

    /**
     * Appends an already accepted GPS fix. connectedMeters must be zero for segment starts/gaps.
     * The new bytes are fsync'ed before any old bytes are eligible for trimming.
     */
    public static synchronized void append(Context context, Location location, boolean newSegment,
                                           double connectedMeters) throws IOException {
        if (location == null) return;
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);

            SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            double total = reconcileDistance(source, state);

            TrackJournal.append(source, location.getLatitude(), location.getLongitude(), location.getTime(), newSegment);
            if (!newSegment && Double.isFinite(connectedMeters) && connectedMeters > 0d && connectedMeters < 2000d) {
                total += connectedMeters;
            }

            if (total > TRIM_TRIGGER_METERS) {
                total = TrackJournal.trimToDistance(source, MAX_RETAINED_METERS);
            }
            persistState(state, source, total);
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized List<GeoPoint> loadActive(Context context) {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            try {
                TrackJournal.recoverInterruptedTrim(source);
                reconcileDistance(source, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
                return TrackJournal.preview(source, 5000);
            } catch (IOException error) {
                // Display failure cannot modify the authoritative journal.
                return Collections.emptyList();
            }
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized double retainedDistanceMeters(Context context) throws IOException {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            return reconcileDistance(source, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        } finally {
            DataStoreLock.unlock();
        }
    }

    /** Creates a consistent GPX copy without stopping or clearing automatic recording. */
    public static synchronized File snapshotActive(Context context) throws IOException {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            if (!source.isFile() || source.length() == 0L) {
                throw new IOException("لا يوجد مسار تلقائي محفوظ حتى الآن");
            }
            File saved = new File(source.getParentFile(),
                    "مسار-محفوظ-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".gpx");
            TrackJournal.export(source, saved, "نسخة من مسار دربك التلقائي");
            return saved;
        } finally {
            DataStoreLock.unlock();
        }
    }

    /**
     * Compatibility with the old stop/finalize flow. It now creates a snapshot only: the rolling
     * automatic journal must survive stops, reboots and manual saves.
     */
    public static synchronized File finalizeActive(Context context) throws IOException {
        return snapshotActive(context);
    }

    public static File activeFile(Context context) {
        return new File(new File(context.getFilesDir(), "tracks"), "active-track.csv");
    }

    private static double reconcileDistance(File source, SharedPreferences state) throws IOException {
        if (!source.isFile() || source.length() == 0L) {
            persistState(state, source, 0d);
            return 0d;
        }
        long knownLength = state.getLong(KEY_LENGTH, -1L);
        if (knownLength == source.length() && state.contains(KEY_DISTANCE)) {
            return Math.max(0d, Double.longBitsToDouble(
                    state.getLong(KEY_DISTANCE, Double.doubleToLongBits(0d))));
        }
        double measured = TrackJournal.distanceMeters(source);
        persistState(state, source, measured);
        return measured;
    }

    private static void persistState(SharedPreferences state, File source, double distance) throws IOException {
        boolean committed = state.edit()
                .putLong(KEY_DISTANCE, Double.doubleToLongBits(Math.max(0d, distance)))
                .putLong(KEY_LENGTH, source != null && source.isFile() ? source.length() : 0L)
                .commit();
        if (!committed) throw new IOException("تعذر تثبيت حالة سجل المسار");
    }
}
