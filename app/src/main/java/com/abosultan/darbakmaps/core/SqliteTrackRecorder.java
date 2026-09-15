package com.abosultan.darbakmaps.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import static com.abosultan.darbakmaps.core.CoreContracts.TrackRecorder;

/** Persistent breadcrumb recorder. UI/map rendering is deliberately separate. */
public final class SqliteTrackRecorder extends SQLiteOpenHelper implements TrackRecorder {
    private static final String DB = "darbak_tracks.db";
    private static final int VERSION = 3;
    private static final long MIN_INTERVAL_MS = 1500L;
    private static final long MAX_CONTINUOUS_GAP_MS = 10000L;
    private static final double MIN_DISTANCE_METERS = 2.0d;
    private static final double MAX_REASONABLE_SPEED_KMH = 220.0d;
    private static final double MAX_IMPLIED_SPEED_KMH = 260.0d;
    public static final double MAX_RETAINED_DISTANCE_METERS = 1000000d;

    private final SessionStore session;
    private volatile State state = State.STOPPED;
    private LocationSnapshot lastAccepted;
    private long currentSegmentId = -1L;
    private double retainedDistanceCache = Double.NaN;

    public static final class TrackSegment {
        public final long id;
        public final List<LocationSnapshot> points;
        TrackSegment(long id) {
            this.id = id;
            this.points = new ArrayList<>();
        }
    }

