package com.abosultan.darbakmaps.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TrackNavigatorTest {
    @Test
    public void followsBendInsteadOfShortcuttingToStart() {
        List<GeoPoint> points = Arrays.asList(
                new GeoPoint(25.0000, 45.0000, 1L, true),
                new GeoPoint(25.0000, 45.0100, 2L),
                new GeoPoint(25.0100, 45.0100, 3L),
                new GeoPoint(25.0200, 45.0100, 4L)
        );
        TrackNavigator navigator = new TrackNavigator(points);
        GeoPoint target = navigator.update(25.0195, 45.0100);
        assertEquals(2, navigator.targetIndex());
        assertEquals(points.get(2).latitude, target.latitude, 0.000001);
    }

    @Test
    public void offTrackUsesSegmentDistance() {
        GeoPoint a = new GeoPoint(25.0000, 45.0000, 1L, true);
        GeoPoint b = new GeoPoint(25.0000, 45.0100, 2L);
        TrackNavigator navigator = new TrackNavigator(Arrays.asList(a, b));
        double distance = navigator.offTrack(25.0002, 45.0050);
        assertTrue(distance < 30d);
    }

    @Test
    public void doesNotBridgeAcrossSegmentGap() {
        List<GeoPoint> points = Arrays.asList(
                new GeoPoint(25.0000, 45.0000, 1L, true),
                new GeoPoint(25.0000, 45.0100, 2L),
                new GeoPoint(26.0000, 46.0000, 3L, true),
                new GeoPoint(26.0000, 46.0100, 4L)
        );
        TrackNavigator navigator = new TrackNavigator(points);
        double gapMiddle = navigator.offTrack(25.5000, 45.5000);
        assertTrue(gapMiddle > 10000d);
    }

    @Test
    public void reverseGuidanceStopsAtStartOfCurrentSegment() {
        List<GeoPoint> points = Arrays.asList(
                new GeoPoint(25.0000, 45.0000, 1L, true),
                new GeoPoint(25.0000, 45.0100, 2L),
                new GeoPoint(26.0000, 46.0000, 3L, true),
                new GeoPoint(26.0000, 46.0100, 4L)
        );
        TrackNavigator navigator = new TrackNavigator(points);
        GeoPoint firstTarget = navigator.update(26.0000, 46.0099);
        assertEquals(2, navigator.targetIndex());
        assertEquals(points.get(2).longitude, firstTarget.longitude, 0.000001);

        GeoPoint acrossGap = navigator.update(26.0000, 46.0000);
        assertNull(acrossGap);
        assertTrue(navigator.stoppedAtGap());
        assertEquals(2, navigator.targetIndex());
    }

    @Test
    public void refusesDefaultTargetWhenEveryPointStartsANewSegment() {
        List<GeoPoint> points = Arrays.asList(
                new GeoPoint(25.0000, 45.0000, 1L, true),
                new GeoPoint(26.0000, 46.0000, 2L, true),
                new GeoPoint(27.0000, 47.0000, 3L, true)
        );
        TrackNavigator navigator = new TrackNavigator(points);
        assertNull(navigator.update(26.5, 46.5));
        assertEquals(-1, navigator.targetIndex());
        assertTrue(navigator.stoppedAtGap());
    }
}
