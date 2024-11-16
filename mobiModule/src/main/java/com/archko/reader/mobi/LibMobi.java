package com.archko.reader.mobi;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * added for support doc, docx files.
 * <p>
 * by longluo
 */
public class LibMobi {

    static {
        System.loadLibrary("mobi");
    }

    public static native int convertToEpub(String input, String output);

    public static boolean convertMobiToEpub(File file, Context context) {
        String input = file.getAbsolutePath();
        int hashCode = (input + file.length() + file.lastModified()).hashCode();
        //String folderPath = input.substring(0, input.lastIndexOf("/"));
        File outputFile = new File(context.getExternalCacheDir(), hashCode + ".epub");///folderPath + File.separator + hashCode + ".epub";
        Log.d("", String.format("convertMobiToEpub: file=%s, convertFilePath=%s",
                input, outputFile.getAbsoluteFile()));

        int res = -1;
        if (!outputFile.exists()) {
            try {
                res = LibMobi.convertToEpub(input, outputFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e("", e.getMessage());
            }
        } else {
            res = 0;
        }

        if (res != 0) {
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }
        return res == 0;
    }
}
