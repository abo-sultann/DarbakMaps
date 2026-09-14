package com.abosultan.darbakmaps.data;
import android.content.Context;
import android.location.Location;
import java.io.*;
import java.util.*;
public final class BackgroundTrackStore {
    private BackgroundTrackStore() {}
    public static synchronized void append(Context c, Location fix) throws IOException { append(c,fix,false); }
    public static synchronized void append(Context c, Location fix, boolean newSegment) throws IOException {
        TrackJournal.append(activeFile(c),fix.getLatitude(),fix.getLongitude(),fix.getTime(),newSegment);
    }
    public static synchronized List<GeoPoint> loadActive(Context c) {
        try {return TrackJournal.preview(activeFile(c),5000);}
        catch(IOException e){return Collections.emptyList();}
    }
    public static synchronized File finalizeActive(Context c) throws IOException {
        File source=activeFile(c);if(!source.isFile()||source.length()==0)return null;
        File saved=new File(source.getParentFile(),"مسار-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+".gpx");
        TrackJournal.export(source,saved,"مسار دربك");
        if(!source.delete())throw new IOException("تم حفظ GPX لكن تعذر إغلاق الملف النشط؛ أعد المحاولة");
        return saved;
    }
    public static File activeFile(Context c){return new File(new File(c.getFilesDir(),"tracks"),"active-track.csv");}
}
