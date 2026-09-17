package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.BacktrackNavigator;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.CoreContracts.Place;
import com.abosultan.darbakmaps.core.LiveLocationStore;
import com.abosultan.darbakmaps.core.OfflineMapLocator;
import com.abosultan.darbakmaps.core.PlaceMath;
import com.abosultan.darbakmaps.core.SessionStore;
import com.abosultan.darbakmaps.core.SqlitePlaceRepository;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder.TrackSegment;

import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Real offline map surface. Never downloads tiles or requires Play Services. */
public final class OfflineMapView extends FrameLayout {
    private static final int RESTORE_TRACK_POINTS = 4000;
    private static final int MAX_PLACE_MARKERS = 500;
    private static final long LIVE_SEGMENT_GAP_MS = 10000L;
    private static final long LIVE_DRAW_MAX_INTERVAL_MS = 15000L;
    private static final double LIVE_DRAW_MIN_DISTANCE_METERS = 10d;
    private static final float MIN_HEADING_SPEED_KMH = 4f;

    private MapView mapView;
    private TileCache tileCache;
    private MapDataStore mapDataStore;
    private TileRendererLayer renderer;
    private Polyline trackOutline;
    private Polyline activeTrack;
    private Polyline guidanceLine;
    private Marker vehicleMarker;
    private final List<Marker> placeMarkers = new ArrayList<>();
    private final Map<Long, Marker> placeMarkerById = new HashMap<>();
    private final Map<String, org.mapsforge.core.graphics.Bitmap> placeIconCache = new HashMap<>();
    private File activeMap;
    private SessionStore sessionStore;
    private SqliteTrackRecorder trackStore;
    private SqlitePlaceRepository placeStore;
    private long lastFixTime;
    private boolean followedFirstFix;
    private LatLong lastTrackPoint;
    private long lastTrackPointTime;
    private long lastLiveTrackFixTime;
    private float lastVehicleBearing = Float.NaN;
    private Place guidanceTarget;
    private boolean liveRecordingEnabled;
    private boolean gpsLostVisualsCleared;
    private BacktrackNavigator backtrackNavigator;

    private final Runnable gpsPump = new Runnable() {
        @Override public void run() {
            LocationSnapshot fix = LiveLocationStore.latest();
            if (mapView != null && fix.valid && fix.timestampMs != lastFixTime) {
                gpsLostVisualsCleared = false;
                lastFixTime = fix.timestampMs;
                LatLong position = new LatLong(fix.latitude, fix.longitude);
                if (!followedFirstFix) { centerOn(position, (byte) 15); followedFirstFix = true; }
                boolean recording = sessionStore != null && sessionStore.shouldResumeTrackRecording();
                if (recording) { if (!liveRecordingEnabled) startNewLiveTrackSegment(); appendLiveTrack(fix, position); }
                else if (liveRecordingEnabled) { lastTrackPoint = null; lastTrackPointTime = 0L; lastLiveTrackFixTime = 0L; }
                liveRecordingEnabled = recording;
                updateVehicleMarker(position, fix.bearing);
                if (backtrackNavigator != null) updateBacktrack(position); else updateGuidance(position);
            } else if (mapView != null && !fix.valid && !gpsLostVisualsCleared) {
                clearLiveGpsVisuals();
                gpsLostVisualsCleared = true;
            }
            postDelayed(this, 750L);
        }
    };

    public OfflineMapView(Context context) { super(context); sessionStore=new SessionStore(context); liveRecordingEnabled=sessionStore.shouldResumeTrackRecording(); trackStore=new SqliteTrackRecorder(context); placeStore=new SqlitePlaceRepository(context); setBackgroundColor(0xFFE8E1CF); tryOpen(context); }
    public boolean hasMap(){return activeMap!=null;} public String activeMapName(){return activeMap==null?null:activeMap.getName();}
    public void zoomIn(){if(mapView!=null){byte z=mapView.getModel().mapViewPosition.getZoomLevel();if(z<20)mapView.setZoomLevel((byte)(z+1));}}
    public void zoomOut(){if(mapView!=null){byte z=mapView.getModel().mapViewPosition.getZoomLevel();if(z>3)mapView.setZoomLevel((byte)(z-1));}}
    public boolean recenterOnGps(){LocationSnapshot f=LiveLocationStore.latest();if(mapView==null||!f.valid)return false;centerOn(new LatLong(f.latitude,f.longitude),(byte)15);followedFirstFix=true;return true;}
    public boolean focusPlace(double lat,double lon){if(mapView==null)return false;centerOn(new LatLong(lat,lon),(byte)16);followedFirstFix=true;return true;}

