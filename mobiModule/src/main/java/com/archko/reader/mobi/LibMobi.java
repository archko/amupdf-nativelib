package com.archko.reader.mobi;

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

    public static void openDocFile(File file) {
        String input = file.getAbsolutePath();
        int hashCode = (input + file.length() + file.lastModified()).hashCode();
        String folderPath = input.substring(0, input.lastIndexOf("/"));
        String output = folderPath + File.separator + hashCode + ".epub";
        Log.d("", String.format("openDocFile: file=%s, folder=%s, convertFilePath=%s", input, folderPath, output));

        int res = -1;
        File convertFile = new File(output);
        if (!convertFile.exists()) {
            try {
                res = LibMobi.convertToEpub(input, output);
            } catch (Exception e) {
                Log.e("", e.getMessage());
            }
        } else {
            res = 0;
        }

        if (res != 0) {
            convertFile.delete();
        }
    }
}
