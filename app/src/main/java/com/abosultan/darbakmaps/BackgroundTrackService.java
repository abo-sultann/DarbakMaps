package com.abosultan.darbakmaps;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import com.abosultan.darbakmaps.data.BackgroundTrackStore;
import com.abosultan.darbakmaps.location.LocationController;
import java.io.File;
import java.util.concurrent.*;

/** Serial durable writes on a worker. Success is reported only after GPX is committed. */
public final class BackgroundTrackService extends Service implements LocationListener {
    private static final String START="com.abosultan.darbakmaps.TRACK_START", STOP="com.abosultan.darbakmaps.TRACK_STOP";
    public static final String RESULT="com.abosultan.darbakmaps.TRACK_RESULT";
    private static final String CHANNEL="darbak_track";
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    private LocationManager manager;private Location last;private boolean listening;private long revision=-1;
    private static SharedPreferences state(Context c){return c.getSharedPreferences("track_health",MODE_PRIVATE);}
    public static String status(Context c){return state(c).getString("status","");}
    public static boolean finishing(Context c){return state(c).getBoolean("finishing",false);}
    private static void status(Context c,String text){state(c).edit().putString("status",text).apply();}
    public static void setEnabled(Context c,boolean enabled){
        if(finishing(c))return;
        MapUiPreferences.setBackgroundTrackEnabled(c,enabled);
        if(enabled)TrackSessionState.beginIfNeeded(c);else state(c).edit().putBoolean("finishing",true).commit();
        launch(c,new Intent(c,BackgroundTrackService.class).setAction(enabled?START:STOP));
    }
    public static void ensureRunning(Context c){if(MapUiPreferences.backgroundTrackEnabled(c))launch(c,new Intent(c,BackgroundTrackService.class).setAction(START));}
    private static void launch(Context c,Intent i){try{
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }catch(RuntimeException e){state(c).edit().putBoolean("finishing",false).putString("status","تعذر تشغيل خدمة التسجيل؛ افتح التطبيق وأعد المحاولة").commit();}}
    @Override public void onCreate(){super.onCreate();manager=(LocationManager)getSystemService(LOCATION_SERVICE);state(this).edit().putBoolean("finishing",false).commit();startForeground(2306,notification("بانتظار GPS"));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if((intent!=null&&STOP.equals(intent.getAction()))||!MapUiPreferences.backgroundTrackEnabled(this)){
            stopGps();state(this).edit().putBoolean("finishing",true).commit();status(this,"جارٍ حفظ المسار…");
            writer.execute(()->{
                String message;boolean success=false;
                try{File saved=BackgroundTrackStore.finalizeActive(this);TrackSessionState.reset(this);message=saved==null?"تم إيقاف التسجيل؛ لا توجد نقاط بعد":"تم حفظ المسار بالكامل";success=true;}
                catch(Exception e){message="تعذر إكمال الحفظ؛ التسجيل الأصلي محفوظ ويمكن استئنافه. "+e.getMessage();}
                state(this).edit().putBoolean("finishing",false).putString("status",message).commit();
                sendBroadcast(new Intent(RESULT).setPackage(getPackageName()).putExtra("message",message).putExtra("success",success));
                new Handler(Looper.getMainLooper()).post(()->{stopForeground(true);stopSelf(startId);});
            });return START_NOT_STICKY;
        }
        if(!listening&&manager!=null&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            try{manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,0,this);listening=true;status(this,"بانتظار GPS");}
            catch(RuntimeException e){status(this,"تعذر استقبال GPS؛ تحقق من إعدادات الموقع");}
        }
        return START_STICKY;
    }
    private void stopGps(){if(manager!=null)try{manager.removeUpdates(this);}catch(RuntimeException ignored){}listening=false;}
    @Override public void onLocationChanged(Location input){
        if(!listening||TrackSessionState.isPaused(this)||!LocationController.isUsable(input))return;
        final Location fix=new Location(input);final long currentRevision=TrackSessionState.segmentRevision(this);
        writer.execute(()->{
            boolean split=last==null||revision!=currentRevision||(fix.getTime()-last.getTime()>30000);
            if(!split){long dt=fix.getTime()-last.getTime();float d=last.distanceTo(fix);
                if(dt<=0||d<3||d>Math.max(100,dt/1000f*85))return;
            }
            try{
                BackgroundTrackStore.append(this,fix,split);
                if(split)TrackSessionState.breakSegment(this);
                TrackSessionState.onFix(this,fix);last=fix;revision=currentRevision;status(this,"التسجيل يعمل");
            }catch(Exception e){status(this,"تعذر كتابة المسار؛ افحص المساحة. النقاط السابقة محفوظة");}
        });
    }
    @Override public void onProviderDisabled(String p){status(this,"انقطعت إشارة GPS؛ بانتظار عودتها");}
    @Override public void onProviderEnabled(String p){status(this,"بانتظار إشارة GPS");}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
    @Override public void onDestroy(){stopGps();writer.shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
    private Notification notification(String text){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26&&nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"تسجيل مسار دربك",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("دربك — تسجيل المسار").setContentText(text).setContentIntent(open).setOngoing(true).build();
    }
}
