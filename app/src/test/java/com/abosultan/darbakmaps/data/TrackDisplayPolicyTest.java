package com.abosultan.darbakmaps.data;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class TrackDisplayPolicyTest {
    @Test public void layerCapKeepsWholeHistoryAndAllRecentSegments() {
        Set<Integer> selected = TrackDisplayPolicy.selectSegments(1000, 160);
        assertTrue(selected.size() <= 160);
        assertTrue(selected.contains(0));
        assertTrue(selected.contains(999));
        for (int i = 920; i < 1000; i++) assertTrue("recent segment " + i, selected.contains(i));
        boolean middleHistory = false;
        for (Integer i : selected) if (i > 200 && i < 700) middleHistory = true;
        assertTrue(middleHistory);
    }
}
