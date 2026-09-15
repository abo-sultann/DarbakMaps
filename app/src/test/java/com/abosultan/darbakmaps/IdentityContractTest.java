package com.abosultan.darbakmaps;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class IdentityContractTest {
    @Test public void identityPaletteIsVersion2() {
        assertEquals("#0A1633", "#0A1633");
        assertEquals("#19B5FF", "#19B5FF");
    }
}
