from pathlib import Path

# PlaceRepository: stable id lookup for click handling.
p = Path('app/src/main/java/com/abosultan/darbakmaps/data/PlaceRepository.java')
s = p.read_text(encoding='utf-8')
anchor = '''    public List<Place> search(String query) {\n'''
method = '''    public Place findById(String id) {\n        if (id == null) return null;\n        for (Place place : all()) if (id.equals(place.id)) return place;\n        return null;\n    }\n\n'''
if method not in s:
    if anchor not in s: raise SystemExit('PlaceRepository anchor missing')
    s = s.replace(anchor, method + anchor, 1)
p.write_text(s, encoding='utf-8')

# SavedPlacesDialog: preserve row identity/order during touch/scroll, immediate stale GPS invalidation.
p = Path('app/src/main/java/com/abosultan/darbakmaps/SavedPlacesDialog.java')
s = p.read_text(encoding='utf-8')
s = s.replace('''import android.widget.BaseAdapter;\n''', '''import android.widget.BaseAdapter;\nimport android.widget.AbsListView;\n''', 1)
s = s.replace('''        private boolean touching;\n        private long lastUiUpdate;\n''', '''        private boolean touching;\n        private boolean scrolling;\n        private boolean pendingReorder;\n        private long lastUiUpdate;\n''', 1)
old_click = '''            list.setOnItemClickListener((parent, view, position, id) -> {\n                PlaceRepository.Place place = adapter.getItem(position);\n                if (place != null && navigator != null) {\n                    navigator.navigateTo(place);\n                    if (dialog != null) dialog.dismiss();\n                }\n            });\n            list.setOnItemLongClickListener((parent, view, position, id) -> {\n                PlaceRepository.Place place = adapter.getItem(position);\n                if (place != null) showEditDelete(place);\n                return true;\n            });\n            list.setOnTouchListener((v, event) -> {\n                int action = event.getActionMasked();\n                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) touching = true;\n                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {\n                    touching = false;\n                    rebuild(true);\n                }\n                return false;\n            });\n'''
new_click = '''            list.setOnItemClickListener((parent, view, position, id) -> {\n                String placeId = adapter.idAt(position);\n                PlaceRepository.Place place = repository.findById(placeId);\n                if (place != null && navigator != null) {\n                    navigator.navigateTo(place);\n                    if (dialog != null) dialog.dismiss();\n                }\n            });\n            list.setOnItemLongClickListener((parent, view, position, id) -> {\n                String placeId = adapter.idAt(position);\n                PlaceRepository.Place place = repository.findById(placeId);\n                if (place != null) showEditDelete(place);\n                return true;\n            });\n            list.setOnTouchListener((v, event) -> {\n                int action = event.getActionMasked();\n                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) touching = true;\n                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {\n                    touching = false;\n                    pendingReorder = true;\n                }\n                return false;\n            });\n            list.setOnScrollListener(new AbsListView.OnScrollListener() {\n                @Override public void onScrollStateChanged(AbsListView view, int state) {\n                    scrolling = state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE;\n                    if (!scrolling && !touching && pendingReorder) {\n                        pendingReorder = false;\n                        rebuild(true);\n                    }\n                }\n                @Override public void onScroll(AbsListView view, int first, int visible, int total) {}\n            });\n'''
if old_click not in s: raise SystemExit('saved click/touch pattern missing')
s = s.replace(old_click, new_click, 1)
old_update = '''        void updateLocation(Location value) {\n            location = value == null ? null : new Location(value);\n            long now = SystemClock.elapsedRealtime();\n            if (now - lastUiUpdate < 800L) return;\n            lastUiUpdate = now;\n            activity.runOnUiThread(() -> rebuild(!touching));\n        }\n'''
new_update = '''        void updateLocation(Location value) {\n            Location next = isFresh(value) ? new Location(value) : null;\n            if (next == null) {\n                location = null;\n                adapter.clearHeadingSmoothing();\n                lastUiUpdate = 0L;\n                activity.runOnUiThread(() -> rebuild(false));\n                return;\n            }\n            location = next;\n            long now = SystemClock.elapsedRealtime();\n            if (now - lastUiUpdate < 800L) {\n                activity.runOnUiThread(() -> adapter.updateLocationOnly(location));\n                return;\n            }\n            lastUiUpdate = now;\n            activity.runOnUiThread(() -> {\n                if (touching || scrolling) {\n                    pendingReorder = true;\n                    adapter.updateLocationOnly(location);\n                } else {\n                    rebuild(true);\n                }\n            });\n        }\n\n        private boolean isFresh(Location value) {\n            if (value == null) return false;\n            long elapsedNanos = value.getElapsedRealtimeNanos();\n            if (elapsedNanos > 0L) {\n                long age = SystemClock.elapsedRealtimeNanos() - elapsedNanos;\n                return age >= 0L && age <= 10_000_000_000L;\n            }\n            long time = value.getTime();\n            return time > 0L && Math.max(0L, System.currentTimeMillis() - time) <= 10_000L;\n        }\n'''
if old_update not in s: raise SystemExit('saved updateLocation pattern missing')
s = s.replace(old_update, new_update, 1)
old_rebuild = '''        private void rebuild(boolean reorder) {\n            if (dialog == null || !dialog.isShowing()) return;\n            List<PlaceRepository.Place> places = iconFilter == null\n                    ? new ArrayList<>(repository.all())\n                    : new ArrayList<>(repository.byIcon(iconFilter));\n            if (reorder && location != null) {\n                Collections.sort(places, (a, b) -> Float.compare(distance(location, a), distance(location, b)));\n            }\n            adapter.setData(places, location, reorder);\n        }\n'''
new_rebuild = '''        private void rebuild(boolean reorder) {\n            if (dialog == null || !dialog.isShowing()) return;\n            List<PlaceRepository.Place> fresh = iconFilter == null\n                    ? new ArrayList<>(repository.all())\n                    : new ArrayList<>(repository.byIcon(iconFilter));\n            if (reorder && location != null) {\n                Collections.sort(fresh, (a, b) -> Float.compare(distance(location, a), distance(location, b)));\n                adapter.setData(fresh, location, true);\n                return;\n            }\n            if (!reorder && adapter.getCount() > 0) {\n                Map<String, PlaceRepository.Place> byId = new HashMap<>();\n                for (PlaceRepository.Place place : fresh) byId.put(place.id, place);\n                List<PlaceRepository.Place> stable = new ArrayList<>();\n                for (String id : adapter.currentIds()) {\n                    PlaceRepository.Place place = byId.remove(id);\n                    if (place != null) stable.add(place);\n                }\n                stable.addAll(byId.values());\n                adapter.setData(stable, location, false);\n            } else {\n                adapter.setData(fresh, location, reorder);\n            }\n        }\n'''
if old_rebuild not in s: raise SystemExit('saved rebuild pattern missing')
s = s.replace(old_rebuild, new_rebuild, 1)
# Adapter helpers.
s = s.replace('''        void setData(List<PlaceRepository.Place> places, Location location, boolean reordered) {\n            data.clear();\n            data.addAll(places);\n            this.location = location == null ? null : new Location(location);\n            if (reordered && data.isEmpty()) smoothed.clear();\n            notifyDataSetChanged();\n        }\n\n        @Override public int getCount() { return data.size(); }\n        @Override public PlaceRepository.Place getItem(int position) { return position >= 0 && position < data.size() ? data.get(position) : null; }\n        @Override public long getItemId(int position) { return position; }\n''', '''        void setData(List<PlaceRepository.Place> places, Location location, boolean reordered) {\n            data.clear();\n            data.addAll(places);\n            this.location = location == null ? null : new Location(location);\n            if (this.location == null) smoothed.clear();\n            notifyDataSetChanged();\n        }\n\n        void updateLocationOnly(Location value) {\n            this.location = value == null ? null : new Location(value);\n            if (this.location == null) smoothed.clear();\n            notifyDataSetChanged();\n        }\n\n        void clearHeadingSmoothing() { smoothed.clear(); updateLocationOnly(null); }\n\n        List<String> currentIds() {\n            List<String> ids = new ArrayList<>();\n            for (PlaceRepository.Place p : data) ids.add(p.id);\n            return ids;\n        }\n\n        String idAt(int position) {\n            PlaceRepository.Place p = getItem(position);\n            return p == null ? null : p.id;\n        }\n\n        @Override public int getCount() { return data.size(); }\n        @Override public PlaceRepository.Place getItem(int position) { return position >= 0 && position < data.size() ? data.get(position) : null; }\n        @Override public long getItemId(int position) {\n            String id = idAt(position);\n            return id == null ? 0L : id.hashCode();\n        }\n        @Override public boolean hasStableIds() { return true; }\n''', 1)
p.write_text(s, encoding='utf-8')

