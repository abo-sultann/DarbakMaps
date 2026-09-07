package com.abosultan.darbakmaps.map;

import android.content.Context;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

/** Lazily initializes the Mapsforge Android graphics bridge. */
final class MapsforgeRuntime {
    private static boolean initialized;

    private MapsforgeRuntime() {
    }

    static synchronized void ensureInitialized(Context context) {
        if (initialized) {
            return;
        }
        AndroidGraphicFactory.createInstance(context.getApplicationContext());
        initialized = true;
    }
}
