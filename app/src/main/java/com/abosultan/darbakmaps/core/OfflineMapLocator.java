package com.abosultan.darbakmaps.core;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Finds Mapsforge .map packs without network access, with the approved Saudi pack first. */
public final class OfflineMapLocator {
    public static final String APPROVED_MAP_NAME = "darbak-saudi.map";

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

        Collections.sort(result, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                boolean aApproved = isApproved(a);
                boolean bApproved = isApproved(b);
                if (aApproved != bApproved) return aApproved ? -1 : 1;
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return result;
    }

    private static boolean isApproved(File file) {
        return file != null && APPROVED_MAP_NAME.equals(file.getName().toLowerCase(Locale.US));
    }

    private static void collect(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) collect(file, out);
            else if (file.getName().toLowerCase(Locale.US).endsWith(".map") && file.length() > 0L) out.add(file);
        }
    }
}
