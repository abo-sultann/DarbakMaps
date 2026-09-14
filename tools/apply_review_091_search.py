from pathlib import Path

# Replace OfflineMapSearchEngine with a sharded, crash-safe, bounded-query implementation.
Path('app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapSearchEngine.java').write_text(r'''package com.abosultan.darbakmaps.map;

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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Persistent low-memory Arabic search for Mapsforge.
 *
 * The national index is split into fsync'ed tile shards. Progress is derived from validated
 * shard filenames, so a stale state file cannot claim completion when index bytes are missing.
 * Queries use bounded Top-K memory and real page offsets; no fixed item cap permanently stops
 * indexing in the middle of a tile.
 */
public final class OfflineMapSearchEngine {
    public static final String CATEGORY_ALL = "الكل";
    public static final String CATEGORY_WADIS = "شعاب وأودية";
    public static final String CATEGORY_MOUNTAINS = "جبال";
    public static final String CATEGORY_LANDMARKS = "معالم";
    public static final String CATEGORY_VILLAGES = "قرى";
    public static final String CATEGORY_WATER = "مياه وآبار";
    public static final String CATEGORY_SERVICES = "خدمات";

    private static final int INDEX_ZOOM = 9;
    private static final int TILES_PER_SHARD = 16;
    private static final long INDEX_BUDGET_NANOS = 1_400_000_000L;
    private static final int MAX_LOCAL_TILES = 220;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_QUERY_WINDOW = 2000;

    private volatile boolean complete;
    private volatile boolean failed;
    private volatile boolean indexTruncated;
    private volatile boolean queryTruncated;
    private volatile int indexedCount;
    private volatile int nextOffset;
    private String mapIdentity;

    public synchronized boolean isComplete() { return complete; }
    public synchronized boolean hasFailed() { return failed; }
    /** Index truncation means an external resource constraint stopped indexing, not page limiting. */
    public synchronized boolean isTruncated() { return indexTruncated; }
    public synchronized boolean isQueryTruncated() { return queryTruncated; }
    public synchronized boolean hasMoreResults() { return queryTruncated; }
    public synchronized int nextOffset() { return nextOffset; }
    public synchronized int indexedCount() { return indexedCount; }

    public synchronized void clear() {
        complete = false; failed = false; indexTruncated = false; queryTruncated = false;
        indexedCount = 0; nextOffset = 0; mapIdentity = null;
    }

    public synchronized List<Result> search(String query, File activeMap,
                                             List<PlaceRepository.Place> savedPlaces,
                                             Double currentLatitude, Double currentLongitude,
                                             int limit) {
        return search(new SearchRequest(query, CATEGORY_ALL, currentLatitude, currentLongitude,
                0f, false, limit, 0), activeMap, savedPlaces);
    }

    public synchronized List<Result> search(SearchRequest request, File activeMap,
                                             List<PlaceRepository.Place> savedPlaces) {
        failed = false;
        queryTruncated = false;
        nextOffset = request.offset;
        String wanted = normalize(request.query);
        if (activeMap != null && activeMap.isFile()) ensureIndex(activeMap);
        else { complete = true; indexTruncated = false; indexedCount = 0; }

        int pageSize = Math.max(5, Math.min(MAX_PAGE_SIZE, request.limit));
        int window = Math.min(MAX_QUERY_WINDOW, Math.max(pageSize + 1, request.offset + pageSize + 1));
        BoundedMatches matches = new BoundedMatches(window, request);

        if (savedPlaces != null) {
            for (PlaceRepository.Place place : savedPlaces) {
                Result result = new Result("saved:" + place.id,
                        place.name == null || place.name.trim().isEmpty() ? "موقع محفوظ" : place.name,
                        place.latitude, place.longitude, "موقع محفوظ", "محفوظات", true,
                        distanceMeters(request.centerLatitude, request.centerLongitude, place.latitude, place.longitude));
                matches.offer(result, wanted);
            }
        }

        if (activeMap != null && activeMap.isFile()) {
            readMatchesFromShards(activeMap, matches, wanted);
            if (request.centerLatitude != null && request.centerLongitude != null && request.radiusMeters > 0f) {
                scanNearbyDirect(activeMap, matches, wanted, request);
            }
        }

        List<RankedResult> sorted = matches.sortedBest();
        int from = Math.min(request.offset, sorted.size());
        int to = Math.min(sorted.size(), from + pageSize);
        List<Result> out = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (int i = from; i < to; i++) {
            Result r = sorted.get(i).result;
            if (emitted.add(dedupeKey(r))) out.add(r);
        }
        queryTruncated = matches.sawBeyondWindow() || sorted.size() > to;
        nextOffset = queryTruncated ? request.offset + pageSize : request.offset;
        return out;
    }

    private void ensureIndex(File map) {
        String identity = identity(map);
        mapIdentity = identity;
        File dir = indexDirectory(map, identity);
        if (!dir.exists() && !dir.mkdirs()) { failed = true; complete = false; return; }
        deleteOtherIndexDirectories(map, identity);
        cleanupPendingShards(dir);

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
            long height = maxY - minY + 1L;
            long totalTilesLong = (maxX - minX + 1L) * height;
            if (totalTilesLong > Integer.MAX_VALUE) { failed = true; complete = false; return; }
            int totalTiles = (int) totalTilesLong;

            Progress progress = inspectShards(dir, totalTiles);
            indexedCount = progress.itemCount;
            int nextTile = progress.nextTile;
            complete = nextTile >= totalTiles;
            indexTruncated = false;
            if (complete) return;

            long deadline = System.nanoTime() + INDEX_BUDGET_NANOS;
            while (nextTile < totalTiles && System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                int shardStart = nextTile;
                int shardEnd = Math.min(totalTiles, shardStart + TILES_PER_SHARD);
                File pending = new File(dir, shardName(shardStart, shardEnd) + ".pending");
                File target = new File(dir, shardName(shardStart, shardEnd));
                int shardItems = 0;
                Set<String> shardSeen = new HashSet<>();
                try (FileOutputStream bytes = new FileOutputStream(pending);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(bytes, "UTF-8"))) {
                    writer.write("#darbak-search-v2\t" + identity + "\t" + shardStart + "\t" + shardEnd); writer.newLine();
                    for (int tileIndex = shardStart; tileIndex < shardEnd; tileIndex++) {
                        long offsetX = tileIndex / height;
                        long offsetY = tileIndex % height;
                        Tile tile = new Tile((int) (minX + offsetX), (int) (minY + offsetY), zoom, tileSize);
                        MapReadResult data;
                        try { data = mapFile.readMapData(tile); }
                        catch (Exception error) { failed = true; continue; }
                        if (data == null) continue;
                        for (PointOfInterest poi : data.pois) {
                            IndexItem item = createItem(poi.tags, poi.position, "معلم");
                            if (item != null && shardSeen.add(item.id)) { writeIndexItem(writer, item); shardItems++; }
                        }
                        for (Way way : data.ways) {
                            LatLong position = representativePosition(way);
                            IndexItem item = position == null ? null : createItem(way.tags, position, "معلم");
                            if (item != null && shardSeen.add(item.id)) { writeIndexItem(writer, item); shardItems++; }
                        }
                    }
                    writer.flush(); bytes.getFD().sync();
                } catch (IOException error) {
                    pending.delete(); failed = true; break;
                }
                if (!pending.renameTo(target)) { pending.delete(); failed = true; break; }
                indexedCount += shardItems;
                nextTile = shardEnd;
            }
            complete = nextTile >= totalTiles;
        } catch (Exception error) {
            failed = true; complete = false;
        } finally {
            if (mapFile != null) mapFile.close();
        }
    }

    private Progress inspectShards(File dir, int totalTiles) {
        File[] files = dir.listFiles();
        if (files == null) return new Progress(0, 0);
        List<ShardMeta> shards = new ArrayList<>();
        for (File file : files) {
            if (!file.getName().startsWith("shard-") || !file.getName().endsWith(".idx")) continue;
            ShardMeta meta = validateShard(file);
            if (meta == null) { file.delete(); continue; }
            shards.add(meta);
        }
        Collections.sort(shards, Comparator.comparingInt(a -> a.start));
        int next = 0, count = 0;
        for (ShardMeta shard : shards) {
            if (shard.start != next || shard.end <= shard.start || shard.end > totalTiles) break;
            next = shard.end;
            count += shard.items;
        }
        // Delete shards after the first gap: they cannot be trusted as contiguous progress.
        for (ShardMeta shard : shards) if (shard.start >= next && shard.start != 0 && shard.start != next) shard.file.delete();
        return new Progress(next, count);
    }

    private ShardMeta validateShard(File file) {
        int items = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            if (header == null) return null;
            String[] h = header.split("\\t");
            if (h.length != 4 || !"#darbak-search-v2".equals(h[0]) || !identityFromHeaderMatches(h[1])) return null;
            int start = Integer.parseInt(h[2]), end = Integer.parseInt(h[3]);
            String line;
            while ((line = reader.readLine()) != null) {
                if (parseIndexItem(line) == null) return null;
                items++;
            }
            return new ShardMeta(file, start, end, items);
        } catch (Exception error) { return null; }
    }

    private boolean identityFromHeaderMatches(String value) { return mapIdentity != null && mapIdentity.equals(value); }

    private void readMatchesFromShards(File map, BoundedMatches matches, String wanted) {
        File dir = indexDirectory(map, identity(map));
        File[] files = dir.listFiles((d, name) -> name.startsWith("shard-") && name.endsWith(".idx"));
        if (files == null) return;
        List<File> ordered = new ArrayList<>(); Collections.addAll(ordered, files);
        Collections.sort(ordered, Comparator.comparing(File::getName));
        for (File file : ordered) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.readLine(); // header
                String line;
                while ((line = reader.readLine()) != null) {
                    IndexItem item = parseIndexItem(line);
                    if (item == null) { failed = true; continue; }
                    Result r = item.toResult(distanceMeters(matches.request.centerLatitude, matches.request.centerLongitude,
                            item.latitude, item.longitude));
                    matches.offer(r, wanted);
                }
            } catch (IOException error) { failed = true; }
        }
    }

    private void scanNearbyDirect(File map, BoundedMatches matches, String wanted, SearchRequest request) {
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
                    try { data = mapFile.readMapData(tile); } catch (Exception error) { failed = true; continue; }
                    if (data == null) continue;
                    for (PointOfInterest poi : data.pois) {
                        IndexItem item = createItem(poi.tags, poi.position, "معلم");
                        if (item != null) matches.offer(item.toResult(distanceMeters(request.centerLatitude,
                                request.centerLongitude, item.latitude, item.longitude)), wanted);
                    }
                    for (Way way : data.ways) {
                        NearestWayPoint nearest = nearestPointOnWay(request.centerLatitude, request.centerLongitude, way);
                        LatLong representative = representativePosition(way);
                        if (representative == null) continue;
                        IndexItem item = createItem(way.tags, representative, "معلم");
                        if (item == null) continue;
                        if (nearest != null) {
                            Result precise = new Result(item.id, item.name, nearest.latitude, nearest.longitude,
                                    item.source, item.category, false, nearest.distanceMeters, item.searchText);
                            matches.offer(precise, wanted);
                        } else {
                            matches.offer(item.toResult(distanceMeters(request.centerLatitude, request.centerLongitude,
                                    item.latitude, item.longitude)), wanted);
                        }
                    }
                }
            }
        } catch (Exception error) { failed = true; }
        finally { if (mapFile != null) mapFile.close(); }
    }

    private NearestWayPoint nearestPointOnWay(double lat, double lon, Way way) {
        if (way == null || way.latLongs == null) return null;
        NearestWayPoint best = null;
        for (LatLong[] ring : way.latLongs) {
            if (ring == null || ring.length == 0) continue;
            for (int i = 0; i < ring.length; i++) {
                LatLong a = ring[i];
                if (a == null) continue;
                double pointDistance = haversine(lat, lon, a.latitude, a.longitude);
                if (best == null || pointDistance < best.distanceMeters) best = new NearestWayPoint(a.latitude, a.longitude, (float) pointDistance);
                if (i + 1 < ring.length && ring[i + 1] != null) {
                    NearestWayPoint segment = nearestOnSegment(lat, lon, a, ring[i + 1]);
                    if (segment != null && (best == null || segment.distanceMeters < best.distanceMeters)) best = segment;
                }
            }
        }
        return best;
    }

    static NearestWayPoint nearestOnSegment(double lat, double lon, LatLong a, LatLong b) {
        double scale = Math.cos(Math.toRadians(lat));
        double ax = (a.longitude - lon) * 111320d * scale, ay = (a.latitude - lat) * 111320d;
        double bx = (b.longitude - lon) * 111320d * scale, by = (b.latitude - lat) * 111320d;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 <= 0d ? 0d : Math.max(0d, Math.min(1d, -(ax * dx + ay * dy) / len2));
        double x = ax + t * dx, y = ay + t * dy;
        double outLat = lat + y / 111320d;
        double outLon = lon + x / (111320d * Math.max(0.01d, scale));
        return new NearestWayPoint(outLat, outLon, (float) Math.hypot(x, y));
    }

    private IndexItem createItem(List<Tag> tags, LatLong position, String fallbackSource) {
        if (position == null) return null;
        Classification c = classify(tags, fallbackSource);
        String primary = firstNotBlank(tagValue(tags, "name:ar"), tagValue(tags, "name"), tagValue(tags, "name:en"));
        String aliases = joinNonBlank(tagValue(tags, "alt_name"), tagValue(tags, "old_name"), tagValue(tags, "loc_name"),
                tagValue(tags, "short_name"), tagValue(tags, "official_name"), tagValue(tags, "name:ar"), tagValue(tags, "name:en"));
        if (primary == null && !c.indexUnnamed) return null;
        String shown = primary == null ? "بدون اسم" : primary.trim();
        String searchable = shown + " " + aliases + " " + c.source + " " + c.category;
        String id = normalize(shown + "|" + c.source) + ':' + Math.round(position.latitude * 10000d) + ':' + Math.round(position.longitude * 10000d);
        return new IndexItem(id, shown, c.source, c.category, position.latitude, position.longitude, searchable);
    }

    private Classification classify(List<Tag> tags, String fallback) {
        String amenity = lower(tagValue(tags, "amenity")), healthcare = lower(tagValue(tags, "healthcare"));
        String shop = lower(tagValue(tags, "shop")), tourism = lower(tagValue(tags, "tourism"));
        String office = lower(tagValue(tags, "office")), place = lower(tagValue(tags, "place"));
        String highway = lower(tagValue(tags, "highway")), waterway = lower(tagValue(tags, "waterway"));
        String natural = lower(tagValue(tags, "natural")), leisure = lower(tagValue(tags, "leisure"));
        String emergency = lower(tagValue(tags, "emergency")), publicTransport = lower(tagValue(tags, "public_transport"));
        String manMade = lower(tagValue(tags, "man_made"));
        if (waterway != null || oneOf(natural, "valley", "gully")) return new Classification("وادي/شعيب", CATEGORY_WADIS, true);
        if (oneOf(natural, "peak", "ridge", "cliff", "saddle")) return new Classification("جبل/قمة", CATEGORY_MOUNTAINS, true);
        if (oneOf(manMade, "water_well", "water_tower") || oneOf(natural, "spring", "water") || "drinking_water".equals(amenity)) return new Classification("ماء/بئر", CATEGORY_WATER, true);
        if (place != null) {
            if (oneOf(place, "village", "hamlet", "town", "city", "isolated_dwelling")) return new Classification("قرية/تجمع", CATEGORY_VILLAGES, false);
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

    private Classification service(String source) { return new Classification(source, CATEGORY_SERVICES, false); }

    private LatLong representativePosition(Way way) {
        if (way == null) return null;
        if (way.labelPosition != null) return way.labelPosition;
        if (way.latLongs != null && way.latLongs.length > 0 && way.latLongs[0] != null && way.latLongs[0].length > 0) {
            LatLong[] ring = way.latLongs[0]; double lat = 0d, lon = 0d; int count = Math.min(ring.length, 64);
            for (int i = 0; i < count; i++) { lat += ring[i].latitude; lon += ring[i].longitude; }
            return new LatLong(lat / count, lon / count);
        }
        return null;
    }

    private byte indexZoom(MapFile mapFile) {
        int min = mapFile.getMapFileInfo().zoomLevelMin, max = mapFile.getMapFileInfo().zoomLevelMax;
        return (byte) Math.max(min, Math.min(max, INDEX_ZOOM));
    }

    private void writeIndexItem(BufferedWriter writer, IndexItem item) throws IOException {
        writer.write(clean(item.id)); writer.write('\t'); writer.write(clean(item.name)); writer.write('\t');
        writer.write(clean(item.source)); writer.write('\t'); writer.write(clean(item.category)); writer.write('\t');
        writer.write(Double.toString(item.latitude)); writer.write('\t'); writer.write(Double.toString(item.longitude)); writer.write('\t');
        writer.write(clean(item.searchText)); writer.newLine();
    }

    private IndexItem parseIndexItem(String line) {
        try {
            String[] v = line.split("\\t", 7); if (v.length != 7) return null;
            return new IndexItem(v[0], v[1], v[2], v[3], Double.parseDouble(v[4]), Double.parseDouble(v[5]), v[6]);
        } catch (RuntimeException error) { return null; }
    }

    private String identity(File map) { return map.length() + "-" + map.lastModified(); }
    private File indexDirectory(File map, String id) { return new File(map.getParentFile(), ".darbak-search-v2-" + id); }
    private String shardName(int start, int end) { return String.format(Locale.US, "shard-%08d-%08d.idx", start, end); }

    private void cleanupPendingShards(File dir) {
        File[] files = dir.listFiles((d,n) -> n.endsWith(".pending")); if (files != null) for (File f : files) f.delete();
    }
    private void deleteOtherIndexDirectories(File map, String keep) {
        File[] files = map.getParentFile().listFiles(); if (files == null) return;
        String keepName = ".darbak-search-v2-" + keep;
        for (File f : files) if (f.isDirectory() && f.getName().startsWith(".darbak-search-v2-") && !f.getName().equals(keepName)) deleteTree(f);
    }
    private void deleteTree(File file) { if (file == null || !file.exists()) return; File[] kids=file.listFiles(); if(kids!=null) for(File k:kids) deleteTree(k); file.delete(); }

    private String tagValue(List<Tag> tags, String key) { if(tags==null)return null; for(Tag t:tags) if(key.equalsIgnoreCase(t.key)) return t.value; return null; }
    private String firstNotBlank(String... values) { for(String v:values) if(v!=null&&!v.trim().isEmpty()) return v; return null; }
    private String joinNonBlank(String... values) { StringBuilder out=new StringBuilder(); for(String v:values){ if(v==null||v.trim().isEmpty())continue; if(out.length()>0)out.append(' '); out.append(v.trim()); } return out.toString(); }
    private boolean oneOf(String value,String...choices){if(value==null)return false;for(String c:choices)if(c.equals(value))return true;return false;}
    private String lower(String value){return value==null||value.isEmpty()?null:value.toLowerCase(Locale.ROOT);}
    private Float distanceMeters(Double a,Double b,double c,double d){if(a==null||b==null)return null;return(float)haversine(a,b,c,d);}
    private static double haversine(double lat1,double lon1,double lat2,double lon2){double dl=Math.toRadians(lat2-lat1),dn=Math.toRadians(lon2-lon1);double h=Math.sin(dl/2)*Math.sin(dl/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dn/2)*Math.sin(dn/2);return 6371000d*2d*Math.asin(Math.sqrt(Math.max(0d,Math.min(1d,h))));}
    private String dedupeKey(Result r){return normalize(r.name+"|"+r.source)+':'+Math.round(r.latitude*10000d)+':'+Math.round(r.longitude*10000d);}
    private String clean(String v){return v==null?"":v.replace('\t',' ').replace('\n',' ').replace('\r',' ');}

    static String normalize(String value) {
        if (value == null) return "";
        String out = value.trim().toLowerCase(Locale.ROOT)
                .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ٱ','ا')
                .replace('ى','ي').replace('ة','ه').replace('ؤ','و').replace('ئ','ي')
                .replace("ـ", "");
        return out.replaceAll("[ًٌٍَُِّْٰ]", "").replaceAll("\\s+", " ").trim();
    }

    private int matchScore(String value,String query){if(query.isEmpty())return 10;if(value.equals(query))return 100;if(value.startsWith(query))return 88;if(value.contains(query))return 70;for(String w:query.split(" "))if(!w.isEmpty()&&!value.contains(w))return 0;return 48;}

    public static final class SearchRequest {
        public final String query, category; public final Double centerLatitude, centerLongitude;
        public final float radiusMeters; public final boolean sortNearest; public final int limit, offset;
        public SearchRequest(String q,String c,Double lat,Double lon,float radius,boolean nearest,int limit){this(q,c,lat,lon,radius,nearest,limit,0);}
        public SearchRequest(String q,String c,Double lat,Double lon,float radius,boolean nearest,int limit,int offset){
            this.query=q==null?"":q; this.category=c==null?CATEGORY_ALL:c; centerLatitude=lat; centerLongitude=lon;
            radiusMeters=Math.max(0f,radius); sortNearest=nearest; this.limit=limit; this.offset=Math.max(0,offset);
        }
        public SearchRequest nextPage(){return new SearchRequest(query,category,centerLatitude,centerLongitude,radiusMeters,sortNearest,limit,offset+Math.max(5,Math.min(MAX_PAGE_SIZE,limit)));}
    }

    public static final class Result {
        public final String id,name; public final double latitude,longitude; public final String source,category; public final boolean saved; public final Float distanceMeters; final String searchText;
        Result(String id,String name,double lat,double lon,String source,String category,boolean saved,Float distance){this(id,name,lat,lon,source,category,saved,distance,name+" "+source+" "+category);}
        Result(String id,String name,double lat,double lon,String source,String category,boolean saved,Float distance,String text){this.id=id;this.name=name;latitude=lat;longitude=lon;this.source=source;this.category=category;this.saved=saved;distanceMeters=distance;searchText=text==null?"":text;}
    }
    private static final class IndexItem { final String id,name,source,category,searchText; final double latitude,longitude; IndexItem(String i,String n,String s,String c,double a,double o,String t){id=i;name=n;source=s;category=c;latitude=a;longitude=o;searchText=t;} Result toResult(Float d){return new Result(id,name,latitude,longitude,source,category,false,d,searchText);} }
    private static final class RankedResult { final Result result; final int score; RankedResult(Result r,int s){result=r;score=s;} }
    private static final class Classification { final String source,category; final boolean indexUnnamed; Classification(String s,String c,boolean i){source=s;category=c;indexUnnamed=i;} }
    private static final class Progress { final int nextTile,itemCount; Progress(int n,int c){nextTile=n;itemCount=c;} }
    private static final class ShardMeta { final File file; final int start,end,items; ShardMeta(File f,int s,int e,int i){file=f;start=s;end=e;items=i;} }
    static final class NearestWayPoint { final double latitude,longitude; final float distanceMeters; NearestWayPoint(double a,double o,float d){latitude=a;longitude=o;distanceMeters=d;} }

    private final class BoundedMatches {
        final int capacity; final SearchRequest request; final PriorityQueue<RankedResult> heap;
        final Set<String> heapKeys = new HashSet<>(); boolean beyond;
        BoundedMatches(int capacity, SearchRequest request) {
            this.capacity=capacity; this.request=request;
            this.heap=new PriorityQueue<>(Math.max(1,capacity), (a,b)->compareRank(a,b,request)); // best at head; replaced via explicit worst scan
        }
        void offer(Result result,String wanted){
            if(!CATEGORY_ALL.equals(request.category)&&!request.category.equals(result.category)&&!"محفوظات".equals(result.category))return;
            if(request.radiusMeters>0f&&(result.distanceMeters==null||result.distanceMeters>request.radiusMeters))return;
            String haystack=normalize(result.name+" "+result.source+" "+result.searchText); int score=wanted.isEmpty()?10:matchScore(haystack,wanted); if(score<=0)return;
            RankedResult candidate=new RankedResult(result,score); String key=dedupeKey(result); if(heapKeys.contains(key))return;
            if(heap.size()<capacity){heap.add(candidate);heapKeys.add(key);return;}
            beyond=true; RankedResult worst=null; for(RankedResult x:heap) if(worst==null||compareRank(x,worst,request)>0) worst=x;
            if(worst!=null&&compareRank(candidate,worst,request)<0){heap.remove(worst);heapKeys.remove(dedupeKey(worst.result));heap.add(candidate);heapKeys.add(key);}
        }
        List<RankedResult> sortedBest(){List<RankedResult> out=new ArrayList<>(heap);Collections.sort(out,(a,b)->compareRank(a,b,request));return out;}
        boolean sawBeyondWindow(){return beyond;}
    }

    private int compareRank(RankedResult a,RankedResult b,SearchRequest request){
        if(request.sortNearest){int d=Float.compare(distanceOrMax(a.result),distanceOrMax(b.result));if(d!=0)return d;}
        int s=Integer.compare(b.score,a.score);if(s!=0)return s;
        int d=Float.compare(distanceOrMax(a.result),distanceOrMax(b.result));if(d!=0)return d;
        return a.result.name.compareTo(b.result.name);
    }
    private float distanceOrMax(Result r){return r.distanceMeters==null?Float.MAX_VALUE:r.distanceMeters;}
}
''', encoding='utf-8')

