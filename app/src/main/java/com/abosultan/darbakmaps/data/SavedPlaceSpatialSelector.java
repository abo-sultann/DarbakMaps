package com.abosultan.darbakmaps.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Storage-order-independent viewport selector for saved map markers. */
public final class SavedPlaceSpatialSelector {
    private SavedPlaceSpatialSelector() {}

    public static List<PlaceRepository.Place> select(List<PlaceRepository.Place> places,
                                                      double centerLat, double centerLon,
                                                      double radiusMeters, int maxItems) {
        List<PlaceRepository.Place> candidates = new ArrayList<>();
        if (places == null || maxItems <= 0 || radiusMeters < 0d) return candidates;
        for (PlaceRepository.Place place : places) {
            if (distanceMeters(centerLat, centerLon, place.latitude, place.longitude) <= radiusMeters) {
                candidates.add(place);
            }
        }
        Collections.sort(candidates, Comparator.comparingDouble(
                place -> distanceMeters(centerLat, centerLon, place.latitude, place.longitude)));
        if (candidates.size() > maxItems) {
            return new ArrayList<>(candidates.subList(0, maxItems));
        }
        return candidates;
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        return 6371000d * 2d * Math.asin(Math.sqrt(Math.max(0d, Math.min(1d, h))));
    }
}
