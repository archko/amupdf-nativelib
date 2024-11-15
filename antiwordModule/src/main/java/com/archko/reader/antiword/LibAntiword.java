package com.archko.reader.antiword;

import android.util.Log;

import java.io.File;

/**
 * added for support doc, docx files.
 * <p>
 * by longluo
 */
public class LibAntiword {

    static {
        System.loadLibrary("antiword");
    }

    public static native int convertDocToHtml(String input, String output);

    public static void openDocFile(File file) {
        String input = file.getAbsolutePath();
        int hashCode = (input + file.length() + file.lastModified()).hashCode();
        String folderPath = input.substring(0, input.lastIndexOf("/"));
        String output = folderPath + File.separator + hashCode + ".html";
        Log.d("", String.format("openDocFile: file=%s, folder=%s, convertFilePath=%s", input, folderPath, output));

        int res = 0;
        File convertFile = new File(output);
        if (!convertFile.exists()) {
            try {
                res = LibAntiword.convertDocToHtml(input, output);
            } catch (Exception e) {
                Log.e("", e.getMessage());
            }
        } else {
            res = 1;
        }

        if (res == 0) {
            convertFile.delete();
        }
    }
}
