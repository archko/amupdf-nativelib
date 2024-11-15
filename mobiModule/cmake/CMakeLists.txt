cmake_minimum_required(VERSION 3.10.2)
project("mobi")
set(MY_ROOT "${PROJECT_SOURCE_DIR}/../../libmobi/")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI})
set(CMAKE_CFLAGS "${CMAKE_CFLAGS} -std=c99")

add_library(mobi SHARED
        ${MY_ROOT}/src/buffer.c
        ${MY_ROOT}/src/compression.c
        ${MY_ROOT}/src/debug.c
        ${MY_ROOT}/src/encryption.c
        ${MY_ROOT}/src/index.c
        ${MY_ROOT}/src/memory.c
        ${MY_ROOT}/src/meta.c
        ${MY_ROOT}/src/miniz.c
        ${MY_ROOT}/src/opf.c
        ${MY_ROOT}/src/parse_rawml.c
        ${MY_ROOT}/src/read.c
        ${MY_ROOT}/src/sha1.c
        ${MY_ROOT}/src/structure.c
        ${MY_ROOT}/src/util.c
        ${MY_ROOT}/src/write.c
        ${MY_ROOT}/src/xmlwriter.c
        ${MY_ROOT}/src/randombytes.c
        ${MY_ROOT}/tools/common.c
        ${MY_ROOT}/tools/mobitool.c
)

#mobi lib
target_include_directories(mobi PUBLIC ${MY_ROOT}/src ${MY_ROOT}/tools)

target_compile_options(mobi PUBLIC
        -Wl
        -DUSE_XMLWRITER
)

target_link_libraries(mobi -lz)