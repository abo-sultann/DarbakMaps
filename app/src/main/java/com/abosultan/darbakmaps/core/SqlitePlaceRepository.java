package com.abosultan.darbakmaps.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.abosultan.darbakmaps.core.CoreContracts.Place;
import static com.abosultan.darbakmaps.core.CoreContracts.PlaceRepository;

/** Small dependency-free store designed for Android 7.1 and offline use. */
public final class SqlitePlaceRepository extends SQLiteOpenHelper implements PlaceRepository {
    private static final String DB = "darbak_places.db";
    private static final int VERSION = 2;

    public SqlitePlaceRepository(Context context) {
        super(context.getApplicationContext(), DB, null, VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL UNIQUE, lat REAL NOT NULL, lon REAL NOT NULL, category TEXT NOT NULL, name TEXT, note TEXT)");
        db.execSQL("CREATE INDEX places_category ON places(category)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) migrateStableUuids(db);
    }

    private static void migrateStableUuids(SQLiteDatabase db) {
        if (!hasColumn(db, "places", "uuid")) {
            db.execSQL("ALTER TABLE places ADD COLUMN uuid TEXT");
        }

        Cursor rows = db.query("places", new String[]{"id", "uuid"},
                "uuid IS NULL OR TRIM(uuid)=''", null, null, null, null);
        try {
            int idColumn = rows.getColumnIndexOrThrow("id");
            while (rows.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("uuid", UUID.randomUUID().toString());
                int changed = db.update("places", values, "id=?",
                        new String[]{Long.toString(rows.getLong(idColumn))});
                if (changed != 1) throw new IllegalStateException("Failed to assign stable place UUID");
            }
        } finally {
            rows.close();
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS places_uuid ON places(uuid)");
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

    @Override public long save(Place place) {
        String uuid = place.uuid == null || place.uuid.trim().isEmpty()
                ? UUID.randomUUID().toString() : place.uuid.trim();
        ContentValues v = new ContentValues();
        v.put("uuid", uuid);
        v.put("lat", place.latitude);
        v.put("lon", place.longitude);
        v.put("category", safe(place.category));
        v.put("name", place.name);
        v.put("note", place.note);
        return getWritableDatabase().insertOrThrow("places", null, v);
    }

    /** Deletes exactly one saved place by its stable database id. */
    public boolean delete(long id) {
        if (id <= 0L) return false;
        return getWritableDatabase().delete("places", "id=?", new String[]{Long.toString(id)}) == 1;
    }

    public List<Place> all(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        ArrayList<Place> result = new ArrayList<>();
        Cursor c = getReadableDatabase().query("places", null, null, null, null, null, "id DESC", Integer.toString(safeLimit));
        try {
            int id = c.getColumnIndexOrThrow("id");
            int uuid = c.getColumnIndexOrThrow("uuid");
            int lat = c.getColumnIndexOrThrow("lat");
            int lon = c.getColumnIndexOrThrow("lon");
            int cat = c.getColumnIndexOrThrow("category");
            int name = c.getColumnIndexOrThrow("name");
            int note = c.getColumnIndexOrThrow("note");
            while (c.moveToNext()) {
                result.add(new Place(c.getLong(id), c.getString(uuid), c.getDouble(lat), c.getDouble(lon),
                        c.getString(cat), c.getString(name), c.getString(note)));
            }
        } finally { c.close(); }
        return result;
    }

    @Override public List<Place> nearest(final double latitude, final double longitude, String category, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        ArrayList<Place> result = new ArrayList<>();
        String selection = category == null || category.length() == 0 ? null : "category=?";
        String[] args = selection == null ? null : new String[]{category};
        Cursor c = getReadableDatabase().query("places", null, selection, args, null, null, null);
        try {
            int id = c.getColumnIndexOrThrow("id");
            int uuid = c.getColumnIndexOrThrow("uuid");
            int lat = c.getColumnIndexOrThrow("lat");
            int lon = c.getColumnIndexOrThrow("lon");
            int cat = c.getColumnIndexOrThrow("category");
            int name = c.getColumnIndexOrThrow("name");
            int note = c.getColumnIndexOrThrow("note");
            while (c.moveToNext()) {
                result.add(new Place(c.getLong(id), c.getString(uuid), c.getDouble(lat), c.getDouble(lon),
                        c.getString(cat), c.getString(name), c.getString(note)));
            }
        } finally {
            c.close();
        }
        Collections.sort(result, new Comparator<Place>() {
            @Override public int compare(Place a, Place b) {
                return Double.compare(
                        PlaceMath.distanceMeters(latitude, longitude, a.latitude, a.longitude),
                        PlaceMath.distanceMeters(latitude, longitude, b.latitude, b.longitude));
            }
        });
        if (result.size() > safeLimit) return new ArrayList<>(result.subList(0, safeLimit));
        return result;
    }

    private static String safe(String value) { return value == null ? "other" : value; }
}
