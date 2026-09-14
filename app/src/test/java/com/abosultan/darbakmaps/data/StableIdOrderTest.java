package com.abosultan.darbakmaps.data;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class StableIdOrderTest {
    @Test public void gpsRefreshDoesNotReorderRowsUnderTouch() {
        assertEquals(Arrays.asList("c","a","b","d"),
                StableIdOrder.preserve(Arrays.asList("c","a","b"), Arrays.asList("b","a","c","d")));
    }
}
