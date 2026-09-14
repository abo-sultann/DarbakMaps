package com.abosultan.darbakmaps;

import android.content.Context;

import com.abosultan.darbakmaps.data.GeoPoint;
import com.abosultan.darbakmaps.data.PlaceRepository;
import com.abosultan.darbakmaps.map.OfflineMapController;

import java.util.List;

public final class MapRuntimeBridge {
    private static OfflineMapController activeController;

    private MapRuntimeBridge() {}

    public static synchronized void attach(OfflineMapController controller) {
        activeController = controller;
    }

    public static synchronized void detach(OfflineMapController controller) {
        if (activeController == controller) activeController = null;
    }

    public static synchronized int cycleOrientation(Context context) {
        int current = MapUiPreferences.orientation(context);
        int next = current == MapUiPreferences.ORIENTATION_NORTH
                ? MapUiPreferences.ORIENTATION_HEADING
                : current == MapUiPreferences.ORIENTATION_HEADING
                ? MapUiPreferences.ORIENTATION_FREE
                : MapUiPreferences.ORIENTATION_NORTH;
        setOrientation(context, next);
        return next;
    }

    public static synchronized void setOrientation(Context context, int mode) {
        MapUiPreferences.setOrientation(context, mode);
        if (activeController != null) activeController.setOrientationMode(mode);
    }

    public static synchronized void showPoint(double latitude, double longitude) {
        if (activeController != null) activeController.showPoint(latitude, longitude);
    }

    public static synchronized void refreshSavedPlaces(Context context) {
        if (activeController == null) return;
        if (!MapUiPreferences.showSavedPlaces(context)) {
            activeController.showSavedPlaces(java.util.Collections.emptyList(), false);
            return;
        }
        activeController.showSavedPlaces(new PlaceRepository(context).all(), MapUiPreferences.showSavedLabels(context));
    }

    public static synchronized void navigateTo(Context context, double latitude, double longitude) {
        if (activeController == null) return;
        activeController.setNavigationTarget(latitude, longitude, MapUiPreferences.routingMode(context));
    }

    public static synchronized void clearNavigation() {
        if (activeController != null) activeController.clearNavigationTarget();
    }

    public static synchronized boolean showStoredTrack(List<GeoPoint> points) {
        if (activeController == null || points == null || points.isEmpty()) return false;
        activeController.showStoredTrack(points);
        return true;
    }

    public static synchronized void clearStoredTrack() {
        if (activeController != null) activeController.clearStoredTrack();
    }

    public static synchronized void resumeFollow() {
        if (activeController == null) return;
        MapUiPreferences.setFollowVehicle(activeController.view().getContext(), true);
    }

    public static synchronized boolean hasActiveMap() {
        return activeController != null;
    }

    public static String label(int mode) {
        if (mode == MapUiPreferences.ORIENTATION_HEADING) return "اتجاه ↥";
        if (mode == MapUiPreferences.ORIENTATION_FREE) return "حر ⟳";
        return "شمال ↑";
    }

    public static String routingLabel(int mode) {
        return mode == MapUiPreferences.ROUTING_ROADS ? "مع الطرق" : "مباشر";
    }
}
