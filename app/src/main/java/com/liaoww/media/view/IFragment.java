package com.liaoww.media.view;

public interface IFragment {
    /**
     * 通知权限授权
     */
    void permissionGranted();

    /**
     * 切换摄像头
     */
    void changeCamera();
}
