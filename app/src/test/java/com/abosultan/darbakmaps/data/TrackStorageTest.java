package com.abosultan.darbakmaps.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrackStorageTest {
    @Test
    public void parsesZuluFractionAndOffsetTimes() throws Exception {
        long base = TrackStorage.parseGpxTime("2026-09-14T12:30:00Z");
        assertEquals(base + 123L, TrackStorage.parseGpxTime("2026-09-14T12:30:00.123Z"));
        assertEquals(base, TrackStorage.parseGpxTime("2026-09-14T15:30:00+03:00"));
        assertEquals(base + 456L, TrackStorage.parseGpxTime("2026-09-14T15:30:00.456+03:00"));
    }

    @Test
    public void reductionKeepsSegmentBoundaryAndSharpTurn() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(25.0000, 45.0000, 1L, true));
        points.add(new GeoPoint(25.0000, 45.0010, 2L));
        points.add(new GeoPoint(25.0000, 45.0020, 3L));
        GeoPoint turn = new GeoPoint(25.0000, 45.0030, 4L);
        points.add(turn);
        points.add(new GeoPoint(25.0010, 45.0030, 5L));
        GeoPoint secondSegment = new GeoPoint(26.0000, 46.0000, 6L, true);
        points.add(secondSegment);
        points.add(new GeoPoint(26.0010, 46.0000, 7L));
        points.add(new GeoPoint(26.0020, 46.0000, 8L));
        points.add(new GeoPoint(26.0030, 46.0000, 9L));

        List<GeoPoint> reduced = TrackStorage.reducePreservingGeometry(points, 7);
        assertTrue(reduced.contains(turn));
        assertTrue(reduced.contains(secondSegment));
        assertTrue(reduced.get(0).segmentStart);
        assertEquals(points.get(points.size() - 1), reduced.get(reduced.size() - 1));
    }
}
