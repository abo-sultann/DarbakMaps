package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class TrackStorage {
    private static final int MAX_LOADED_POINTS = 12000;

    private TrackStorage() {
    }

    public static File save(Context context, String displayName, List<GeoPoint> points) throws IOException {
        File directory = new File(context.getFilesDir(), "tracks");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create tracks directory");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String safeName = sanitize(displayName);
        File target = new File(directory, safeName + "-" + stamp + "-"+System.nanoTime()+".gpx");
        File file=new File(target.getAbsolutePath()+".pending");

        FileOutputStream output = new FileOutputStream(file);
        try {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(output, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializer.setPrefix("", "http://www.topografix.com/GPX/1/1");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "gpx");
            serializer.attribute(null, "version", "1.1");
            serializer.attribute(null, "creator", "DarbakMaps");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "trk");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "name");
            serializer.text(displayName);
            serializer.endTag("http://www.topografix.com/GPX/1/1", "name");
            serializer.startTag("http://www.topografix.com/GPX/1/1", "trkseg");
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            boolean first=true;
            for (GeoPoint point : points) {
                if(point.segmentStart&&!first){serializer.endTag("http://www.topografix.com/GPX/1/1","trkseg");serializer.startTag("http://www.topografix.com/GPX/1/1","trkseg");}
                first=false;
                serializer.startTag("http://www.topografix.com/GPX/1/1", "trkpt");
                serializer.attribute(null, "lat", Double.toString(point.latitude));
                serializer.attribute(null, "lon", Double.toString(point.longitude));
                serializer.startTag("http://www.topografix.com/GPX/1/1", "time");
                serializer.text(timeFormat.format(new Date(point.timeMillis)));
                serializer.endTag("http://www.topografix.com/GPX/1/1", "time");
                serializer.endTag("http://www.topografix.com/GPX/1/1", "trkpt");
            }
            serializer.endTag("http://www.topografix.com/GPX/1/1", "trkseg");
            serializer.endTag("http://www.topografix.com/GPX/1/1", "trk");
            serializer.endTag("http://www.topografix.com/GPX/1/1", "gpx");
            serializer.endDocument();
            serializer.flush();output.getFD().sync();
        } finally {
            output.close();
        }
        if(!file.renameTo(target))throw new IOException("تعذر تثبيت ملف المسار");
        return target;
    }

    public static File[] list(Context context) {
        File directory = new File(context.getFilesDir(), "tracks");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".gpx"));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        return files;
    }

    public static List<GeoPoint> load(File file) throws IOException {
        if(file==null||!file.isFile())throw new IOException("ملف المسار غير موجود");
        List<GeoPoint> points=new ArrayList<>();
        try(FileInputStream in=new FileInputStream(file)){
            XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(in,"UTF-8");
            double lat=0,lon=0;long time=0;boolean reading=false,segment=true,pendingSegment=false;long seen=0,step=1;
            SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            GeoPoint last=null;
            for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
                String name=parser.getName();
                if(event==XmlPullParser.START_TAG){
                    if("trkseg".equals(name))segment=true;
                    if("trkpt".equals(name)||"rtept".equals(name)){
                        reading=false;try{lat=Double.parseDouble(parser.getAttributeValue(null,"lat"));lon=Double.parseDouble(parser.getAttributeValue(null,"lon"));reading=Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180;}catch(RuntimeException ignored){}
                        time=file.lastModified();
                    }else if(reading&&"time".equals(name))try{time=fmt.parse(parser.nextText()).getTime();}catch(Exception ignored){}
                }else if(event==XmlPullParser.END_TAG&&reading&&("trkpt".equals(name)||"rtept".equals(name))){
                    pendingSegment|=segment;last=new GeoPoint(lat,lon,time,pendingSegment);segment=false;
                    if(seen%step==0){points.add(last);pendingSegment=false;}
                    seen++;reading=false;
                    if(points.size()>MAX_LOADED_POINTS){
                        List<GeoPoint> reduced=new ArrayList<>();boolean gap=false;
                        for(int i=0;i<points.size();i++){GeoPoint p=points.get(i);gap|=p.segmentStart;if(i%2==0){reduced.add(new GeoPoint(p.latitude,p.longitude,p.timeMillis,gap));gap=false;}}
                        pendingSegment|=gap;points=reduced;step*=2;
                    }
                }
            }
            if(last!=null&&(points.isEmpty()||points.get(points.size()-1)!=last))points.add(new GeoPoint(last.latitude,last.longitude,last.timeMillis,pendingSegment));
        }catch(Exception e){throw new IOException("تعذر قراءة GPX",e);}
        if(points.size()<2)throw new IOException("المسار لا يحتوي نقاطًا كافية");
        return points;
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length() && result.length() < 40; i++) {
            char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            } else if (result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                result.append('_');
            }
        }
        return result.length() == 0 ? "مسار" : result.toString();
    }
}

