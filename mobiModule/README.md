### modify file [mobitool.c](../libmobi/tools/mobitool.c)mobitool.c,add jni
```
#include <jni.h>

JNIEXPORT int JNICALL
Java_com_archko_reader_mobi_LibMobi_convertToEpub(JNIEnv *env, jclass clazz, jstring jinput,
                                                 jstring joutpub) {
    jboolean iscopy;

    char *fullpath = (char *) (*env)->GetStringUTFChars(env, jinput, &iscopy);
    char *outdir = (char *) (*env)->GetStringUTFChars(env, joutpub, &iscopy);

    MOBI_RET mobi_ret;
    int ret = SUCCESS;

    MOBIData *m = mobi_init();
    if (m == NULL) {
        printf("Memory allocation failed\n");
        return ERROR;
    }

    FILE *file = fopen(fullpath, "rb");
    if (file == NULL) {
        printf("Error opening file: %s\n", fullpath);
        mobi_free(m);
        return ERROR;
    }

    mobi_ret = mobi_load_file(m, file);
    fclose(file);

    print_meta(m);

    if (mobi_ret != MOBI_SUCCESS) {
        printf("Error while loading document (%s)\n", libmobi_msg(mobi_ret));
        mobi_free(m);
        return ERROR;
    }

    MOBIRawml *rawml = mobi_init_rawml(m);
    if (rawml == NULL) {
        printf("Memory allocation failed\n");
        mobi_free(m);
        return ERROR;
    }

    mobi_ret = mobi_parse_rawml(rawml, m);
    if (mobi_ret != MOBI_SUCCESS) {
        printf("Parsing rawml failed (%s)\n", libmobi_msg(mobi_ret));
        mobi_free(m);
        mobi_free_rawml(rawml);
        return ERROR;
    }

    ret = create_epub(rawml, outdir);

    if (ret != SUCCESS) {
        printf("Creating EPUB failed\n");
    }

    mobi_free_rawml(rawml);
    mobi_free(m);
    return ret;
}
```