package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlaceRepository {
    private static final String PREFS = "darbak_places";
    private static final String KEY_PLACES = "places";
    private static final Object STORE_LOCK = new Object();

    public static final String ICON_QUAIL = "quail";
    public static final String ICON_CAMP = "camp";
    public static final String ICON_WATER = "water";
    public static final String ICON_TREE = "tree";
    public static final String ICON_HUNTING = "hunting";
    public static final String ICON_STAR = "star";

    // Kept for backward compatibility with already-saved locations.
    public static final String ICON_HOME = "home";
    public static final String ICON_CAR = "car";
    public static final String ICON_GATE = "gate";

    private final SharedPreferences preferences;

    public PlaceRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Place add(String name, double latitude, double longitude) {
        return addDetailed(name, latitude, longitude, ICON_STAR, "عام", "");
    }

    public Place addDetailed(String name, double latitude, double longitude,
                             String iconKey, String category, String note) {
        validateCoordinates(latitude, longitude);
        synchronized (STORE_LOCK) {
            List<Place> places = new ArrayList<>(load(true));
            long createdAt = System.currentTimeMillis();
            String safeIcon = safe(iconKey, ICON_STAR);
            Place place = new Place(
                    UUID.randomUUID().toString(),
                    displayName(name, safeIcon, createdAt),
                    latitude,
                    longitude,
                    createdAt,
                    safeIcon,
                    safe(category, iconLabel(safeIcon)),
                    note == null ? "" : note.trim()
            );
            places.add(0, place);
            persist(places);
            return place;
        }
    }

    public boolean update(String id, String name, String iconKey, String category, String note) {
        synchronized (STORE_LOCK) {
            List<Place> places = new ArrayList<>(load(true));
            for (int i = 0; i < places.size(); i++) {
                Place old = places.get(i);
                if (old.id.equals(id)) {
                    String safeIcon = safe(iconKey, old.iconKey);
                    places.set(i, new Place(old.id, displayName(name, safeIcon, old.createdAt),
                            old.latitude, old.longitude, old.createdAt, safeIcon,
                            safe(category, iconLabel(safeIcon)), note == null ? "" : note.trim()));
                    persist(places);
                    return true;
                }
            }
            return false;
        }
    }

    public boolean delete(String id) {
        synchronized (STORE_LOCK) {
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
    }

    /** Restores a previously deleted object with the same stable id and coordinates. */
    public boolean restore(Place place) {
        if (place == null) return false;
        validateCoordinates(place.latitude, place.longitude);
        synchronized (STORE_LOCK) {
            List<Place> places = new ArrayList<>(load(true));
            for (Place existing : places) if (existing.id.equals(place.id)) return false;
            places.add(0, place);
            persist(places);
            return true;
        }
    }

    /** Safe read for UI. Corrupt bytes stay untouched and are never replaced by an empty store. */
    public List<Place> all() {
        synchronized (STORE_LOCK) {
            try {
                return load(false);
            } catch (IllegalStateException impossible) {
                return Collections.emptyList();
            }
        }
    }

    public boolean hasCorruptStore() {
        synchronized (STORE_LOCK) {
            String raw = preferences.getString(KEY_PLACES, "[]");
            try {
                decode(raw);
                return false;
            } catch (JSONException | RuntimeException error) {
                return true;
            }
        }
    }

    public List<Place> byIcon(String iconKey) {
        if (iconKey == null || iconKey.trim().isEmpty()) return all();
        List<Place> result = new ArrayList<>();
        for (Place place : all()) if (iconKey.equals(place.iconKey)) result.add(place);
        return result;
    }

    public List<Place> search(String query) {
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
            long createdAt = item.optLong("createdAt");
            if (createdAt <= 0L) createdAt = 1L;
            String icon = item.optString("icon", ICON_STAR);
            String id = item.optString("id", "").trim();
            if (id.isEmpty()) id = legacyId(latitude, longitude, createdAt);
            result.add(new Place(
                    id,
                    displayName(item.optString("name", ""), icon, createdAt),
                    latitude,
                    longitude,
                    createdAt,
                    icon,
                    item.optString("category", iconLabel(icon)),
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

    private static String displayName(String value, String iconKey, long createdAt) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isEmpty() ? automaticLabel(iconKey, createdAt) : cleaned;
    }

    public static String automaticLabel(String iconKey, long createdAt) {
        String stamp = new SimpleDateFormat("dd/MM HH:mm", Locale.US)
                .format(new Date(Math.max(1L, createdAt)));
        return iconLabel(iconKey) + " — " + stamp;
    }

    public static String iconLabel(String key) {
        if (ICON_QUAIL.equals(key)) return "سمان";
        if (ICON_CAMP.equals(key)) return "مخيم";
        if (ICON_WATER.equals(key)) return "ماء";
        if (ICON_TREE.equals(key)) return "شجرة";
        if (ICON_HUNTING.equals(key)) return "موقع صيد";
        if (ICON_HOME.equals(key)) return "استراحة";
        if (ICON_CAR.equals(key)) return "سيارة";
        if (ICON_GATE.equals(key)) return "بوابة";
        return "علامة";
    }

    /** Text-only fallback for dialogs. Map/list icons are drawn with fixed Canvas artwork. */
    public static String iconGlyph(String key) {
        if (ICON_QUAIL.equals(key)) return "سمان";
        if (ICON_CAMP.equals(key)) return "مخيم";
        if (ICON_WATER.equals(key)) return "ماء";
        if (ICON_TREE.equals(key)) return "شجرة";
        if (ICON_HUNTING.equals(key)) return "صيد";
        return "علامة";
    }

    private static String safe(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String legacyId(double latitude, double longitude, long createdAt) {
        return "legacy-" + createdAt + "-" + Math.round(latitude * 1_000_000d)
                + "-" + Math.round(longitude * 1_000_000d);
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