    public SqliteTrackRecorder(Context context) {
        super(context.getApplicationContext(), DB, null, VERSION);
        session = new SessionStore(context);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE track_points (id INTEGER PRIMARY KEY AUTOINCREMENT, segment_id INTEGER NOT NULL DEFAULT 0, lat REAL NOT NULL, lon REAL NOT NULL, bearing REAL, speed REAL, time_ms INTEGER NOT NULL, distance_from_prev REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX track_points_time ON track_points(time_ms)");
        db.execSQL("CREATE INDEX track_points_segment_time ON track_points(segment_id,time_ms)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && !hasColumn(db, "track_points", "segment_id")) {
            // Preserve every legacy breadcrumb. Version 3 will refine gaps into real segments.
            db.execSQL("ALTER TABLE track_points ADD COLUMN segment_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS track_points_segment_time ON track_points(segment_id,time_ms)");
        }
        if (oldVersion < 3) {
            if (!hasColumn(db, "track_points", "distance_from_prev")) {
                db.execSQL("ALTER TABLE track_points ADD COLUMN distance_from_prev REAL NOT NULL DEFAULT 0");
            }
            backfillDistancesAndRepairSegments(db);
            trimToMaxDistance(db);
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int name = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (column.equalsIgnoreCase(cursor.getString(name))) return true;
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    /**
     * One-time v3 migration: preserve all points, but split old same-segment GPS gaps/jumps and
     * calculate the edge length used by the rolling 1000 km retention window.
     */
    private static void backfillDistancesAndRepairSegments(SQLiteDatabase db) {
        long nextGeneratedSegment = maxSegmentId(db) + 1L;
        long previousSourceSegment = Long.MIN_VALUE;
        long effectiveSegment = Long.MIN_VALUE;
        double previousLat = 0d;
        double previousLon = 0d;
        long previousTime = 0L;
        boolean havePrevious = false;

        Cursor cursor = db.query("track_points",
                new String[]{"id", "segment_id", "lat", "lon", "time_ms"},
                null, null, null, null, "id ASC");
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long sourceSegment = cursor.getLong(1);
                double lat = cursor.getDouble(2);
                double lon = cursor.getDouble(3);
                long time = cursor.getLong(4);
                double edgeDistance = 0d;

                if (!havePrevious || sourceSegment != previousSourceSegment) {
                    effectiveSegment = sourceSegment;
                } else {
                    long elapsed = time - previousTime;
                    double distance = PlaceMath.distanceMeters(previousLat, previousLon, lat, lon);
                    if (!continuousEdge(elapsed, distance)) {
                        effectiveSegment = Math.max(1L, nextGeneratedSegment++);
                    } else {
                        edgeDistance = distance;
                    }
                }

                ContentValues values = new ContentValues();
                values.put("segment_id", effectiveSegment);
                values.put("distance_from_prev", edgeDistance);
                db.update("track_points", values, "id=?", new String[]{Long.toString(id)});

                previousSourceSegment = sourceSegment;
                previousLat = lat;
                previousLon = lon;
                previousTime = time;
                havePrevious = true;
            }
        } finally {
            cursor.close();
        }
    }

    private static long maxSegmentId(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(segment_id),0) FROM track_points", null);
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private static boolean continuousEdge(long elapsedMs, double distanceMeters) {
        if (elapsedMs <= 0L || elapsedMs > MAX_CONTINUOUS_GAP_MS) return false;
        double seconds = elapsedMs / 1000d;
        if (seconds <= 0d) return false;
        double impliedKmh = distanceMeters / seconds * 3.6d;
        return impliedKmh <= MAX_IMPLIED_SPEED_KMH;
    }

    @Override public State state() { return state; }

    @Override public synchronized void ensureAutomaticRecording() {
        if (state == State.RECORDING) return;
        currentSegmentId = nextSegmentId();
        lastAccepted = null;
        state = State.RECORDING;
        session.setTrackRecording(true);
    }

    public synchronized void pause() {
        state = State.PAUSED;
        lastAccepted = null;
        currentSegmentId = -1L;
        session.setTrackRecording(false);
    }

    public synchronized boolean append(LocationSnapshot point) {
        if (state != State.RECORDING || point == null || !point.valid) return false;
        if (point.latitude < -90d || point.latitude > 90d || point.longitude < -180d || point.longitude > 180d) return false;
        if (point.speedKmh < 0f || point.speedKmh > MAX_REASONABLE_SPEED_KMH) return false;
        if (currentSegmentId < 0L) currentSegmentId = nextSegmentId();

        double edgeDistance = 0d;
        if (lastAccepted != null) {
            long elapsed = point.timestampMs - lastAccepted.timestampMs;
            if (elapsed <= 0L) return false;
            if (elapsed > MAX_CONTINUOUS_GAP_MS) {
                currentSegmentId = nextSegmentId();
                lastAccepted = null;
            } else {
                if (elapsed < MIN_INTERVAL_MS) return false;
                double distance = PlaceMath.distanceMeters(lastAccepted.latitude, lastAccepted.longitude,
                        point.latitude, point.longitude);
                if (distance < MIN_DISTANCE_METERS) return false;
                if (!continuousEdge(elapsed, distance)) return false;
                edgeDistance = distance;
            }
        }

        ContentValues values = new ContentValues();
        values.put("segment_id", currentSegmentId);
        values.put("lat", point.latitude);
        values.put("lon", point.longitude);
        values.put("bearing", point.bearing);
        values.put("speed", point.speedKmh);
        values.put("time_ms", point.timestampMs);
        values.put("distance_from_prev", edgeDistance);

        try {
            SQLiteDatabase db = getWritableDatabase();
            if (Double.isNaN(retainedDistanceCache)) retainedDistanceCache = storedDistance(db);
            boolean inserted = db.insertOrThrow("track_points", null, values) != -1L;
            if (!inserted) return false;
            lastAccepted = point;
            retainedDistanceCache += edgeDistance;
            if (retainedDistanceCache > MAX_RETAINED_DISTANCE_METERS) {
                retainedDistanceCache = trimToMaxDistance(db);
            }
            return true;
        } catch (RuntimeException error) {
            state = State.ERROR;
            return false;
        }
    }

    /** Distance currently retained in the rolling breadcrumb store. */
    public synchronized double retainedDistanceMeters() {
        try {
            if (Double.isNaN(retainedDistanceCache)) retainedDistanceCache = storedDistance(getReadableDatabase());
            return Math.max(0d, retainedDistanceCache);
        } catch (RuntimeException ignored) {
            return 0d;
        }
    }

    private static double storedDistance(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT COALESCE(SUM(distance_from_prev),0) FROM track_points", null);
        try {
            return cursor.moveToFirst() ? Math.max(0d, cursor.getDouble(0)) : 0d;
        } finally {
            cursor.close();
        }
    }

    /** Keeps newest breadcrumbs whose connected distance is at most 1000 km. */
    private static double trimToMaxDistance(SQLiteDatabase db) {
        double kept = 0d;
        long cutoffId = -1L;
        Cursor cursor = db.query("track_points", new String[]{"id", "distance_from_prev"},
                null, null, null, null, "id DESC");
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                double edge = Math.max(0d, cursor.getDouble(1));
                if (kept + edge > MAX_RETAINED_DISTANCE_METERS) {
                    cutoffId = id;
                    break;
                }
                kept += edge;
            }
        } finally {
            cursor.close();
        }

