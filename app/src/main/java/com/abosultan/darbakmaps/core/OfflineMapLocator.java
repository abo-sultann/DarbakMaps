package com.abosultan.darbakmaps.core;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds Mapsforge .map packs without network access, preferring removable storage. */
public final class OfflineMapLocator {
    private OfflineMapLocator() {}

    public static List<File> find(Context context) {
        ArrayList<File> result = new ArrayList<>();
        File[] roots = context.getExternalFilesDirs(null);
        if (roots != null) {
            for (File root : roots) {
                if (root == null) continue;
                File appMaps = new File(root, "maps");
                collect(appMaps, result);
                File parent = root;
                for (int i = 0; i < 4 && parent != null; i++) parent = parent.getParentFile();
                if (parent != null) {
                    collect(new File(parent, "DarbakMaps"), result);
                    collect(new File(parent, "Maps"), result);
                }
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    private static void collect(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) collect(file, out);
            else if (file.getName().toLowerCase().endsWith(".map") && file.length() > 0L) out.add(file);
        }
    }
}
