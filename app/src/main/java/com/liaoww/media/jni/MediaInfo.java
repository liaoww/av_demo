package com.liaoww.media.jni;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MediaInfo {
    private String path;
    private long duration;

    private String durationText;

    private long bitrate;

    private int height;

    private int width;

    private int fps;

    public String getDurationText() {
        if (durationText == null) {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.CHINESE);
            format.setTimeZone(TimeZone.getTimeZone("GMT+0"));
            durationText = format.format(new Date(duration));
        }
        return durationText;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public int getFps() {
        return fps;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getHeight() {
        return height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getWidth() {
        return width;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getDuration() {
        return duration;
    }

    public void setBitrate(long bitrate) {
        this.bitrate = bitrate;
    }

    public long getBitrate() {
        return bitrate;
    }

    @NonNull
    @Override
    public String toString() {
        return "path : " + path + "\n" +
                "duration : " + duration + "\n" +
                "bitrate : " + bitrate + "\n" +
                "video size : " + width + " / " + height + "\n" +
                "fps: " + fps + "\n";
    }
}
