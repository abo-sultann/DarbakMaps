package com.abosultan.darbakmaps.core;

import java.util.List;

/**
 * Stable boundaries between the UI and the new engines.
 * UI code must depend on these contracts, not on Mapsforge/GPS/storage implementations.
 */
public final class CoreContracts {
    private CoreContracts() {}

    public interface MapEngine {
        enum State { NOT_READY, READY, ERROR }
        State state();
        void attach(Object surfaceToken);
        void center(double latitude, double longitude);
    }

    public interface LocationEngine {
        LocationSnapshot latest();
        void start();
        void stop();
    }

    public interface PlaceRepository {
        long save(Place place);
        List<Place> nearest(double latitude, double longitude, String category, int limit);
    }

    public interface TrackRecorder {
        enum State { STOPPED, RECORDING, PAUSED, ERROR }
        State state();
        void ensureAutomaticRecording();
    }

    public interface SearchEngine {
        List<SearchResult> search(String query, int limit);
    }

    public static final class LocationSnapshot {
        public final double latitude;
        public final double longitude;
        public final float bearing;
        public final float speedKmh;
        public final long timestampMs;
        public final boolean valid;

        public LocationSnapshot(double latitude, double longitude, float bearing, float speedKmh, long timestampMs, boolean valid) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.bearing = bearing;
            this.speedKmh = speedKmh;
            this.timestampMs = timestampMs;
            this.valid = valid;
        }
    }

    public static final class Place {
        public final long id;
        /** Stable identity across database row changes, exports and future imports. */
        public final String uuid;
        public final double latitude;
        public final double longitude;
        public final String category;
        public final String name;
        public final String note;

        /** Compatibility constructor for new/transient places; the repository assigns a UUID on save. */
        public Place(long id, double latitude, double longitude, String category, String name, String note) {
            this(id, null, latitude, longitude, category, name, note);
        }

        public Place(long id, String uuid, double latitude, double longitude, String category, String name, String note) {
            this.id = id;
            this.uuid = uuid;
            this.latitude = latitude;
            this.longitude = longitude;
            this.category = category;
            this.name = name;
            this.note = note;
        }
    }

    public static final class SearchResult {
        public final String title;
        public final double latitude;
        public final double longitude;
        public final String source;

        public SearchResult(String title, double latitude, double longitude, String source) {
            this.title = title;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source;
        }
    }
}