        if (cutoffId > 0L) {
            db.delete("track_points", "id < ?", new String[]{Long.toString(cutoffId)});
            ContentValues boundary = new ContentValues();
            boundary.put("distance_from_prev", 0d);
            db.update("track_points", boundary, "id=?", new String[]{Long.toString(cutoffId)});
        }
        return kept;
    }

    private long nextSegmentId() {
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(segment_id),0)+1 FROM track_points", null);
            return cursor.moveToFirst() ? Math.max(1L, cursor.getLong(0)) : 1L;
        } catch (RuntimeException ignored) {
            return System.currentTimeMillis();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /** Returns newest persisted points in chronological order; kept for compatibility. */
    public synchronized List<LocationSnapshot> recentPoints(int limit) {
        if (limit <= 0) return Collections.emptyList();
        ArrayList<LocationSnapshot> points = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query("track_points",
                    new String[]{"lat", "lon", "bearing", "speed", "time_ms"},
                    null, null, null, null, "time_ms DESC", Integer.toString(limit));
            while (cursor.moveToNext()) {
                points.add(new LocationSnapshot(cursor.getDouble(0), cursor.getDouble(1), cursor.getFloat(2),
                        cursor.getFloat(3), cursor.getLong(4), true));
            }
            Collections.reverse(points);
            return points;
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /** Returns segments separately so the map never draws a false bridge across pauses/restarts. */
    public synchronized List<TrackSegment> recentSegments(int pointLimit) {
        if (pointLimit <= 0) return Collections.emptyList();
        ArrayList<Row> rows = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query("track_points",
                    new String[]{"segment_id", "lat", "lon", "bearing", "speed", "time_ms"},
                    null, null, null, null, "time_ms DESC", Integer.toString(pointLimit));
            while (cursor.moveToNext()) {
                rows.add(new Row(cursor.getLong(0), new LocationSnapshot(cursor.getDouble(1), cursor.getDouble(2),
                        cursor.getFloat(3), cursor.getFloat(4), cursor.getLong(5), true)));
            }
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        } finally {
            if (cursor != null) cursor.close();
        }
        Collections.reverse(rows);
        ArrayList<TrackSegment> result = new ArrayList<>();
        TrackSegment current = null;
        for (Row row : rows) {
            if (current == null || current.id != row.segmentId) {
                current = new TrackSegment(row.segmentId);
                result.add(current);
            }
            current.points.add(row.point);
        }
        return result;
    }

    private static final class Row {
        final long segmentId;
        final LocationSnapshot point;
        Row(long segmentId, LocationSnapshot point) {
            this.segmentId = segmentId;
            this.point = point;
        }
    }

    public void restoreAutomaticState() {
        if (session.shouldResumeTrackRecording()) ensureAutomaticRecording();
    }
}
