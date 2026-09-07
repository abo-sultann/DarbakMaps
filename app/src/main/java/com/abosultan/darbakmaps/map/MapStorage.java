package com.abosultan.darbakmaps.map;

import android.content.Context;
import android.net.Uri;

import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class MapStorage {
    private static final String ACTIVE_MAP = "saudi-active.map";

    private MapStorage() {
    }

    public static File activeMap(Context context) {
        return new File(mapDirectory(context), ACTIVE_MAP);
    }

    static File mapDirectory(Context context) {
        File external = context.getExternalFilesDir("maps");
        return external != null ? external : new File(context.getFilesDir(), "maps");
    }

    public static File importMap(Context context, Uri source) throws IOException {
        File target = activeMap(context);
        File directory = mapDirectory(context);
        if (directory == null || (!directory.exists() && !directory.mkdirs())) {
            throw new IOException("Unable to create map directory");
        }

        File pending = new File(directory, ACTIVE_MAP + ".pending");
        InputStream input = context.getContentResolver().openInputStream(source);
        if (input == null) {
            throw new IOException("Unable to open selected map");
        }

        FileOutputStream output = new FileOutputStream(pending);
        try {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } finally {
            output.close();
            input.close();
        }

        MapFile validator = null;
        try {
            validator = new MapFile(pending);
            if (validator.boundingBox() == null) {
                throw new IOException("Invalid map bounds");
            }
        } catch (RuntimeException error) {
            pending.delete();
            throw new IOException("الملف ليس خريطة Mapsforge صالحة", error);
        } finally {
            if (validator != null) {
                validator.close();
            }
        }

        if (target.exists() && !target.delete()) {
            pending.delete();
            throw new IOException("Unable to replace old map");
        }
        if (!pending.renameTo(target)) {
            pending.delete();
            throw new IOException("Unable to activate imported map");
        }
        return target;
    }
}
