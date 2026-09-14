package com.abosultan.darbakmaps.map;

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

    @Test public void keysetPagingReachesAllMatchesWithBoundedPages() throws Exception {
        java.lang.reflect.Constructor<com.abosultan.darbakmaps.data.PlaceRepository.Place> ctor =
                com.abosultan.darbakmaps.data.PlaceRepository.Place.class.getDeclaredConstructor(
                        String.class, String.class, double.class, double.class, long.class,
                        String.class, String.class, String.class);
        ctor.setAccessible(true);
        java.util.List<com.abosultan.darbakmaps.data.PlaceRepository.Place> places = new java.util.ArrayList<>();
        for (int i = 0; i < 95; i++) places.add(ctor.newInstance(
                "id-" + i, String.format(java.util.Locale.US, "سمان %03d", i),
                25d + i * 0.00001d, 45d, 1L,
                com.abosultan.darbakmaps.data.PlaceRepository.ICON_QUAIL, "سمان", ""));

        OfflineMapSearchEngine engine = new OfflineMapSearchEngine();
        OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(
                "سمان", OfflineMapSearchEngine.CATEGORY_ALL, null, null, 0f, false, 40);
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.List<OfflineMapSearchEngine.Result> page1 = engine.search(request, null, places);
        assertEquals(40, page1.size()); for (OfflineMapSearchEngine.Result r : page1) assertTrue(ids.add(r.id));
        assertTrue(engine.hasMoreResults());
        request = engine.nextPageRequest(request);
        java.util.List<OfflineMapSearchEngine.Result> page2 = engine.search(request, null, places);
        assertEquals(40, page2.size()); for (OfflineMapSearchEngine.Result r : page2) assertTrue(ids.add(r.id));
        assertTrue(engine.hasMoreResults());
        request = engine.nextPageRequest(request);
        java.util.List<OfflineMapSearchEngine.Result> page3 = engine.search(request, null, places);
        assertEquals(15, page3.size()); for (OfflineMapSearchEngine.Result r : page3) assertTrue(ids.add(r.id));
        assertFalse(engine.hasMoreResults());
        assertEquals(95, ids.size());
    }
}
