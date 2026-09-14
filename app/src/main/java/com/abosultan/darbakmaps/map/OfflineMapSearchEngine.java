package com.abosultan.darbakmaps.map;

import com.abosultan.darbakmaps.data.PlaceRepository;

import org.mapsforge.core.model.BoundingBox;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Location-first Arabic search over an installed Mapsforge map and saved places. */
public final class OfflineMapSearchEngine {
    private static final int MAX_INDEX_ITEMS = 10_000;
    private static final int INDEX_ZOOM = 9;
    private static final int NEARBY_RADIUS_TILES = 5;
    private static final long INDEX_BUDGET_NANOS = 2_800_000_000L;

    private static final Set<String> GENERIC_SERVICE_NAMES = new HashSet<>();

    static {
        Collections.addAll(GENERIC_SERVICE_NAMES,
                "محطة وقود", "مطعم", "مقهى", "مستشفى/عيادة", "صيدلية",
                "مسجد/دار عبادة", "بنك/صراف", "تعليم", "طوارئ", "مواقف",
                "خدمات سيارات", "نقل", "تموينات/سوبرماركت", "سوق/مجمع تجاري",
                "محل تجاري", "سياحة/إقامة", "ترفيه");
    }

    private String indexedKey;
    private String activeQuery = "";
    private int nextTile;
    private volatile boolean complete;
    private volatile boolean failed;
    private List<Result> mapIndex = Collections.emptyList();

    public synchronized boolean isComplete() { return complete; }
    public synchronized boolean hasFailed() { return failed; }

    public synchronized void clear() {
        indexedKey = null;
        activeQuery = "";
        nextTile = 0;
        complete = false;
        failed = false;
        mapIndex = Collections.emptyList();
    }

