package com.abosultan.darbakmaps.data;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Append-only source of truth. Display limits never limit exported data. */
public final class TrackJournal {
    private TrackJournal() {}
    public static void append(File file, double lat, double lon, long time, boolean newSegment) throws IOException {
        File parent=file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("تعذر إنشاء مجلد المسارات");
        try (FileOutputStream out=new FileOutputStream(file,true)) {
            String row=(newSegment ? "#segment\n" : "")+lat+","+lon+","+time+"\n";
            out.write(row.getBytes("UTF-8"));
            out.getFD().sync();
        }
    }
    public static GeoPoint parse(String line, boolean segmentStart) {
        try {
            String[] v=line.split(","); if(v.length!=3)return null;
            double lat=Double.parseDouble(v[0]),lon=Double.parseDouble(v[1]);
            long t=Long.parseLong(v[2]);
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat < -90||lat>90||lon< -180||lon>180||t<0)return null;
            return new GeoPoint(lat,lon,t,segmentStart);
        } catch(RuntimeException e){return null;}
    }
    public static List<GeoPoint> preview(File file, int limit) throws IOException {
        List<GeoPoint> points=new ArrayList<>();
        if(!file.isFile())return points;
        long count=0; try(BufferedReader in=new BufferedReader(new FileReader(file))){String s;while((s=in.readLine())!=null)if(parse(s,false)!=null)count++;}
        long step=Math.max(1,(count+Math.max(2,limit)-1)/Math.max(2,limit));
        try(BufferedReader in=new BufferedReader(new FileReader(file))){
            String s;long index=0;boolean segment=true;GeoPoint last=null;
            while((s=in.readLine())!=null){
                if(s.startsWith("#segment")){segment=true;continue;}
                GeoPoint p=parse(s,segment);if(p==null)continue;
                if(index%step==0 || index==count-1){points.add(new GeoPoint(p.latitude,p.longitude,p.timeMillis,segment));segment=false;}
                index++;last=p;
            }
        }
        return points;
    }
    public static long export(File source, File target, String name) throws IOException {
        File pending=new File(target.getAbsolutePath()+".pending");long count=0;
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        try(FileOutputStream bytes=new FileOutputStream(pending);
            Writer out=new BufferedWriter(new OutputStreamWriter(bytes,"UTF-8"));
            BufferedReader in=new BufferedReader(new FileReader(source))) {
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"DarbakMaps\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>"+escape(name)+"</name><trkseg>");
            String line;boolean segment=false;
            while((line=in.readLine())!=null){
                if(line.startsWith("#segment")){segment=count>0;continue;}
                GeoPoint p=parse(line,false);if(p==null)continue;
                if(segment){out.write("</trkseg><trkseg>");segment=false;}
                out.write("<trkpt lat=\""+p.latitude+"\" lon=\""+p.longitude+"\"><time>"+fmt.format(new Date(p.timeMillis))+"</time></trkpt>");count++;
            }
            out.write("</trkseg></trk></gpx>");out.flush();bytes.getFD().sync();
        } catch(IOException e){pending.delete();throw e;}
        if(count==0){pending.delete();throw new IOException("لا توجد نقاط صالحة لحفظ المسار؛ تم الاحتفاظ بالملف الأصلي");}
        if(target.exists()||!pending.renameTo(target)){pending.delete();throw new IOException("تعذر حفظ المسار؛ تم الاحتفاظ بالتسجيل الأصلي");}
        return count;
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
