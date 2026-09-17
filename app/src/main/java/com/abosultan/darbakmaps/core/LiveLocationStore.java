package com.abosultan.darbakmaps.core;

import static com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;

/** Process-local latest GPS fix. The recording service owns the single LocationManager listener. */
public final class LiveLocationStore {
    private static final long MAX_FIX_AGE_MS = 15000L;
    private static volatile LocationSnapshot latest = invalid();

    private LiveLocationStore() {}

    public static LocationSnapshot latest() {
        LocationSnapshot snapshot = latest;
        if (!snapshot.valid || snapshot.timestampMs <= 0L) return snapshot.valid ? invalid() : snapshot;
        long age = System.currentTimeMillis() - snapshot.timestampMs;
        return age >= 0L && age <= MAX_FIX_AGE_MS ? snapshot : invalid();
    }

    public static void publish(LocationSnapshot snapshot) {
        latest = snapshot == null ? invalid() : snapshot;
    }

    public static void invalidate() { latest = invalid(); }

    private static LocationSnapshot invalid() {
        return new LocationSnapshot(0d, 0d, -1f, 0f, 0L, false);
    }
}
