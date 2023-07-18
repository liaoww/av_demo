package com.liaoww.media;

class FFmpeg {
    static {
        System.loadLibrary("ffmpeg-util");
    }

    public static native void yuv2Mp4(String path, byte[] yuvData, int length, int width, int height);

    public static native void yuv2Mp4_422(String path, byte[] yuvData, int length, int width, int height);


    public static native void releaseEncoder();
}
