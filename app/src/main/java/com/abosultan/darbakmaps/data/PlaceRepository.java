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

    public static final String ICON_CAMP = "camp";
    public static final String ICON_HOME = "home";
    public static final String ICON_QUAIL = "quail";
    public static final String ICON_WATER = "water";
    public static final String ICON_TREE = "tree";
    public static final String ICON_CAR = "car";
    public static final String ICON_GATE = "gate";
    public static final String ICON_STAR = "star";

    private final SharedPreferences preferences;

    public PlaceRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized Place add(String name, double latitude, double longitude) {
        return addDetailed(name, latitude, longitude, ICON_STAR, "عام", "");
    }

    public synchronized Place addDetailed(String name, double latitude, double longitude,
                                          String iconKey, String category, String note) {
        List<Place> places = new ArrayList<>(all());
        Place place = new Place(
                String.valueOf(System.currentTimeMillis()),
                safeName(name),
                latitude,
                longitude,
                System.currentTimeMillis(),
                safe(iconKey, ICON_STAR),
                safe(category, "عام"),
                note == null ? "" : note.trim()
        );
        places.add(0, place);
        // Never silently discard old saved places. User data is more important than a fixed count cap.
        persist(places);
        return place;
    }

    public synchronized boolean update(String id, String name, String iconKey, String category, String note) {
        List<Place> places = new ArrayList<>(all());
        for (int i = 0; i < places.size(); i++) {
            Place old = places.get(i);
            if (old.id.equals(id)) {
                places.set(i, new Place(old.id, safeName(name), old.latitude, old.longitude,
                        old.createdAt, safe(iconKey, old.iconKey), safe(category, old.category),
                        note == null ? "" : note.trim()));
                persist(places);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean delete(String id) {
        List<Place> places = new ArrayList<>(all());
        boolean removed = false;
        for (int i = places.size() - 1; i >= 0; i--) {
            if (places.get(i).id.equals(id)) {
                places.remove(i);
                removed = true;
            }
        }
        if (removed) persist(places);
        return removed;
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
                        item.optLong("createdAt"),
                        item.optString("icon", ICON_STAR),
                        item.optString("category", "عام"),
                        item.optString("note", "")
                ));
            }
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
        return result;
    }

    public synchronized List<Place> search(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return all();
        List<Place> result = new ArrayList<>();
        for (Place place : all()) {
            String haystack = (place.name + " " + place.category + " " + place.note).toLowerCase(Locale.ROOT);
            if (haystack.contains(normalized)) result.add(place);
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
                item.put("icon", place.iconKey);
                item.put("category", place.category);
                item.put("note", place.note);
                array.put(item);
            }
        } catch (JSONException ignored) {
            return;
        }
        preferences.edit().putString(KEY_PLACES, array.toString()).apply();
    }

    private static String safeName(String value) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isEmpty() ? "موقع محفوظ" : cleaned;
    }

    private static String safe(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    public static String iconGlyph(String key) {
        if (ICON_CAMP.equals(key)) return "⛺";
        if (ICON_HOME.equals(key)) return "⌂";
        if (ICON_QUAIL.equals(key)) return "ط";
        if (ICON_WATER.equals(key)) return "💧";
        if (ICON_TREE.equals(key)) return "♣";
        if (ICON_CAR.equals(key)) return "◆";
        if (ICON_GATE.equals(key)) return "▣";
        return "★";
    }

    public static final class Place {
        public final String id;
        public final String name;
        public final double latitude;
        public final double longitude;
        public final long createdAt;
        public final String iconKey;
        public final String category;
        public final String note;

        Place(String id, String name, double latitude, double longitude, long createdAt,
              String iconKey, String category, String note) {
            this.id = id;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.createdAt = createdAt;
            this.iconKey = iconKey;
            this.category = category;
            this.note = note;
        }
    }
}
