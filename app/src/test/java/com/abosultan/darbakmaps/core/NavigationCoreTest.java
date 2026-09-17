package com.abosultan.darbakmaps.core;

import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NavigationCoreTest {
    @After public void resetLocationStore() {
        LiveLocationStore.invalidate();
    }

    @Test public void freshGpsFixRemainsUsable() {
        long now = System.currentTimeMillis();
        LiveLocationStore.publish(point(26.3592d, 43.9818d, now));

        LocationSnapshot latest = LiveLocationStore.latest();
        assertTrue(latest.valid);
        assertEquals(26.3592d, latest.latitude, 0.000001d);
        assertEquals(43.9818d, latest.longitude, 0.000001d);
    }

    @Test public void staleGpsFixExpiresInsteadOfLookingLive() {
        long stale = System.currentTimeMillis() - 16000L;
        LiveLocationStore.publish(point(26.3592d, 43.9818d, stale));

        assertFalse(LiveLocationStore.latest().valid);
    }

    @Test public void backtrackStartsTowardThePreviousBreadcrumb() {
        BacktrackNavigator navigator = navigator();

        LocationSnapshot target = navigator.update(0d, 0.002d);

        assertEquals(1, navigator.targetIndex());
        assertEquals(0.001d, target.longitude, 0.000001d);
    }

    @Test public void backtrackWalksAllTheWayToTheRecordedStart() {
        BacktrackNavigator navigator = navigator();
        navigator.update(0d, 0.002d);

        LocationSnapshot start = navigator.update(0d, 0.001d);

        assertEquals(0, navigator.targetIndex());
        assertEquals(0d, start.longitude, 0.000001d);
        assertTrue(navigator.finished(0d, 0d));
    }

    private static BacktrackNavigator navigator() {
        long t = System.currentTimeMillis();
        return new BacktrackNavigator(Arrays.asList(
                point(0d, 0d, t),
                point(0d, 0.001d, t + 1000L),
                point(0d, 0.002d, t + 2000L)));
    }

    private static LocationSnapshot point(double latitude, double longitude, long timestampMs) {
        return new LocationSnapshot(latitude, longitude, 90f, 20f, timestampMs, true);
    }
}
