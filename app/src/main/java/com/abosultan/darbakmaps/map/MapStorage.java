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
    private static final String BACKUP_MAP = ACTIVE_MAP + ".backup";

    private MapStorage() {
    }

    public static File activeMap(Context context) {
        File directory = mapDirectory(context);
        File target = new File(directory, ACTIVE_MAP);
        File backup = new File(directory, BACKUP_MAP);
        if (!target.isFile() && backup.isFile()) {
            backup.renameTo(target);
        }
        return target;
    }

    static File mapDirectory(Context context) {
        File external = context.getExternalFilesDir("maps");
        return external != null ? external : new File(context.getFilesDir(), "maps");
    }

    public static File importMap(Context context, Uri source) throws IOException {
        File target = activeMap(context);
        File directory = mapDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("تعذر إنشاء مجلد الخرائط");
        }

        File pending = new File(directory, ACTIVE_MAP + ".pending");
        File backup = new File(directory, BACKUP_MAP);
        pending.delete();
        backup.delete();

        InputStream input = context.getContentResolver().openInputStream(source);
        if (input == null) {
            throw new IOException("تعذر فتح ملف الخريطة المحدد");
        }

        try (InputStream openedInput = input; FileOutputStream output = new FileOutputStream(pending)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = openedInput.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } catch (IOException error) {
            pending.delete();
            throw error;
        }

        MapFile validator = null;
        try {
            validator = new MapFile(pending, "ar");
            if (validator.boundingBox() == null) {
                throw new IOException("ملف الخريطة لا يحتوي حدودًا صالحة");
            }
        } catch (RuntimeException error) {
            pending.delete();
            throw new IOException("الملف ليس خريطة Mapsforge صالحة", error);
        } finally {
            if (validator != null) {
                validator.close();
            }
        }

        boolean hadCurrent = target.isFile();
        if (hadCurrent && !target.renameTo(backup)) {
            pending.delete();
            throw new IOException("تعذر تجهيز الخريطة الحالية للاستبدال");
        }

        if (!pending.renameTo(target)) {
            if (hadCurrent && backup.isFile()) {
                backup.renameTo(target);
            }
            pending.delete();
            throw new IOException("تعذر تفعيل الخريطة الجديدة وتم الاحتفاظ بالخريطة السابقة");
        }

        backup.delete();
        return target;
    }
}
