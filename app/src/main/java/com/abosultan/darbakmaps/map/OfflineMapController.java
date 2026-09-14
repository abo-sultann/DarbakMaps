package com.abosultan.darbakmaps.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

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
    private final MapView mapView;
    private final TileCache tileCache;
    private final TileRendererLayer rendererLayer;
    private final XmlRenderTheme darbakTheme;
    private final List<Marker> savedMarkers = new ArrayList<>();
    private Marker locationMarker;
    private Marker selectedMarker;
    private Marker navigationMarker;
    private Polyline navigationLine;
    private Polyline activeTrack;
    private Polyline storedTrack;
    private int lastArrowBucket = Integer.MIN_VALUE;
    private int lastMapBearingBucket = Integer.MIN_VALUE;
    private boolean centeredOnFirstFix;
    private boolean followSuspended;
    private final List<GeoPoint> livePoints=new ArrayList<>();
    private final List<Polyline> liveLines=new ArrayList<>(), savedTrackLines=new ArrayList<>();
    private long lastTrackTime;private long lastSegmentRevision=-1;
    private int orientationMode;
    private LatLong lastLocation;
    private LatLong navigationTarget;
    private float lastBearing;
    private int navigationMode = MapUiPreferences.ROUTING_DIRECT;

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
        lastBearing = normalize(bearing);

        if (orientationMode == MapUiPreferences.ORIENTATION_HEADING) {
            int bearingBucket = Math.round(lastBearing / 10f) * 10;
            if (bearingBucket != lastMapBearingBucket) {
                lastMapBearingBucket = bearingBucket;
                rotateMapTo(-bearingBucket);
            }
        }

        updateLocationMarker(lastLocation, lastBearing);
        updateNavigationLine();

        if (!followSuspended && MapUiPreferences.followVehicle(mapView.getContext()) && centeredOnFirstFix) {
            mapView.getModel().mapViewPosition.setCenter(lastLocation);
        }
        if (!centeredOnFirstFix && !followSuspended) {
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
        if (lastLocation != null) updateLocationMarker(lastLocation, lastBearing);
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
        if (mapView.getWidth() == 0 || mapView.getHeight() == 0) mapView.post(action); else action.run();
    }

    public void suspendFollow(){followSuspended=true;}
    public void resumeFollow(){followSuspended=false;}
    public void markLocationStale(){
        if(locationMarker!=null){mapView.getLayerManager().getLayers().remove(locationMarker);locationMarker.onDestroy();locationMarker=null;}
        lastLocation=null;
        if(navigationLine!=null){mapView.getLayerManager().getLayers().remove(navigationLine);navigationLine=null;}
        mapView.getLayerManager().redrawLayers();
    }
    public void showPoint(double latitude, double longitude) {
        suspendFollow();
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
        for (Marker marker : savedMarkers) {mapView.getLayerManager().getLayers().remove(marker);marker.onDestroy();}
        savedMarkers.clear();
        if (places != null) {
            int limit = places.size();
            for (int i = 0; i < limit; i++) {
                PlaceRepository.Place place = places.get(i);
                Marker marker = new Marker(new LatLong(place.latitude, place.longitude),
                        createSavedMarker(place, showLabels && i < 100), 0, -20);
                marker.setBillboard(true);
                savedMarkers.add(marker);
                mapView.getLayerManager().getLayers().add(marker);
            }
        }
        mapView.getLayerManager().redrawLayers();
    }

    public void setNavigationTarget(double latitude,double longitude,int mode){updateNavigationTarget(latitude,longitude,mode);}
    public void updateNavigationTarget(double latitude, double longitude, int mode) {
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

    private Polyline trackLine(int color) {
        org.mapsforge.core.graphics.Paint p=AndroidGraphicFactory.INSTANCE.createPaint();
        p.setColor(color);p.setStrokeWidth(7f);p.setStyle(Style.STROKE);
        return new Polyline(p,AndroidGraphicFactory.INSTANCE);
    }
    private void removeLines(List<Polyline> lines){for(Polyline l:lines)mapView.getLayerManager().getLayers().remove(l);lines.clear();}
    private void drawPoints(List<GeoPoint> points,List<Polyline> lines,int color){
        removeLines(lines);Polyline line=null;
        for(GeoPoint p:points){
            if(line==null||p.segmentStart){line=trackLine(color);lines.add(line);mapView.getLayerManager().getLayers().add(line);}
            line.addPoint(new LatLong(p.latitude,p.longitude));
        }
    }
    public void showStoredTrack(List<GeoPoint> points){
        drawPoints(points,savedTrackLines,AndroidGraphicFactory.INSTANCE.createColor(240,57,169,255));
        suspendFollow();
        if(!points.isEmpty()){GeoPoint last=points.get(points.size()-1);centerOn(last.latitude,last.longitude);}
        mapView.getLayerManager().redrawLayers();
    }
    public void clearStoredTrack(){removeLines(savedTrackLines);mapView.getLayerManager().redrawLayers();}
    public void beginTrack(){livePoints.clear();removeLines(liveLines);activeTrack=null;lastTrackTime=0;lastSegmentRevision=-1;}
    public void restoreRecordedTrack(List<GeoPoint> points){
        // Merge fixes received while the file preview was being loaded.
        List<GeoPoint> newer=new ArrayList<>();long end=points.isEmpty()?0:points.get(points.size()-1).timeMillis;
        for(GeoPoint p:livePoints)if(p.timeMillis>end)newer.add(p);
        livePoints.clear();livePoints.addAll(points);livePoints.addAll(newer);compactLive();
        drawPoints(livePoints,liveLines,AndroidGraphicFactory.INSTANCE.createColor(255,236,122,37));
        activeTrack=liveLines.isEmpty()?null:liveLines.get(liveLines.size()-1);
        if(!livePoints.isEmpty())lastTrackTime=livePoints.get(livePoints.size()-1).timeMillis;
        mapView.getLayerManager().redrawLayers();
    }
    private void compactLive(){
        if(livePoints.size()<=6000)return;
        List<GeoPoint> compact=new ArrayList<>();boolean segment=false;
        for(int i=0;i<livePoints.size();i++){
            GeoPoint p=livePoints.get(i);segment|=p.segmentStart;
            if(i%2==0||i==livePoints.size()-1){compact.add(new GeoPoint(p.latitude,p.longitude,p.timeMillis,segment));segment=false;}
        }
        livePoints.clear();livePoints.addAll(compact);
    }
    public void addTrackPoint(double latitude,double longitude){
        long now=System.currentTimeMillis(),revision=com.abosultan.darbakmaps.TrackSessionState.segmentRevision(mapView.getContext());
        boolean split=lastTrackTime==0||now-lastTrackTime>30000||revision!=lastSegmentRevision;
        lastSegmentRevision=revision;lastTrackTime=now;
        GeoPoint point=new GeoPoint(latitude,longitude,now,split);livePoints.add(point);
        if(livePoints.size()>6000){compactLive();drawPoints(livePoints,liveLines,AndroidGraphicFactory.INSTANCE.createColor(255,236,122,37));activeTrack=liveLines.get(liveLines.size()-1);}
        else{
            if(activeTrack==null||split){activeTrack=trackLine(AndroidGraphicFactory.INSTANCE.createColor(255,236,122,37));liveLines.add(activeTrack);mapView.getLayerManager().getLayers().add(activeTrack);}
            activeTrack.addPoint(new LatLong(latitude,longitude));
        }
        mapView.getLayerManager().redrawLayers();
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

