this is a native lib for amupdf-android:
https://github.com/archko/amupdf-android.git

## Building
thirdparty_build.gradle may not run corretly.

### run module
mupdfModule is a application, you can run it,and install app to test mupdf
antiwordModule is a application, you can run it,and install app to test antiword
djvuModule is a not application

## gradle task(not implemented)
`./gradlew -b thirdparty_build.gradle downloadDjvu downloadAndMakeMupdf`