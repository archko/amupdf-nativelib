this is a native lib for amupdf-android:
https://github.com/archko/amupdf-android.git

## Building
thirdparty_build.gradle may not run corretly.

build mupdf aar, see compile.md, the offical aar is also avalibale

### run module
```
before run app, download deps, source code from thirdparty_build.gradle 

mupdfModule is a application, you can run it,and install app to test mupdf
antiwordModule is a application, you can run it,and install app to test antiword
djvuModule is a not application
mobiModule is a application, you can run it,and install app to test antiword
```

## gradle task(not implemented)
`./gradlew -b thirdparty_build.gradle downloadDjvu downloadAndMakeMupdf`