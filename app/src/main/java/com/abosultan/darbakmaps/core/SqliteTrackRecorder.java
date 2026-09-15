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
    private static final int VERSION = 1;
    private static final long MIN_INTERVAL_MS = 1500L;
    private static final double MIN_DISTANCE_METERS = 2.0d;
    private static final double MAX_REASONABLE_SPEED_KMH = 220.0d;
    private final SessionStore session;
    private volatile State state = State.STOPPED;
    private LocationSnapshot lastAccepted;

    public SqliteTrackRecorder(Context context) {
        super(context.getApplicationContext(), DB, null, VERSION);
        session = new SessionStore(context);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE track_points (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL NOT NULL, lon REAL NOT NULL, bearing REAL, speed REAL, time_ms INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX track_points_time ON track_points(time_ms)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations must never delete recorded tracks automatically.
    }

    @Override public State state() { return state; }

    @Override public void ensureAutomaticRecording() {
        state = State.RECORDING;
        session.setTrackRecording(true);
    }

    public void pause() {
        state = State.PAUSED;
        session.setTrackRecording(false);
    }

    public synchronized boolean append(LocationSnapshot point) {
        if (state != State.RECORDING || point == null || !point.valid) return false;
        if (point.latitude < -90d || point.latitude > 90d || point.longitude < -180d || point.longitude > 180d) return false;
        if (point.speedKmh < 0f || point.speedKmh > MAX_REASONABLE_SPEED_KMH) return false;

        if (lastAccepted != null) {
            long elapsed = point.timestampMs - lastAccepted.timestampMs;
            if (elapsed <= 0L || elapsed < MIN_INTERVAL_MS) return false;
            double distance = PlaceMath.distanceMeters(lastAccepted.latitude, lastAccepted.longitude,
                    point.latitude, point.longitude);
            if (distance < MIN_DISTANCE_METERS) return false;
        }

        ContentValues v = new ContentValues();
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

    /** Returns the newest persisted breadcrumb window in chronological order. */
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

    public void restoreAutomaticState() {
        if (session.shouldResumeTrackRecording()) ensureAutomaticRecording();
    }
}
