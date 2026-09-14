package com.abosultan.darbakmaps;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectionMathTest {
    @Test
    public void relativeDirectionHandles359ToZeroWrap() {
        assertEquals(1f, DirectionMath.relative(0f, 359f), 0.001f);
        assertEquals(-1f, DirectionMath.relative(359f, 0f), 0.001f);
    }

    @Test
    public void smoothingUsesShortestCircularPath() {
        float smoothed = DirectionMath.smoothAngle(179f, -179f, 0.5f);
        assertTrue(Math.abs(Math.abs(smoothed) - 180f) < 1.1f);
    }

    @Test
    public void cardinalNamesAreStable() {
        assertEquals("شمال", DirectionMath.cardinalArabic(359f));
        assertEquals("شرق", DirectionMath.cardinalArabic(91f));
        assertEquals("جنوب غرب", DirectionMath.cardinalArabic(226f));
    }
}
