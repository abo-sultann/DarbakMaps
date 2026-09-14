package com.abosultan.darbakmaps.data;
import java.util.List;
/** Reverse breadcrumb cursor. Match segments, then follow preceding vertices, never shortcut to start. */
public final class TrackNavigator {
    private final List<GeoPoint> points;private int cursor=-1;private boolean gap;
    public TrackNavigator(List<GeoPoint> points){this.points=points;}
    public int targetIndex(){return cursor;}
    public GeoPoint update(double lat,double lon){
        if(points.size()<2||gap)return null;
        if(cursor<0){double best=Double.MAX_VALUE;int index=-1;
            for(int i=0;i<points.size()-1;i++){
                if(points.get(i+1).segmentStart)continue;
                double distance=segmentDistance(lat,lon,points.get(i),points.get(i+1));
                if(distance<=best){best=distance;index=i;}
            }if(index<0){gap=true;return null;}cursor=index;
        }
        while(cursor>0&&distance(lat,lon,points.get(cursor).latitude,points.get(cursor).longitude)<30){
            if(points.get(cursor).segmentStart){gap=true;return null;}
            cursor--;
        }
        return points.get(cursor);
    }
    public double offTrack(double lat,double lon){
        double best=Double.MAX_VALUE;
        for(int i=0;i<points.size();i++){
            GeoPoint p=points.get(i);best=Math.min(best,distance(lat,lon,p.latitude,p.longitude));
            if(i>0&&!p.segmentStart)best=Math.min(best,segmentDistance(lat,lon,points.get(i-1),p));
        }return best;
    }
    public boolean crossesGap(){return gap;}
    public static double segmentDistance(double lat,double lon,GeoPoint a,GeoPoint b){
        double scale=Math.cos(Math.toRadians(lat));
        double ax=(a.longitude-lon)*111320*scale,ay=(a.latitude-lat)*111320;
        double bx=(b.longitude-lon)*111320*scale,by=(b.latitude-lat)*111320;
        double dx=bx-ax,dy=by-ay;double len=dx*dx+dy*dy;
        double t=len==0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/len));
        return Math.hypot(ax+t*dx,ay+t*dy);
    }
    public static double distance(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);return 6371000*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));}
}
