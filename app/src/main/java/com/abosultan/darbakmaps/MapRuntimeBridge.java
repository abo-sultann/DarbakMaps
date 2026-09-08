package com.abosultan.darbakmaps;

import android.content.Context;

import com.abosultan.darbakmaps.map.OfflineMapController;

final class MapRuntimeBridge {
    private static OfflineMapController activeController;

    private MapRuntimeBridge() {}

    static synchronized void attach(OfflineMapController controller) {
        activeController = controller;
    }

    static synchronized void detach(OfflineMapController controller) {
        if (activeController == controller) {
            activeController = null;
        }
    }

    static synchronized int cycleOrientation(Context context) {
        int current = MapUiPreferences.orientation(context);
        int next = current == MapUiPreferences.ORIENTATION_NORTH
                ? MapUiPreferences.ORIENTATION_HEADING
                : current == MapUiPreferences.ORIENTATION_HEADING
                ? MapUiPreferences.ORIENTATION_FREE
                : MapUiPreferences.ORIENTATION_NORTH;
        MapUiPreferences.setOrientation(context, next);
        if (activeController != null) {
            activeController.setOrientationMode(next);
        }
        return next;
    }

    static String label(int mode) {
        if (mode == MapUiPreferences.ORIENTATION_HEADING) {
            return "اتجاه ↥";
        }
        if (mode == MapUiPreferences.ORIENTATION_FREE) {
            return "حر ⟳";
        }
        return "شمال ↑";
    }
}
