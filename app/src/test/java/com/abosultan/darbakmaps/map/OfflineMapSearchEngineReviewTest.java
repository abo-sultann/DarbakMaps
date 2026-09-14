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

    @Test public void requestSupportsRealPagingOffsets() {
        OfflineMapSearchEngine.SearchRequest first = new OfflineMapSearchEngine.SearchRequest(
                "سمان", OfflineMapSearchEngine.CATEGORY_ALL, null, null, 0f, false, 40);
        assertEquals(0, first.offset);
        assertEquals(40, first.nextPage().offset);
        assertEquals(80, first.nextPage().nextPage().offset);
    }
}
