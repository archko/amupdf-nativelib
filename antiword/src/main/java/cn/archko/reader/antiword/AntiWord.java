package cn.archko.reader.antiword;

public class AntiWord {

    private static boolean alreadyLoaded = false;

    public static void load() {
        if (alreadyLoaded) {
            return;
        }
        System.loadLibrary("antiword");
        alreadyLoaded = true;
    }

    static {
        AntiWord.load();
    }

    public static native int convertDocToHtml(String input, String output);
}
