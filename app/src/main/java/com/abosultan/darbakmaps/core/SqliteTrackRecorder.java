package com.abosultan.darbakmaps.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import static com.abosultan.darbakmaps.core.CoreContracts.TrackRecorder;

/** Persistent breadcrumb recorder. UI/map rendering is deliberately separate. */
public final class SqliteTrackRecorder extends SQLiteOpenHelper implements TrackRecorder {
    private static final String DB = "darbak_tracks.db";
    private static final int VERSION = 1;
    private final SessionStore session;
    private volatile State state = State.STOPPED;

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

    public boolean append(LocationSnapshot point) {
        if (state != State.RECORDING || point == null || !point.valid) return false;
        ContentValues v = new ContentValues();
        v.put("lat", point.latitude);
        v.put("lon", point.longitude);
        v.put("bearing", point.bearing);
        v.put("speed", point.speedKmh);
        v.put("time_ms", point.timestampMs);
        try {
            return getWritableDatabase().insertOrThrow("track_points", null, v) != -1L;
        } catch (RuntimeException e) {
            state = State.ERROR;
            return false;
        }
    }

    public void restoreAutomaticState() {
        if (session.shouldResumeTrackRecording()) ensureAutomaticRecording();
    }
}
