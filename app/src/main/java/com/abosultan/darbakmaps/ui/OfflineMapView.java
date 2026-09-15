package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.widget.FrameLayout;
import java.io.File;
import java.util.List;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
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
    private File activeMap;
    private long lastFixTime;
    private boolean followedFirstFix;
    private LatLong lastTrackPoint;

    private final Runnable gpsPump = new Runnable() {
        @Override public void run() {
            LocationSnapshot fix = LiveLocationStore.latest();
            if (mapView != null && fix.valid && fix.timestampMs != lastFixTime) {
                lastFixTime = fix.timestampMs;
                LatLong position = new LatLong(fix.latitude, fix.longitude);
                if (!followedFirstFix) {
                    mapView.setCenter(position);
                    if (mapView.getModel().mapViewPosition.getZoomLevel() < 15) mapView.setZoomLevel((byte) 15);
                    followedFirstFix = true;
                }
                appendLiveTrack(position);
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
