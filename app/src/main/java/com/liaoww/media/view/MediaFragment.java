package com.liaoww.media.view;

import android.hardware.camera2.CameraCharacteristics;

import androidx.fragment.app.Fragment;

public class MediaFragment extends Fragment implements IFragment {

    //默认启动摄像头
    protected int mFacingId = CameraCharacteristics.LENS_FACING_BACK;

    @Override
    public void changeCamera() {
        //正反摄像头切换
        mFacingId = mFacingId == CameraCharacteristics.LENS_FACING_FRONT ?
                CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
    }
}
