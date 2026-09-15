package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;
import java.io.File;
import java.util.List;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidBitmap;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.OfflineMapLocator;

/** Real offline map surface. Never downloads tiles or requires Play Services. */
public final class OfflineMapView extends FrameLayout {
    private MapView mapView;
    private TileCache tileCache;
    private MapDataStore mapDataStore;
    private TileRendererLayer renderer;
    private Polyline trackOutline;
    private Polyline activeTrack;
    private Marker vehicleMarker;
    private File activeMap;
    private long lastFixTime;
    private boolean followedFirstFix;
    private LatLong lastTrackPoint;
    private float lastVehicleBearing = Float.NaN;

    private final Runnable gpsPump = new Runnable() {
        @Override public void run() {
            LocationSnapshot fix = LiveLocationStore.latest();
            if (mapView != null && fix.valid && fix.timestampMs != lastFixTime) {
                lastFixTime = fix.timestampMs;
                LatLong position = new LatLong(fix.latitude, fix.longitude);
                if (!followedFirstFix) {
                    centerOn(position, (byte) 15);
                    followedFirstFix = true;
                }
                appendLiveTrack(position);
                updateVehicleMarker(position, fix.bearing);
            }
            postDelayed(this, 750L);
        }
    };

    public OfflineMapView(Context context) {
        super(context);
        setBackgroundColor(0xFFE8E1CF);
        tryOpen(context);
    }

    public boolean hasMap() { return activeMap != null; }
    public String activeMapName() { return activeMap == null ? null : activeMap.getName(); }

    public void zoomIn() {
        if (mapView == null) return;
        byte zoom = mapView.getModel().mapViewPosition.getZoomLevel();
        if (zoom < 20) mapView.setZoomLevel((byte) (zoom + 1));
    }

    public void zoomOut() {
        if (mapView == null) return;
        byte zoom = mapView.getModel().mapViewPosition.getZoomLevel();
        if (zoom > 3) mapView.setZoomLevel((byte) (zoom - 1));
    }

    public boolean recenterOnGps() {
        LocationSnapshot fix = LiveLocationStore.latest();
        if (mapView == null || !fix.valid) return false;
        centerOn(new LatLong(fix.latitude, fix.longitude), (byte) 15);
        followedFirstFix = true;
        return true;
    }

    private void centerOn(LatLong position, byte minimumZoom) {
        mapView.setCenter(position);
        if (mapView.getModel().mapViewPosition.getZoomLevel() < minimumZoom) mapView.setZoomLevel(minimumZoom);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(gpsPump);
        post(gpsPump);
    }

