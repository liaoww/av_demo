package com.liaoww.media;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {
    public static String getPictureRootPath(Context context) {
        return getRootPath(context, "pic");
    }

    public static String getVideoRootPath(Context context) {
        return getRootPath(context, "video");
    }


    public static String getVideoOutputPath(Context context) {
        return getRootPath(context, "video") + File.separator + "video_" + System.currentTimeMillis() + ".mp4";
    }

    public static String saveJpeg2File(byte[] data, String root) {
        String realPath = root + File.separator + "pic_" + System.currentTimeMillis() + ".jpg";
        try (FileOutputStream fileOutputStream = new FileOutputStream(new File(realPath))) {
            fileOutputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return realPath;
    }

    private static String getRootPath(Context context, String tag) {
        File file = new File(context.getFilesDir().getAbsolutePath() + File.separator + tag);
        boolean result;
        if (!file.exists()) {
            result = file.mkdirs();
            if (result) {
                return file.getAbsolutePath();
            } else {
                return "";
            }
        } else {
            return file.getAbsolutePath();
        }
    }
}