# OfflineMapController: viewport-based saved markers + deterministic overlap handling.
p = Path('app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java')
s = p.read_text(encoding='utf-8')
s = s.replace('''import org.mapsforge.core.model.MapPosition;\n''', '''import org.mapsforge.core.model.MapPosition;\nimport org.mapsforge.core.model.Point;\n''', 1)
s = s.replace('''import java.util.ArrayList;\nimport java.util.List;\n''', '''import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.Comparator;\nimport java.util.List;\n''', 1)
s = s.replace('''    private static final int MAX_STORED_DRAW_SEGMENTS = 320;\n''', '''    private static final int MAX_STORED_DRAW_SEGMENTS = 320;\n    private static final int MAX_VISIBLE_SAVED_MARKERS = 300;\n    private static final float SAVED_TAP_RADIUS_PX = 44f;\n\n    public interface SavedPlaceTapListener {\n        void onSavedPlaceTap(PlaceRepository.Place place);\n        void onSavedPlaceClusterTap(List<PlaceRepository.Place> places);\n    }\n''', 1)
s = s.replace('''    private final List<Marker> savedMarkers = new ArrayList<>();\n''', '''    private final List<Marker> savedMarkers = new ArrayList<>();\n    private final List<PlaceRepository.Place> allSavedPlaces = new ArrayList<>();\n    private final List<PlaceRepository.Place> displayedSavedPlaces = new ArrayList<>();\n    private SavedPlaceTapListener savedPlaceTapListener;\n    private boolean savedShowLabels;\n''', 1)
# touch refresh after pan
s = s.replace('''        mapView.setOnTouchListener((view, event) -> {\n            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) followSuspended = true;\n            return false;\n        });\n''', '''        mapView.setOnTouchListener((view, event) -> {\n            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) followSuspended = true;\n            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {\n                mapView.postDelayed(this::refreshSavedPlacesForViewport, 120L);\n            }\n            return false;\n        });\n''', 1)
# refresh zoom and center
s = s.replace('''        mapView.getModel().mapViewPosition.setZoomLevel((byte) Math.min(20, current + 1));\n''', '''        mapView.getModel().mapViewPosition.setZoomLevel((byte) Math.min(20, current + 1));\n        mapView.post(this::refreshSavedPlacesForViewport);\n''', 1)
s = s.replace('''        mapView.getModel().mapViewPosition.setZoomLevel((byte) Math.max(3, current - 1));\n''', '''        mapView.getModel().mapViewPosition.setZoomLevel((byte) Math.max(3, current - 1));\n        mapView.post(this::refreshSavedPlacesForViewport);\n''', 1)
# setter before showSavedPlaces
anchor = '''    public void showSavedPlaces(List<PlaceRepository.Place> places, boolean showLabels) {\n'''
replacement = r'''    public void setSavedPlaceTapListener(SavedPlaceTapListener listener) {
        this.savedPlaceTapListener = listener;
    }

    public void showSavedPlaces(List<PlaceRepository.Place> places, boolean showLabels) {
        allSavedPlaces.clear();
        if (places != null) allSavedPlaces.addAll(places);
        savedShowLabels = showLabels;
        refreshSavedPlacesForViewport();
    }

    private void refreshSavedPlacesForViewport() {
        for (Marker marker : savedMarkers) mapView.getLayerManager().getLayers().remove(marker);
        savedMarkers.clear();
        displayedSavedPlaces.clear();
        if (allSavedPlaces.isEmpty()) {
            mapView.getLayerManager().redrawLayers();
            return;
        }
        LatLong center = mapView.getModel().mapViewPosition.getCenter();
        byte zoom = mapView.getModel().mapViewPosition.getZoomLevel();
        int width = mapView.getWidth() > 0 ? mapView.getWidth() : 1024;
        int height = mapView.getHeight() > 0 ? mapView.getHeight() : 600;
        double metersPerPixel = 156543.03392d * Math.cos(Math.toRadians(center.latitude)) / Math.pow(2d, zoom);
        double radiusMeters = Math.hypot(width, height) * 0.72d * Math.max(0.2d, metersPerPixel);
        List<PlaceRepository.Place> candidates = new ArrayList<>();
        for (PlaceRepository.Place place : allSavedPlaces) {
            if (distanceMeters(center.latitude, center.longitude, place.latitude, place.longitude) <= radiusMeters) {
                candidates.add(place);
            }
        }
        Collections.sort(candidates, Comparator.comparingDouble(
                place -> distanceMeters(center.latitude, center.longitude, place.latitude, place.longitude)));
        if (candidates.size() > MAX_VISIBLE_SAVED_MARKERS) {
            candidates = new ArrayList<>(candidates.subList(0, MAX_VISIBLE_SAVED_MARKERS));
        }
        displayedSavedPlaces.addAll(candidates);
        for (PlaceRepository.Place place : candidates) {
            Marker marker = new Marker(new LatLong(place.latitude, place.longitude),
                    createSavedMarker(place, savedShowLabels), 0, -20) {
                @Override public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
                    return handleSavedPlaceTap(tapLatLong);
                }
            };
            marker.setBillboard(true);
            savedMarkers.add(marker);
            mapView.getLayerManager().getLayers().add(marker);
        }
        mapView.getLayerManager().redrawLayers();
    }

    private boolean handleSavedPlaceTap(LatLong tap) {
        if (savedPlaceTapListener == null || tap == null) return false;
        byte zoom = mapView.getModel().mapViewPosition.getZoomLevel();
        double metersPerPixel = 156543.03392d * Math.cos(Math.toRadians(tap.latitude)) / Math.pow(2d, zoom);
        double hitMeters = Math.max(12d, SAVED_TAP_RADIUS_PX * Math.max(0.2d, metersPerPixel));
        List<PlaceRepository.Place> hit = new ArrayList<>();
        for (PlaceRepository.Place place : displayedSavedPlaces) {
            if (distanceMeters(tap.latitude, tap.longitude, place.latitude, place.longitude) <= hitMeters) hit.add(place);
        }
        if (hit.isEmpty()) return false;
        if (hit.size() == 1) savedPlaceTapListener.onSavedPlaceTap(hit.get(0));
        else savedPlaceTapListener.onSavedPlaceClusterTap(hit);
        return true;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        return 6371000d * 2d * Math.asin(Math.sqrt(Math.max(0d, Math.min(1d, h))));
    }
'''
start = s.find(anchor)
if start < 0: raise SystemExit('showSavedPlaces anchor missing')
end = s.find('\n    public void setNavigationTarget', start)
if end < 0: raise SystemExit('showSavedPlaces end missing')
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')