    public LatLong mapPoint(float x,float y){if(mapView==null)return null;try{return mapView.getMapViewProjection().fromPixels(x,y);}catch(RuntimeException e){return null;}}
    public boolean saveCurrentPlace(String category){LocationSnapshot f=LiveLocationStore.latest();if(!f.valid)return false;return savePlace(f.latitude,f.longitude,category);}
    public boolean saveMapPlace(float x,float y,String category){LatLong p=mapPoint(x,y);return p!=null&&savePlace(p.latitude,p.longitude,category);}
    private boolean savePlace(double lat,double lon,String category){if(placeStore==null||lat<-90d||lat>90d||lon<-180d||lon>180d)return false;String safe=category==null||category.length()==0?"other":category;try{long id=placeStore.save(new Place(0L,lat,lon,safe,null,null));if(id<=0L)return false;if(mapView!=null)addPlaceMarker(new Place(id,lat,lon,safe,null,null));return true;}catch(RuntimeException e){return false;}}

    boolean removeSavedPlaceMarker(long placeId){if(mapView==null||placeId<=0L)return false;Marker marker=placeMarkerById.remove(placeId);if(marker==null)return false;placeMarkers.remove(marker);boolean removed=mapView.getLayerManager().getLayers().remove(marker);if(removed){marker.onDestroy();mapView.getLayerManager().redrawLayers();}return removed;}
    boolean isBacktrackActive(){return backtrackNavigator!=null;}
    boolean startBacktrack(){if(trackStore==null||mapView==null)return false;List<TrackSegment> segments=trackStore.recentSegments(RESTORE_TRACK_POINTS);TrackSegment selected=null;for(int i=segments.size()-1;i>=0;i--){TrackSegment c=segments.get(i);if(c.points.size()>=2){selected=c;break;}}if(selected==null)return false;BacktrackNavigator n=new BacktrackNavigator(selected.points);if(!n.usable())return false;guidanceTarget=null;backtrackNavigator=n;if(guidanceLine!=null)guidanceLine.clear();LocationSnapshot f=LiveLocationStore.latest();if(f.valid){LatLong cur=new LatLong(f.latitude,f.longitude);updateBacktrack(cur);centerOn(cur,(byte)15);}return true;}
    void stopBacktrack(){backtrackNavigator=null;if(guidanceLine!=null)guidanceLine.clear();if(mapView!=null)mapView.getLayerManager().redrawLayers();}

