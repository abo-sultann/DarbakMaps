package com.abosultan.darbakmaps.data;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class TrackNavigatorTest {
 @Test public void reverseGuidanceTargetsBendBeforeStart(){
  List<GeoPoint> p=Arrays.asList(new GeoPoint(25,45,1),new GeoPoint(25,45.01,2),new GeoPoint(25.01,45.01,3));
  TrackNavigator n=new TrackNavigator(p);assertSame(p.get(1),n.update(25.01,45.01));assertSame(p.get(0),n.update(25,45.01));
 }
 @Test public void onSegmentIsNotOffTrack(){assertTrue(TrackNavigator.segmentDistance(25,45.005,new GeoPoint(25,45,1),new GeoPoint(25,45.01,2))<1);}
 @Test public void recordingGapIsNotTreatedAsRoad(){List<GeoPoint> p=Arrays.asList(new GeoPoint(25,45,1),new GeoPoint(25,45.1,2,true));assertTrue(new TrackNavigator(p).offTrack(25,45.05)>4000);}
 @Test public void reverseGuidanceStopsAtRecordingGap(){
  List<GeoPoint> p=Arrays.asList(new GeoPoint(25,45,1),new GeoPoint(25,45.01,2),
      new GeoPoint(25,45.02,3,true),new GeoPoint(25,45.03,4));
  TrackNavigator n=new TrackNavigator(p);
  assertSame(p.get(2),n.update(25,45.03));
  assertNull(n.update(25,45.02));assertTrue(n.crossesGap());
 }
}
