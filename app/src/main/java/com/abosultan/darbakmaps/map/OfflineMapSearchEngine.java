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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Persistent, low-memory Arabic search over an installed Mapsforge map and saved places. */
public final class OfflineMapSearchEngine {
    public static final String CATEGORY_ALL = "الكل";
    public static final String CATEGORY_WADIS = "شعاب وأودية";
    public static final String CATEGORY_MOUNTAINS = "جبال";
    public static final String CATEGORY_LANDMARKS = "معالم";
    public static final String CATEGORY_VILLAGES = "قرى";
    public static final String CATEGORY_WATER = "مياه وآبار";
    public static final String CATEGORY_SERVICES = "خدمات";

    private static final int MAX_INDEX_ITEMS = 250_000;
    private static final int INDEX_ZOOM = 9;
    private static final long INDEX_BUDGET_NANOS = 1_400_000_000L;
    private static final int MAX_LOCAL_TILES = 180;

    private volatile boolean complete;
    private volatile boolean failed;
    private volatile boolean truncated;
    private volatile int indexedCount;
    private String mapIdentity;

    public synchronized boolean isComplete() { return complete; }
    public synchronized boolean hasFailed() { return failed; }
    public synchronized boolean isTruncated() { return truncated; }
    public synchronized int indexedCount() { return indexedCount; }

    public synchronized void clear() {
        complete = false;
        failed = false;
        truncated = false;
        indexedCount = 0;
        mapIdentity = null;
    }

    /** Compatibility entry point used by the main text search. */
    public synchronized List<Result> search(String query, File activeMap,
                                             List<PlaceRepository.Place> savedPlaces,
                                             Double currentLatitude, Double currentLongitude,
                                             int limit) {
        SearchRequest request = new SearchRequest(query, CATEGORY_ALL,
                currentLatitude, currentLongitude, 0f, false, limit);
        return search(request, activeMap, savedPlaces);
    }

    public synchronized List<Result> search(SearchRequest request, File activeMap,
                                             List<PlaceRepository.Place> savedPlaces) {
        String wanted = normalize(request.query);
        if (activeMap != null && activeMap.isFile()) {
            ensureIndex(activeMap);
        } else {
            complete = true;
            failed = false;
            truncated = false;
            indexedCount = 0;
        }

        List<RankedResult> ranked = new ArrayList<>();
        if (savedPlaces != null) {
            for (PlaceRepository.Place place : savedPlaces) {
                Result result = new Result("saved:" + place.id,
                        place.name == null || place.name.trim().isEmpty() ? "موقع محفوظ" : place.name,
                        place.latitude, place.longitude, "موقع محفوظ", "محفوظات", true,
                        distanceMeters(request.centerLatitude, request.centerLongitude,
                                place.latitude, place.longitude));
                addIfMatches(ranked, result, wanted, request);
            }
        }

        if (activeMap != null && activeMap.isFile()) {
            readMatchesFromPersistentIndex(activeMap, ranked, wanted, request);
            // Nearby searches must work even before the national index reaches this region.
            if (request.centerLatitude != null && request.centerLongitude != null
                    && request.radiusMeters > 0f) {
                scanNearbyDirect(activeMap, ranked, wanted, request);
            }
        }

        ranked.sort((a, b) -> {
            if (request.sortNearest) {
                int byDistance = Float.compare(distanceOrMax(a.result), distanceOrMax(b.result));
                if (byDistance != 0) return byDistance;
            }
            int byScore = Integer.compare(b.score, a.score);
            if (byScore != 0) return byScore;
            int byDistance = Float.compare(distanceOrMax(a.result), distanceOrMax(b.result));
            if (byDistance != 0) return byDistance;
            return a.result.name.compareTo(b.result.name);
        });

        int wantedLimit = Math.max(5, Math.min(200, request.limit));
        LinkedHashMap<String, Result> dedup = new LinkedHashMap<>();
        for (RankedResult item : ranked) {
            String key = dedupeKey(item.result);
            if (!dedup.containsKey(key)) dedup.put(key, item.result);
            if (dedup.size() >= wantedLimit) break;
        }
        return new ArrayList<>(dedup.values());
    }