    void showPlaceActions(final Place place){if(place==null)return;focusPlace(place.latitude,place.longitude);LocationSnapshot fix=LiveLocationStore.latest();StringBuilder m=new StringBuilder(categoryLabel(place.category));if(fix.valid){double meters=PlaceMath.distanceMeters(fix.latitude,fix.longitude,place.latitude,place.longitude);double targetBearing=bearingDegrees(fix.latitude,fix.longitude,place.latitude,place.longitude);boolean headingReliable=fix.speedKmh>=MIN_HEADING_SPEED_KMH&&validBearing(fix.bearing);m.append("\nالمسافة: ").append(formatDistance(meters));if(headingReliable)m.append("\nالاتجاه حسب السيارة: ").append(directionArrow(relativeBearing(targetBearing,fix.bearing)));else m.append("\nالاتجاه: ").append(cardinalDirection(targetBearing));}else m.append("\nGPS غير متاح لحساب المسافة والاتجاه");new AlertDialog.Builder(getContext()).setTitle("الموقع المحفوظ").setMessage(m.toString()).setPositiveButton("توجيه مباشر",(d,w)->startGuidance(place)).setNeutralButton("إلغاء التوجيه",(d,w)->stopGuidance()).setNegativeButton("إغلاق",null).show();}
    private void startGuidance(Place p){backtrackNavigator=null;guidanceTarget=p;LocationSnapshot f=LiveLocationStore.latest();if(f.valid)updateGuidance(new LatLong(f.latitude,f.longitude));focusPlace(p.latitude,p.longitude);Toast.makeText(getContext(),"تم بدء التوجيه المباشر للموقع",Toast.LENGTH_SHORT).show();}
    private void stopGuidance(){guidanceTarget=null;if(guidanceLine!=null)guidanceLine.clear();if(mapView!=null)mapView.getLayerManager().redrawLayers();Toast.makeText(getContext(),"تم إلغاء التوجيه",Toast.LENGTH_SHORT).show();}
    private void updateGuidance(LatLong current){if(guidanceLine==null||guidanceTarget==null||current==null)return;guidanceLine.clear();guidanceLine.addPoint(current);guidanceLine.addPoint(new LatLong(guidanceTarget.latitude,guidanceTarget.longitude));if(mapView!=null)mapView.getLayerManager().redrawLayers();}
    private void updateBacktrack(LatLong current){if(guidanceLine==null||backtrackNavigator==null||current==null)return;LocationSnapshot target=backtrackNavigator.update(current.latitude,current.longitude);if(target==null){stopBacktrack();return;}if(backtrackNavigator.finished(current.latitude,current.longitude)){stopBacktrack();Toast.makeText(getContext(),"وصلت إلى بداية المسار",Toast.LENGTH_SHORT).show();return;}guidanceLine.clear();guidanceLine.addPoint(current);guidanceLine.addPoint(new LatLong(target.latitude,target.longitude));if(mapView!=null)mapView.getLayerManager().redrawLayers();}
    private void centerOn(LatLong p,byte minZoom){if(mapView==null)return;mapView.setCenter(p);if(mapView.getModel().mapViewPosition.getZoomLevel()<minZoom)mapView.setZoomLevel(minZoom);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(gpsPump);post(gpsPump);}

