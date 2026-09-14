package com.abosultan.darbakmaps.location;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
public final class LocationController implements LocationListener {
    public interface Callback { void onLocation(Location location); void onProviderState(boolean enabled); }
    private final Context context; private final LocationManager manager; private final Callback callback;
    private final Handler handler=new Handler(Looper.getMainLooper()); private Location last;private boolean listening;
    private final Runnable freshness=new Runnable(){public void run(){if(listening){if(!isUsable(last)){last=null;callback.onProviderState(false);}handler.postDelayed(this,2000);}}};
    public LocationController(Context c,Callback cb){context=c.getApplicationContext();callback=cb;manager=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);}
    public static boolean isUsable(Location l){
        if(l==null||l.getElapsedRealtimeNanos()<=0)return false;
        long age=(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000;
        return FixQuality.usable(age,l.hasAccuracy(),l.getAccuracy(),l.getLatitude(),l.getLongitude());
    }
    public boolean hasPermission(){return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public void start(){
        if(listening)return;
        if(!hasPermission()||manager==null){callback.onProviderState(false);return;}
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);
            listening=true;callback.onProviderState(manager.isProviderEnabled(LocationManager.GPS_PROVIDER));
            Location cached=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(isUsable(cached))onLocationChanged(cached);
            handler.post(freshness);
        }catch(RuntimeException e){callback.onProviderState(false);}
    }
    public void stop(){handler.removeCallbacks(freshness);listening=false;if(manager!=null)try{manager.removeUpdates(this);}catch(RuntimeException ignored){} }
    public Location getLastLocation(){return isUsable(last)?new Location(last):null;}
    public void onLocationChanged(Location l){if(!isUsable(l))return;last=new Location(l);callback.onLocation(new Location(l));}
    public void onProviderEnabled(String p){callback.onProviderState(true);}
    public void onProviderDisabled(String p){last=null;callback.onProviderState(false);}
    public void onStatusChanged(String p,int s,Bundle b){if(s!=LocationProvider.AVAILABLE&&!isUsable(last))callback.onProviderState(false);}
}
