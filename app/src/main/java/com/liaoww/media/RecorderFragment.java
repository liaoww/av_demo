package com.liaoww.media;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 录视频fragment
 */
public class RecorderFragment extends MediaFragment {
    private static final int COLOR_FORMAT = ImageFormat.YUV_420_888;

    private CameraManager cameraManager;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mSession;

    private ImageReader mImageReader;

    private String mCameraId;

    private HandlerThread mHandlerThread;

    private Handler mHandler;

    private AutoFitTextureView mTextureView;

    private Size mPreviewSize;

    private int mWidth, mHeight;

    private String mPath;

    private volatile boolean encoderRunning = false;

    private Button mStartButton;

    private CustomMediaRecorder mMediaRecorder;

    private Size mDefaultAspectRatio = new Size(16, 9);

    private Size mRangeSize = new Size(1920, 1080);

    public static RecorderFragment of() {
        return new RecorderFragment();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/output.mp4";
        initTexture(view);
        findViews(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        waitingForPrepared();
    }

    @Override
    public void onPause() {
        super.onPause();
        FFmpeg.releaseEncoder();
        closeCamera();
        releaseHandler();
    }

    private void findViews(View view) {
        mStartButton = view.findViewById(R.id.start_button);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!encoderRunning) {
                    if (mWidth >= 0 && mHeight > 0) {
                        encoderRunning = true;
                        mStartButton.setText("结束录制");
                        initMediaRecorder();
                        createCameraPreviewSession(mCameraDevice, buildRecorderSurface());
                    } else {
                        Toast.makeText(getActivity(), "还没准备好", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    encoderRunning = false;
//                    FFmpeg.releaseEncoder();
                    mStartButton.setText("开始录制");
                    mMediaRecorder.stopRecorder();
                    createCameraPreviewSession(mCameraDevice, buildPreviewSurface());
                }
            }
        });
    }

    private void initMediaRecorder() {
        if (mMediaRecorder == null) {
            mMediaRecorder = new CustomMediaRecorder();
        }
        Size size = CameraUtil.findTargetSize(cameraManager, mCameraId, mDefaultAspectRatio, mRangeSize, MediaRecorder.class);
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        mMediaRecorder.init(mPath, size.getWidth(), size.getHeight(), CameraUtil.findSensorOrientation(cameraManager, mCameraId), rotation);
    }

    private void initTexture(View view) {
        mTextureView = view.findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                mWidth = width;
                mHeight = height;
                mSteps.countDown();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                Log.d("liaoww", "onSurfaceTextureSizeChanged");

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                Log.d("liaoww", "onSurfaceTextureDestroyed");

                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }

    private void waitingForPrepared() {
        new Thread(() -> {
            try {
                mSteps.await();
                getActivity().runOnUiThread(() -> {
                    startPreview(mWidth, mHeight);
                });

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }).start();
    }

    private void startPreview(int width, int height) {
        cameraManager = CameraUtil.fetchCameraManager(getActivity());

        if (cameraManager != null) {
            mCameraId = CameraUtil.findCameraId(cameraManager, mFacingId);
            if (!mCameraId.equals("")) {
                int orientation = getResources().getConfiguration().orientation;
                //计算MediaRecorder可用的尺寸，16/9 不超过1080P
                Size videoSize = CameraUtil.findTargetSize(cameraManager, mCameraId, mDefaultAspectRatio, mRangeSize, MediaRecorder.class);
                //计算和16/9相同宽高比的预览尺寸，并且需要大于预览surface width height
                mPreviewSize = CameraUtil.findPreviewSize(videoSize, cameraManager, mCameraId, orientation == Configuration.ORIENTATION_LANDSCAPE ? new Size(width, height) : new Size(height, width));
                //横竖屏需要宽高做交换
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }
                CameraUtil.configureTransform(getActivity(), mTextureView, mPreviewSize, width, height);

                mHandler = initCameraHandler();

                mImageReader = initImageReader(mWidth, mHeight, mHandler);

                openCamera(cameraManager, mCameraId, mHandler, buildPreviewSurface());
            }
        }
    }

    private ImageReader initImageReader(int width, int height, Handler handler) {
        ImageReader mImageReader = ImageReader.newInstance(width, height, COLOR_FORMAT, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //当前有画面可用,执行在handler 所在线程
                Image image = reader.acquireNextImage();
                if (image != null) {
                    if (encoderRunning) {
                        byte[] yuv = CameraUtil.toYuvImage(image);
                        if (encoderRunning) {
                            FFmpeg.yuv2Mp4(mPath, yuv, yuv.length, image.getWidth(), image.getHeight());
                        }
                    }
                    image.close();
                }
            }
        }, handler);
        return mImageReader;
    }


    private Handler initCameraHandler() {
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("camera thread");
        }
        mHandlerThread.start();
        return new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                Log.d("liaoww", "handleMessage");
            }
        };
    }

    private void releaseHandler() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }

        if (null != mHandler) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(CameraManager cameraManager, String cameraId, Handler handler, List<Surface> surfaces) {
        try {
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d("liaoww", "onOpened");
                    mCameraDevice = camera;
                    createCameraPreviewSession(camera, surfaces);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.d("liaoww", "onDisconnected");
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.d("liaoww", "onError");
                }
            }, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            if (mSession != null) {
                mSession.close();
                mSession = null;
            }

            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession(CameraDevice cameraDevice, List<Surface> surfaces) {
        try {
            closePreviewSession();
            List<OutputConfiguration> outputConfigurations = new ArrayList<>();
            //TEMPLATE_PREVIEW : 创建预览的请求
            //TEMPLATE_STILL_CAPTURE: 创建一个适合于静态图像捕获的请求，图像质量优先于帧速率
            //TEMPLATE_RECORD : 创建视频录制的请求 将会使用标准帧率
            //TEMPLATE_VIDEO_SNAPSHOT : 创建视视频录制时截屏的请求
            //TEMPLATE_ZERO_SHUTTER_LAG : 创建一个适用于零快门延迟的请求。在不影响预览帧率的情况下最大化图像质量
            //TEMPLATE_MANUAL : 创建一个基本捕获请求，这种请求中所有的自动控制都是禁用的(自动曝光，自动白平衡、自动焦点)
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                builder.addTarget(surface);
                outputConfigurations.add(new OutputConfiguration(surface));
            }

            SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, getActivity().getMainExecutor(), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d("liaoww", "onConfigured ");
                    try {
                        mSession = session;
                        //设置对焦模式为视频模式下的自动对焦
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

                        session.setRepeatingRequest(builder.build(), null, mHandler);
                        if (encoderRunning) {
                            mMediaRecorder.startRecorder();
                        }
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            });
            cameraDevice.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void closePreviewSession() {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
    }

    private List<Surface> buildPreviewSurface() {
        SurfaceTexture surfaceView = mTextureView.getSurfaceTexture();
        surfaceView.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(new Surface(surfaceView));//预览用
        return surfaces;
    }

    private List<Surface> buildRecorderSurface() {
        SurfaceTexture surfaceView = mTextureView.getSurfaceTexture();
        surfaceView.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(new Surface(surfaceView));//预览用
//                surfaces.add(mImageReader.getSurface());//接受YUV
        surfaces.add(mMediaRecorder.getSurface());//设置录制视频源
        return surfaces;
    }


    @Override
    public void changeCamera() {
        super.changeCamera();
        if (isResumed()) {
            closeCamera();
            releaseHandler();
            startPreview(mWidth, mHeight);
        }
    }
}
