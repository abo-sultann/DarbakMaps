package com.abosultan.darbakmaps.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.map.android.graphics.AndroidBitmap;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;

public final class OfflineMapController {
    private final MapView mapView;
    private final TileCache tileCache;
    private final TileRendererLayer rendererLayer;
    private Marker locationMarker;
    private Polyline activeTrack;
    private int lastBearingBucket = Integer.MIN_VALUE;

    public OfflineMapController(Context context, File file) {
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

        MapFile mapFile = new MapFile(file);
        rendererLayer = AndroidUtil.createTileRendererLayer(
                tileCache,
                mapView.getModel().mapViewPosition,
                mapFile,
                MapsforgeThemes.MOTORIDER,
                false,
                true,
                false
        );
        mapView.getLayerManager().getLayers().add(rendererLayer);
        mapView.getModel().mapViewPosition.setMapPosition(
                new MapPosition(mapFile.boundingBox().getCenterPoint(), (byte) 7)
        );
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

    public void setDesertMode(boolean desert) {
        rendererLayer.setXmlRenderTheme(desert ? MapsforgeThemes.MOTORIDER : MapsforgeThemes.DEFAULT);
        tileCache.purge();
        mapView.getLayerManager().redrawLayers();
    }

    public void updateLocation(double latitude, double longitude, float bearing) {
        LatLong position = new LatLong(latitude, longitude);
        int bucket = Math.round(bearing / 10f) * 10;
        if (locationMarker == null) {
            locationMarker = new Marker(position, createArrow(bucket), 0, 0);
            mapView.getLayerManager().getLayers().add(locationMarker);
            lastBearingBucket = bucket;
        } else {
            locationMarker.setLatLong(position);
            if (bucket != lastBearingBucket) {
                locationMarker.setBitmap(createArrow(bucket));
                lastBearingBucket = bucket;
            }
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
        mapView.destroyAll();
        AndroidGraphicFactory.clearResourceMemoryCache();
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
        fill.setColor(Color.rgb(236, 122, 37));
        canvas.drawPath(arrow, fill);
        canvas.restore();
        return new AndroidBitmap(bitmap);
    }
}

