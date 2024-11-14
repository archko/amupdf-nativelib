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

    public static void openDocFile( File file) {
        String path = file.getAbsolutePath();
        String folderPath = path.substring(0, path.lastIndexOf("/"));

        String hashCodeStr = path.hashCode() + "";
        String convertFilePath = folderPath + File.separator + hashCodeStr + ".html";
        Log.d("", "openDocFile: file=" + path + ", folder=" + folderPath
                + ",convertFilePath=" + convertFilePath);
        File convertFile = new File(convertFilePath);
        if (!convertFile.exists()) {
            LibAntiword.convertDocToHtml(path, new File(folderPath, hashCodeStr).getPath());
        }
        File firstConvertFile = new File(folderPath + File.separator + hashCodeStr + hashCodeStr + ".html");
        if (firstConvertFile.exists()) {
            firstConvertFile.renameTo(new File(convertFilePath));
        }
        //openEpubPdfBook(activity, convertFile);
    }
}
