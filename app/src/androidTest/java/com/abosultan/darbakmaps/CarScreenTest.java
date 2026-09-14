package com.abosultan.darbakmaps;
import android.app.*;import android.content.*;import android.graphics.Bitmap;import android.view.*;import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import com.abosultan.darbakmaps.data.*;
import org.junit.*;import org.junit.runner.RunWith;import java.io.*;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class CarScreenTest {
 private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
 @Before public void setup(){context().getSharedPreferences("darbak_places",0).edit().clear().commit();MapUiPreferences.setBackgroundTrackEnabled(context(),false);TrackSessionState.reset(context());}
 @Test public void savesMoreThanFiveHundredPlacesWithoutDeletingOldest(){PlaceRepository repo=new PlaceRepository(context());String first=repo.add("المخيم الأول",25,45).id;for(int i=0;i<500;i++)repo.add("موقع "+i,25,45);assertEquals(501,repo.all().size());assertEquals(first,repo.all().get(500).id);}
 @Test public void malformedStoredPlacesAreNotOverwritten(){context().getSharedPreferences("darbak_places",0).edit().putString("places","bad json").commit();try{new PlaceRepository(context()).add("x",25,45);fail();}catch(IllegalStateException expected){}assertEquals("bad json",context().getSharedPreferences("darbak_places",0).getString("places",""));}
 @Test public void finalizeRetainsAllPointsAndSegments()throws Exception{
  File active=BackgroundTrackStore.activeFile(context());active.getParentFile().mkdirs();
  try(PrintWriter w=new PrintWriter(active)){for(int i=0;i<50003;i++){if(i==25000)w.println("#segment");w.println("25,45,"+(1700000000000L+i*1000L));}}
  File saved=BackgroundTrackStore.finalizeActive(context());assertFalse(active.exists());int count=0;try(BufferedReader in=new BufferedReader(new FileReader(saved))){String line;while((line=in.readLine())!=null)count+=line.split("<trkpt ",-1).length-1;}assertEquals(50003,count);
  java.util.List<GeoPoint> points=TrackStorage.load(saved);assertTrue(points.size()<=12002);assertEquals(1700050002000L,points.get(points.size()-1).timeMillis);
 }
 @Test public void carScreenFlowsAndScreenshots()throws Exception{
  new PlaceRepository(context()).addDetailed("مخيم الاختبار",25,45,"camp","مخيم","نقطة التحقق");
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{assertNotNull(a.findViewById(R.id.map_container));assertFalse(DarbakPlatformRuntime.healthReport().contains("غير مهيأ"));assertEquals("—",((TextView)a.findViewById(R.id.speed_value)).getText().toString());});
   Thread.sleep(1500);screenshot("01-home");
   scenario.onActivity(a->a.findViewById(R.id.action_saved).performClick());Thread.sleep(200);screenshot("02-saved-hub");
   InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
   scenario.onActivity(a->DarbakPanels.showSettings(a));Thread.sleep(200);screenshot("03-settings");
   InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
   scenario.onActivity(a->PointEditor.show(a,25,45,null));Thread.sleep(200);screenshot("04-save-point");
  }
 }
 private void screenshot(String name)throws Exception{File dir=new File(context().getExternalFilesDir(null),"qa");dir.mkdirs();Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
