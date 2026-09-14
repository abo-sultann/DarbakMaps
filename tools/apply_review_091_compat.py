from pathlib import Path

ROOT = Path('.')

def replace(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'marker not found in {path}: {old[:80]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# MapUiPreferences: preserve the current setting name, add source-compat alias only.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/MapUiPreferences.java',
    '    public static void setShowSavedLabels(Context context, boolean value) {\n        prefs(context).edit().putBoolean(KEY_SHOW_SAVED_LABELS, value).apply();\n    }\n',
    '    public static void setShowSavedLabels(Context context, boolean value) {\n        prefs(context).edit().putBoolean(KEY_SHOW_SAVED_LABELS, value).apply();\n    }\n\n    /** Source-compatibility alias for review-era MainActivity. */\n    public static boolean showSavedNames(Context context) {\n        return showSavedLabels(context);\n    }\n'
)

# PlaceRepository: compatibility helper used by the saved hub; bounded, deterministic nearest list.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/data/PlaceRepository.java',
    '    public Place findById(String id) {\n        if (id == null) return null;\n        for (Place place : all()) if (id.equals(place.id)) return place;\n        return null;\n    }\n',
    '    public Place findById(String id) {\n        if (id == null) return null;\n        for (Place place : all()) if (id.equals(place.id)) return place;\n        return null;\n    }\n\n    public List<Place> nearest(double latitude, double longitude, int limit) {\n        validateCoordinates(latitude, longitude);\n        List<Place> result = new ArrayList<>(all());\n        result.sort((a, b) -> Double.compare(\n                distanceMeters(latitude, longitude, a.latitude, a.longitude),\n                distanceMeters(latitude, longitude, b.latitude, b.longitude)));\n        int cap = Math.max(0, limit);\n        return result.size() <= cap ? result : new ArrayList<>(result.subList(0, cap));\n    }\n\n    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {\n        double dLat = Math.toRadians(lat2 - lat1);\n        double dLon = Math.toRadians(lon2 - lon1);\n        double h = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)\n                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))\n                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);\n        return 6371000d * 2d * Math.asin(Math.sqrt(Math.max(0d, Math.min(1d, h))));\n    }\n'
)

# TrackStorage: re-expose review-era names while keeping the geometry-preserving loader.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/data/TrackStorage.java',
    '    static List<GeoPoint> reducePreservingGeometry(List<GeoPoint> input, int limit) {\n',
    '    public static List<GeoPoint> readDisplay(File file, int limit) throws IOException {\n        List<GeoPoint> points = load(file);\n        return points.size() <= limit ? points : reducePreservingGeometry(points, Math.max(2, limit));\n    }\n\n    public static List<GeoPoint> readForNavigation(File file) throws IOException {\n        return load(file);\n    }\n\n    static List<GeoPoint> reducePreservingGeometry(List<GeoPoint> input, int limit) {\n'
)

# BacktrackGuidance: source-compat overload; still uses the segment-aware TrackStorage loader.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/BacktrackGuidance.java',
    '    public static void start(Activity activity, File file, List<GeoPoint> points) {\n',
    '    public static void start(Activity activity, File file) {\n        try {\n            start(activity, file, TrackStorage.readForNavigation(file));\n        } catch (Exception error) {\n            Toast.makeText(activity, "تعذر قراءة مسار الرجوع", Toast.LENGTH_LONG).show();\n        }\n    }\n\n    public static void start(Activity activity, File file, List<GeoPoint> points) {\n'
)

# MainActivity: restore two public/package helpers still referenced by DarbakPanels/CarScreenLayout.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java',
    '    private void showNearbySearchOptions(double latitude, double longitude, String centerLabel) {\n',
    '    void showNearbySearchFromCurrentLocation() {\n        Location current = locationController == null ? null : locationController.getLastLocation();\n        if (current == null) {\n            toast("بانتظار GPS للبحث حول موقعي");\n            return;\n        }\n        showNearbySearchOptions(current.getLatitude(), current.getLongitude(), "موقعي");\n    }\n\n    private void showNearbySearchOptions(double latitude, double longitude, String centerLabel) {\n'
)
replace(
    'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java',
    '    private void chooseMapFile() {\n        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);\n        intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);\n        startActivityForResult(intent, REQUEST_MAP_FILE);\n    }\n',
    '    private void chooseMapFile() {\n        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);\n        intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);\n        startActivityForResult(intent, REQUEST_MAP_FILE);\n    }\n\n    void chooseLegacyMigration() {\n        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);\n        intent.addCategory(Intent.CATEGORY_OPENABLE);\n        intent.setType("application/zip");\n        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);\n        startActivityForResult(intent, REQUEST_MIGRATION);\n    }\n'
)

print('0.9.1 compatibility patch applied')