    public synchronized List<Result> search(String query, File activeMap,
                                             List<PlaceRepository.Place> savedPlaces,
                                             Double currentLatitude, Double currentLongitude,
                                             int limit) {
        String wanted = normalize(query);
        if (wanted.isEmpty()) return Collections.emptyList();

        if (!wanted.equals(activeQuery)) {
            indexedKey = null;
            nextTile = 0;
            complete = false;
            failed = false;
            mapIndex = Collections.emptyList();
            activeQuery = wanted;
        }
        if (activeMap != null && activeMap.isFile()) {
            ensureIndexed(activeMap, currentLatitude, currentLongitude);
        } else {
            indexedKey = null;
            nextTile = 0;
            mapIndex = Collections.emptyList();
            complete = true;
        }

        List<RankedResult> ranked = new ArrayList<>();
        for (PlaceRepository.Place place : savedPlaces) {
            Result result = new Result("saved:" + place.id, place.name,
                    place.latitude, place.longitude, "موقع محفوظ", true,
                    distanceMeters(currentLatitude, currentLongitude, place.latitude, place.longitude));
            addRanked(ranked, result, wanted);
        }
        for (Result item : mapIndex) {
            Result result = item.withDistance(distanceMeters(
                    currentLatitude, currentLongitude, item.latitude, item.longitude));
            addRanked(ranked, result, wanted);
        }

        ranked.sort((first, second) -> {
            int byScore = Integer.compare(second.score, first.score);
            if (byScore != 0) return byScore;
            float firstDistance = first.result.distanceMeters == null ? Float.MAX_VALUE : first.result.distanceMeters;
            float secondDistance = second.result.distanceMeters == null ? Float.MAX_VALUE : second.result.distanceMeters;
            int byDistance = Float.compare(firstDistance, secondDistance);
            if (byDistance != 0) return byDistance;
            return Integer.compare(first.result.name.length(), second.result.name.length());
        });

        int wantedLimit = Math.max(5, Math.min(80, limit));
        List<Result> output = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RankedResult item : ranked) {
            String key = normalize(item.result.name) + ':'
                    + Math.round(item.result.latitude * 10_000d) + ':'
                    + Math.round(item.result.longitude * 10_000d);
            if (seen.add(key)) output.add(item.result);
            if (output.size() >= wantedLimit) break;
        }
        return output;
    }

    private void addRanked(List<RankedResult> output, Result result, String query) {
        int score = matchScore(normalize(result.name + " " + result.source), query);
        if (score > 0) output.add(new RankedResult(result, score));
    }

    private int matchScore(String value, String query) {
        if (value.equals(query)) return 100;
        if (value.startsWith(query)) return 85;
        if (value.contains(query)) return 65;
        String[] words = query.split(" ");
        for (String word : words) if (word.isEmpty() || !value.contains(word)) return 0;
        return 45;
    }

    private void ensureIndexed(File file, Double latitude, Double longitude) {
        TileRef focus = focusTile(file, latitude, longitude);
        String key = file.getAbsolutePath() + ':' + file.length() + ':' + file.lastModified()
                + ':' + focus.x + ':' + focus.y + ':' + activeQuery;
        if (!key.equals(indexedKey)) {
            mapIndex = Collections.emptyList();
            nextTile = 0;
            complete = false;
            failed = false;
            indexedKey = key;
        }
        if (!complete) mapIndex = buildIndex(file, latitude, longitude);
    }

    private TileRef focusTile(File file, Double latitude, Double longitude) {
        MapFile mapFile = null;
        try {
            mapFile = new MapFile(file, "ar");
            BoundingBox bounds = mapFile.boundingBox();
            byte zoom = indexZoom(mapFile);
            LatLong center = bounds.getCenterPoint();
            double lat = inside(latitude, bounds.minLatitude, bounds.maxLatitude) ? latitude : center.latitude;
            double lon = inside(longitude, bounds.minLongitude, bounds.maxLongitude) ? longitude : center.longitude;
            return new TileRef(MercatorProjection.longitudeToTileX(lon, zoom),
                    MercatorProjection.latitudeToTileY(lat, zoom));
        } catch (Exception ignored) {
            return new TileRef(0L, 0L);
        } finally {
            if (mapFile != null) mapFile.close();
        }
    }

    private List<Result> buildIndex(File file, Double latitude, Double longitude) {
        LinkedHashMap<String, Result> output = new LinkedHashMap<>();
        for (Result item : mapIndex) output.put(item.id, item);
        final long deadline = System.nanoTime() + INDEX_BUDGET_NANOS;
        MapFile mapFile = null;
        try {
            mapFile = new MapFile(file, "ar");
            BoundingBox bounds = mapFile.boundingBox();
            byte zoom = indexZoom(mapFile);
            int tileSize = Math.max(128, mapFile.getMapFileInfo().tilePixelSize);
            long minX = MercatorProjection.longitudeToTileX(bounds.minLongitude, zoom);
            long maxX = MercatorProjection.longitudeToTileX(bounds.maxLongitude, zoom);
            long minY = MercatorProjection.latitudeToTileY(bounds.maxLatitude, zoom);
            long maxY = MercatorProjection.latitudeToTileY(bounds.minLatitude, zoom);

            LatLong center = bounds.getCenterPoint();
            double focusLatitude = inside(latitude, bounds.minLatitude, bounds.maxLatitude) ? latitude : center.latitude;
            double focusLongitude = inside(longitude, bounds.minLongitude, bounds.maxLongitude) ? longitude : center.longitude;
            long focusX = clamp(MercatorProjection.longitudeToTileX(focusLongitude, zoom), minX, maxX);
            long focusY = clamp(MercatorProjection.latitudeToTileY(focusLatitude, zoom), minY, maxY);

            long rawCount = (maxX - minX + 1L) * (maxY - minY + 1L);
            List<TileRef> tiles = new ArrayList<>((int) Math.min(4096L, Math.max(16L, rawCount)));
            for (long x = minX; x <= maxX; x++) for (long y = minY; y <= maxY; y++) tiles.add(new TileRef(x, y));
            final long orderedFocusX = focusX;
            final long orderedFocusY = focusY;
            tiles.sort(Comparator.comparingLong(tile -> tile.distanceSquared(orderedFocusX, orderedFocusY)));

            while (nextTile < tiles.size()) {
                if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) break;
                TileRef tileRef = tiles.get(nextTile++);
                Tile tile = new Tile((int) tileRef.x, (int) tileRef.y, zoom, tileSize);
                boolean nearby = Math.abs(tileRef.x - focusX) <= NEARBY_RADIUS_TILES
                        && Math.abs(tileRef.y - focusY) <= NEARBY_RADIUS_TILES;
                MapReadResult data;
                try {
                    data = nearby ? mapFile.readMapData(tile) : mapFile.readNamedItems(tile);
                } catch (Exception error) {
                    failed = true;
                    continue;
                }
                if (data == null) continue;
                for (PointOfInterest poi : data.pois) {
                    if (output.size() >= MAX_INDEX_ITEMS) break;
                    addItem(output, poi.tags, poi.position, "معلم");
                }
                for (Way way : data.ways) {
                    if (output.size() >= MAX_INDEX_ITEMS) break;
                    LatLong position = way.labelPosition;
                    if (position == null && way.latLongs.length > 0 && way.latLongs[0].length > 0) position = way.latLongs[0][0];
                    if (position != null) addItem(output, way.tags, position, "اسم على الخريطة");
                }
            }
            complete = nextTile >= tiles.size();
        } catch (Exception error) {
            failed = true;
        } finally {
            if (mapFile != null) mapFile.close();
        }
        return new ArrayList<>(output.values());
    }

    private byte indexZoom(MapFile mapFile) {
        int min = mapFile.getMapFileInfo().zoomLevelMin;
        int max = mapFile.getMapFileInfo().zoomLevelMax;
        return (byte) Math.max(min, Math.min(max, INDEX_ZOOM));
    }

    private void addItem(Map<String, Result> output, List<Tag> tags, LatLong position, String fallbackSource) {
        String source = classify(tags, fallbackSource);
        String name = firstNotBlank(tagValue(tags, "name:ar"), tagValue(tags, "name"), tagValue(tags, "name:en"));
        if (name == null && GENERIC_SERVICE_NAMES.contains(source)) name = source;
        if (name == null) return;
        name = name.trim();
        if (name.length() < 2 || matchScore(normalize(name + " " + source), activeQuery) <= 0) return;
        String key = normalize(name) + ':' + Math.round(position.latitude * 10_000d) + ':' + Math.round(position.longitude * 10_000d);
        String id = "map:" + key;
        if (output.size() < MAX_INDEX_ITEMS && !output.containsKey(id)) {
            output.put(id, new Result(id, name, position.latitude, position.longitude, source, false, null));
        }
    }

    private String classify(List<Tag> tags, String fallback) {
        String amenity = lower(tagValue(tags, "amenity"));
        String healthcare = lower(tagValue(tags, "healthcare"));
        String shop = lower(tagValue(tags, "shop"));
        String tourism = lower(tagValue(tags, "tourism"));
        String office = lower(tagValue(tags, "office"));
        String place = lower(tagValue(tags, "place"));
        String highway = lower(tagValue(tags, "highway"));
        String waterway = lower(tagValue(tags, "waterway"));
        String natural = lower(tagValue(tags, "natural"));
        String leisure = lower(tagValue(tags, "leisure"));
        String emergency = lower(tagValue(tags, "emergency"));
        String publicTransport = lower(tagValue(tags, "public_transport"));
        if ("fuel".equals(amenity)) return "محطة وقود";
        if (oneOf(amenity, "restaurant", "fast_food", "food_court")) return "مطعم";
        if ("cafe".equals(amenity)) return "مقهى";
        if (oneOf(amenity, "hospital", "clinic", "doctors") || oneOf(healthcare, "hospital", "clinic", "doctor")) return "مستشفى/عيادة";
        if ("pharmacy".equals(amenity) || "pharmacy".equals(healthcare)) return "صيدلية";
        if ("place_of_worship".equals(amenity)) return "مسجد/دار عبادة";
        if (oneOf(amenity, "bank", "atm")) return "بنك/صراف";
        if (oneOf(amenity, "school", "college", "university", "kindergarten")) return "تعليم";
        if (oneOf(amenity, "police", "fire_station") || emergency != null) return "طوارئ";
        if (oneOf(amenity, "parking", "parking_entrance")) return "مواقف";
        if (oneOf(amenity, "car_wash", "car_repair")) return "خدمات سيارات";
        if (publicTransport != null || "bus_station".equals(amenity)) return "نقل";
        if (shop != null) {
            if (oneOf(shop, "supermarket", "convenience", "grocery")) return "تموينات/سوبرماركت";
            if (oneOf(shop, "mall", "department_store")) return "سوق/مجمع تجاري";
            return "محل تجاري";
        }
        if (tourism != null) return "سياحة/إقامة";
        if (leisure != null) return "ترفيه";
        if (office != null) return "نشاط تجاري";
        if (place != null) {
            if ("city".equals(place)) return "مدينة";
            if ("town".equals(place)) return "بلدة";
            if ("village".equals(place)) return "قرية";
            if ("hamlet".equals(place)) return "هجرة/تجمع";
            return "مكان";
        }
        if (waterway != null) return "وادي/مجرى";
        if (oneOf(highway, "track", "path", "unclassified")) return "طريق بري";
        if (natural != null) return "معلم طبيعي";
        return fallback;
    }

    private String tagValue(List<Tag> tags, String key) {
        for (Tag tag : tags) if (key.equalsIgnoreCase(tag.key)) return tag.value;
        return null;
    }
    private String firstNotBlank(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }
    private boolean oneOf(String value, String... choices) {
        if (value == null) return false;
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }
    private String lower(String value) { return value == null || value.isEmpty() ? null : value.toLowerCase(Locale.ROOT); }
    private boolean inside(Double value, double min, double max) { return value != null && value >= min && value <= max; }
    private long clamp(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }

    private Float distanceMeters(Double fromLat, Double fromLon, double toLat, double toLon) {
        if (fromLat == null || fromLon == null) return null;
        double earthRadius = 6_371_000d;
        double lat1 = Math.toRadians(fromLat);
        double lat2 = Math.toRadians(toLat);
        double deltaLat = Math.toRadians(toLat - fromLat);
        double deltaLon = Math.toRadians(toLon - fromLon);
        double sinLat = Math.sin(deltaLat / 2d);
        double sinLon = Math.sin(deltaLon / 2d);
        double a = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return (float) (earthRadius * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a)));
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
                .replaceAll("[ًٌٍَُِّْـ]", "").replaceAll("\\s+", " ");
    }

    public static final class Result {
        public final String id;
        public final String name;
        public final double latitude;
        public final double longitude;
        public final String source;
        public final boolean saved;
        public final Float distanceMeters;
        Result(String id, String name, double latitude, double longitude, String source, boolean saved, Float distanceMeters) {
            this.id = id; this.name = name; this.latitude = latitude; this.longitude = longitude;
            this.source = source; this.saved = saved; this.distanceMeters = distanceMeters;
        }
        Result withDistance(Float distance) { return new Result(id, name, latitude, longitude, source, saved, distance); }
    }
    private static final class RankedResult {
        final Result result; final int score;
        RankedResult(Result result, int score) { this.result = result; this.score = score; }
    }
    private static final class TileRef {
        final long x; final long y;
        TileRef(long x, long y) { this.x = x; this.y = y; }
        long distanceSquared(long otherX, long otherY) {
            long dx = x - otherX; long dy = y - otherY; return dx * dx + dy * dy;
        }
    }
}
