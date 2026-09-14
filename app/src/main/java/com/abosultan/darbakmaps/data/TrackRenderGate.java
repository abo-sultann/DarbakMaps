package com.abosultan.darbakmaps.data;

/** Prevents an older asynchronous journal preview from replacing a newer committed render. */
public final class TrackRenderGate {
    private long latestToken;
    private long minimumGeneration;
    private long appliedGeneration = -1L;

    public synchronized long request(long requiredGeneration) {
        latestToken++;
        minimumGeneration = Math.max(minimumGeneration, Math.max(0L, requiredGeneration));
        return latestToken;
    }

    public synchronized boolean mayApply(long token, long generation) {
        if (token != latestToken) return false;
        if (generation < minimumGeneration || generation < appliedGeneration) return false;
        appliedGeneration = generation;
        return true;
    }

    public synchronized long appliedGeneration() {
        return appliedGeneration;
    }
}
