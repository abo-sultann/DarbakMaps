package com.abosultan.darbakmaps.core;

import android.content.Context;
import android.net.Uri;
import org.mapsforge.map.reader.MapFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Safely imports a Mapsforge pack into app-owned storage. */
public final class MapImportManager {
    public static final String ACTIVE_NAME = OfflineMapLocator.APPROVED_MAP_NAME;
    private MapImportManager() {}

    public static File destination(Context c) {
        File root = c.getExternalFilesDir(null);
        if (root == null) root = c.getFilesDir();
        File maps = new File(root, "maps");
        if (!maps.exists()) maps.mkdirs();
        return new File(maps, ACTIVE_NAME);
    }

    public static boolean importUri(Context c, Uri uri) {
        if (c == null || uri == null) return false;
        File dst = destination(c), tmp = new File(dst.getParentFile(), ACTIVE_NAME + ".part");
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            FileOutputStream out = new FileOutputStream(tmp, false);
            byte[] buf = new byte[64 * 1024]; int n; long total = 0;
            while ((n = in.read(buf)) > 0) { out.write(buf,0,n); total += n; if (total > 4L*1024*1024*1024) throw new IllegalArgumentException("map too large"); }
            out.flush(); out.getFD().sync(); out.close(); in.close();
            if (total < 1024L || !valid(tmp)) { tmp.delete(); return false; }
            if (dst.exists() && !dst.delete()) { tmp.delete(); return false; }
            if (!tmp.renameTo(dst)) { tmp.delete(); return false; }
            return true;
        } catch (Exception e) { tmp.delete(); return false; }
    }

    private static boolean valid(File f) {
        MapFile map = null;
        try { map = new MapFile(f); return map.boundingBox() != null; }
        catch (Exception e) { return false; }
        finally { if (map != null) try { map.close(); } catch (Exception ignored) {} }
    }
}