# Add focused unit tests for tatweel, geometry distance and bounded pagination helper behavior.
Path('app/src/test/java/com/abosultan/darbakmaps/map/OfflineMapSearchEngineReviewTest.java').write_text(r'''package com.abosultan.darbakmaps.map;

import org.junit.Test;
import org.mapsforge.core.model.LatLong;
import static org.junit.Assert.*;

public class OfflineMapSearchEngineReviewTest {
    @Test public void tatweelIsRemovedNotTurnedIntoWordBoundary() {
        assertEquals(OfflineMapSearchEngine.normalize("سمان"), OfflineMapSearchEngine.normalize("سـمان"));
        assertEquals("شعيب السمان", OfflineMapSearchEngine.normalize("شـعيب السـمان"));
    }

    @Test public void wayCrossingCenterIsNearEvenWhenEndpointsAreFar() {
        OfflineMapSearchEngine.NearestWayPoint p = OfflineMapSearchEngine.nearestOnSegment(
                25.0, 45.0, new LatLong(25.0, 44.5), new LatLong(25.0, 45.5));
        assertNotNull(p);
        assertTrue(p.distanceMeters < 10f);
    }

    @Test public void requestSupportsRealPagingOffsets() {
        OfflineMapSearchEngine.SearchRequest first = new OfflineMapSearchEngine.SearchRequest(
                "سمان", OfflineMapSearchEngine.CATEGORY_ALL, null, null, 0f, false, 40);
        assertEquals(0, first.offset);
        assertEquals(40, first.nextPage().offset);
        assertEquals(80, first.nextPage().nextPage().offset);
    }
}
''', encoding='utf-8')

