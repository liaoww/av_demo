package com.liaoww.media.view;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TakePicFragment extends MediaFragment {
    private static final int COLOR_FORMAT = ImageFormat.JPEG;

    private CameraManager mCameraManager;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mSession;

    private CaptureRequest.Builder previewBuilder;

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
    private RectF mSensorAreaRect;

    //渲染区域坐标 - 相机坐标系
    private Matrix mSurface2SensorMatrix;

    private Matrix mFace2SurfaceMatrix;

    //默认宽高比
    private AspectRatio.AspectRatioSize mDefaultAspectRatio = AspectRatio.AR_4_3;

    private String mPath;

    //拍照是否完成
    private volatile boolean captureFinished = true;

    private boolean mFlashSupported = false;

    //闪光灯模式，默认关闭
    private int mFlashMode = FlashMode.OFF;

    //人脸识别信息，first 是支持的人脸识别 mode ，second 是最大人脸个数
    private Pair<Integer, Integer> mFaceModeInfo = new Pair<>(CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF, 0);

    private float mDigitalZoomMax = 0f;

    private float mCurrentDigitalZoom = 0f;

    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    private FocusView.FocusListener mListener;

    private Thread mSetupThread;

    private View mView;

    private Rect mDigitalZoomRect;

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
//        mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/output";
        mPath = getContext().getFilesDir().getAbsolutePath() + "/";
        mView = view;
        findViews(view);
        initAspectRatioButton(view);
        initFocusView(view);
        initTexture();
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
        Log.e("liaoww", "onPause");
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
        Log.e("liaoww", "onDestroy");
    }

    @Override
    public void changeCamera() {
        super.changeCamera();
        if (isResumed()) {
            closeCamera();
            releaseHandler();
            setUpAndPreview(mWidth, mHeight);
        }
    }

    private void findViews(View view) {
        view.findViewById(R.id.take_pic_button).setOnClickListener(v -> {
            //拍照
            takePic();
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

    private void initAspectRatioButton(View view) {
        Button aspectRatioButton = view.findViewById(R.id.aspectRatio_button);
        aspectRatioButton.setText(mDefaultAspectRatio.name);
        aspectRatioButton.setOnClickListener(v -> {
            if (mDefaultAspectRatio.equals(AspectRatio.AR_4_3)) {
                mDefaultAspectRatio = AspectRatio.AR_1_1;
            } else if (mDefaultAspectRatio.equals(AspectRatio.AR_1_1)) {
                mDefaultAspectRatio = AspectRatio.AR_16_9;
            } else {
                mDefaultAspectRatio = AspectRatio.AR_4_3;
            }
            aspectRatioButton.setText(mDefaultAspectRatio.name);
            //切换宽高比，需要重新创建TextureView
            interruptSetUpThread();
            initTexture();
            closeCamera();
            releaseHandler();
            waitingForPrepared();
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
                    int left = (int) CameraUtil.clamp(x - areaSize / 2f, 0f, mWidth - areaSize);//防止坐标超出范围
                    int top = (int) CameraUtil.clamp(y - areaSize / 2f, 0f, mHeight - areaSize);
                    //使用matrix 转化
                    RectF rectF = CameraUtil.toCameraSpace(new RectF(left, top, left + areaSize, top + areaSize), mSurface2SensorMatrix);
                    //构造
                    MeteringRectangle meteringRectangle = new MeteringRectangle(new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom)), MeteringRectangle.METERING_WEIGHT_MAX);
                    createControlAFRequest(meteringRectangle, buildPreviewSurface());
                }

                @Override
                public void onZoom(float zoomOffset) {
                    mCurrentDigitalZoom = CameraUtil.clamp(mCurrentDigitalZoom + zoomOffset, 0f, 1f);
                    digitalZoom(mCurrentDigitalZoom);
                    mFocusView.setZoomSize(mCurrentDigitalZoom * mDigitalZoomMax);
                    mFocusView.postInvalidate();
                }
            };
        }
        mFocusView.addTouchFocusListener(mListener);
    }

    @SuppressLint("MissingPermission")
    private void openCamera(CameraManager cameraManager, String cameraId, Handler handler, List<Surface> surfaces) {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MICROSECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    Log.e("liaoww", "onOpened");
                    mCameraDevice = camera;
                    createCameraPreviewSession(camera, surfaces);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    Log.e("liaoww", "onDisconnected");
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
            cameraOpenCloseLock.acquire();

            if (mSession != null) {
                mSession.close();
                mSession = null;
            }

            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cameraOpenCloseLock.release();
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
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
                mHandlerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (null != mHandler) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    private void initTexture() {
        LinearLayout container = mView.findViewById(R.id.surface_container);
        container.removeAllViews();
        mTextureView = null;
        mTextureView = new AutoFitTextureView(getActivity().getApplicationContext());
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.e("liaoww", "onSurfaceTextureAvailable : " + width + " / " + height);
                mWidth = width;
                mHeight = height;
                mSteps.countDown();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                Log.e("liaoww", "onSurfaceTextureSizeChanged : " + width + " / " + height);
                if (mFocusView != null) {
                    //更新实际的画面区域
                    mFocusView.post(new Runnable() {
                        @Override
                        public void run() {
                            mFocusView.updateEffectiveArea(width, height);
                        }
                    });
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                Log.d("liaoww", "onSurfaceTextureDestroyed");

                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
        container.addView(mTextureView);
    }

    private void setUpAndPreview(int width, int height) {
        Log.e("liaoww", "setUpAndPreview width : " + width + " height : " + height);
        mCameraManager = CameraUtil.fetchCameraManager(getActivity());

        if (mCameraManager != null) {
            mCameraId = CameraUtil.findCameraId(mCameraManager, mFacingId);
            if (!mCameraId.equals("")) {
                int orientation = getResources().getConfiguration().orientation;

                CameraCharacteristics characteristics = null;
                try {
                    characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    //找到合适的输出尺寸
                    Size outputSize = CameraUtil.findTargetSize(map, mDefaultAspectRatio.size, ImageFormat.JPEG);

                    //找到合适的预览尺寸
                    mPreviewSize = CameraUtil.findPreviewSize(map, outputSize, orientation == Configuration.ORIENTATION_LANDSCAPE ? new Size(width, height) : new Size(height, width));

                    //判断设备是否支持闪光模式
                    mFlashSupported = CameraUtil.findFlashAvailable(characteristics);
                    mFlashButton.setEnabled(mFlashSupported);

                    //找到sensor方向
                    mSensorOrientation = CameraUtil.findSensorOrientation(mCameraManager, mCameraId);

                    //找到sensor区域(坐标系)
                    mSensorAreaRect = new RectF(CameraUtil.findSensorActiveArraySize(characteristics));

                    //人脸识别模式
                    mFaceModeInfo = CameraUtil.findFaceDetectMode(characteristics);

                    //最大数码变焦倍数
                    mDigitalZoomMax = CameraUtil.findMaxDigitalZoom(characteristics);

                    //切换摄像头之后需要重新计算一下坐标转换matrix
                    mSurface2SensorMatrix = CameraUtil.previewToCameraTransform(mFacingId == CameraCharacteristics.LENS_FACING_FRONT, mSensorOrientation, new RectF(0, 0, mWidth, mHeight), mSensorAreaRect);

                    mFace2SurfaceMatrix = CameraUtil.face2PreviewTransform(mFacingId == CameraCharacteristics.LENS_FACING_FRONT, mSensorOrientation);

                    //设置textureView宽高比和 预览尺寸保持一致
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }
                    CameraUtil.configureTransform(getActivity(), mTextureView, mPreviewSize, width, height);

                    mHandler = initCameraHandler();

                    mImageReader = initImageReader(outputSize.getWidth(), outputSize.getHeight(), mHandler);

                    
                    //初始化变焦
                    mDigitalZoomRect = null;
                    mCurrentDigitalZoom = 0f;

                    openCamera(mCameraManager, mCameraId, mHandler, buildPreviewSurface());
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void createCameraPreviewSession(CameraDevice cameraDevice, List<Surface> surfaces) {
        try {
            closePreviewSession();
            List<OutputConfiguration> outputConfigurations = new ArrayList<>();
            //TEMPLATE_PREVIEW : 创建预览的请求
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                previewBuilder.addTarget(surface);
                outputConfigurations.add(new OutputConfiguration(surface));
            }

            SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, getActivity().getMainExecutor(), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        mSession = session;
                        //设置对焦模式为照片模式下的自动对焦
                        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        // 人脸检测模式
                        previewBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mFaceModeInfo.first);

                        if(mDigitalZoomRect != null){
                            //变焦
                            previewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mDigitalZoomRect);
                        }

                        //预览模式不需要设置监听
                        session.setRepeatingRequest(previewBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
                                for (Face face : faces) {
                                    RectF out = new RectF();
                                    mFace2SurfaceMatrix.mapRect(out, new RectF(face.getBounds()));
//                                    Log.d("liaoww", face.toString() + " ----- out  : " + out.toString());
                                    float right = Math.abs(out.left - mSensorAreaRect.bottom);
                                    float bottom = Math.abs(out.top - mSensorAreaRect.right);
                                    float left = Math.abs(out.right - mSensorAreaRect.bottom);
                                    float top = Math.abs(out.bottom - mSensorAreaRect.right);
                                    mFocusView.updateFaceRect(left, top, right, bottom, mSensorAreaRect);
                                }
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

            SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, getActivity().getMainExecutor(), new CameraCaptureSession.StateCallback() {
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

                        builder.set(CaptureRequest.JPEG_ORIENTATION, (Orientations.DEFAULT_ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360);

                        if(mDigitalZoomRect != null){
                            //变焦
                            builder.set(CaptureRequest.SCALER_CROP_REGION, mDigitalZoomRect);
                        }

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
                    //重新开启预览
                    createCameraPreviewSession(mCameraDevice, buildPreviewSurface());
                }
            }, mHandler);

        } catch (Exception exception) {
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


    /**
     * 数码变焦
     *
     * @param zoom 0-1f ，从不变焦到最大zoomMax
     */
    private void digitalZoom(float zoom) {
        if (previewBuilder != null && mSession != null) {
            mDigitalZoomRect = CameraUtil.getZoomRect(zoom, mDigitalZoomMax, mSensorAreaRect);
            previewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mDigitalZoomRect);
            try {
                mSession.setRepeatingRequest(previewBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
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
        int previewWidth = mPreviewSize.getWidth();
        int previewHeight = mPreviewSize.getHeight();
        Log.e("liaoww", "buildPreviewSurface --- previewWidth : " + previewWidth + "  previewHeight : " + previewHeight);
        surfaceView.setDefaultBufferSize(previewWidth, previewHeight);
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(new Surface(surfaceView));//预览用
        return surfaces;
    }

    private List<Surface> buildCaptureSurface() {
        Log.e("liaoww", "buildCaptureSurface");
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
                    if (mSetupThread.isInterrupted()) {
                        return;
                    }
                    getActivity().runOnUiThread(() -> {
                        setUpAndPreview(mWidth, mHeight);
                    });

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    Log.e("liaoww", "mSetupThread is TERMINATED");
                }
            });
        }
    }

    private void interruptSetUpThread() {
        if (mSetupThread != null) {
            mSetupThread.interrupt();
            mSetupThread = null;
        }
        if (mSteps != null) {
            mSteps.countDown();
        }
    }

    private void waitingForPrepared() {
        setUpThread();
        mSetupThread.start();
    }
}