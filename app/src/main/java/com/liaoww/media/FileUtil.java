package com.liaoww.media;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public static String getPictureOutputPath(Context context) {
        return getPictureRootPath(context) + File.separator + "pic_" + System.currentTimeMillis() + ".jpg";
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

    public static List<File> loadAllPhoto(Context context) {
        File photoRootPath = new File(FileUtil.getPictureRootPath(context.getApplicationContext()));
        List<File> fileList = null;
        if (photoRootPath.exists()) {
            File[] files = photoRootPath.listFiles();
            if (files != null) {
                fileList = new ArrayList<>();
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.add(file);
                    }
                }
                fileList.sort((file1, file2) -> {
                    return Long.compare(file2.lastModified(), file1.lastModified());// 最后修改的文件在前
                });
            }
        }
        return fileList;
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