# Map layer for a bounded set of search results.
p = Path('app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java')
s = p.read_text(encoding='utf-8')
s = s.replace('''    private final List<Marker> savedMarkers = new ArrayList<>();\n''', '''    private final List<Marker> savedMarkers = new ArrayList<>();\n    private final List<Marker> searchMarkers = new ArrayList<>();\n''', 1)
anchor = '''    public void setNavigationTarget(double latitude, double longitude, int mode) {\n'''
method = '''    public void showSearchResults(List<OfflineMapSearchEngine.Result> results) {\n        for (Marker marker : searchMarkers) mapView.getLayerManager().getLayers().remove(marker);\n        searchMarkers.clear();\n        if (results != null) {\n            int limit = Math.min(80, results.size());\n            for (int i = 0; i < limit; i++) {\n                OfflineMapSearchEngine.Result result = results.get(i);\n                Marker marker = new Marker(new LatLong(result.latitude, result.longitude), createPin(), 0, -24);\n                marker.setBillboard(true);\n                searchMarkers.add(marker);\n                mapView.getLayerManager().getLayers().add(marker);\n            }\n        }\n        mapView.getLayerManager().redrawLayers();\n    }\n\n    public void clearSearchResults() { showSearchResults(java.util.Collections.emptyList()); }\n\n'''
if anchor not in s: raise SystemExit('search marker anchor missing')
s = s.replace(anchor, method + anchor, 1)
s = s.replace('''        clearStoredTrack();\n        clearActiveTrackLayers();\n''', '''        clearStoredTrack();\n        clearActiveTrackLayers();\n        clearSearchResults();\n''', 1)
p.write_text(s, encoding='utf-8')

