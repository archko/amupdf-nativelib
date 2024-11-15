### this project maybe used to debug jni.

#### [if you want to build an aar, you can modify build script to publish an aar, or create a new project mupdf-lib:]
```
->cd mupdf-lib
->git clone http://git.ghostscript.com/mupdf.git libmupdf
->cd libmupdf
->git pull
->git submodule init
->git submodule update
->make generate
->cd ..
->./gradlew publishMupdfPublicationToMavenLocal
```

then publish aar to local file system "./m2/com/artifex", the amupdf-android deps the aar

#### some modification for reflow:path file from dir "pdf-patch":
- pdf-patch/css-apply.c
- pdf-patch/page.c
- pdf-patch/Page.java
- pdf-patch/stext-output.c

```
	public native byte[] textAsHtml2(String options);
	public native byte[] textAsXHtml(String options);
	public native byte[] textAsText(String options);
```

#### [the project dir structure:]

```
-mupdf-lib
--libmupdf
--gradle
--build.gradle
--gradlew
--AndroidManifest.xml
```

#### the content of AndroidManifest.xml is:
```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.artifex.mupdf.fitz" />
```

--------- mupdf-lib'2 top-level build.gradle content
```
apply plugin: 'com.android.library'
apply plugin: 'maven-publish'

group = 'com.artifex.mupdf'
version = '1.25.0'

buildscript {
	repositories {
        maven {
            url "https://maven.aliyun.com/repository/public"
        }
        maven {
            url "https://maven.aliyun.com/repository/google"
        }
		maven { url "https://jitpack.io" }
		google()
		mavenCentral()
		maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
		maven { url "https://kotlin.bintray.com/kotlinx/" }
	}
	dependencies {
		classpath 'com.android.tools.build:gradle:8.6.0'
	}
}

allprojects {
	repositories {
        maven {
            url "https://maven.aliyun.com/repository/public"
        }
        maven {
            url "https://maven.aliyun.com/repository/google"
        }
		maven { url "https://jitpack.io" }
		google()
		mavenCentral()
		maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
		maven { url "https://kotlin.bintray.com/kotlinx/" }
	}
}

publishing {
	publications {
        // 定义一个名为 mupdf 的发布,
		// ./gradlew publishMupdfPublicationToMavenLocal发布到.m2下面
		// ./gradlew publishMupdfPublicationToRemoteRepoRepository则是发布到远程,要配置远程仓库
        mupdf(MavenPublication) {
            groupId = "com.artifex.mupdf"
            artifactId = "mupdf-fitz"
            version = '1.25.0'
            // 必须有这个 否则不会上传AAR包
            afterEvaluate { artifact(tasks.getByName("bundleReleaseAar")) }
            // 上传source，这样使用方可以看到方法注释
            //artifact generateSourcesJar
        }
    }
    repositories {
        maven {
            // 基于版本名称选择不同的仓库地址
            def releasesRepoUrl = "file:///Users/archko/pdf/mupdf_c/"
            def snapshotsRepoUrl = "file:///Users/archko/pdf/mupdf_c/"
			name = 'localRepo'
            // url是必须要配置的
            url = version.endsWith('SNAPSHOT') ? snapshotsRepoUrl : releasesRepoUrl
            // 仓库用户名密码
            //credentials {
            //    username = "root"
            //    password = "root"
            //}
        }
    }
}

android {
	compileSdkVersion 35
	buildToolsVersion '35.0.0'
	namespace "com.artifex.mupdf.fitz"

	defaultConfig {
		minSdkVersion 19
		targetSdkVersion 33
		externalNativeBuild.ndkBuild.arguments '-j4'

		ndk{
			abiFilters  "arm64-v8a" //,"armeabi-v7a"
		}
	}

	sourceSets {
		main {
			manifest.srcFile 'AndroidManifest.xml'
			java.srcDirs 'libmupdf/platform/java/src'
		}
	}

	externalNativeBuild {
		ndkBuild.path 'libmupdf/platform/java/Android.mk'
	}
	android.ndkVersion '25.2.9519653'
}
```