    private void tryOpen(Context c){try{AndroidGraphicFactory.createInstance(c.getApplicationContext());List<File> maps=OfflineMapLocator.find(c);if(maps.isEmpty())return;activeMap=maps.get(0);mapView=new MapView(c);mapView.getMapScaleBar().setVisible(true);mapView.setBuiltInZoomControls(false);addView(mapView,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));tileCache=AndroidUtil.createTileCache(c,"darbak-mapcache",mapView.getModel().displayModel.getTileSize(),1f,mapView.getModel().frameBufferModel.getOverdrawFactor());mapDataStore=new MapFile(activeMap);renderer=new TileRendererLayer(tileCache,mapDataStore,mapView.getModel().mapViewPosition,AndroidGraphicFactory.INSTANCE);renderer.setXmlRenderTheme(MapsforgeThemes.MOTORIDER);mapView.getLayerManager().getLayers().add(renderer);restoreTrack(c);createTrackLayers(c);restorePlaceMarkers();SessionStore.Viewport s=sessionStore.restoreViewport();if(s!=null&&s.latitude>=-90d&&s.latitude<=90d&&s.longitude>=-180d&&s.longitude<=180d){mapView.setCenter(new LatLong(s.latitude,s.longitude));mapView.setZoomLevel((byte)Math.max(3,Math.min(20,s.zoom)));followedFirstFix=true;}else{mapView.setCenter(mapDataStore.boundingBox().getCenterPoint());mapView.setZoomLevel((byte)10);}}catch(Exception e){activeMap=null;if(mapView!=null){mapView.destroyAll();removeAllViews();mapView=null;}}}
    private Polyline newTrackLine(Context c,boolean outline){Paint paint=AndroidGraphicFactory.INSTANCE.createPaint();paint.setColor(outline?TrackStyle.ACTIVE_TRACK_OUTLINE:TrackStyle.ACTIVE_TRACK);paint.setStyle(Style.STROKE);paint.setStrokeWidth(DarbakUi.dp(c,(int)(outline?TrackStyle.ACTIVE_TRACK_OUTLINE_WIDTH_DP:TrackStyle.ACTIVE_TRACK_WIDTH_DP)));return new Polyline(paint,AndroidGraphicFactory.INSTANCE);}
    private void createTrackLayers(Context c){trackOutline=newTrackLine(c,true);activeTrack=newTrackLine(c,false);mapView.getLayerManager().getLayers().add(trackOutline);mapView.getLayerManager().getLayers().add(activeTrack);Paint gp=AndroidGraphicFactory.INSTANCE.createPaint();gp.setColor(0xFFF2B84B);gp.setStyle(Style.STROKE);gp.setStrokeWidth(DarbakUi.dp(c,4));guidanceLine=new Polyline(gp,AndroidGraphicFactory.INSTANCE);mapView.getLayerManager().getLayers().add(guidanceLine);}
    private void restoreTrack(Context c){if(trackStore==null||mapView==null)return;for(TrackSegment s:trackStore.recentSegments(RESTORE_TRACK_POINTS)){if(s.points.isEmpty())continue;Polyline o=newTrackLine(c,true),l=newTrackLine(c,false);for(LocationSnapshot p:s.points){LatLong x=new LatLong(p.latitude,p.longitude);o.addPoint(x);l.addPoint(x);}mapView.getLayerManager().getLayers().add(o);mapView.getLayerManager().getLayers().add(l);}}
    private void startNewLiveTrackSegment(){if(mapView==null)return;if(guidanceLine!=null)mapView.getLayerManager().getLayers().remove(guidanceLine);trackOutline=newTrackLine(getContext(),true);activeTrack=newTrackLine(getContext(),false);mapView.getLayerManager().getLayers().add(trackOutline);mapView.getLayerManager().getLayers().add(activeTrack);if(guidanceLine!=null)mapView.getLayerManager().getLayers().add(guidanceLine);lastTrackPoint=null;lastTrackPointTime=0L;lastLiveTrackFixTime=0L;}
    private void restorePlaceMarkers(){if(placeStore==null||mapView==null)return;for(Place p:placeStore.all(MAX_PLACE_MARKERS))addPlaceMarker(p);}
    private void addPlaceMarker(final Place place){if(mapView==null)return;Marker marker=new Marker(new LatLong(place.latitude,place.longitude),createPlaceIcon(place.category),0,0){@Override public boolean onTap(LatLong tap,Point layer,Point hit){if(contains(layer,hit,mapView)){showPlaceActions(place);return true;}return false;}};marker.setBillboard(true);mapView.getLayerManager().getLayers().add(marker);placeMarkers.add(marker);if(place.id>0L)placeMarkerById.put(place.id,marker);mapView.getLayerManager().redrawLayers();}
    private org.mapsforge.core.graphics.Bitmap createPlaceIcon(String category){String key=iconCategoryKey(category);org.mapsforge.core.graphics.Bitmap cached=placeIconCache.get(key);if(cached!=null&&!cached.isDestroyed()){cached.incrementRefCount();return cached;}int size=DarbakUi.dp(getContext(),38);Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);float center=size/2f,radius=center-DarbakUi.dp(getContext(),2);paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(categoryColor(key));canvas.drawCircle(center,center,radius,paint);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(DarbakUi.dp(getContext(),2));paint.setColor(0xFFFFFFFF);canvas.drawCircle(center,center,radius-DarbakUi.dp(getContext(),2),paint);paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(0xFFFFFFFF);drawCategorySymbol(canvas,paint,key,center,size);org.mapsforge.core.graphics.Bitmap created=new AndroidBitmap(bitmap);placeIconCache.put(key,created);return created;}
    private static String iconCategoryKey(String c){if(c==null)return"other";if(c.contains("bird")||c.contains("summan"))return"summan";if(c.contains("water"))return"water";if(c.contains("camp"))return"camp";if(c.contains("fuel"))return"fuel";return"other";}
    private void drawCategorySymbol(Canvas canvas,android.graphics.Paint paint,String value,float c,int size){float u=size/38f;if(value.contains("bird")||value.contains("summan")){canvas.drawOval(new android.graphics.RectF(c-9*u,c-3*u,c+6*u,c+7*u),paint);canvas.drawCircle(c+7*u,c-5*u,4*u,paint);Path b=new Path();b.moveTo(c+10*u,c-6*u);b.lineTo(c+15*u,c-4*u);b.lineTo(c+10*u,c-2*u);b.close();canvas.drawPath(b,paint);Path w=new Path();w.moveTo(c-4*u,c-2*u);w.quadTo(c,c-11*u,c+4*u,c-1*u);w.quadTo(c,c+2*u,c-4*u,c-2*u);w.close();canvas.drawPath(w,paint);return;}if(value.contains("water")){Path d=new Path();d.moveTo(c,c-12*u);d.cubicTo(c-2*u,c-7*u,c-8*u,c-1*u,c-8*u,c+4*u);d.cubicTo(c-8*u,c+10*u,c-4*u,c+13*u,c,c+13*u);d.cubicTo(c+4*u,c+13*u,c+8*u,c+10*u,c+8*u,c+4*u);d.cubicTo(c+8*u,c-1*u,c+2*u,c-7*u,c,c-12*u);d.close();canvas.drawPath(d,paint);return;}if(value.contains("camp")){Path t=new Path();t.moveTo(c,c-11*u);t.lineTo(c-13*u,c+11*u);t.lineTo(c+13*u,c+11*u);t.close();canvas.drawPath(t,paint);android.graphics.Paint cut=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);cut.setColor(categoryColor(value));Path door=new Path();door.moveTo(c,c-u);door.lineTo(c-4*u,c+11*u);door.lineTo(c+4*u,c+11*u);door.close();canvas.drawPath(door,cut);return;}if(value.contains("fuel")){canvas.drawRoundRect(new android.graphics.RectF(c-9*u,c-11*u,c+5*u,c+11*u),2*u,2*u,paint);return;}canvas.drawCircle(c,c-3*u,7*u,paint);Path pin=new Path();pin.moveTo(c-5*u,c+u);pin.lineTo(c,c+12*u);pin.lineTo(c+5*u,c+u);pin.close();canvas.drawPath(pin,paint);}
    private static int categoryColor(String c){if(c==null)return 0xFF6D5B3E;if(c.contains("bird")||c.contains("summan"))return 0xFFB8860B;if(c.contains("water"))return 0xFF1976D2;if(c.contains("camp"))return 0xFF2E7D32;if(c.contains("fuel"))return 0xFFC62828;return 0xFF6D5B3E;}
    private static String categoryLabel(String c){if("summan".equals(c))return"طير سمان";if("water".equals(c))return"ماء";if("camp".equals(c))return"مخيم";if("fuel".equals(c))return"وقود";return"موقع محفوظ";}
    private void appendLiveTrack(LocationSnapshot fix,LatLong p){if(activeTrack==null||trackOutline==null)return;long prev=lastLiveTrackFixTime;if(prev>0&&fix.timestampMs-prev>LIVE_SEGMENT_GAP_MS)startNewLiveTrackSegment();if(lastTrackPoint!=null){double dist=lastTrackPoint.sphericalDistance(p);long since=fix.timestampMs-lastTrackPointTime;if(since<=0L)return;double impliedKmh=dist/(since/1000d)*3.6d;if(impliedKmh>260d){startNewLiveTrackSegment();}else if(dist<LIVE_DRAW_MIN_DISTANCE_METERS&&since<LIVE_DRAW_MAX_INTERVAL_MS){lastLiveTrackFixTime=fix.timestampMs;return;}}lastLiveTrackFixTime=fix.timestampMs;trackOutline.addPoint(p);activeTrack.addPoint(p);lastTrackPoint=p;lastTrackPointTime=fix.timestampMs;if(mapView!=null)mapView.getLayerManager().redrawLayers();}
    private void updateVehicleMarker(LatLong p,float bearing){if(mapView==null)return;if(vehicleMarker==null){vehicleMarker=new Marker(p,createVehicleArrow(bearing),0,0);vehicleMarker.setBillboard(true);mapView.getLayerManager().getLayers().add(vehicleMarker);lastVehicleBearing=bearing;}else{vehicleMarker.setLatLong(p);if(Float.isNaN(lastVehicleBearing)||Math.abs(angleDelta(lastVehicleBearing,bearing))>=5f){vehicleMarker.setBitmap(createVehicleArrow(bearing));lastVehicleBearing=bearing;}}mapView.getLayerManager().redrawLayers();}
    private void clearLiveGpsVisuals(){if(mapView==null)return;boolean changed=false;if(vehicleMarker!=null){mapView.getLayerManager().getLayers().remove(vehicleMarker);vehicleMarker.onDestroy();vehicleMarker=null;lastVehicleBearing=Float.NaN;changed=true;}if(guidanceLine!=null){guidanceLine.clear();changed=true;}if(changed)mapView.getLayerManager().redrawLayers();}
    private org.mapsforge.core.graphics.Bitmap createVehicleArrow(float bearing){int size=DarbakUi.dp(getContext(),44);Bitmap base=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(base);android.graphics.Paint shadow=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);shadow.setColor(0xDD111111);android.graphics.Paint fill=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);fill.setColor(0xFFFFFFFF);android.graphics.Paint edge=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);edge.setStyle(android.graphics.Paint.Style.STROKE);edge.setStrokeWidth(DarbakUi.dp(getContext(),2));edge.setColor(0xFF111111);float center=size/2f;Path arrow=new Path();arrow.moveTo(center,DarbakUi.dp(getContext(),3));arrow.lineTo(size-DarbakUi.dp(getContext(),8),size-DarbakUi.dp(getContext(),7));arrow.lineTo(center,size-DarbakUi.dp(getContext(),14));arrow.lineTo(DarbakUi.dp(getContext(),8),size-DarbakUi.dp(getContext(),7));arrow.close();canvas.save();canvas.rotate(bearing,center,center);canvas.translate(DarbakUi.dp(getContext(),1),DarbakUi.dp(getContext(),2));canvas.drawPath(arrow,shadow);canvas.translate(-DarbakUi.dp(getContext(),1),-DarbakUi.dp(getContext(),2));canvas.drawPath(arrow,fill);canvas.drawPath(arrow,edge);canvas.restore();return new AndroidBitmap(base);}
    private static float angleDelta(float from,float to){return(to-from+540f)%360f-180f;}
    private static double bearingDegrees(double lat1,double lon1,double lat2,double lon2){double y=Math.sin(Math.toRadians(lon2-lon1))*Math.cos(Math.toRadians(lat2));double x=Math.cos(Math.toRadians(lat1))*Math.sin(Math.toRadians(lat2))-Math.sin(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.cos(Math.toRadians(lon2-lon1));return(Math.toDegrees(Math.atan2(y,x))+360d)%360d;}
    private static boolean validBearing(float bearing){return !Float.isNaN(bearing)&&bearing>=0f&&bearing<360f;}
    private static double relativeBearing(double targetBearing,float vehicleBearing){return(targetBearing-vehicleBearing+360d)%360d;}
    private static String cardinalDirection(double b){if(b<22.5||b>=337.5)return"شمال";if(b<67.5)return"شمال شرق";if(b<112.5)return"شرق";if(b<157.5)return"جنوب شرق";if(b<202.5)return"جنوب";if(b<247.5)return"جنوب غرب";if(b<292.5)return"غرب";return"شمال غرب";}
    private static String directionArrow(double b){if(b<22.5||b>=337.5)return"↑";if(b<67.5)return"↗";if(b<112.5)return"→";if(b<157.5)return"↘";if(b<202.5)return"↓";if(b<247.5)return"↙";if(b<292.5)return"←";return"↖";}
    private static String formatDistance(double m){return m<1000?Math.round(m)+" م":String.format(Locale.US,"%.1f كم",m/1000d);}
    private void saveViewport(){if(mapView==null||sessionStore==null)return;LatLong c=mapView.getModel().mapViewPosition.getCenter();if(c!=null)sessionStore.saveViewport(c.latitude,c.longitude,mapView.getModel().mapViewPosition.getZoomLevel());}
    @Override protected void onDetachedFromWindow(){removeCallbacks(gpsPump);saveViewport();if(mapView!=null){mapView.destroyAll();mapView=null;}placeMarkers.clear();placeMarkerById.clear();placeIconCache.clear();if(mapDataStore!=null){mapDataStore.close();mapDataStore=null;}if(trackStore!=null){trackStore.close();trackStore=null;}if(placeStore!=null){placeStore.close();placeStore=null;}super.onDetachedFromWindow();}
}