# DarbakMapContainer: long press offers save or nearby-search around the exact touched point.
p = Path('app/src/main/java/com/abosultan/darbakmaps/DarbakMapContainer.java')
s = p.read_text(encoding='utf-8')
s = s.replace('''import android.app.Activity;\n''', '''import android.app.Activity;\nimport android.app.AlertDialog;\n''', 1)
old = '''        Activity activity = (Activity) getContext();\n        MapRuntimeBridge.showPoint(point.latitude, point.longitude);\n        PointEditor.show(activity, point.latitude, point.longitude, null);\n'''
new = '''        Activity activity = (Activity) getContext();\n        MapRuntimeBridge.showPoint(point.latitude, point.longitude);\n        String[] actions = {"حفظ موقع هنا", "البحث حول هذه النقطة"};\n        new AlertDialog.Builder(activity)\n                .setTitle("النقطة المحددة")\n                .setItems(actions, (dialog, which) -> {\n                    if (which == 0) PointEditor.show(activity, point.latitude, point.longitude, null);\n                    else if (activity instanceof MainActivity) {\n                        ((MainActivity) activity).showNearbySearchAroundPoint(point.latitude, point.longitude);\n                    }\n                })\n                .setNegativeButton("إلغاء", null)\n                .show();\n'''
if old not in s: raise SystemExit('long press action missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

# MainActivity: page-aware search state, map result layer, and point search entry.
p = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = p.read_text(encoding='utf-8')
s = s.replace('''    private volatile boolean searchRunning;\n''', '''    private volatile boolean searchRunning;\n    private OfflineMapSearchEngine.SearchRequest lastSearchRequest;\n''', 1)
# performSearch construct request then unified runner
start=s.index('    private void performSearch(String rawQuery) {')
end=s.index('\n    private void showSearchResultActions', start)
replacement=r'''    private void performSearch(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) { toast("اكتب اسم مدينة أو مكان للبحث"); return; }
        Location current = locationController == null ? null : locationController.getLastLocation();
        OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(
                query, OfflineMapSearchEngine.CATEGORY_ALL,
                current == null ? null : current.getLatitude(), current == null ? null : current.getLongitude(),
                0f, false, 40, 0);
        executeSearch(request, "بحث");
    }

    private void executeSearch(OfflineMapSearchEngine.SearchRequest request, String title) {
        if (searchRunning) { toast("البحث السابق ما زال جاريًا"); return; }
        searchRunning = true;
        lastSearchRequest = request;
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle(title);
        progress.setMessage(request.offset > 0 ? "جارٍ تحميل الصفحة التالية…" : "جارٍ البحث…");
        progress.setIndeterminate(true); progress.setCancelable(false); showImmersive(progress);
        File activeMap = MapStorage.activeMap(this);
        ioExecutor.execute(() -> {
            List<OfflineMapSearchEngine.Result> results = searchEngine.search(request,
                    activeMap.isFile() ? activeMap : null, placeRepository.all());
            boolean complete = searchEngine.isComplete();
            boolean failed = searchEngine.hasFailed();
            boolean queryMore = searchEngine.hasMoreResults();
            runOnUiThread(() -> {
                searchRunning = false;
                if (isActivityUnavailable()) return;
                progress.dismiss();
                showSearchResults(request, results, complete, failed, queryMore);
            });
        });
    }

    private void showSearchResults(OfflineMapSearchEngine.SearchRequest request,
                                   List<OfflineMapSearchEngine.Result> results,
                                   boolean complete, boolean failed, boolean queryMore) {
        if (mapController != null) mapController.showSearchResults(results);
        if (results.isEmpty()) {
            if (!complete) toast("لا توجد نتائج في الجزء المفهرس حتى الآن؛ الفهرسة ما زالت جارية");
            else if (failed) toast("اكتمل الفهرس مع تعذر قراءة بعض أجزاء الخريطة");
            else toast(MapStorage.activeMap(this).isFile() ? "لا توجد نتائج مطابقة" : "أضف خريطة دربك للبحث في المعالم");
            return;
        }
        String[] labels = new String[results.size()];
        for (int i=0;i<results.size();i++) {
            OfflineMapSearchEngine.Result result=results.get(i); String distance=formatDistance(result.distanceMeters);
            labels[i]=result.name+"\n"+result.source+(distance.isEmpty()?"":" • "+distance);
        }
        String state = complete ? "نتائج البحث" : "نتائج من الجزء المفهرس — الفهرسة مستمرة";
        if (failed) state += " • تعذر جزء من الخريطة";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(state + (request.offset > 0 ? " • صفحة " + (request.offset / Math.max(1, request.limit) + 1) : ""))
                .setItems(labels, (dialog, which) -> showSearchResultActions(results.get(which)))
                .setNegativeButton("إغلاق", null);
        if (queryMore) builder.setPositiveButton("المزيد", (dialog, which) -> executeSearch(request.nextPage(), "بحث"));
        showImmersive(builder.create());
    }
'''
s=s[:start]+replacement+s[end:]
# runNearbySearch replace execution body with unified request.
start=s.index('    private void runNearbySearch(double latitude, double longitude, String centerLabel,')
end=s.index('\n    private void showSavedPlacesPanel()', start)
replacement=r'''    private void runNearbySearch(double latitude, double longitude, String centerLabel,
                                 String category, int radiusKm) {
        OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(
                "", category, latitude, longitude, radiusKm * 1000f, true, 120, 0);
        executeSearch(request, "بحث قريب • " + centerLabel + " • " + radiusKm + " كم");
    }

    void showNearbySearchAroundPoint(double latitude, double longitude) {
        showNearbySearchOptions(latitude, longitude, "النقطة المحددة");
    }
'''
s=s[:start]+replacement+s[end:]
p.write_text(s,encoding='utf-8')
