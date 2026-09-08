package com.abosultan.darbakmaps;

import android.app.Application;

/**
 * Intentionally lightweight. Mapsforge is initialized only when a real map is opened because
 * some Android 7 car-head-unit ROMs cannot initialize its graphics stack during process startup.
 * DarbakPlatformRuntime installs only a local uncaught-exception handler and performs no map,
 * GPS, network, license, or large-file work here.
 */
public final class DarbakApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DarbakPlatformRuntime.install(this);
    }
}
