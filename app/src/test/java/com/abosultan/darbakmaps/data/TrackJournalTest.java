package com.abosultan.darbakmaps.data;
import org.junit.*;import java.io.*;import java.nio.file.*;import java.util.*;import static org.junit.Assert.*;
public class TrackJournalTest {
 @Test public void exportsBeyondOldLimitWithoutDroppingTail()throws Exception{
  File dir=Files.createTempDirectory("track").toFile(),source=new File(dir,"active.csv"),target=new File(dir,"saved.gpx");
  try(PrintWriter w=new PrintWriter(source)){for(int i=0;i<60001;i++){if(i==30000)w.println("#segment");w.println("25.0,45.0,"+(1700000000000L+i));}}
  assertEquals(60001,TrackJournal.export(source,target,"البر & العودة"));
  String text=new String(Files.readAllBytes(target.toPath()),"UTF-8");assertTrue(text.contains("&amp;"));assertEquals(60001,text.split("<trkpt ").length-1);assertEquals(2,text.split("<trkseg>").length-1);assertTrue(source.isFile());
  List<GeoPoint> preview=TrackJournal.preview(source,5000);assertTrue(preview.size()<=5001);assertEquals(1700000060000L,preview.get(preview.size()-1).timeMillis);
 }
 @Test public void failedPublishKeepsJournal()throws Exception{
  File dir=Files.createTempDirectory("track").toFile(),source=new File(dir,"active.csv"),target=new File(dir,"saved.gpx");
  TrackJournal.append(source,25,45,1700000000000L,true);target.createNewFile();
  try{TrackJournal.export(source,target,"x");fail();}catch(IOException expected){}assertTrue(source.length()>0);
 }
 @Test public void malformedTrailingRecordDoesNotEraseEarlierFixes()throws Exception{
  File f=Files.createTempFile("track",".csv").toFile();try(PrintWriter w=new PrintWriter(f)){w.println("25,45,1");w.println("#segment");w.println("25.1,45.1,2");w.println("25,");}
  List<GeoPoint> p=TrackJournal.preview(f,5000);assertEquals(2,p.size());assertTrue(p.get(1).segmentStart);
 }
}
