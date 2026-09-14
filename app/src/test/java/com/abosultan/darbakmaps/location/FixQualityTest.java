package com.abosultan.darbakmaps.location;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FixQualityTest {
    @Test
    public void rejectsStalePoorOrInvalidFixes() {
        assertFalse(FixQuality.usable(16000L, 15000L, true, 5f, 250f, 25d, 45d));
        assertFalse(FixQuality.usable(-1L, 15000L, true, 5f, 250f, 25d, 45d));
        assertFalse(FixQuality.usable(1000L, 15000L, false, 0f, 250f, 25d, 45d));
        assertFalse(FixQuality.usable(1000L, 15000L, true, 300f, 250f, 25d, 45d));
        assertFalse(FixQuality.usable(1000L, 15000L, true, 5f, 250f, Double.NaN, 45d));
        assertFalse(FixQuality.usable(1000L, 15000L, true, 5f, 250f, 91d, 45d));
        assertTrue(FixQuality.usable(1000L, 15000L, true, 8f, 250f, 25d, 45d));
    }

    @Test
    public void cachedWindowCanBeLongerWithoutAcceptingBadAccuracy() {
        assertTrue(FixQuality.usable(25000L, 30000L, true, 25f, 250f, 25d, 45d));
        assertFalse(FixQuality.usable(25000L, 30000L, true, 251f, 250f, 25d, 45d));
    }
}
