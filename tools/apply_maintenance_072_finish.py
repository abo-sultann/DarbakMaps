from pathlib import Path


def replace(path, old, new):
    p = Path(path)
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing expected block in {path}: {old[:80]!r}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

# MainActivity: never offer the obsolete generic GCC downloader.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java',
    'findViewById(R.id.download_map).setOnClickListener(view -> confirmRecommendedMapDownload());',
    'findViewById(R.id.download_map).setOnClickListener(view -> chooseMapFile());'
)

# Re-centering explicitly resumes vehicle-follow mode.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java',
    '''        if (mapController != null) {\n            mapController.centerOn(location.getLatitude(), location.getLongitude());\n        }''',
    '''        if (mapController != null) {\n            MapUiPreferences.setFollowVehicle(this, true);\n            mapController.centerOn(location.getLatitude(), location.getLongitude());\n        }'''
)

# Track finalization is asynchronous; do not claim it is already saved.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java',
    '                : "تم إيقاف التسجيل وحفظ المسار");',
    '                : "تم إيقاف التسجيل — جارٍ حفظ المسار بأمان");'
)

# Expired GPS must also clear the stale speed display.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/MainActivity.java',
    '''    public void onProviderState(boolean enabled) {\n        runOnUiThread(() -> gpsStatus.setText(enabled ? "GPS يبحث عن الإشارة" : "GPS غير متاح"));\n    }''',
    '''    public void onProviderState(boolean enabled) {\n        runOnUiThread(() -> {\n            gpsStatus.setText(enabled ? "GPS يبحث عن الإشارة" : "GPS غير متاح");\n            if (!enabled && speedValue != null) speedValue.setText("—");\n        });\n    }'''
)

# OfflineMapController: user drag suspends automatic follow until center button is pressed.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java',
    'import android.graphics.RectF;\n',
    'import android.graphics.RectF;\nimport android.view.MotionEvent;\n'
)
replace(
    'app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java',
    '''        mapView.setBuiltInZoomControls(false);\n        mapView.getMapScaleBar().setVisible(false);''',
    '''        mapView.setBuiltInZoomControls(false);\n        mapView.getMapScaleBar().setVisible(false);\n        mapView.setOnTouchListener((view, event) -> {\n            if (event != null && event.getActionMasked() == MotionEvent.ACTION_MOVE) {\n                MapUiPreferences.setFollowVehicle(view.getContext(), false);\n            }\n            return false;\n        });'''
)

# Keep the live breadcrumb bounded for the low-memory T3. When it reaches the cap,
# rebuild it from a compacted sample instead of allowing the Polyline to grow forever.
replace(
    'app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java',
    '    private Polyline activeTrack;\n',
    '    private Polyline activeTrack;\n    private final List<LatLong> activeTrackPoints = new ArrayList<>();\n    private static final int MAX_ACTIVE_TRACK_POINTS = 12000;\n'
)
replace(
    'app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java',
    '''    public void beginTrack() {\n        if (activeTrack != null) mapView.getLayerManager().getLayers().remove(activeTrack);\n        org.mapsforge.core.graphics.Paint trackPaint = AndroidGraphicFactory.INSTANCE.createPaint();\n        trackPaint.setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 236, 122, 37));\n        trackPaint.setStrokeWidth(7f);\n        trackPaint.setStyle(Style.STROKE);\n        activeTrack = new Polyline(trackPaint, AndroidGraphicFactory.INSTANCE);\n        mapView.getLayerManager().getLayers().add(activeTrack);\n    }\n\n    public void addTrackPoint(double latitude, double longitude) {\n        if (activeTrack != null) {\n            activeTrack.addPoint(new LatLong(latitude, longitude));\n            mapView.getLayerManager().redrawLayers();\n        }\n    }''',
    '''    public void beginTrack() {\n        if (activeTrack != null) mapView.getLayerManager().getLayers().remove(activeTrack);\n        activeTrackPoints.clear();\n        activeTrack = createActiveTrackPolyline();\n        mapView.getLayerManager().getLayers().add(activeTrack);\n    }\n\n    private Polyline createActiveTrackPolyline() {\n        org.mapsforge.core.graphics.Paint trackPaint = AndroidGraphicFactory.INSTANCE.createPaint();\n        trackPaint.setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 236, 122, 37));\n        trackPaint.setStrokeWidth(7f);\n        trackPaint.setStyle(Style.STROKE);\n        return new Polyline(trackPaint, AndroidGraphicFactory.INSTANCE);\n    }\n\n    public void addTrackPoint(double latitude, double longitude) {\n        if (activeTrack == null) return;\n        LatLong point = new LatLong(latitude, longitude);\n        activeTrackPoints.add(point);\n        activeTrack.addPoint(point);\n        if (activeTrackPoints.size() > MAX_ACTIVE_TRACK_POINTS) {\n            List<LatLong> compacted = new ArrayList<>(MAX_ACTIVE_TRACK_POINTS / 2 + 1);\n            for (int i = 0; i < activeTrackPoints.size(); i += 2) compacted.add(activeTrackPoints.get(i));\n            LatLong last = activeTrackPoints.get(activeTrackPoints.size() - 1);\n            if (compacted.isEmpty() || compacted.get(compacted.size() - 1) != last) compacted.add(last);\n            activeTrackPoints.clear();\n            activeTrackPoints.addAll(compacted);\n            mapView.getLayerManager().getLayers().remove(activeTrack);\n            activeTrack = createActiveTrackPolyline();\n            for (LatLong item : activeTrackPoints) activeTrack.addPoint(item);\n            mapView.getLayerManager().getLayers().add(activeTrack);\n        }\n        mapView.getLayerManager().redrawLayers();\n    }'''
)

print('maintenance 0.7.2 finish patches applied')
