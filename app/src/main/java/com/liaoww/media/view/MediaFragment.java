package com.liaoww.media.view;

import android.hardware.camera2.CameraCharacteristics;

import androidx.fragment.app.Fragment;

import com.liaoww.media.view.IFragment;

import java.util.concurrent.CountDownLatch;

public class MediaFragment extends Fragment implements IFragment {

    //必须等待权限和surfaceView 同时准备好 2 steps
    protected CountDownLatch mSteps = new CountDownLatch(2);

    //默认启动摄像头
    protected int mFacingId = CameraCharacteristics.LENS_FACING_BACK;


    @Override
    public void permissionGranted() {
        mSteps.countDown();
    }

    @Override
    public void changeCamera() {
        //正反摄像头切换
        mFacingId = mFacingId == CameraCharacteristics.LENS_FACING_FRONT ?
                CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
    }
}
