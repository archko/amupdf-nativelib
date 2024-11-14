cmake_minimum_required(VERSION 3.10.2)
project("antiword")
set(MY_ROOT "${PROJECT_SOURCE_DIR}/../../libantiword-1.3.1/")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI})
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--no-merge-exidx-entries, -DCR3_ANTIWORD_PATCH_2=0, -DCR3_ANTIWORD_PATCH=0, -DENABLE_ANTIWORD=1")

#add_definitions(-DNDEBUG)

add_library(antiword SHARED
        ${MY_ROOT}/main_u.c
        ${MY_ROOT}/asc85enc.c
        ${MY_ROOT}/blocklist.c
        ${MY_ROOT}/chartrans.c
        ${MY_ROOT}/datalist.c
        ${MY_ROOT}/depot.c
        ${MY_ROOT}/dib2eps.c
        ${MY_ROOT}/doclist.c
        ${MY_ROOT}/fail.c
        ${MY_ROOT}/finddata.c
        ${MY_ROOT}/findtext.c
        ${MY_ROOT}/fmt_text.c
        ${MY_ROOT}/fontlist.c
        ${MY_ROOT}/fonts.c
        ${MY_ROOT}/fonts_u.c
        ${MY_ROOT}/hdrftrlist.c
        ${MY_ROOT}/imgexam.c
        ${MY_ROOT}/imgtrans.c
        ${MY_ROOT}/jpeg2eps.c
        ${MY_ROOT}/listlist.c
        ${MY_ROOT}/misc.c
        ${MY_ROOT}/notes.c
        ${MY_ROOT}/options.c
        ${MY_ROOT}/out2window.c
        ${MY_ROOT}/output.c
        ${MY_ROOT}/pdf.c
        ${MY_ROOT}/pictlist.c
        ${MY_ROOT}/png2eps.c
        ${MY_ROOT}/postscript.c
        ${MY_ROOT}/prop0.c
        ${MY_ROOT}/prop2.c
        ${MY_ROOT}/prop6.c
        ${MY_ROOT}/prop8.c
        ${MY_ROOT}/properties.c
        ${MY_ROOT}/propmod.c
        ${MY_ROOT}/rowlist.c
        ${MY_ROOT}/sectlist.c
        ${MY_ROOT}/stylelist.c
        ${MY_ROOT}/stylesheet.c
        ${MY_ROOT}/summary.c
        ${MY_ROOT}/tabstop.c
        ${MY_ROOT}/text.c
        ${MY_ROOT}/unix.c
        ${MY_ROOT}/utf8.c
        ${MY_ROOT}/word2text.c
        ${MY_ROOT}/worddos.c
        ${MY_ROOT}/wordlib.c
        ${MY_ROOT}/wordmac.c
        ${MY_ROOT}/wordole.c
        ${MY_ROOT}/wordwin.c
        ${MY_ROOT}/xmalloc.c
        ${MY_ROOT}/xml.c
)

#antiword lib
target_include_directories(antiword PUBLIC ${MY_ROOT}/)

target_compile_options(antiword PUBLIC
        -fexceptions -frtti
        -funwind-tables
        -Wl
)
find_library(
        log-lib
        log)

target_link_libraries(antiword -lm ${log-lib})