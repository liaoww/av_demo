package com.liaoww.media.jni;

public class FFmpeg {
    static {
        System.loadLibrary("ffmpeg-util");
        System.loadLibrary("ffmpeg-filter");
    }

    public static native void yuv2Mp4(String path, byte[] yuvData, int length, int width, int height);

    public static native void yuv2Mp4_422(String path, byte[] yuvData, int length, int width, int height);


    public static native void releaseEncoder();

    public static int filter(String input, String output,
                             int output_rotation, int mirror_rotation) {
        return filter(input, output, output_rotation, mirror_rotation,
                -1, -1, -1, -1, 0, 0);
    }

    public static native int filter(String input, String output,
                                    int output_rotation, int mirror_rotation,
                                    int rectLeft, int rectTop,
                                    int rectRight, int rectBottom, int areaWidth, int areaHeight);

    public static native MediaInfo fetchMediaInfo(String path);
}
