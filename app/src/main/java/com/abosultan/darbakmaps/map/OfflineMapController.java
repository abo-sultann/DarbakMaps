package com.abosultan.darbakmaps.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.abosultan.darbakmaps.MapRuntimeBridge;
import com.abosultan.darbakmaps.MapUiPreferences;
import com.abosultan.darbakmaps.data.GeoPoint;
import com.abosultan.darbakmaps.data.PlaceRepository;

import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.core.model.Rotation;
import org.mapsforge.map.android.graphics.AndroidBitmap;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class OfflineMapController {
    private static final int MAX_ACTIVE_DRAW_POINTS = 4000;
    private static final int MAX_STORED_DRAW_POINTS = 10000;

    private final MapView mapView;
    private final TileCache tileCache;
    private final TileRendererLayer rendererLayer;
    private final XmlRenderTheme darbakTheme;
    private final List<Marker> savedMarkers = new ArrayList<>();
    private final List<Polyline> activeTrackSegments = new ArrayList<>();
    private final List<Polyline> storedTrackSegments = new ArrayList<>();
    private Marker locationMarker;
    private Marker selectedMarker;
    private Marker navigationMarker;
    private Polyline navigationLine;
    private Polyline activeTrack;
    private int activeTrackPointCount;
    private int lastArrowBucket = Integer.MIN_VALUE;
    private int lastMapBearingBucket = Integer.MIN_VALUE;
    private boolean centeredOnFirstFix;
    private boolean followSuspended;
    private int orientationMode;
    private LatLong lastLocation;
    private LatLong navigationTarget;
    private float lastBearing;
    private boolean hasLastBearing;
    private int navigationMode = MapUiPreferences.ROUTING_DIRECT;

    public OfflineMapController(Context context, File file) {
        MapsforgeRuntime.ensureInitialized(context);
        mapView = new MapView(context);
        mapView.setClickable(true);
        mapView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) followSuspended = true;
            return false;
        });
        mapView.setBuiltInZoomControls(false);
        mapView.getMapScaleBar().setVisible(false);

        tileCache = AndroidUtil.createTileCache(
                context,
                "darbak-vector-cache",
                mapView.getModel().displayModel.getTileSize(),
                1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor()
        );

        MapFile mapFile = new MapFile(file, "ar");
        darbakTheme = createDarbakTheme(context);
        rendererLayer = AndroidUtil.createTileRendererLayer(
                tileCache,
                mapView.getModel().mapViewPosition,
                mapFile,
                darbakTheme,
                false,
                true,
                false
        );
        mapView.getLayerManager().getLayers().add(rendererLayer);
        mapView.getModel().mapViewPosition.setMapPosition(
                new MapPosition(mapFile.boundingBox().getCenterPoint(), (byte) 7)
        );

        orientationMode = MapUiPreferences.orientation(context);
        navigationMode = MapUiPreferences.routingMode(context);
        applyOrientationBehavior();
        MapRuntimeBridge.attach(this);
    }

    public MapView view() { return mapView; }

    public void zoomIn() {
        byte current = mapView.getModel().mapViewPosition.getZoomLevel();
        mapView.getModel().mapViewPosition.setZoomLevel((byte) Math.min(20, current + 1));
    }

    public void zoomOut() {
        byte current = mapView.getModel().mapViewPosition.getZoomLevel();
        mapView.getModel().mapViewPosition.setZoomLevel((byte) Math.max(3, current - 1));
    }

    public void centerOn(double latitude, double longitude) {
        mapView.getModel().mapViewPosition.setCenter(new LatLong(latitude, longitude));
        if (mapView.getModel().mapViewPosition.getZoomLevel() < 13) {
            mapView.getModel().mapViewPosition.setZoomLevel((byte) 13);
        }
    }

    public void resumeFollow() {
        followSuspended = false;
        if (lastLocation != null) {
            mapView.getModel().mapViewPosition.setCenter(lastLocation);
            if (mapView.getModel().mapViewPosition.getZoomLevel() < 13) {
                mapView.getModel().mapViewPosition.setZoomLevel((byte) 13);
            }
        }
    }

    public void setDesertMode(boolean ignored) {
        rendererLayer.setXmlRenderTheme(darbakTheme);
        tileCache.purge();
        mapView.getLayerManager().redrawLayers();
    }

    public void setOrientationMode(int mode) {
        if (mode < MapUiPreferences.ORIENTATION_NORTH || mode > MapUiPreferences.ORIENTATION_FREE) {
            mode = MapUiPreferences.ORIENTATION_NORTH;
        }
        orientationMode = mode;
        lastMapBearingBucket = Integer.MIN_VALUE;
        applyOrientationBehavior();
        refreshOrientationFromLastFix();
    }

    private void applyOrientationBehavior() {
        boolean manualRotation = orientationMode == MapUiPreferences.ORIENTATION_FREE;
        mapView.getTouchGestureHandler().setRotationEnabled(manualRotation);
        mapView.setMapViewCenterY(orientationMode == MapUiPreferences.ORIENTATION_HEADING ? 0.62f : 0.5f);
        if (orientationMode == MapUiPreferences.ORIENTATION_NORTH) rotateMapTo(0f);
    }

    public void updateLocation(double latitude, double longitude, float bearing) {
        lastLocation = new LatLong(latitude, longitude);
        boolean bearingValid = !Float.isNaN(bearing) && !Float.isInfinite(bearing);
        if (bearingValid) {
            lastBearing = normalize(bearing);
            hasLastBearing = true;
        }

        if (orientationMode == MapUiPreferences.ORIENTATION_HEADING && bearingValid) {
            int bearingBucket = Math.round(lastBearing / 10f) * 10;
            if (bearingBucket != lastMapBearingBucket) {
                lastMapBearingBucket = bearingBucket;
                rotateMapTo(-bearingBucket);
            }
        }

        updateLocationMarker(lastLocation, bearingValid ? lastBearing : Float.NaN);
        updateNavigationLine();

        if (MapUiPreferences.followVehicle(mapView.getContext()) && !followSuspended && centeredOnFirstFix) {
            mapView.getModel().mapViewPosition.setCenter(lastLocation);
        }
        if (!centeredOnFirstFix) {
            centeredOnFirstFix = true;
            centerOn(latitude, longitude);
        }
        mapView.getLayerManager().redrawLayers();
    }

    private void refreshOrientationFromLastFix() {
        if (orientationMode == MapUiPreferences.ORIENTATION_HEADING && lastLocation != null && hasLastBearing) {
            rotateMapTo(-lastBearing);
        } else if (orientationMode == MapUiPreferences.ORIENTATION_NORTH) {
            rotateMapTo(0f);
        }
        if (lastLocation != null) updateLocationMarker(lastLocation, hasLastBearing ? lastBearing : Float.NaN);
    }

    private void updateLocationMarker(LatLong position, float bearing) {
        boolean bearingValid = !Float.isNaN(bearing) && !Float.isInfinite(bearing);
        float screenBearing = bearingValid ? normalize(bearing + mapView.getMapRotation().degrees) : 0f;
        int bucket = bearingValid ? Math.round(screenBearing / 10f) * 10 : Integer.MIN_VALUE;
        if (locationMarker == null) {
            locationMarker = new Marker(position, bearingValid ? createArrow(bucket) : createLocationDot(), 0, 0);
            locationMarker.setBillboard(true);
            mapView.getLayerManager().getLayers().add(locationMarker);
            lastArrowBucket = bucket;
        } else {
            locationMarker.setLatLong(position);
            if (bucket != lastArrowBucket) {
                locationMarker.setBitmap(bearingValid ? createArrow(bucket) : createLocationDot());
                lastArrowBucket = bucket;
            }
        }
    }

    private void rotateMapTo(float degrees) {
        final float normalized = normalizeSigned(degrees);
        Runnable action = () -> {
            float px = mapView.getWidth() > 0 ? mapView.getWidth() * 0.5f : 0f;
            float py = mapView.getHeight() > 0 ? mapView.getHeight() * 0.5f : 0f;
            mapView.rotate(new Rotation(normalized, px, py));
            mapView.getLayerManager().redrawLayers();
        };
        if (mapView.getWidth() == 0 || mapView.getHeight() == 0) mapView.post(action); else action.run();
    }

    public void showPoint(double latitude, double longitude) {
        followSuspended = true;
        LatLong position = new LatLong(latitude, longitude);
        if (selectedMarker == null) {
            selectedMarker = new Marker(position, createPin(), 0, -24);
            selectedMarker.setBillboard(true);
            mapView.getLayerManager().getLayers().add(selectedMarker);
        } else {
            selectedMarker.setLatLong(position);
        }
        centerOn(latitude, longitude);
        mapView.getLayerManager().redrawLayers();
    }

    public void showSavedPlaces(List<PlaceRepository.Place> places, boolean showLabels) {
        for (Marker marker : savedMarkers) mapView.getLayerManager().getLayers().remove(marker);
        savedMarkers.clear();
        if (places != null) {
            int limit = Math.min(places.size(), 250);
            for (int i = 0; i < limit; i++) {
                PlaceRepository.Place place = places.get(i);
                Marker marker = new Marker(new LatLong(place.latitude, place.longitude),
                        createSavedMarker(place, showLabels), 0, -20);
                marker.setBillboard(true);
                savedMarkers.add(marker);
                mapView.getLayerManager().getLayers().add(marker);
            }
        }
        mapView.getLayerManager().redrawLayers();
    }

    public void setNavigationTarget(double latitude, double longitude, int mode) {
        navigationTarget = new LatLong(latitude, longitude);
        navigationMode = mode == MapUiPreferences.ROUTING_ROADS ? MapUiPreferences.ROUTING_ROADS : MapUiPreferences.ROUTING_DIRECT;
        if (navigationMarker == null) {
            navigationMarker = new Marker(navigationTarget, createNavigationTarget(), 0, -22);
            navigationMarker.setBillboard(true);
            mapView.getLayerManager().getLayers().add(navigationMarker);
        } else {
            navigationMarker.setLatLong(navigationTarget);
        }
        updateNavigationLine();
        showPoint(latitude, longitude);
        mapView.getLayerManager().redrawLayers();
    }

    public void clearNavigationTarget() {
        navigationTarget = null;
        if (navigationMarker != null) {
            mapView.getLayerManager().getLayers().remove(navigationMarker);
            navigationMarker = null;
        }
        if (navigationLine != null) {
            mapView.getLayerManager().getLayers().remove(navigationLine);
            navigationLine = null;
        }
        mapView.getLayerManager().redrawLayers();
    }

    private void updateNavigationLine() {
        if (navigationTarget == null || lastLocation == null) return;
        if (navigationLine != null) mapView.getLayerManager().getLayers().remove(navigationLine);
        org.mapsforge.core.graphics.Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        int color = navigationMode == MapUiPreferences.ROUTING_ROADS
                ? AndroidGraphicFactory.INSTANCE.createColor(245, 215, 173, 85)
                : AndroidGraphicFactory.INSTANCE.createColor(245, 57, 169, 255);
        paint.setColor(color);
        paint.setStrokeWidth(navigationMode == MapUiPreferences.ROUTING_ROADS ? 9f : 7f);
        paint.setStyle(Style.STROKE);
        navigationLine = new Polyline(paint, AndroidGraphicFactory.INSTANCE);
        navigationLine.addPoint(lastLocation);
        navigationLine.addPoint(navigationTarget);
        mapView.getLayerManager().getLayers().add(navigationLine);
    }

    public void showStoredTrack(List<GeoPoint> points) {
        clearStoredTrack();
        drawSegmentedTrack(points, storedTrackSegments, MAX_STORED_DRAW_POINTS, 230, 8, 62, 45, 8f);
        if (points != null && !points.isEmpty()) {
            GeoPoint last = points.get(points.size() - 1);
            followSuspended = true;
            centerOn(last.latitude, last.longitude);
        }
        mapView.getLayerManager().redrawLayers();
    }

    public void clearStoredTrack() {
        removeTrackLayers(storedTrackSegments);
        mapView.getLayerManager().redrawLayers();
    }

    public void showActiveTrack(List<GeoPoint> points) {
        clearActiveTrackLayers();
        drawSegmentedTrack(points, activeTrackSegments, MAX_ACTIVE_DRAW_POINTS, 255, 236, 122, 37, 7f);
        activeTrack = activeTrackSegments.isEmpty() ? null : activeTrackSegments.get(activeTrackSegments.size() - 1);
        activeTrackPointCount = countPolylinePoints(points, MAX_ACTIVE_DRAW_POINTS);
        mapView.getLayerManager().redrawLayers();
    }

    public void beginTrack() {
        clearActiveTrackLayers();
        startTrackSegment();
    }

    public void startTrackSegment() {
        activeTrack = newTrackPolyline(255, 236, 122, 37, 7f);
        activeTrackSegments.add(activeTrack);
        mapView.getLayerManager().getLayers().add(activeTrack);
    }

    public void addTrackPoint(double latitude, double longitude) {
        if (activeTrack == null) startTrackSegment();
        if (activeTrackPointCount >= MAX_ACTIVE_DRAW_POINTS) {
            // Bound only the live representation. The authoritative journal/GPX remains complete.
            clearActiveTrackLayers();
            startTrackSegment();
            activeTrackPointCount = 0;
        }
        activeTrack.addPoint(new LatLong(latitude, longitude));
        activeTrackPointCount++;
        mapView.getLayerManager().redrawLayers();
    }

    private void drawSegmentedTrack(List<GeoPoint> points, List<Polyline> targetLayers, int maxPoints,
                                    int alpha, int red, int green, int blue, float width) {
        if (points == null || points.isEmpty()) return;
        int step = Math.max(1, (int) Math.ceil(points.size() / (double) maxPoints));
        Polyline segment = null;
        for (int index = 0; index < points.size(); index++) {
            GeoPoint point = points.get(index);
            boolean boundary = point.segmentStart || index == 0;
            boolean keep = boundary || index == points.size() - 1 || index % step == 0
                    || (index + 1 < points.size() && points.get(index + 1).segmentStart);
            if (!keep) continue;
            if (segment == null || boundary) {
                segment = newTrackPolyline(alpha, red, green, blue, width);
                targetLayers.add(segment);
                mapView.getLayerManager().getLayers().add(segment);
            }
            segment.addPoint(new LatLong(point.latitude, point.longitude));
        }
    }

    private int countPolylinePoints(List<GeoPoint> points, int maxPoints) {
        return points == null ? 0 : Math.min(points.size(), maxPoints);
    }

    private Polyline newTrackPolyline(int alpha, int red, int green, int blue, float width) {
        org.mapsforge.core.graphics.Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setColor(AndroidGraphicFactory.INSTANCE.createColor(alpha, red, green, blue));
        paint.setStrokeWidth(width);
        paint.setStyle(Style.STROKE);
        return new Polyline(paint, AndroidGraphicFactory.INSTANCE);
    }

    private void clearActiveTrackLayers() {
        removeTrackLayers(activeTrackSegments);
        activeTrack = null;
        activeTrackPointCount = 0;
    }

    private void removeTrackLayers(List<Polyline> layers) {
        for (Polyline line : layers) mapView.getLayerManager().getLayers().remove(line);
        layers.clear();
    }

    public void destroy() {
        MapRuntimeBridge.detach(this);
        clearStoredTrack();
        clearActiveTrackLayers();
        mapView.destroyAll();
        AndroidGraphicFactory.clearResourceMemoryCache();
    }

    private XmlRenderTheme createDarbakTheme(Context context) {
        try {
            return new AssetsRenderTheme(context.getAssets(), "renderthemes/", "darbak_desert.xml");
        } catch (RuntimeException error) {
            return MapsforgeThemes.MOTORIDER;
        }
    }

    private org.mapsforge.core.graphics.Bitmap createSavedMarker(PlaceRepository.Place place, boolean showLabel) {
        String glyph = PlaceRepository.iconGlyph(place.iconKey);
        String label = place.name == null ? "" : place.name.trim();
        int width = showLabel ? 220 : 66;
        int height = 68;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(235, 7, 17, 29));
        canvas.drawRoundRect(new RectF(1, 1, width - 1, height - 8), 18, 18, bg);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f);
        border.setColor(Color.rgb(215, 173, 85));
        canvas.drawRoundRect(new RectF(2, 2, width - 2, height - 9), 18, 18, border);

        Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        icon.setTextSize(30f);
        icon.setColor(Color.rgb(215, 173, 85));
        icon.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(glyph, 34f, 42f, icon);

        if (showLabel) {
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setTextSize(20f);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.RIGHT);
            String shown = label.length() > 18 ? label.substring(0, 18) + "…" : label;
            canvas.drawText(shown, width - 14f, 41f, text);
        }

        Path tip = new Path();
        tip.moveTo(24f, height - 9f);
        tip.lineTo(34f, height);
        tip.lineTo(44f, height - 9f);
        tip.close();
        Paint tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipPaint.setColor(Color.rgb(215, 173, 85));
        canvas.drawPath(tip, tipPaint);
        return new AndroidBitmap(bitmap);
    }

    private org.mapsforge.core.graphics.Bitmap createNavigationTarget() {
        int size = 60;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f, 25f, outer);
        Paint middle = new Paint(Paint.ANTI_ALIAS_FLAG);
        middle.setColor(Color.rgb(215, 173, 85));
        canvas.drawCircle(size / 2f, size / 2f, 19f, middle);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(Color.rgb(7, 17, 29));
        canvas.drawCircle(size / 2f, size / 2f, 8f, inner);
        return new AndroidBitmap(bitmap);
    }

    private org.mapsforge.core.graphics.Bitmap createLocationDot() {
        int size = 44;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f, 15f, outer);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(Color.rgb(57, 169, 255));
        canvas.drawCircle(size / 2f, size / 2f, 10f, inner);
        return new AndroidBitmap(bitmap);
    }

    private org.mapsforge.core.graphics.Bitmap createArrow(float bearing) {
        int size = 58;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.rotate(bearing, size / 2f, size / 2f);

        Path arrow = new Path();
        arrow.moveTo(size / 2f, 4f);
        arrow.lineTo(size - 8f, size - 7f);
        arrow.lineTo(size / 2f, size - 18f);
        arrow.lineTo(8f, size - 7f);
        arrow.close();

        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setStyle(Paint.Style.FILL);
        outline.setColor(Color.WHITE);
        canvas.drawPath(arrow, outline);

        canvas.save();
        canvas.scale(0.72f, 0.72f, size / 2f, size / 2f);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(57, 169, 255));
        canvas.drawPath(arrow, fill);
        canvas.restore();
        return new AndroidBitmap(bitmap);
    }

    private org.mapsforge.core.graphics.Bitmap createPin() {
        int width = 52;
        int height = 64;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setColor(Color.WHITE);
        outline.setStyle(Paint.Style.FILL);
        canvas.drawCircle(width / 2f, 23f, 21f, outline);
        Path outerTip = new Path();
        outerTip.moveTo(8f, 27f);
        outerTip.lineTo(width / 2f, 63f);
        outerTip.lineTo(width - 8f, 27f);
        outerTip.close();
        canvas.drawPath(outerTip, outline);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.rgb(215, 173, 85));
        canvas.drawCircle(width / 2f, 23f, 16f, fill);
        Path tip = new Path();
        tip.moveTo(12f, 27f);
        tip.lineTo(width / 2f, 57f);
        tip.lineTo(width - 12f, 27f);
        tip.close();
        canvas.drawPath(tip, fill);

        Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
        center.setColor(Color.rgb(7, 17, 29));
        canvas.drawCircle(width / 2f, 22f, 7f, center);
        return new AndroidBitmap(bitmap);
    }

    private float normalize(float value) {
        float normalized = value % 360f;
        if (normalized < 0f) normalized += 360f;
        return normalized;
    }

    private float normalizeSigned(float value) {
        float normalized = normalize(value);
        return normalized > 180f ? normalized - 360f : normalized;
    }
}
