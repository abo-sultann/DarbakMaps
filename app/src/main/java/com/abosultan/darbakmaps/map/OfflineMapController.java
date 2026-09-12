package com.abosultan.darbakmaps.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import com.abosultan.darbakmaps.MapRuntimeBridge;
import com.abosultan.darbakmaps.MapUiPreferences;
import com.abosultan.darbakmaps.data.GeoPoint;

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
import java.util.List;

public final class OfflineMapController {
    private final MapView mapView;
    private final TileCache tileCache;
    private final TileRendererLayer rendererLayer;
    private final XmlRenderTheme darbakTheme;
    private Marker locationMarker;
    private Marker selectedMarker;
    private Polyline activeTrack;
    private Polyline storedTrack;
    private int lastArrowBucket = Integer.MIN_VALUE;
    private int lastMapBearingBucket = Integer.MIN_VALUE;
    private boolean centeredOnFirstFix;
    private int orientationMode;
    private LatLong lastLocation;
    private float lastBearing;

    public OfflineMapController(Context context, File file) {
        MapsforgeRuntime.ensureInitialized(context);
        mapView = new MapView(context);
        mapView.setClickable(true);
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
        applyOrientationBehavior();
        MapRuntimeBridge.attach(this);
    }

    public MapView view() {
        return mapView;
    }

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

    /** DarbakMaps is a single-purpose off-road map. Legacy mode calls keep the Darbak theme. */
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
        if (orientationMode == MapUiPreferences.ORIENTATION_NORTH) {
            rotateMapTo(0f);
        }
    }

    public void updateLocation(double latitude, double longitude, float bearing) {
        lastLocation = new LatLong(latitude, longitude);
        lastBearing = normalize(bearing);

        if (orientationMode == MapUiPreferences.ORIENTATION_HEADING) {
            int bearingBucket = Math.round(lastBearing / 10f) * 10;
            if (bearingBucket != lastMapBearingBucket) {
                lastMapBearingBucket = bearingBucket;
                rotateMapTo(-bearingBucket);
            }
        }

        updateLocationMarker(lastLocation, lastBearing);

        if (!centeredOnFirstFix) {
            centeredOnFirstFix = true;
            centerOn(latitude, longitude);
        }
        mapView.getLayerManager().redrawLayers();
    }

    private void refreshOrientationFromLastFix() {
        if (orientationMode == MapUiPreferences.ORIENTATION_HEADING && lastLocation != null) {
            rotateMapTo(-lastBearing);
        } else if (orientationMode == MapUiPreferences.ORIENTATION_NORTH) {
            rotateMapTo(0f);
        }
        if (lastLocation != null) {
            updateLocationMarker(lastLocation, lastBearing);
        }
    }

    private void updateLocationMarker(LatLong position, float bearing) {
        float screenBearing = normalize(bearing + mapView.getMapRotation().degrees);
        int bucket = Math.round(screenBearing / 10f) * 10;
        if (locationMarker == null) {
            locationMarker = new Marker(position, createArrow(bucket), 0, 0);
            locationMarker.setBillboard(true);
            mapView.getLayerManager().getLayers().add(locationMarker);
            lastArrowBucket = bucket;
        } else {
            locationMarker.setLatLong(position);
            if (bucket != lastArrowBucket) {
                locationMarker.setBitmap(createArrow(bucket));
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
        if (mapView.getWidth() == 0 || mapView.getHeight() == 0) {
            mapView.post(action);
        } else {
            action.run();
        }
    }

    public void showPoint(double latitude, double longitude) {
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

    public void showStoredTrack(List<GeoPoint> points) {
        if (storedTrack != null) {
            mapView.getLayerManager().getLayers().remove(storedTrack);
        }
        org.mapsforge.core.graphics.Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setColor(AndroidGraphicFactory.INSTANCE.createColor(230, 8, 62, 45));
        paint.setStrokeWidth(8f);
        paint.setStyle(Style.STROKE);
        storedTrack = new Polyline(paint, AndroidGraphicFactory.INSTANCE);

        int step = Math.max(1, points.size() / 10_000);
        for (int index = 0; index < points.size(); index += step) {
            GeoPoint point = points.get(index);
            storedTrack.addPoint(new LatLong(point.latitude, point.longitude));
        }
        if (!points.isEmpty() && (points.size() - 1) % step != 0) {
            GeoPoint last = points.get(points.size() - 1);
            storedTrack.addPoint(new LatLong(last.latitude, last.longitude));
        }
        mapView.getLayerManager().getLayers().add(storedTrack);
        if (!points.isEmpty()) {
            GeoPoint last = points.get(points.size() - 1);
            centerOn(last.latitude, last.longitude);
        }
        mapView.getLayerManager().redrawLayers();
    }

    public void beginTrack() {
        if (activeTrack != null) {
            mapView.getLayerManager().getLayers().remove(activeTrack);
        }
        org.mapsforge.core.graphics.Paint trackPaint = AndroidGraphicFactory.INSTANCE.createPaint();
        trackPaint.setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 236, 122, 37));
        trackPaint.setStrokeWidth(7f);
        trackPaint.setStyle(Style.STROKE);
        activeTrack = new Polyline(trackPaint, AndroidGraphicFactory.INSTANCE);
        mapView.getLayerManager().getLayers().add(activeTrack);
    }

    public void addTrackPoint(double latitude, double longitude) {
        if (activeTrack != null) {
            activeTrack.addPoint(new LatLong(latitude, longitude));
            mapView.getLayerManager().redrawLayers();
        }
    }

    public void destroy() {
        MapRuntimeBridge.detach(this);
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
