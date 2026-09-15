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
    private static final int VERSION = 2;
    private static final long MIN_INTERVAL_MS = 1500L;
    private static final double MIN_DISTANCE_METERS = 2.0d;
    private static final double MAX_REASONABLE_SPEED_KMH = 220.0d;
    private final SessionStore session;
    private volatile State state = State.STOPPED;
    private LocationSnapshot lastAccepted;
    private long currentSegmentId = -1L;

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
        db.execSQL("CREATE TABLE track_points (id INTEGER PRIMARY KEY AUTOINCREMENT, segment_id INTEGER NOT NULL DEFAULT 0, lat REAL NOT NULL, lon REAL NOT NULL, bearing REAL, speed REAL, time_ms INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX track_points_time ON track_points(time_ms)");
        db.execSQL("CREATE INDEX track_points_segment_time ON track_points(segment_id,time_ms)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Preserve every existing breadcrumb as legacy segment 0. Never drop user tracks.
            db.execSQL("ALTER TABLE track_points ADD COLUMN segment_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS track_points_segment_time ON track_points(segment_id,time_ms)");
        }
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

        if (lastAccepted != null) {
            long elapsed = point.timestampMs - lastAccepted.timestampMs;
            if (elapsed <= 0L || elapsed < MIN_INTERVAL_MS) return false;
            double distance = PlaceMath.distanceMeters(lastAccepted.latitude, lastAccepted.longitude,
                    point.latitude, point.longitude);
            if (distance < MIN_DISTANCE_METERS) return false;
        }

        ContentValues v = new ContentValues();
        v.put("segment_id", currentSegmentId);
        v.put("lat", point.latitude);
        v.put("lon", point.longitude);
        v.put("bearing", point.bearing);
        v.put("speed", point.speedKmh);
        v.put("time_ms", point.timestampMs);
        try {
            boolean inserted = getWritableDatabase().insertOrThrow("track_points", null, v) != -1L;
            if (inserted) lastAccepted = point;
            return inserted;
        } catch (RuntimeException e) {
            state = State.ERROR;
            return false;
        }
    }

    private long nextSegmentId() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(segment_id),0)+1 FROM track_points", null);
            return c.moveToFirst() ? Math.max(1L, c.getLong(0)) : 1L;
        } catch (RuntimeException ignored) {
            return System.currentTimeMillis();
        } finally {
            if (c != null) c.close();
        }
    }

    /** Returns newest persisted points in chronological order; kept for compatibility. */
    public synchronized List<LocationSnapshot> recentPoints(int limit) {
        if (limit <= 0) return Collections.emptyList();
        ArrayList<LocationSnapshot> points = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("track_points",
                    new String[]{"lat", "lon", "bearing", "speed", "time_ms"},
                    null, null, null, null, "time_ms DESC", Integer.toString(limit));
            while (c.moveToNext()) {
                points.add(new LocationSnapshot(c.getDouble(0), c.getDouble(1), c.getFloat(2),
                        c.getFloat(3), c.getLong(4), true));
            }
            Collections.reverse(points);
            return points;
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        } finally {
            if (c != null) c.close();
        }
    }

    /** Returns segments separately so the map never draws a false bridge across pauses/restarts. */
    public synchronized List<TrackSegment> recentSegments(int pointLimit) {
        if (pointLimit <= 0) return Collections.emptyList();
        ArrayList<Row> rows = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("track_points",
                    new String[]{"segment_id", "lat", "lon", "bearing", "speed", "time_ms"},
                    null, null, null, null, "time_ms DESC", Integer.toString(pointLimit));
            while (c.moveToNext()) {
                rows.add(new Row(c.getLong(0), new LocationSnapshot(c.getDouble(1), c.getDouble(2),
                        c.getFloat(3), c.getFloat(4), c.getLong(5), true)));
            }
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        } finally {
            if (c != null) c.close();
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
