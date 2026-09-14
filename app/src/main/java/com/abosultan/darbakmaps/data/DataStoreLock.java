package com.abosultan.darbakmaps.data;

import java.util.concurrent.locks.ReentrantLock;

/** Process-local write lock shared by recorder persistence and destructive data maintenance. */
public final class DataStoreLock {
    private static final ReentrantLock LOCK = new ReentrantLock(true);

    private DataStoreLock() {}

    public static void lock() {
        LOCK.lock();
    }

    public static void unlock() {
        LOCK.unlock();
    }
}
