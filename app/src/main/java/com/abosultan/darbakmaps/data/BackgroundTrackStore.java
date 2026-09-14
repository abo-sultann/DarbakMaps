package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Persistent automatic-track store. The journal is the single source of truth for rendering. */
public final class BackgroundTrackStore {
    public static final double MAX_RETAINED_METERS = 1_000_000d;
    private static final double TRIM_TRIGGER_METERS = 1_001_000d;
    private static final String PREFS = "darbak_rolling_track";
    private static final String KEY_DISTANCE = "distance_m";
    private static final String KEY_LENGTH = "journal_length";
    private static final String KEY_GENERATION = "journal_generation";

    private BackgroundTrackStore() {}

    public static synchronized AppendResult append(Context context, Location location) throws IOException {
        return append(context, location, false, 0d);
    }

    public static synchronized AppendResult append(Context context, Location location, boolean newSegment) throws IOException {
        return append(context, location, newSegment, 0d);
    }

    public static synchronized AppendResult append(Context context, Location location, boolean newSegment,
                                                    double connectedMeters) throws IOException {
        if (location == null) return new AppendResult(generation(context), false, newSegment);
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            State reconciled = reconcileState(source, state);
            double total = reconciled.distance;

            TrackJournal.append(source, location.getLatitude(), location.getLongitude(), location.getTime(), newSegment);
            if (!newSegment && Double.isFinite(connectedMeters) && connectedMeters > 0d && connectedMeters < 2000d) {
                total += connectedMeters;
            }
            boolean trimmed = false;
            if (total > TRIM_TRIGGER_METERS) {
                total = TrackJournal.trimToDistance(source, MAX_RETAINED_METERS);
                trimmed = true;
            }
            long nextGeneration = Math.max(reconciled.generation + 1L, 1L);
            persistState(state, source, total, nextGeneration);
            return new AppendResult(nextGeneration, trimmed, newSegment);
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized Snapshot loadActiveSnapshot(Context context) {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            try {
                TrackJournal.recoverInterruptedTrim(source);
                SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                State reconciled = reconcileState(source, state);
                List<GeoPoint> points = TrackJournal.preview(source, 5000);
                return new Snapshot(reconciled.generation, points);
            } catch (IOException error) {
                return new Snapshot(generation(context), Collections.emptyList());
            }
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized List<GeoPoint> loadActive(Context context) {
        return loadActiveSnapshot(context).points;
    }

    public static synchronized long generation(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_GENERATION, 0L);
    }

    public static synchronized double retainedDistanceMeters(Context context) throws IOException {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            return reconcileState(source, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).distance;
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized File snapshotActive(Context context) throws IOException {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            if (!source.isFile() || source.length() == 0L) throw new IOException("لا يوجد مسار تلقائي محفوظ حتى الآن");
            File saved = new File(source.getParentFile(),
                    "مسار-محفوظ-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".gpx");
            TrackJournal.export(source, saved, "نسخة من مسار دربك التلقائي");
            return saved;
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized File finalizeActive(Context context) throws IOException { return snapshotActive(context); }

    public static File activeFile(Context context) {
        return new File(new File(context.getFilesDir(), "tracks"), "active-track.csv");
    }

    private static State reconcileState(File source, SharedPreferences state) throws IOException {
        long generation = Math.max(0L, state.getLong(KEY_GENERATION, 0L));
        if (!source.isFile() || source.length() == 0L) {
            persistState(state, source, 0d, generation);
            return new State(0d, generation);
        }
        long knownLength = state.getLong(KEY_LENGTH, -1L);
        if (knownLength == source.length() && state.contains(KEY_DISTANCE)) {
            double distance = Math.max(0d, Double.longBitsToDouble(
                    state.getLong(KEY_DISTANCE, Double.doubleToLongBits(0d))));
            return new State(distance, generation);
        }
        // Journal bytes changed without the metadata commit (for example process death after fsync).
        // Reconcile from the authoritative file and advance generation so stale renders cannot win.
        double measured = TrackJournal.distanceMeters(source);
        long repairedGeneration = generation + 1L;
        persistState(state, source, measured, repairedGeneration);
        return new State(measured, repairedGeneration);
    }

    private static void persistState(SharedPreferences state, File source, double distance, long generation) throws IOException {
        boolean committed = state.edit()
                .putLong(KEY_DISTANCE, Double.doubleToLongBits(Math.max(0d, distance)))
                .putLong(KEY_LENGTH, source != null && source.isFile() ? source.length() : 0L)
                .putLong(KEY_GENERATION, Math.max(0L, generation))
                .commit();
        if (!committed) throw new IOException("تعذر تثبيت حالة سجل المسار");
    }

    private static final class State {
        final double distance; final long generation;
        State(double distance, long generation) { this.distance = distance; this.generation = generation; }
    }

    public static final class AppendResult {
        public final long generation;
        public final boolean trimmed;
        public final boolean segmentStart;
        AppendResult(long generation, boolean trimmed, boolean segmentStart) {
            this.generation = generation; this.trimmed = trimmed; this.segmentStart = segmentStart;
        }
    }

    public static final class Snapshot {
        public final long generation;
        public final List<GeoPoint> points;
        Snapshot(long generation, List<GeoPoint> points) {
            this.generation = generation;
            this.points = points == null ? Collections.emptyList() : points;
        }
    }
}
