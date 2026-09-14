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
        validateCoordinates(latitude, longitude);
        List<Place> places = new ArrayList<>(load(true));
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
        persist(places);
        return place;
    }

    public synchronized boolean update(String id, String name, String iconKey, String category, String note) {
        List<Place> places = new ArrayList<>(load(true));
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
        List<Place> places = new ArrayList<>(load(true));
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

    /** Safe read for UI. Corrupt bytes stay untouched and are never replaced by an empty store. */
    public synchronized List<Place> all() {
        try {
            return load(false);
        } catch (IllegalStateException impossible) {
            return Collections.emptyList();
        }
    }

    public synchronized boolean hasCorruptStore() {
        String raw = preferences.getString(KEY_PLACES, "[]");
        try {
            decode(raw);
            return false;
        } catch (JSONException | RuntimeException error) {
            return true;
        }
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

    private List<Place> load(boolean forMutation) {
        String raw = preferences.getString(KEY_PLACES, "[]");
        try {
            return decode(raw);
        } catch (JSONException | RuntimeException error) {
            if (forMutation) {
                throw new IllegalStateException("بيانات المواقع تحتاج إصلاحًا؛ احتفظ التطبيق بالأصل ولم يكتب فوقه", error);
            }
            return Collections.emptyList();
        }
    }

    private static List<Place> decode(String raw) throws JSONException {
        JSONArray array = new JSONArray(raw == null ? "[]" : raw);
        List<Place> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            double latitude = item.getDouble("lat");
            double longitude = item.getDouble("lon");
            validateCoordinates(latitude, longitude);
            result.add(new Place(
                    item.optString("id"),
                    item.optString("name", "موقع محفوظ"),
                    latitude,
                    longitude,
                    item.optLong("createdAt"),
                    item.optString("icon", ICON_STAR),
                    item.optString("category", "عام"),
                    item.optString("note", "")
            ));
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
        } catch (JSONException error) {
            throw new IllegalStateException("تعذر تجهيز بيانات المواقع للحفظ", error);
        }
        if (!preferences.edit().putString(KEY_PLACES, array.toString()).commit()) {
            throw new IllegalStateException("تعذر حفظ المواقع على الجهاز؛ لم يتم تأكيد الكتابة");
        }
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90d || latitude > 90d || longitude < -180d || longitude > 180d) {
            throw new IllegalArgumentException("إحداثيات الموقع غير صالحة");
        }
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