    private void tryOpen(Context context) {
        try {
            AndroidGraphicFactory.createInstance(context.getApplicationContext());
            List<File> maps = OfflineMapLocator.find(context);
            if (maps.isEmpty()) return;
            activeMap = maps.get(0);

            mapView = new MapView(context);
            mapView.getMapScaleBar().setVisible(true);
            mapView.setBuiltInZoomControls(false);
            addView(mapView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            tileCache = AndroidUtil.createTileCache(context, "darbak-mapcache",
                    mapView.getModel().displayModel.getTileSize(), 1f,
                    mapView.getModel().frameBufferModel.getOverdrawFactor());
            mapDataStore = new MapFile(activeMap);
            renderer = new TileRendererLayer(tileCache, mapDataStore,
                    mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
            renderer.setXmlRenderTheme(MapsforgeThemes.MOTORIDER);
            mapView.getLayerManager().getLayers().add(renderer);
            createTrackLayers(context);

            LatLong start = mapDataStore.boundingBox().getCenterPoint();
            mapView.setCenter(start);
            mapView.setZoomLevel((byte) 10);
        } catch (Exception failed) {
            activeMap = null;
            if (mapView != null) {
                mapView.destroyAll();
                removeAllViews();
                mapView = null;
            }
        }
    }

    private void createTrackLayers(Context context) {
        Paint outlinePaint = AndroidGraphicFactory.INSTANCE.createPaint();
        outlinePaint.setColor(TrackStyle.ACTIVE_TRACK_OUTLINE);
        outlinePaint.setStyle(Style.STROKE);
        outlinePaint.setStrokeWidth(DarbakUi.dp(context, (int) TrackStyle.ACTIVE_TRACK_OUTLINE_WIDTH_DP));
        trackOutline = new Polyline(outlinePaint, AndroidGraphicFactory.INSTANCE);
        mapView.getLayerManager().getLayers().add(trackOutline);

        Paint trackPaint = AndroidGraphicFactory.INSTANCE.createPaint();
        trackPaint.setColor(TrackStyle.ACTIVE_TRACK);
        trackPaint.setStyle(Style.STROKE);
        trackPaint.setStrokeWidth(DarbakUi.dp(context, (int) TrackStyle.ACTIVE_TRACK_WIDTH_DP));
        activeTrack = new Polyline(trackPaint, AndroidGraphicFactory.INSTANCE);
        mapView.getLayerManager().getLayers().add(activeTrack);
    }

    private void appendLiveTrack(LatLong position) {
        if (activeTrack == null || trackOutline == null) return;
        if (lastTrackPoint != null && lastTrackPoint.sphericalDistance(position) < 2d) return;
        trackOutline.addPoint(position);
        activeTrack.addPoint(position);
        lastTrackPoint = position;
        if (mapView != null) mapView.getLayerManager().redrawLayers();
    }

    private void updateVehicleMarker(LatLong position, float bearing) {
        if (mapView == null) return;
        if (vehicleMarker == null) {
            vehicleMarker = new Marker(position, createVehicleArrow(bearing), 0, 0);
            vehicleMarker.setBillboard(true);
            mapView.getLayerManager().getLayers().add(vehicleMarker);
            lastVehicleBearing = bearing;
        } else {
            vehicleMarker.setLatLong(position);
            if (Float.isNaN(lastVehicleBearing) || Math.abs(angleDelta(lastVehicleBearing, bearing)) >= 5f) {
                vehicleMarker.setBitmap(createVehicleArrow(bearing));
                lastVehicleBearing = bearing;
            }
        }
        mapView.getLayerManager().redrawLayers();
    }

    private org.mapsforge.core.graphics.Bitmap createVehicleArrow(float bearing) {
        int size = DarbakUi.dp(getContext(), 44);
        Bitmap base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(base);
        android.graphics.Paint shadow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        shadow.setStyle(android.graphics.Paint.Style.FILL);
        shadow.setColor(0xDD111111);
        android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(android.graphics.Paint.Style.FILL);
        fill.setColor(0xFFFFFFFF);
        android.graphics.Paint edge = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(android.graphics.Paint.Style.STROKE);
        edge.setStrokeWidth(DarbakUi.dp(getContext(), 2));
        edge.setColor(0xFF111111);

        float c = size / 2f;
        Path arrow = new Path();
        arrow.moveTo(c, DarbakUi.dp(getContext(), 3));
        arrow.lineTo(size - DarbakUi.dp(getContext(), 8), size - DarbakUi.dp(getContext(), 7));
        arrow.lineTo(c, size - DarbakUi.dp(getContext(), 14));
        arrow.lineTo(DarbakUi.dp(getContext(), 8), size - DarbakUi.dp(getContext(), 7));
        arrow.close();
        canvas.save();
        canvas.rotate(bearing, c, c);
        canvas.translate(DarbakUi.dp(getContext(), 1), DarbakUi.dp(getContext(), 2));
        canvas.drawPath(arrow, shadow);
        canvas.translate(-DarbakUi.dp(getContext(), 1), -DarbakUi.dp(getContext(), 2));
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, edge);
        canvas.restore();
        return new AndroidBitmap(base);
    }

    private static float angleDelta(float from, float to) {
        return (to - from + 540f) % 360f - 180f;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(gpsPump);
        if (mapView != null) {
            mapView.destroyAll();
            mapView = null;
        }
        if (mapDataStore != null) {
            mapDataStore.close();
            mapDataStore = null;
        }
        super.onDetachedFromWindow();
    }
}
