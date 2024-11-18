package com.archko.reader.antiword;

import android.content.Context;
import android.util.Log;

import org.zwobble.mammoth.DocumentConverter;
import org.zwobble.mammoth.Result;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Metadata;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubWriter;

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
        String output = folderPath + File.separator + file.getName() + ".html";
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

    /**
     * docx先转为html,图片可以保存下来,然后通过下面的库转为epub.
     * https://github.com/gonejack/html_to_epub rust写的
     * https://github.com/gonejack/html-to-epub-kotlin
     *
     * @param file
     */
    public static void convertDocxToHtml(File file, Context context) {
        String input = file.getAbsolutePath();
        String folderPath = input.substring(0, input.lastIndexOf("/"));
        Map<String, String> images = new HashMap<>();
        DocumentConverter converter = new DocumentConverter().
                imageConverter(image -> {
                    String imageName = image.hashCode() + "." + image.getContentType().replace("image/", "");
                    Log.d("", "ImageConverter:" + imageName);

                    File imageFile = new File(folderPath, imageName);
                    StreamUtils.saveStreamToFile(image.getInputStream(), imageFile);
                    images.put(imageName, imageFile.getAbsolutePath());

                    Map<String, String> map = new HashMap<>();
                    map.put("src", imageName);
                    return map;
                });

        Result<String> result = null;
        try {
            result = converter.convertToHtml(file);
            String html = result.getValue(); // The generated HTML

            int hashCode = (input + file.length() + file.lastModified()).hashCode();
            String outputHtml = folderPath + File.separator + hashCode + ".html";
            String outputEpub = folderPath + File.separator + hashCode + ".epub";
            Log.d("", String.format("convertDocxToHtml: file=%s, folder=%s, convertFilePath=%s", input, folderPath, outputHtml));
            boolean res = StreamUtils.saveStringToFile("<html><head></head><body>" + html + "</body></html>", outputHtml);
            if (res) {
                //new HTMLToEpub("", "archko", file.getName(), outputEpub).run(output, images);
                convertToEpub("archko", file.getName(), outputEpub, outputHtml, images, context);
                for (Map.Entry<String, String> image : images.entrySet()) {
                    new File(image.getValue()).delete();
                }
                new File(outputHtml).delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static InputStream getResource(String path) throws FileNotFoundException {
        return new FileInputStream(path);
    }

    private static Resource getResource(String path, String href) throws IOException {
        return new Resource(getResource(path), href);
    }

    private static void convertToEpub(String author, String name, String output, String outputHtml, Map<String, String> images, Context context) {
        try {
            // Create new Book
            Book book = new Book();
            Metadata metadata = book.getMetadata();

            metadata.addTitle(name);
            metadata.addAuthor(new Author(author, "Author"));

            // Set cover image
            //book.setCoverImage(getResource("/book1/test_cover.png", "cover.png"));

            // Add Chapter 1
            book.addSection("Introduction", getResource(outputHtml, "chapter1.html"));

            // Add css file
            //Resource css = new Resource(context.getAssets().open("epub.css"), "epub.css");
            //book.getResources().add(css);

            // Add Chapter 2
            //TOCReference chapter2 = book.addSection("Second Chapter", getResource("/book1/chapter2.html", "chapter2.html"));

            // Add image used by Chapter 2
            //book.getResources().add(getResource("/book1/flowers_320x240.jpg", "flowers.jpg"));
            for (Map.Entry<String, String> image : images.entrySet()) {
                book.getResources().add(getResource(image.getValue(), image.getKey()));
            }

            // Add Chapter2, Section 1
            //book.addSection(chapter2, "Chapter 2, section 1", getResource("/book1/chapter2_1.html", "chapter2_1.html"));

            EpubWriter epubWriter = new EpubWriter();

            // Write the Book as Epub
            epubWriter.write(book, new FileOutputStream(output));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
