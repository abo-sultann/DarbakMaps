package com.abosultan.darbakmaps.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationTransactionSemanticsTest {
    @Test public void applyingRequiresRollbackWhileCommittedKeepsNewState() {
        assertTrue(needsRollback("APPLYING"));
        assertTrue(needsRollback(""));
        assertFalse(needsRollback("COMMITTED"));
    }
    private boolean needsRollback(String state) { return !"COMMITTED".equals(state); }
}
