package com.abosultan.darbakmaps;

import android.app.Application;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

public final class DarbakApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AndroidGraphicFactory.createInstance(this);
    }
}