    private void ensureIndex(File map) {
        String identity = identity(map);
        File index = indexFile(map, identity);
        File state = stateFile(map);
        IndexState saved = readState(state);
        if (!identity.equals(saved.identity)) {
            deleteOldIndexes(map, identity);
            saved = new IndexState(identity, 0, 0, false, false);
            writeState(state, saved);
        }
        mapIdentity = identity;
        complete = saved.complete;
        truncated = saved.truncated;
        indexedCount = saved.count;
        if (complete || truncated) return;

        MapFile mapFile = null;
        try {
            mapFile = new MapFile(map, "ar");
            BoundingBox bounds = mapFile.boundingBox();
            byte zoom = indexZoom(mapFile);
            int tileSize = Math.max(128, mapFile.getMapFileInfo().tilePixelSize);
            long minX = MercatorProjection.longitudeToTileX(bounds.minLongitude, zoom);
            long maxX = MercatorProjection.longitudeToTileX(bounds.maxLongitude, zoom);
            long minY = MercatorProjection.latitudeToTileY(bounds.maxLatitude, zoom);
            long maxY = MercatorProjection.latitudeToTileY(bounds.minLatitude, zoom);
            long totalTiles = (maxX - minX + 1L) * (maxY - minY + 1L);
            long deadline = System.nanoTime() + INDEX_BUDGET_NANOS;
            Set<String> seen = readIds(index);
            int next = saved.nextTile;
            int count = saved.count;

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(index, true))) {
                while (next < totalTiles && System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                    long width = maxY - minY + 1L;
                    long offsetX = next / width;
                    long offsetY = next % width;
                    long x = minX + offsetX;
                    long y = minY + offsetY;
                    next++;
                    Tile tile = new Tile((int) x, (int) y, zoom, tileSize);
                    MapReadResult data;
                    try {
                        data = mapFile.readMapData(tile);
                    } catch (Exception error) {
                        failed = true;
                        continue;
                    }
                    if (data == null) continue;
                    for (PointOfInterest poi : data.pois) {
                        IndexItem item = createItem(poi.tags, poi.position, "معلم");
                        if (item != null && seen.add(item.id)) {
                            writeIndexItem(writer, item);
                            count++;
                            if (count >= MAX_INDEX_ITEMS) break;
                        }
                    }
                    if (count >= MAX_INDEX_ITEMS) break;
                    for (Way way : data.ways) {
                        LatLong position = representativePosition(way);
                        if (position == null) continue;
                        IndexItem item = createItem(way.tags, position, "معلم");
                        if (item != null && seen.add(item.id)) {
                            writeIndexItem(writer, item);
                            count++;
                            if (count >= MAX_INDEX_ITEMS) break;
                        }
                    }
                    writer.flush();
                }
            }
            boolean nowTruncated = count >= MAX_INDEX_ITEMS && next < totalTiles;
            boolean nowComplete = next >= totalTiles;
            IndexState nextState = new IndexState(identity, next, count, nowComplete, nowTruncated);
            writeState(state, nextState);
            complete = nowComplete;
            truncated = nowTruncated;
            indexedCount = count;
        } catch (Exception error) {
            failed = true;
        } finally {
            if (mapFile != null) mapFile.close();
        }
    }

    private void readMatchesFromPersistentIndex(File map, List<RankedResult> out,
                                                 String wanted, SearchRequest request) {
        File index = indexFile(map, identity(map));
        if (!index.isFile()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(index))) {
            String line;
            while ((line = reader.readLine()) != null) {
                IndexItem item = parseIndexItem(line);
                if (item == null) continue;
                Result result = item.toResult(distanceMeters(request.centerLatitude, request.centerLongitude,
                        item.latitude, item.longitude));
                addIfMatches(out, result, wanted, request);
            }
        } catch (IOException error) {
            failed = true;
        }
    }

    private void scanNearbyDirect(File map, List<RankedResult> out, String wanted, SearchRequest request) {
        MapFile mapFile = null;
        try {
            mapFile = new MapFile(map, "ar");
            byte zoom = indexZoom(mapFile);
            int tileSize = Math.max(128, mapFile.getMapFileInfo().tilePixelSize);
            long centerX = MercatorProjection.longitudeToTileX(request.centerLongitude, zoom);
            long centerY = MercatorProjection.latitudeToTileY(request.centerLatitude, zoom);
            double tileMeters = 40075016.686d * Math.cos(Math.toRadians(request.centerLatitude)) / (1L << zoom);
            int radiusTiles = Math.max(1, (int) Math.ceil(request.radiusMeters / Math.max(1000d, tileMeters)));
            int scanned = 0;
            for (int dx = -radiusTiles; dx <= radiusTiles && scanned < MAX_LOCAL_TILES; dx++) {
                for (int dy = -radiusTiles; dy <= radiusTiles && scanned < MAX_LOCAL_TILES; dy++) {
                    Tile tile = new Tile((int) (centerX + dx), (int) (centerY + dy), zoom, tileSize);
                    scanned++;
                    MapReadResult data;
                    try {
                        data = mapFile.readMapData(tile);
                    } catch (Exception error) {
                        continue;
                    }
                    if (data == null) continue;
                    for (PointOfInterest poi : data.pois) {
                        IndexItem item = createItem(poi.tags, poi.position, "معلم");
                        if (item != null) addIfMatches(out, item.toResult(distanceMeters(
                                request.centerLatitude, request.centerLongitude, item.latitude, item.longitude)), wanted, request);
                    }
                    for (Way way : data.ways) {
                        LatLong position = representativePosition(way);
                        IndexItem item = position == null ? null : createItem(way.tags, position, "معلم");
                        if (item != null) addIfMatches(out, item.toResult(distanceMeters(
                                request.centerLatitude, request.centerLongitude, item.latitude, item.longitude)), wanted, request);
                    }
                }
            }
        } catch (Exception error) {
            failed = true;
        } finally {
            if (mapFile != null) mapFile.close();
        }
    }

    private void addIfMatches(List<RankedResult> out, Result result, String wanted, SearchRequest request) {
        if (!CATEGORY_ALL.equals(request.category) && !request.category.equals(result.category)) return;
        if (request.radiusMeters > 0f && result.distanceMeters != null && result.distanceMeters > request.radiusMeters) return;
        if (request.radiusMeters > 0f && result.distanceMeters == null) return;
        String haystack = normalize(result.name + " " + result.source + " " + result.searchText);
        int score = wanted.isEmpty() ? 10 : matchScore(haystack, wanted);
        if (score > 0) out.add(new RankedResult(result, score));
    }

    private int matchScore(String value, String query) {
        if (query.isEmpty()) return 10;
        if (value.equals(query)) return 100;
        if (value.startsWith(query)) return 88;
        if (value.contains(query)) return 70;
        String[] words = query.split(" ");
        for (String word : words) if (!word.isEmpty() && !value.contains(word)) return 0;
        return 48;
    }

    private IndexItem createItem(List<Tag> tags, LatLong position, String fallbackSource) {
        if (position == null) return null;
        Classification classification = classify(tags, fallbackSource);
        String primary = firstNotBlank(tagValue(tags, "name:ar"), tagValue(tags, "name"), tagValue(tags, "name:en"));
        String aliases = joinNonBlank(tagValue(tags, "alt_name"), tagValue(tags, "old_name"),
                tagValue(tags, "loc_name"), tagValue(tags, "short_name"), tagValue(tags, "official_name"),
                tagValue(tags, "name:ar"), tagValue(tags, "name:en"));
        if (primary == null && !classification.indexUnnamed) return null;
        String shown = primary == null ? "بدون اسم" : primary.trim();
        String searchable = shown + " " + aliases + " " + classification.source + " " + classification.category;
        String id = normalize(shown + "|" + classification.source) + ':'
                + Math.round(position.latitude * 10000d) + ':' + Math.round(position.longitude * 10000d);
        return new IndexItem(id, shown, classification.source, classification.category,
                position.latitude, position.longitude, searchable);
    }

    private Classification classify(List<Tag> tags, String fallback) {
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
        String manMade = lower(tagValue(tags, "man_made"));

        if (waterway != null || oneOf(natural, "valley", "gully"))
            return new Classification("وادي/شعيب", CATEGORY_WADIS, true);
        if (oneOf(natural, "peak", "ridge", "cliff", "saddle"))
            return new Classification("جبل/قمة", CATEGORY_MOUNTAINS, true);
        if (oneOf(manMade, "water_well", "water_tower") || oneOf(natural, "spring", "water") || "drinking_water".equals(amenity))
            return new Classification("ماء/بئر", CATEGORY_WATER, true);
        if (place != null) {
            if (oneOf(place, "village", "hamlet", "town", "city", "isolated_dwelling"))
                return new Classification("قرية/تجمع", CATEGORY_VILLAGES, false);
            return new Classification("مكان", CATEGORY_LANDMARKS, false);
        }
        if ("fuel".equals(amenity)) return service("محطة وقود");
        if (oneOf(amenity, "restaurant", "fast_food", "food_court")) return service("مطعم");
        if ("cafe".equals(amenity)) return service("مقهى");
        if (oneOf(amenity, "hospital", "clinic", "doctors") || oneOf(healthcare, "hospital", "clinic", "doctor")) return service("مستشفى/عيادة");
        if ("pharmacy".equals(amenity) || "pharmacy".equals(healthcare)) return service("صيدلية");
        if ("place_of_worship".equals(amenity)) return service("مسجد/دار عبادة");
        if (oneOf(amenity, "bank", "atm")) return service("بنك/صراف");
        if (oneOf(amenity, "school", "college", "university", "kindergarten")) return service("تعليم");
        if (oneOf(amenity, "police", "fire_station") || emergency != null) return service("طوارئ");
        if (oneOf(amenity, "parking", "parking_entrance")) return service("مواقف");
        if (oneOf(amenity, "car_wash", "car_repair")) return service("خدمات سيارات");
        if (publicTransport != null || "bus_station".equals(amenity)) return service("نقل");
        if (shop != null) return service(oneOf(shop, "supermarket", "convenience", "grocery") ? "تموينات" : "محل/سوق");
        if (tourism != null) return service("سياحة/إقامة");
        if (leisure != null) return new Classification("ترفيه", CATEGORY_LANDMARKS, false);
        if (office != null) return service("نشاط تجاري");
        if (oneOf(highway, "track", "path", "unclassified")) return new Classification("طريق بري", CATEGORY_LANDMARKS, false);
        if (natural != null) return new Classification("معلم طبيعي", CATEGORY_LANDMARKS, true);
        return new Classification(fallback, CATEGORY_LANDMARKS, false);
    }

    private Classification service(String source) {
        return new Classification(source, CATEGORY_SERVICES, false);
    }

    private LatLong representativePosition(Way way) {
        if (way == null) return null;
        if (way.labelPosition != null) return way.labelPosition;
        if (way.latLongs != null && way.latLongs.length > 0 && way.latLongs[0] != null && way.latLongs[0].length > 0) {
            LatLong[] ring = way.latLongs[0];
            double lat = 0d, lon = 0d;
            int count = Math.min(ring.length, 64);
            for (int i = 0; i < count; i++) {
                lat += ring[i].latitude;
                lon += ring[i].longitude;
            }
            return new LatLong(lat / count, lon / count);
        }
        return null;
    }

    private byte indexZoom(MapFile mapFile) {
        int min = mapFile.getMapFileInfo().zoomLevelMin;
        int max = mapFile.getMapFileInfo().zoomLevelMax;
        return (byte) Math.max(min, Math.min(max, INDEX_ZOOM));
    }

    private Set<String> readIds(File index) throws IOException {
        Set<String> ids = new HashSet<>();
        if (!index.isFile()) return ids;
        try (BufferedReader reader = new BufferedReader(new FileReader(index))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab > 0) ids.add(line.substring(0, tab));
            }
        }
        return ids;
    }

    private void writeIndexItem(BufferedWriter writer, IndexItem item) throws IOException {
        writer.write(clean(item.id)); writer.write('\t');
        writer.write(clean(item.name)); writer.write('\t');
        writer.write(clean(item.source)); writer.write('\t');
        writer.write(clean(item.category)); writer.write('\t');
        writer.write(Double.toString(item.latitude)); writer.write('\t');
        writer.write(Double.toString(item.longitude)); writer.write('\t');
        writer.write(clean(item.searchText)); writer.newLine();
    }

    private IndexItem parseIndexItem(String line) {
        try {
            String[] v = line.split("\\t", 7);
            if (v.length != 7) return null;
            return new IndexItem(v[0], v[1], v[2], v[3], Double.parseDouble(v[4]),
                    Double.parseDouble(v[5]), v[6]);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private String identity(File map) {
        return map.length() + "-" + map.lastModified();
    }

    private File indexFile(File map, String identity) {
        return new File(map.getParentFile(), ".darbak-search-" + identity + ".idx");
    }

    private File stateFile(File map) {
        return new File(map.getParentFile(), ".darbak-search.state");
    }

    private void deleteOldIndexes(File map, String keepIdentity) {
        File[] files = map.getParentFile().listFiles();
        if (files == null) return;
        String keep = ".darbak-search-" + keepIdentity + ".idx";
        for (File file : files) {
            if (file.getName().startsWith(".darbak-search-") && file.getName().endsWith(".idx")
                    && !keep.equals(file.getName())) file.delete();
        }
    }

    private IndexState readState(File state) {
        if (!state.isFile()) return IndexState.empty();
        try (BufferedReader reader = new BufferedReader(new FileReader(state))) {
            String identity = reader.readLine();
            int next = Integer.parseInt(reader.readLine());
            int count = Integer.parseInt(reader.readLine());
            boolean done = Boolean.parseBoolean(reader.readLine());
            boolean cut = Boolean.parseBoolean(reader.readLine());
            return new IndexState(identity == null ? "" : identity, next, count, done, cut);
        } catch (Exception error) {
            return IndexState.empty();
        }
    }

    private void writeState(File state, IndexState value) {
        File pending = new File(state.getAbsolutePath() + ".pending");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pending))) {
            writer.write(value.identity); writer.newLine();
            writer.write(Integer.toString(value.nextTile)); writer.newLine();
            writer.write(Integer.toString(value.count)); writer.newLine();
            writer.write(Boolean.toString(value.complete)); writer.newLine();
            writer.write(Boolean.toString(value.truncated)); writer.newLine();
        } catch (IOException error) {
            failed = true;
            pending.delete();
            return;
        }
        if (state.exists() && !state.delete()) {
            pending.delete();
            failed = true;
            return;
        }
        if (!pending.renameTo(state)) {
            pending.delete();
            failed = true;
        }
    }

    private String tagValue(List<Tag> tags, String key) {
        if (tags == null) return null;
        for (Tag tag : tags) if (key.equalsIgnoreCase(tag.key)) return tag.value;
        return null;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }

    private String joinNonBlank(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(value.trim());
        }
        return out.toString();
    }

    private boolean oneOf(String value, String... choices) {
        if (value == null) return false;
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }

    private String lower(String value) {
        return value == null || value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
    }

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
        return (float) (earthRadius * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0d, 1d - a))));
    }

    private float distanceOrMax(Result result) {
        return result.distanceMeters == null ? Float.MAX_VALUE : result.distanceMeters;
    }

    private String dedupeKey(Result result) {
        return normalize(result.name + "|" + result.source) + ':'
                + Math.round(result.latitude * 10000d) + ':' + Math.round(result.longitude * 10000d);
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    static String normalize(String value) {
        if (value == null) return "";
        String out = value.trim().toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ٱ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
                .replace('ؤ', 'و').replace('ئ', 'ي').replace('ـ', ' ');
        return out.replaceAll("[ًٌٍَُِّْٰ]", "").replaceAll("\\s+", " ").trim();
    }

    public static final class SearchRequest {
        public final String query;
        public final String category;
        public final Double centerLatitude;
        public final Double centerLongitude;
        public final float radiusMeters;
        public final boolean sortNearest;
        public final int limit;

        public SearchRequest(String query, String category, Double centerLatitude, Double centerLongitude,
                             float radiusMeters, boolean sortNearest, int limit) {
            this.query = query == null ? "" : query;
            this.category = category == null ? CATEGORY_ALL : category;
            this.centerLatitude = centerLatitude;
            this.centerLongitude = centerLongitude;
            this.radiusMeters = Math.max(0f, radiusMeters);
            this.sortNearest = sortNearest;
            this.limit = limit;
        }
    }

    public static final class Result {
        public final String id;
        public final String name;
        public final double latitude;
        public final double longitude;
        public final String source;
        public final String category;
        public final boolean saved;
        public final Float distanceMeters;
        final String searchText;

        Result(String id, String name, double latitude, double longitude, String source,
               String category, boolean saved, Float distanceMeters) {
            this(id, name, latitude, longitude, source, category, saved, distanceMeters,
                    name + " " + source + " " + category);
        }

        Result(String id, String name, double latitude, double longitude, String source,
               String category, boolean saved, Float distanceMeters, String searchText) {
            this.id = id;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source;
            this.category = category;
            this.saved = saved;
            this.distanceMeters = distanceMeters;
            this.searchText = searchText == null ? "" : searchText;
        }
    }

    private static final class IndexItem {
        final String id;
        final String name;
        final String source;
        final String category;
        final double latitude;
        final double longitude;
        final String searchText;

        IndexItem(String id, String name, String source, String category,
                  double latitude, double longitude, String searchText) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.category = category;
            this.latitude = latitude;
            this.longitude = longitude;
            this.searchText = searchText;
        }

        Result toResult(Float distance) {
            return new Result(id, name, latitude, longitude, source, category, false, distance, searchText);
        }
    }

    private static final class RankedResult {
        final Result result;
        final int score;
        RankedResult(Result result, int score) { this.result = result; this.score = score; }
    }

    private static final class Classification {
        final String source;
        final String category;
        final boolean indexUnnamed;
        Classification(String source, String category, boolean indexUnnamed) {
            this.source = source;
            this.category = category;
            this.indexUnnamed = indexUnnamed;
        }
    }

    private static final class IndexState {
        final String identity;
        final int nextTile;
        final int count;
        final boolean complete;
        final boolean truncated;

        IndexState(String identity, int nextTile, int count, boolean complete, boolean truncated) {
            this.identity = identity;
            this.nextTile = nextTile;
            this.count = count;
            this.complete = complete;
            this.truncated = truncated;
        }

        static IndexState empty() {
            return new IndexState("", 0, 0, false, false);
        }
    }
}
