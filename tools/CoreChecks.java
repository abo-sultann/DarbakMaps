import com.abosultan.darbakmaps.data.*;
import com.abosultan.darbakmaps.location.FixQuality;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class CoreChecks {
 public static void main(String[] args)throws Exception{
  File dir=Files.createTempDirectory("darbak-core").toFile();
  File source=new File(dir,"active.csv"),target=new File(dir,"saved.gpx");
  try(PrintWriter w=new PrintWriter(source)){for(int i=0;i<60001;i++){if(i==30000)w.println("#segment");w.println("25,45,"+(1700000000000L+i));}}
  assert TrackJournal.export(source,target,"البر & العودة")==60001;
  String xml=new String(Files.readAllBytes(target.toPath()),"UTF-8");
  assert xml.split("<trkpt ",-1).length-1==60001;
  assert xml.split("<trkseg>",-1).length-1==2;
  assert xml.contains("&amp;");
  assert source.exists();
  List<GeoPoint> preview=TrackJournal.preview(source,5000);
  assert preview.size()<=5001;
  assert preview.get(preview.size()-1).timeMillis==1700000060000L;
  try{TrackJournal.export(source,target,"x");throw new AssertionError("Overwrite should fail");}catch(IOException expected){}
  assert source.length()>0;
  assert TrackJournal.parse("25,",false)==null;
  assert TrackJournal.parse("NaN,45,1",false)==null;
  assert !FixQuality.usable(16000,true,5,25,45);
  assert !FixQuality.usable(1000,true,100,25,45);
  assert !FixQuality.usable(0,false,0,25,45);
  assert !FixQuality.usable(-1,true,5,25,45);
  assert FixQuality.usable(1000,true,8,25,45);
  List<GeoPoint> route=Arrays.asList(new GeoPoint(25,45,1),new GeoPoint(25,45.01,2),new GeoPoint(25.01,45.01,3));
  TrackNavigator navigator=new TrackNavigator(route);
  assert navigator.update(25.01,45.01)==route.get(1);
  assert navigator.update(25,45.01)==route.get(0);
  assert TrackNavigator.segmentDistance(25,45.005,route.get(0),route.get(1))<1;
  assert new TrackNavigator(Arrays.asList(new GeoPoint(25,45,1),new GeoPoint(25,45.1,2,true))).offTrack(25,45.05)>4000;
  System.out.println("PASS: complete 60001-point export, segment preservation, bounded preview + final point, failed-write recovery, malformed rows, GPS quality, reverse turn order and segment distance.");
 }
}