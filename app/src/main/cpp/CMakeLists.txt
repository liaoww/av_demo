#指定 cmake 最低版本
cmake_minimum_required(VERSION 3.4.1)

#编译动态库输出路径
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI})

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)


#设置头文件搜索路径，如果跟CMakeLists.txt同路径则不需要设置，同路径下文件夹不行
include_directories(${PROJECT_SOURCE_DIR}/include)

add_library(
        ffmpeg-util

        SHARED

        ffmpeg-util.cpp)

add_library(
        ffmpeg-filter

        SHARED

        ffmpeg-filter.cpp)

#添加libavutil.so
add_library(
        avutil
        SHARED
        IMPORTED)

set_target_properties(avutil
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI}/libavutil.so)

#添加libavcodec.so
add_library(
        avcodec
        SHARED
        IMPORTED)

set_target_properties(
        avcodec
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI}/libavcodec.so)

#添加libavformat.so
add_library(
        avformat
        SHARED
        IMPORTED)

set_target_properties(
        avformat
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI}/libavformat.so)

#添加libswresample.so
add_library(
        swresample
        SHARED
        IMPORTED)

set_target_properties(
        swresample
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI}/libswresample.so)

#添加libavfilter.so
add_library(
        avfilter
        SHARED
        IMPORTED)

set_target_properties(
        avfilter
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI}/libavfilter.so)

#添加libswscale.so
add_library(
        swscale
        SHARED
        IMPORTED)

set_target_properties(
        swscale
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI}/libswscale.so)

#添加libavutil.so
add_library(
        libyuv
        SHARED
        IMPORTED)

set_target_properties(libyuv
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/../nativeLibs/${ANDROID_ABI}/libyuv.so)

target_link_libraries(
        ffmpeg-util
        avutil
        avcodec
        avformat
        swresample
        avfilter
        swscale
        libyuv
        ${log-lib}
)

target_link_libraries(
        ffmpeg-filter
        avutil
        avcodec
        avformat
        swresample
        avfilter
        swscale
        ${log-lib}
)