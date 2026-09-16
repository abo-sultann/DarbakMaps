package com.abosultan.darbakmaps.core;

import android.content.Context;
import android.net.Uri;
import org.mapsforge.map.reader.MapFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Imports a Mapsforge pack without risking the currently working map. */
public final class MapImportManager {
    public static final String ACTIVE_NAME = OfflineMapLocator.APPROVED_MAP_NAME;
    private static final long MAX_BYTES = 4L * 1024L * 1024L * 1024L;
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
        File dst = destination(c);
        File dir = dst.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
        File tmp = new File(dir, ACTIVE_NAME + ".part");
        File bak = new File(dir, ACTIVE_NAME + ".bak");
        if (tmp.exists() && !tmp.delete()) return false;

        long total = 0L;
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp, false)) {
            if (in == null) return false;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) throw new IllegalArgumentException("map too large");
                out.write(buf, 0, n);
            }
            out.flush();
            out.getFD().sync();
        } catch (Exception e) {
            tmp.delete();
            return false;
        }

        if (total < 1024L || !valid(tmp)) { tmp.delete(); return false; }
        if (bak.exists() && !bak.delete()) { tmp.delete(); return false; }

        boolean hadOld = dst.isFile();
        if (hadOld && !dst.renameTo(bak)) { tmp.delete(); return false; }
        if (!tmp.renameTo(dst)) {
            if (hadOld) bak.renameTo(dst);
            tmp.delete();
            return false;
        }
        if (!valid(dst)) {
            dst.delete();
            if (hadOld) bak.renameTo(dst);
            return false;
        }
        if (hadOld) bak.delete();
        return true;
    }

    private static boolean valid(File f) {
        MapFile map = null;
        try { map = new MapFile(f); return map.boundingBox() != null; }
        catch (Exception e) { return false; }
        finally { if (map != null) try { map.close(); } catch (Exception ignored) {} }
    }
}
