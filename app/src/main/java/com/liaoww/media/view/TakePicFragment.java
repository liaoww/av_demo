package com.liaoww.media.view;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liaoww.media.AspectRatio;
import com.liaoww.media.CameraUtil;
import com.liaoww.media.FlashMode;
import com.liaoww.media.Orientations;
import com.liaoww.media.R;
import com.liaoww.media.view.widget.AutoFitTextureView;
import com.liaoww.media.view.widget.FocusView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TakePicFragment extends MediaFragment {
    private static final int COLOR_FORMAT = ImageFormat.JPEG;

    private CameraManager cameraManager;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mSession;
    private ImageReader mImageReader;

    private AutoFitTextureView mTextureView;

    private Size mPreviewSize;

    private String mCameraId;

    private HandlerThread mHandlerThread;

    private Handler mHandler;

    private Button mFlashButton;

    private FocusView mFocusView;

    private int mWidth, mHeight;

    private int mSensorOrientation;

    //相机预览区域坐标系
    private RectF sensorAreaRect;

    //渲染区域坐标 - 相机坐标系
    private Matrix mSurface2SensorMatrix;

    //默认宽高比
    private Size mDefaultAspectRatio = AspectRatio.AR_4_3;

    private String mPath;

    //拍照是否完成
    private volatile boolean captureFinished = true;

    private boolean mFlashSupported = false;

    //闪光灯模式，默认关闭
    private int mFlashMode = FlashMode.OFF;

    private FocusView.FocusListener mListener;

    private Thread mSetupThread;

    public static TakePicFragment of() {
        return new TakePicFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_take_pic, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/output";
        findViews(view);
        initFocusView(view);
        initTexture(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        waitingForPrepared();
    }

    @Override
    public void onPause() {
        super.onPause();
        closeCamera();
        releaseHandler();
        interruptSetUpThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFocusView != null) {
            mFocusView.release();
        }
        closeCamera();
        releaseHandler();
        interruptSetUpThread();
    }

    @Override
    public void changeCamera() {
        super.changeCamera();
        if (isResumed()) {
            closeCamera();
            releaseHandler();
            setUpAndPreview(mWidth, mHeight);
            //切换摄像头之后需要重新计算一下坐标转换matrix
            mSurface2SensorMatrix = CameraUtil.previewToCameraTransform(
                    mFacingId == CameraCharacteristics.LENS_FACING_FRONT,
                    mSensorOrientation,
                    new RectF(0, 0, mWidth, mHeight),
                    sensorAreaRect);
        }
    }

    private void findViews(View view) {
        view.findViewById(R.id.take_pic_button).setOnClickListener(v -> {
            //拍照
            takePic();
        });

        view.findViewById(R.id.aspectRatio_button).setOnClickListener(v -> {
            if (mDefaultAspectRatio.equals(AspectRatio.AR_4_3)) {
                mDefaultAspectRatio = AspectRatio.AR_1_1;
            } else if (mDefaultAspectRatio.equals(AspectRatio.AR_1_1)) {
                mDefaultAspectRatio = AspectRatio.AR_16_9;
            } else {
                mDefaultAspectRatio = AspectRatio.AR_4_3;
            }
            closeCamera();
            releaseHandler();
            setUpAndPreview(mWidth, mHeight);
            //切换摄像头之后需要重新计算一下坐标转换matrix
            mSurface2SensorMatrix = CameraUtil.previewToCameraTransform(
                    mFacingId == CameraCharacteristics.LENS_FACING_FRONT,
                    mSensorOrientation,
                    new RectF(0, 0, mWidth, mHeight),
                    sensorAreaRect);

        });

        mFlashButton = view.findViewById(R.id.flash_button);
        mFlashButton.setOnClickListener(v -> {
            //切换闪光灯模式
            if (mFlashSupported) {
                if (mFlashMode == FlashMode.OFF) {
                    mFlashButton.setText("闪光灯开");
                    mFlashMode = FlashMode.ONCE;
                } else {
                    mFlashButton.setText("闪光灯关");
                    mFlashMode = FlashMode.OFF;
                }
            }
        });
    }

    private void initFocusView(View view) {
        mFocusView = view.findViewById(R.id.focus_view);
        if (mListener == null) {
            mListener = new FocusView.FocusListener() {
                @Override
                public void onFocus(float x, float y) {
                    //坐标系转换
                    int areaSize = mWidth / 5;//对焦区域
                    int left = clamp((int) x - areaSize / 2, 0, mWidth - areaSize);//防止坐标超出范围
                    int top = clamp((int) y - areaSize / 2, 0, mHeight - areaSize);
                    //使用matrix 转化
                    RectF rectF = CameraUtil.toCameraSpace(new RectF(left, top, left + areaSize, top + areaSize), mSurface2SensorMatrix);
                    //构造
                    MeteringRectangle meteringRectangle
                            = new MeteringRectangle(
                            new Rect(Math.round(rectF.left),
                                    Math.round(rectF.top),
                                    Math.round(rectF.right),
                                    Math.round(rectF.bottom)),
                            MeteringRectangle.METERING_WEIGHT_MAX);
                    createControlAFRequest(meteringRectangle, buildPreviewSurface());
                }
            };
        }
        mFocusView.addTouchFocusListener(mListener);
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
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

    private ImageReader initImageReader(int width, int height, Handler handler) {
        ImageReader mImageReader = ImageReader.newInstance(width, height, COLOR_FORMAT, 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.e("liaoww", "onImageAvailable");
                //获取最后一次image
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    //数据平面，JPEG数据存储在0位置
                    Image.Plane plane = image.getPlanes()[0];
                    //返回buffer数据
                    ByteBuffer buffer = plane.getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    image.close();
                    String outputPath = CameraUtil.saveJpeg2File(data, mPath);
                    if (outputPath != null) {
                        //保存成功之后，弹窗显示
                        PicFragment.of(outputPath).show(getActivity().getSupportFragmentManager(), "pic");
                    }
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
            mHandlerThread.quit();
            mHandlerThread = null;
        }

        if (null != mHandler) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
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
                Log.d("liaoww", "onSurfaceTextureSizeChanged : " + width + " / " + height);
                if (mFocusView != null) {
                    //更新实际的画面区域
                    mFocusView.post(new Runnable() {
                        @Override
                        public void run() {
                            mFocusView.updateEffectiveArea(width, height);

                            //变换对焦区域坐标
                            mSurface2SensorMatrix = CameraUtil.previewToCameraTransform(
                                    mFacingId == CameraCharacteristics.LENS_FACING_FRONT,
                                    mSensorOrientation,
                                    new RectF(0, 0, width, height),
                                    sensorAreaRect);
                        }
                    });
                }
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

    private void setUpAndPreview(int width, int height) {
        Log.d("liaoww", "setUpAndPreview");
        cameraManager = CameraUtil.fetchCameraManager(getActivity());

        if (cameraManager != null) {
            mCameraId = CameraUtil.findCameraId(cameraManager, mFacingId);
            if (!mCameraId.equals("")) {
                int orientation = getResources().getConfiguration().orientation;

                CameraCharacteristics characteristics = null;
                try {
                    characteristics = cameraManager.getCameraCharacteristics(mCameraId);
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    //找到合适的输出尺寸
                    Size outputSize = CameraUtil.findTargetSize(map, mDefaultAspectRatio, ImageFormat.JPEG);

                    //找到合适的预览尺寸
                    mPreviewSize = CameraUtil.findPreviewSize(map, outputSize,
                            orientation == Configuration.ORIENTATION_LANDSCAPE ? new Size(width, height) : new Size(height, width));

                    //判断设备是否支持闪光模式
                    mFlashSupported = CameraUtil.findFlashAvailable(characteristics);
                    mFlashButton.setEnabled(mFlashSupported);

                    //找到sensor方向
                    mSensorOrientation = CameraUtil.findSensorOrientation(cameraManager, mCameraId);

                    //找到对焦区域
                    sensorAreaRect = new RectF(CameraUtil.findSensorActiveArraySize(characteristics));

                    //设置textureView宽高比和 预览尺寸保持一致
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }
                    CameraUtil.configureTransform(getActivity(), mTextureView, mPreviewSize, width, height);

                    mHandler = initCameraHandler();

                    mImageReader = initImageReader(outputSize.getWidth(), outputSize.getHeight(), mHandler);

                    openCamera(cameraManager, mCameraId, mHandler, buildPreviewSurface());
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void createCameraPreviewSession(CameraDevice cameraDevice, List<Surface> surfaces) {
        try {
            closePreviewSession();
            List<OutputConfiguration> outputConfigurations = new ArrayList<>();
            //TEMPLATE_PREVIEW : 创建预览的请求
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                builder.addTarget(surface);
                outputConfigurations.add(new OutputConfiguration(surface));
            }

            SessionConfiguration sessionConfiguration = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigurations,
                    getActivity().getMainExecutor(),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                mSession = session;
                                //设置对焦模式为照片模式下的自动对焦
                                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // 人脸检测模式
                                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);

                                //预览模式不需要设置监听
                                session.setRepeatingRequest(builder.build(), null, mHandler);
                            } catch (CameraAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }

                        @Override
                        public void onClosed(@NonNull CameraCaptureSession session) {
                            super.onClosed(session);
                        }
                    });
            cameraDevice.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void createCameraCaptureSession(CameraDevice cameraDevice, List<Surface> surfaces) {
        try {
            closePreviewSession();
            List<OutputConfiguration> outputConfigurations = new ArrayList<>();
            //TEMPLATE_ZERO_SHUTTER_LAG : 创建一个适用于零快门延迟的请求。在不影响预览帧率的情况下最大化图像质量
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                builder.addTarget(surface);
                outputConfigurations.add(new OutputConfiguration(surface));
            }

            SessionConfiguration sessionConfiguration = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigurations,
                    getActivity().getMainExecutor(),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                mSession = session;
                                //设置对焦模式为照片模式下的自动对焦
                                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                //人脸检测模式
                                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);

                                //设置闪光灯模式
                                if (mFlashSupported) {
                                    //经测试 暂时只支持单次闪光模式
                                    builder.set(CaptureRequest.FLASH_MODE, mFlashMode == FlashMode.ONCE ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
                                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                }

                                //通过屏幕方向偏转照片，保证方向正确
                                int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();

                                builder.set(CaptureRequest.JPEG_ORIENTATION,
                                        (Orientations.DEFAULT_ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360);

                                //启动拍照
                                session.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                        super.onCaptureCompleted(session, request, result);
                                        //重新开启预览
                                        createCameraPreviewSession(mCameraDevice, buildPreviewSurface());
                                    }
                                }, mHandler);
                            } catch (CameraAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }

                        @Override
                        public void onClosed(@NonNull CameraCaptureSession session) {
                            super.onClosed(session);
                            captureFinished = true;
                        }
                    });
            cameraDevice.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }


    private void createControlAFRequest(MeteringRectangle rect, List<Surface> surfaces) {
        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            MeteringRectangle[] rectangle = new MeteringRectangle[]{rect};
            // 对焦模式必须设置为AUTO
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            //AE
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, rectangle);
            //AF 此处AF和AE用的同一个rect, 实际AE矩形面积比AF稍大, 这样测光效果更好
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, rectangle);

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                builder.addTarget(surface);
            }

            // AE/AF区域设置通过setRepeatingRequest不断发请求
            mSession.setRepeatingRequest(builder.build(), null, mHandler);
            //触发对焦
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            //触发对焦通过capture发送请求, 因为用户点击屏幕后只需触发一次对焦
            mSession.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.d("liaoww", "对焦完成");
                }
            }, mHandler);

        } catch (CameraAccessException exception) {
            exception.printStackTrace();
        }
    }

    private void takePic() {
        if (!captureFinished) {
            return;
        }
        captureFinished = false;
        createCameraCaptureSession(mCameraDevice, buildCaptureSurface());
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

    private List<Surface> buildCaptureSurface() {
        SurfaceTexture surfaceView = mTextureView.getSurfaceTexture();
        surfaceView.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(new Surface(surfaceView));//预览用
        surfaces.add(mImageReader.getSurface());//捕获拍照数据
        return surfaces;
    }

    private void setUpThread() {
        if (mSetupThread == null) {
            mSetupThread = new Thread(() -> {
                try {
                    mSteps.await();
                    if(mSetupThread.isInterrupted()){
                        return;
                    }
                    getActivity().runOnUiThread(() -> {
                        setUpAndPreview(mWidth, mHeight);
                    });

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            });
        }
    }

    private void interruptSetUpThread(){
        if(mSetupThread != null){
            mSetupThread.interrupt();
            mSetupThread = null;
        }
        if(mSteps != null){
            mSteps.countDown();
        }
    }


    private void waitingForPrepared() {
        setUpThread();
        mSetupThread.start();
    }
}
