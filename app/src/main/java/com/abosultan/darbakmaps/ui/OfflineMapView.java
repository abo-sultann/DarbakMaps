package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.widget.FrameLayout;
import java.io.File;
import java.util.List;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;
import com.abosultan.darbakmaps.core.OfflineMapLocator;

/** Real offline map surface. Never downloads tiles or requires Play Services. */
public final class OfflineMapView extends FrameLayout {
    private MapView mapView;
    private TileCache tileCache;
    private MapDataStore mapDataStore;
    private TileRendererLayer renderer;
    private File activeMap;

    public OfflineMapView(Context context) {
        super(context);
        setBackgroundColor(0xFFE8E1CF);
        tryOpen(context);
    }

    public boolean hasMap() { return activeMap != null; }
    public String activeMapName() { return activeMap == null ? null : activeMap.getName(); }

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

    @Override protected void onDetachedFromWindow() {
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
