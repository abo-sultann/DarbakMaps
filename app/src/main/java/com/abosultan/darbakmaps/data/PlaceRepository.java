package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlaceRepository {
    private static final String PREFS = "darbak_places";
    private static final String KEY_PLACES = "places";
    private static final int MAX_PLACES = 500;

    private final SharedPreferences preferences;

    public PlaceRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized Place add(String name, double latitude, double longitude) {
        List<Place> places = new ArrayList<>(all());
        Place place = new Place(
                String.valueOf(System.currentTimeMillis()),
                name.trim(),
                latitude,
                longitude,
                System.currentTimeMillis()
        );
        places.add(0, place);
        if (places.size() > MAX_PLACES) {
            places = new ArrayList<>(places.subList(0, MAX_PLACES));
        }
        persist(places);
        return place;
    }

    public synchronized List<Place> all() {
        String raw = preferences.getString(KEY_PLACES, "[]");
        List<Place> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Place(
                        item.optString("id"),
                        item.optString("name", "موقع محفوظ"),
                        item.getDouble("lat"),
                        item.getDouble("lon"),
                        item.optLong("createdAt")
                ));
            }
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
        return result;
    }

    public synchronized List<Place> search(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return all();
        }
        List<Place> result = new ArrayList<>();
        for (Place place : all()) {
            if (place.name.toLowerCase(Locale.ROOT).contains(normalized)) {
                result.add(place);
            }
        }
        return result;
    }

    private void persist(List<Place> places) {
        JSONArray array = new JSONArray();
        try {
            for (Place place : places) {
                JSONObject item = new JSONObject();
                item.put("id", place.id);
                item.put("name", place.name);
                item.put("lat", place.latitude);
                item.put("lon", place.longitude);
                item.put("createdAt", place.createdAt);
                array.put(item);
            }
        } catch (JSONException ignored) {
            return;
        }
        preferences.edit().putString(KEY_PLACES, array.toString()).apply();
    }

    public static final class Place {
        public final String id;
        public final String name;
        public final double latitude;
        public final double longitude;
        public final long createdAt;

        Place(String id, String name, double latitude, double longitude, long createdAt) {
            this.id = id;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.createdAt = createdAt;
        }
    }
}