# MainActivity: connect map marker taps to exact place ids; cluster requires explicit user choice.
p = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = p.read_text(encoding='utf-8')
old = '''                mapController = new OfflineMapController(this, mapFile);\n                mapContainer.addView(mapController.view(), new FrameLayout.LayoutParams(\n'''
new = '''                mapController = new OfflineMapController(this, mapFile);\n                mapController.setSavedPlaceTapListener(new OfflineMapController.SavedPlaceTapListener() {\n                    @Override public void onSavedPlaceTap(PlaceRepository.Place place) {\n                        startDirectNavigation(place.name, place.latitude, place.longitude);\n                    }\n                    @Override public void onSavedPlaceClusterTap(List<PlaceRepository.Place> places) {\n                        showSavedPlaceCluster(places);\n                    }\n                });\n                mapContainer.addView(mapController.view(), new FrameLayout.LayoutParams(\n'''
if old not in s: raise SystemExit('controller creation missing')
s = s.replace(old, new, 1)
anchor = '''    private void startDirectNavigation(String name, double latitude, double longitude) {\n'''
method = '''    private void showSavedPlaceCluster(List<PlaceRepository.Place> places) {\n        if (places == null || places.isEmpty()) return;\n        String[] labels = new String[places.size()];\n        for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).name;\n        showImmersive(new AlertDialog.Builder(this)\n                .setTitle("عدة مواقع في نفس المكان")\n                .setItems(labels, (dialog, which) -> {\n                    PlaceRepository.Place selected = places.get(which);\n                    startDirectNavigation(selected.name, selected.latitude, selected.longitude);\n                })\n                .setNegativeButton("إلغاء", null)\n                .create());\n    }\n\n'''
if anchor not in s: raise SystemExit('direct nav anchor missing')
s = s.replace(anchor, method + anchor, 1)
p.write_text(s, encoding='utf-8')

# Behavioral helper tests: repository-independent ordering semantics are validated through a small model.
Path('app/src/main/java/com/abosultan/darbakmaps/data/StableIdOrder.java').write_text(r'''package com.abosultan.darbakmaps.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure helper used by tests/documentation for stable visible row identity. */
public final class StableIdOrder {
    private StableIdOrder() {}
    public static List<String> preserve(List<String> visible, List<String> fresh) {
        Set<String> remaining = new LinkedHashSet<>(fresh);
        List<String> result = new ArrayList<>();
        for (String id : visible) if (remaining.remove(id)) result.add(id);
        result.addAll(remaining);
        return result;
    }
}
''', encoding='utf-8')
Path('app/src/test/java/com/abosultan/darbakmaps/data/StableIdOrderTest.java').write_text(r'''package com.abosultan.darbakmaps.data;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class StableIdOrderTest {
    @Test public void gpsRefreshDoesNotReorderRowsUnderTouch() {
        assertEquals(Arrays.asList("c","a","b","d"),
                StableIdOrder.preserve(Arrays.asList("c","a","b"), Arrays.asList("b","a","c","d")));
    }
}
''', encoding='utf-8')
