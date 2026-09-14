package com.abosultan.darbakmaps;
import android.app.Activity;
import android.content.*;
import android.location.Location;
import android.widget.*;
import com.abosultan.darbakmaps.data.*;
import java.io.File;
import java.util.*;
public final class BacktrackGuidance {
    private static List<GeoPoint> cached=Collections.emptyList();private static TrackNavigator navigator;private static long lastAlert,lastDistanceCheck;private static double nearest;
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("darbak_backtrack",0);}
    public static boolean isActive(Context c){return prefs(c).getBoolean("active",false);}
    public static void start(Activity a,File file,List<GeoPoint> points){
        if(points.size()<2)return;cached=points;navigator=new TrackNavigator(points);
        prefs(a).edit().putBoolean("active",true).putString("file",file.getAbsolutePath()).apply();
        MapRuntimeBridge.showStoredTrack(points);MapRuntimeBridge.resumeFollow();
        Toast.makeText(a,"الرجوع باتباع نقاط المسار بالتتابع",Toast.LENGTH_SHORT).show();
    }
    public static void restore(Activity a){
        if(!isActive(a))return;
        if(!cached.isEmpty()){MapRuntimeBridge.showStoredTrack(cached);MapRuntimeBridge.resumeFollow();return;}
        final String file=prefs(a).getString("file","");
        new Thread(()->{
            try{List<GeoPoint> points=TrackStorage.load(new File(file));a.runOnUiThread(()->{
                if(a.isFinishing()||a.isDestroyed()||!isActive(a)||!file.equals(prefs(a).getString("file","")))return;
                cached=points;navigator=new TrackNavigator(points);MapRuntimeBridge.showStoredTrack(points);MapRuntimeBridge.resumeFollow();
            });}catch(Exception e){a.runOnUiThread(()->{stop(a);Toast.makeText(a,"تعذر استعادة مسار الرجوع",Toast.LENGTH_LONG).show();});}
        },"darbak-backtrack-restore").start();
    }
    public static void stop(Context c){prefs(c).edit().clear().apply();cached=Collections.emptyList();navigator=null;lastAlert=0;lastDistanceCheck=0;}
    public static void update(Activity a,Location fix){
        if(!isActive(a)||navigator==null||fix==null)return;
        GeoPoint target=navigator.update(fix.getLatitude(),fix.getLongitude());if(target==null)return;
        NavigationGuidance.guideTo(a,fix,"رجوع على المسار • نقطة "+(navigator.targetIndex()+1),target.latitude,target.longitude,false);
        long now=System.currentTimeMillis();
        if(now-lastDistanceCheck>=5000){nearest=navigator.offTrack(fix.getLatitude(),fix.getLongitude());lastDistanceCheck=now;}
        TextView detail=a.findViewById(R.id.nav_detail);
        if(detail!=null){
            if(navigator.crossesGap())detail.append(" • فاصل تسجيل؛ اتبع المسار الظاهر بحذر");
            else if(nearest>120)detail.append(" • خارج المسار "+Math.round(nearest)+"م");
        }
        if(MapUiPreferences.offRouteAlert(a)&&nearest>180&&now-lastAlert>30000){lastAlert=now;Toast.makeText(a,"ابتعدت عن المسار المسجل "+Math.round(nearest)+" م",Toast.LENGTH_SHORT).show();}
    }
}
