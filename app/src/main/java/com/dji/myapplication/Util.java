package com.dji.myapplication;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Util {
    public static File copyKMZToExternalStorage(Context context, String fileName) {
        File externalFile = new File(context.getExternalFilesDir(null), fileName);
        try (InputStream inputStream = context.getAssets().open(fileName);
             FileOutputStream outputStream = new FileOutputStream(externalFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return externalFile;
    }
}
