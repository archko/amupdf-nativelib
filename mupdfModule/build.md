### build aar
- change com.android.application to apply plugin: 'com.android.library'
- remove all java code ,res,clean manifest(may conflict with other)
- run task publishMupdfPublicationToMavenLocal

bundleReleaseAar not exist in com.android.application