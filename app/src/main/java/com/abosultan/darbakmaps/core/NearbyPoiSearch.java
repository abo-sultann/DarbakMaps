package com.abosultan.darbakmaps.core;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.PointOfInterest;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Low-memory nearby POI scanner over the installed Mapsforge map.
 * It deliberately scans only a small tile window around the live GPS position instead of building
 * a national in-memory index, which suits the 1 GB Android 7.1 head unit.
 */
public final class NearbyPoiSearch {
    public static final String ALL = "الكل";
    public static final String WADIS = "شعاب وأودية";
    public static final String MOUNTAINS = "جبال";
    public static final String LANDMARKS = "معالم";
    public static final String VILLAGES = "قرى";
    public static final String WATER = "مياه وآبار";
    public static final String SERVICES = "خدمات";

    private static final int TARGET_ZOOM = 12;
    private static final int MAX_RADIUS_TILES = 3; // At most 7x7 tiles.
    private static final int MAX_RESULTS = 120;

    private NearbyPoiSearch() {}

    public static List<Result> search(File mapFile, double latitude, double longitude,
                                      double radiusMeters, String wantedCategory, int limit) {
        return search(mapFile, latitude, longitude, radiusMeters, wantedCategory, "", limit);
    }

    /** Searches nearby names and aliases without building a national index. */
    public static List<Result> search(File mapFile, double latitude, double longitude,
                                      double radiusMeters, String wantedCategory,
                                      String query, int limit) {
        if (mapFile == null || !mapFile.isFile()) return Collections.emptyList();
        double safeRadius = Math.max(1000d, Math.min(radiusMeters, 30000d));
        int safeLimit = Math.max(5, Math.min(limit, MAX_RESULTS));
        String category = wantedCategory == null ? ALL : wantedCategory;
        String wanted = normalize(query);

        MapFile map = null;
        ArrayList<Result> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            map = new MapFile(mapFile);
            int minZoom = map.getMapFileInfo().zoomLevelMin;
            int maxZoom = map.getMapFileInfo().zoomLevelMax;
            byte zoom = (byte) Math.max(minZoom, Math.min(maxZoom, TARGET_ZOOM));
            int tileSize = Math.max(128, map.getMapFileInfo().tilePixelSize);

            long centerX = MercatorProjection.longitudeToTileX(longitude, zoom);
            long centerY = MercatorProjection.latitudeToTileY(latitude, zoom);
            double metersPerTile = 40075016.686d * Math.cos(Math.toRadians(latitude)) / (1L << zoom);
            int radiusTiles = Math.max(1, (int) Math.ceil(safeRadius / Math.max(1000d, metersPerTile)));
            radiusTiles = Math.min(MAX_RADIUS_TILES, radiusTiles);

            for (int dx = -radiusTiles; dx <= radiusTiles; dx++) {
                for (int dy = -radiusTiles; dy <= radiusTiles; dy++) {
                    MapReadResult data;
                    try {
                        data = map.readMapData(new Tile((int) (centerX + dx), (int) (centerY + dy), zoom, tileSize));
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    if (data == null) continue;

                    if (data.pois != null) {
                        for (PointOfInterest poi : data.pois) {
                            Candidate candidate = candidate(poi.tags, poi.position);
                            add(out, seen, candidate, latitude, longitude, safeRadius, category, wanted);
                        }
                    }
                    if (data.ways != null) {
                        for (Way way : data.ways) {
                            LatLong position = representativePosition(way);
                            Candidate candidate = candidate(way.tags, position);
                            add(out, seen, candidate, latitude, longitude, safeRadius, category, wanted);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        } finally {
            if (map != null) map.close();
        }

        Collections.sort(out, new Comparator<Result>() {
            @Override public int compare(Result a, Result b) {
                int byDistance = Double.compare(a.distanceMeters, b.distanceMeters);
                if (byDistance != 0) return byDistance;
                return a.name.compareTo(b.name);
            }
        });
        if (out.size() > safeLimit) return new ArrayList<>(out.subList(0, safeLimit));
        return out;
    }

    private static void add(List<Result> out, Set<String> seen, Candidate candidate,
                            double fromLat, double fromLon, double radiusMeters,
                            String wantedCategory, String wantedQuery) {
        if (candidate == null) return;
        if (!ALL.equals(wantedCategory) && !wantedCategory.equals(candidate.category)) return;
        if (!matches(candidate.searchText, wantedQuery)) return;
        double distance = PlaceMath.distanceMeters(fromLat, fromLon, candidate.latitude, candidate.longitude);
        if (distance > radiusMeters) return;
        String key = normalize(candidate.name + "|" + candidate.source) + ':'
                + Math.round(candidate.latitude * 10000d) + ':' + Math.round(candidate.longitude * 10000d);
        if (!seen.add(key)) return;
        out.add(new Result(candidate.name, candidate.source, candidate.category,
                candidate.latitude, candidate.longitude, distance));
    }

    private static boolean matches(String searchable, String wanted) {
        if (wanted == null || wanted.isEmpty()) return true;
        String haystack = normalize(searchable);
        if (haystack.contains(wanted)) return true;
        String[] words = wanted.split(" ");
        for (String word : words) {
            if (!word.isEmpty() && !haystack.contains(word)) return false;
        }
        return true;
    }

    private static Candidate candidate(List<Tag> tags, LatLong position) {
        if (position == null) return null;
        Classification classification = classify(tags);
        if (classification == null) return null;
        String ar = tagValue(tags, "name:ar");
        String name = tagValue(tags, "name");
        String en = tagValue(tags, "name:en");
        String primary = firstNotBlank(ar, name, en);
        if (primary == null || primary.trim().isEmpty()) {
            if (!classification.allowUnnamed) return null;
            primary = classification.source;
        }
        String aliases = joinNonBlank(
                ar, name, en,
                tagValue(tags, "alt_name"),
                tagValue(tags, "old_name"),
                tagValue(tags, "loc_name"),
                tagValue(tags, "short_name"),
                tagValue(tags, "official_name"));
        String searchText = primary + " " + aliases + " " + classification.source + " " + classification.category;
        return new Candidate(primary.trim(), classification.source, classification.category,
                position.latitude, position.longitude, searchText);
    }

    private static Classification classify(List<Tag> tags) {
        String amenity = lower(tagValue(tags, "amenity"));
        String healthcare = lower(tagValue(tags, "healthcare"));
        String shop = lower(tagValue(tags, "shop"));
        String tourism = lower(tagValue(tags, "tourism"));
        String place = lower(tagValue(tags, "place"));
        String waterway = lower(tagValue(tags, "waterway"));
        String natural = lower(tagValue(tags, "natural"));
        String leisure = lower(tagValue(tags, "leisure"));
        String emergency = lower(tagValue(tags, "emergency"));
        String publicTransport = lower(tagValue(tags, "public_transport"));
        String manMade = lower(tagValue(tags, "man_made"));

        if (waterway != null || oneOf(natural, "valley", "gully"))
            return new Classification("وادي/شعيب", WADIS, true);
        if (oneOf(natural, "peak", "ridge", "cliff", "saddle"))
            return new Classification("جبل/قمة", MOUNTAINS, true);
        if (oneOf(manMade, "water_well", "water_tower") || oneOf(natural, "spring", "water") || "drinking_water".equals(amenity))
            return new Classification("ماء/بئر", WATER, true);
        if (place != null) {
            if (oneOf(place, "village", "hamlet", "town", "city", "isolated_dwelling"))
                return new Classification("قرية/تجمع", VILLAGES, false);
            return new Classification("مكان", LANDMARKS, false);
        }
        if ("fuel".equals(amenity)) return service("محطة وقود");
        if (oneOf(amenity, "restaurant", "fast_food", "food_court")) return service("مطعم");
        if ("cafe".equals(amenity)) return service("مقهى");
        if (oneOf(amenity, "hospital", "clinic", "doctors") || oneOf(healthcare, "hospital", "clinic", "doctor")) return service("مستشفى/عيادة");
        if ("pharmacy".equals(amenity) || "pharmacy".equals(healthcare)) return service("صيدلية");
        if ("place_of_worship".equals(amenity)) return service("مسجد/دار عبادة");
        if (oneOf(amenity, "bank", "atm")) return service("بنك/صراف");
        if (oneOf(amenity, "police", "fire_station") || emergency != null) return service("طوارئ");
        if (oneOf(amenity, "parking", "parking_entrance")) return service("مواقف");
        if (oneOf(amenity, "car_wash", "car_repair")) return service("خدمات سيارات");
        if (publicTransport != null || "bus_station".equals(amenity)) return service("نقل");
        if (shop != null) return service(oneOf(shop, "supermarket", "convenience", "grocery") ? "تموينات" : "محل/سوق");
        if (tourism != null) return service("سياحة/إقامة");
        if (leisure != null) return new Classification("ترفيه", LANDMARKS, false);
        if (natural != null) return new Classification("معلم طبيعي", LANDMARKS, true);
        return null;
    }

    private static Classification service(String source) {
        return new Classification(source, SERVICES, false);
    }

    private static LatLong representativePosition(Way way) {
        if (way == null) return null;
        if (way.labelPosition != null) return way.labelPosition;
        if (way.latLongs == null || way.latLongs.length == 0 || way.latLongs[0] == null || way.latLongs[0].length == 0) return null;
        LatLong[] ring = way.latLongs[0];
        int count = Math.min(ring.length, 48);
        double lat = 0d;
        double lon = 0d;
        for (int i = 0; i < count; i++) {
            lat += ring[i].latitude;
            lon += ring[i].longitude;
        }
        return new LatLong(lat / count, lon / count);
    }

    private static String tagValue(List<Tag> tags, String key) {
        if (tags == null) return null;
        for (Tag tag : tags) if (key.equalsIgnoreCase(tag.key)) return tag.value;
        return null;
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }

    private static String joinNonBlank(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(value.trim());
        }
        return out.toString();
    }

    private static boolean oneOf(String value, String... choices) {
        if (value == null) return false;
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }

    private static String lower(String value) {
        return value == null || value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
    }

    static String normalize(String value) {
        if (value == null) return "";
        String out = value.trim().toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
                .replace('ـ', ' ');
        return out.replaceAll("[ًٌٍَُِّْٰ]", "").replaceAll("\\s+", " ").trim();
    }

    public static final class Result {
        public final String name;
        public final String source;
        public final String category;
        public final double latitude;
        public final double longitude;
        public final double distanceMeters;

        Result(String name, String source, String category, double latitude, double longitude, double distanceMeters) {
            this.name = name;
            this.source = source;
            this.category = category;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceMeters = distanceMeters;
        }
    }

    private static final class Candidate {
        final String name;
        final String source;
        final String category;
        final double latitude;
        final double longitude;
        final String searchText;

        Candidate(String name, String source, String category, double latitude, double longitude, String searchText) {
            this.name = name;
            this.source = source;
            this.category = category;
            this.latitude = latitude;
            this.longitude = longitude;
            this.searchText = searchText;
        }
    }

    private static final class Classification {
        final String source;
        final String category;
        final boolean allowUnnamed;

        Classification(String source, String category, boolean allowUnnamed) {
            this.source = source;
            this.category = category;
            this.allowUnnamed = allowUnnamed;
        }
    }
}
