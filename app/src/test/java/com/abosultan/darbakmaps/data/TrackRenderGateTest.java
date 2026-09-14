package com.abosultan.darbakmaps.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackRenderGateTest {
    @Test public void olderAsyncLoadCannotReplaceNewerRequest() {
        TrackRenderGate gate = new TrackRenderGate();
        long old = gate.request(10L);
        long latest = gate.request(12L);
        assertFalse(gate.mayApply(old, 10L));
        assertFalse(gate.mayApply(latest, 11L));
        assertTrue(gate.mayApply(latest, 12L));
        assertEquals(12L, gate.appliedGeneration());
    }
}
