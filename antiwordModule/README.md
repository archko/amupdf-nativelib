### modify file [main_u.c](../libantiword/main_u.c) main_u.c,add jni
```
static void setOptions() {
    options_type tOptions = {
            0,
            conversion_xml,
            TRUE,
            TRUE,
            FALSE,
            encoding_utf_8,
            LONG_MAX,
            LONG_MAX,
            level_default,
    };

    //vGetOptions(&tOptions);
    vSetOptions(&tOptions);
}

JNIEXPORT int JNICALL
Java_com_archko_reader_antiword_LibAntiword_convertDocToHtml(JNIEnv *env,
                                                             jclass clazz,
                                                             jstring jinput,
                                                             jstring joutpub) {
    char *fullpath = (char *) (*env)->GetStringUTFChars(env, jinput, NULL);
    char *outpath = (char *) (*env)->GetStringUTFChars(env, joutpub, NULL);

    setOptions();

    freopen(outpath, "w", stdout);
    fprintf(stdout, "<html><body>");
    BOOL res = bProcessFile(fullpath);
    fprintf(stdout, "</body></html>");

    fclose(stdout);
    free(fullpath);
    free(outpath);

    if (res) { return 1; }
    return 0;
}

```