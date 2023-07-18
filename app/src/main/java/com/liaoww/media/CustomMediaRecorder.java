package com.liaoww.media;

import android.media.MediaRecorder;
import android.view.Surface;

import java.io.File;
import java.io.IOException;

public class CustomMediaRecorder {
    private MediaRecorder mMediaRecorder;
    private int RECORDER_AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private int RECORDER_VIDEO_SOURCE = MediaRecorder.VideoSource.SURFACE;
    private int RECORDER_OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4;
    private int RECORDER_AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC;
    private int RECORDER_VIDEO_ENCODER = MediaRecorder.VideoEncoder.H264;
    private int RECORDER_VIDEO_FRAME_RATE = 30;

    public CustomMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
    }

    public void init(String outputPath, int width, int height, int sensorOrientation, int rotation) {
        File file = new File(outputPath);
        if (file.exists()) {
            file.delete();
        }
        mMediaRecorder.setAudioSource(RECORDER_AUDIO_SOURCE);//设置音频来源
        mMediaRecorder.setVideoSource(RECORDER_VIDEO_SOURCE);//设置视频来源
        mMediaRecorder.setOutputFormat(RECORDER_OUTPUT_FORMAT);//设置输出格式
        mMediaRecorder.setAudioEncoder(RECORDER_AUDIO_ENCODER);//设置音频编码格式
        mMediaRecorder.setVideoEncoder(RECORDER_VIDEO_ENCODER);//设置视频编码格式，请注意这里使用默认，实际app项目需要考虑兼容问题，应该选择H264
        mMediaRecorder.setVideoEncodingBitRate(width * height * 5);//设置比特率 一般是 1*分辨率 到 10*分辨率 之间波动。比特率越大视频越清晰但是视频文件也越大。
        mMediaRecorder.setVideoFrameRate(RECORDER_VIDEO_FRAME_RATE);//设置帧数 选择 30即可， 过大帧数也会让视频文件更大当然也会更流畅，但是没有多少实际提升。人眼极限也就30帧了。
        mMediaRecorder.setVideoSize(width, height);
        //根据sensor方向和activity方向，计算录制视频偏转角度，保证视频方向正确
        switch (sensorOrientation) {
            case Orientations.SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(Orientations.DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case Orientations.SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(Orientations.INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.setOutputFile(file.getAbsolutePath());
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Surface getSurface() {
        if (mMediaRecorder != null) {
            return mMediaRecorder.getSurface();
        }
        return null;
    }

    public void startRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.start();
        }
    }

    public void stopRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
    }
}
