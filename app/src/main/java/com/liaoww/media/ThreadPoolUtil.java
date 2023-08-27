package com.liaoww.media;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolUtil {
    private static volatile ExecutorService mThreadPool;

    public static ExecutorService getThreadPool() {
        if (mThreadPool == null) {
            synchronized (ThreadPoolUtil.class) {
                if (mThreadPool == null) {
                    mThreadPool = Executors.newCachedThreadPool(r -> new Thread(r, "Photo Thread"));
                }
            }
        }
        return mThreadPool;
    }
}
