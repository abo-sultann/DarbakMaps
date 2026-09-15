package com.abosultan.darbakmaps.core;

import static com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;

/** Process-local latest GPS fix. The recording service owns the single LocationManager listener. */
public final class LiveLocationStore {
    private static volatile LocationSnapshot latest = invalid();

    private LiveLocationStore() {}

    public static LocationSnapshot latest() { return latest; }

    public static void publish(LocationSnapshot snapshot) {
        latest = snapshot == null ? invalid() : snapshot;
    }

    public static void invalidate() { latest = invalid(); }

    private static LocationSnapshot invalid() {
        return new LocationSnapshot(0d, 0d, 0f, 0f, 0L, false);
    }
}
