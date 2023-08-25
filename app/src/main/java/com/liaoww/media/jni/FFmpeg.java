package com.liaoww.media.jni;

public class FFmpeg {
    static {
        System.loadLibrary("ffmpeg-util");
        System.loadLibrary("ffmpeg-filter");
    }

    public static native void yuv2Mp4(String path, byte[] yuvData, int length, int width, int height);

    public static native void yuv2Mp4_422(String path, byte[] yuvData, int length, int width, int height);


    public static native void releaseEncoder();

    public static native int rotation(String input, String output, int output_rotation, int mirror_rotation);
}
