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
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liaoww.media.AspectRatio;
import com.liaoww.media.CameraUtil;
import com.liaoww.media.CustomMediaRecorder;
import com.liaoww.media.FFmpeg;
import com.liaoww.media.FileUtil;
import com.liaoww.media.R;
import com.liaoww.media.view.widget.AutoFitTextureView;
import com.liaoww.media.view.widget.FocusView;
import com.liaoww.media.view.widget.TimerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 录视频fragment
 */
public class RecorderFragment extends MediaFragment {
    private static final int COLOR_FORMAT = ImageFormat.YUV_420_888;

    //camera相关
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mPreviewBuilder;
    private ImageReader mImageReader;


    //view相关
    private AutoFitTextureView mTextureView;
    private Button mStartButton;
    private FocusView mFocusView;
    private LinearLayout mContainer;
    private FocusView.FocusListener mListener;
    private TimerView mTimerView;

    //camera相关参数配置
    private String mCameraId;
    private Size mPreviewSize;
    private int mSurfaceWidth, mSurfaceHeight;
    private RectF mSensorAreaRect;//相机预览区域坐标系
    private Matrix mSurface2SensorMatrix;//渲染区域坐标 - 相机坐标系
    private Matrix mFace2SurfaceMatrix;//人脸坐标系转换matrix
    private final AspectRatio.AspectRatioSize mDefaultAspectRatio = AspectRatio.AR_16_9;//默认宽高比
    private final Size mOutputRangeSize = new Size(1920, 1080);//输出视频最大尺寸
    //人脸识别信息，first 是支持的人脸识别 mode ，second 是最大人脸个数
    private Pair<Integer, Integer> mFaceModeInfo = new Pair<>(CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF, 0);


    //变焦相关
    private float mDigitalZoomMax = 0f;//数码变焦最大倍数
    private float mCurrentDigitalZoom = 0f;//当前变焦倍数
    private Rect mDigitalZoomRect;//数码变焦rect


    //录制相关
    private volatile boolean mEncoderRunning = false;
    private CustomMediaRecorder mMediaRecorder;


    //线程相关
    private HandlerThread mHandlerThread;
    private Handler mHandler;


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
        findViews(view);
        initTexture();
        initFocusView(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTextureView == null) {
            initTexture();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        FFmpeg.releaseEncoder();
        closeCamera();
        releaseHandler();
        removeTexture();
        stopRecorder(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFocusView != null) {
            mFocusView.release();
        }
        closeCamera();
        releaseHandler();
        removeTexture();
    }

    private void findViews(View view) {
        mStartButton = view.findViewById(R.id.start_button);
        mStartButton.setOnClickListener(v -> {
            if (!mEncoderRunning) {
                startRecorder();
            } else {
                stopRecorder(false);
            }
        });
        mContainer = view.findViewById(R.id.surface_container);
        mTimerView = view.findViewById(R.id.timer_view);
    }


    private void startRecorder() {
        if (mSurfaceWidth >= 0 && mSurfaceHeight > 0) {
            mEncoderRunning = true;
            mStartButton.setText("结束录制");
            initMediaRecorder();
            createCameraRecorderSession(mCameraDevice, buildRecorderSurface());
            mTimerView.start();
            mTimerView.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(getActivity(), "还没准备好", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecorder(boolean release) {
        mEncoderRunning = false;
//                    FFmpeg.releaseEncoder();
        mStartButton.setText("开始录制");
        mMediaRecorder.stopRecorder();
        if (!release) {
            createCameraPreviewSession(mCameraDevice, buildPreviewSurface());
        }
        mTimerView.stop();
        mTimerView.setVisibility(View.GONE);
    }

    private void initMediaRecorder() {
        if (mMediaRecorder == null) {
            mMediaRecorder = new CustomMediaRecorder();
        }
        Size size = CameraUtil.findTargetSize(mCameraManager, mCameraId, mDefaultAspectRatio.size, mOutputRangeSize, MediaRecorder.class);
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        mMediaRecorder.init(FileUtil.getVideoOutputPath(getContext()), size.getWidth(), size.getHeight(), CameraUtil.findSensorOrientation(mCameraManager, mCameraId), rotation);
    }

    private void initTexture() {
        removeTexture();
        mTextureView = null;
        mTextureView = new AutoFitTextureView(getActivity().getApplicationContext());
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.e("liaoww", "onSurfaceTextureAvailable2");
                mSurfaceWidth = width;
                mSurfaceHeight = height;
                startPreview(mSurfaceWidth, mSurfaceHeight);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                Log.e("liaoww", "onSurfaceTextureSizeChanged");
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
                Log.e("liaoww", "onSurfaceTextureDestroyed");

                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
        mContainer.addView(mTextureView);
    }

    private void removeTexture() {
        mContainer.removeAllViews();
        mTextureView = null;
    }

    private void initFocusView(View view) {
        mFocusView = view.findViewById(R.id.focus_view);
        if (mListener == null) {
            mListener = new FocusView.FocusListener() {
                @Override
                public void onFocus(float x, float y) {
                    //坐标系转换
                    int areaSize = mSurfaceWidth / 5;//对焦区域
                    int left = (int) CameraUtil.clamp(x - areaSize / 2f, 0f, mSurfaceWidth - areaSize);//防止坐标超出范围
                    int top = (int) CameraUtil.clamp(y - areaSize / 2f, 0f, mSurfaceHeight - areaSize);
                    //使用matrix 转化
                    RectF rectF = CameraUtil.toCameraSpace(new RectF(left, top, left + areaSize, top + areaSize), mSurface2SensorMatrix);
                    //构造
                    MeteringRectangle meteringRectangle = new MeteringRectangle(new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom)), MeteringRectangle.METERING_WEIGHT_MAX);
                    createControlAFRequest(meteringRectangle, mEncoderRunning ? buildRecorderSurface() : buildPreviewSurface());
                }

                @Override
                public void onZoom(float zoomOffset) {
                    mCurrentDigitalZoom = CameraUtil.clamp(mCurrentDigitalZoom + zoomOffset, 0f, 1f);
                    digitalZoom(mCurrentDigitalZoom);
                    mFocusView.setZoomSize(mCurrentDigitalZoom * mDigitalZoomMax);
                }
            };
        }
        mFocusView.addTouchFocusListener(mListener);
    }

    private void startPreview(int width, int height) {
        mCameraManager = CameraUtil.fetchCameraManager(getActivity());

        if (mCameraManager != null) {
            mCameraId = CameraUtil.findCameraId(mCameraManager, mFacingId);
            if (!mCameraId.equals("")) {
                CameraCharacteristics characteristics = null;
                try {
                    characteristics = mCameraManager.getCameraCharacteristics(mCameraId);

                    int orientation = getResources().getConfiguration().orientation;
                    //计算MediaRecorder可用的尺寸，16/9 不超过1080P
                    Size videoSize = CameraUtil.findTargetSize(mCameraManager, mCameraId, mDefaultAspectRatio.size, mOutputRangeSize, MediaRecorder.class);
                    //计算和16/9相同宽高比的预览尺寸，并且需要大于预览surface width height
                    mPreviewSize = CameraUtil.findPreviewSize(videoSize, mCameraManager, mCameraId, orientation == Configuration.ORIENTATION_LANDSCAPE ? new Size(width, height) : new Size(height, width));


                    int sensorOrientation = CameraUtil.findSensorOrientation(mCameraManager, mCameraId);

                    //找到sensor区域(坐标系)
                    mSensorAreaRect = new RectF(CameraUtil.findSensorActiveArraySize(characteristics));

                    //人脸识别模式
                    mFaceModeInfo = CameraUtil.findFaceDetectMode(characteristics);

                    //最大数码变焦倍数
                    mDigitalZoomMax = CameraUtil.findMaxDigitalZoom(characteristics);

                    //切换摄像头之后需要重新计算一下坐标转换matrix
                    mSurface2SensorMatrix = CameraUtil.previewToCameraTransform(mFacingId == CameraCharacteristics.LENS_FACING_FRONT, sensorOrientation, new RectF(0, 0, mSurfaceWidth, mSurfaceHeight), mSensorAreaRect);

                    mFace2SurfaceMatrix = CameraUtil.face2PreviewTransform(mFacingId == CameraCharacteristics.LENS_FACING_FRONT, sensorOrientation);

                    //横竖屏需要宽高做交换
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }
                    CameraUtil.configureTransform(getActivity(), mTextureView, mPreviewSize, width, height);

                    mHandler = initCameraHandler();

                    mImageReader = initImageReader(mSurfaceWidth, mSurfaceHeight, mHandler);

                    //初始化变焦
                    mDigitalZoomRect = null;
                    mCurrentDigitalZoom = 0f;

                    openCamera(mCameraManager, mCameraId, mHandler);

                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }

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
                    if (mEncoderRunning) {
                        byte[] yuv = CameraUtil.toYuvImage(image);
                        if (mEncoderRunning) {
                            FFmpeg.yuv2Mp4(FileUtil.getVideoOutputPath(getContext()), yuv, yuv.length, image.getWidth(), image.getHeight());
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
    private void openCamera(CameraManager cameraManager, String cameraId, Handler handler) {
        try {
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d("liaoww", "onOpened");
                    mCameraDevice = camera;
                    createCameraPreviewSession(camera, buildPreviewSurface());
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
            closeSession();
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeSession() {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
    }

    private void createCameraPreviewSession(CameraDevice cameraDevice, List<Surface> surfaces) {
        try {
            closeSession();
            List<OutputConfiguration> outputConfigurations = new ArrayList<>();
            //TEMPLATE_PREVIEW : 创建预览的请求
            //TEMPLATE_STILL_CAPTURE: 创建一个适合于静态图像捕获的请求，图像质量优先于帧速率
            //TEMPLATE_RECORD : 创建视频录制的请求 将会使用标准帧率
            //TEMPLATE_VIDEO_SNAPSHOT : 创建视视频录制时截屏的请求
            //TEMPLATE_ZERO_SHUTTER_LAG : 创建一个适用于零快门延迟的请求。在不影响预览帧率的情况下最大化图像质量
            //TEMPLATE_MANUAL : 创建一个基本捕获请求，这种请求中所有的自动控制都是禁用的(自动曝光，自动白平衡、自动焦点)
            mPreviewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                mPreviewBuilder.addTarget(surface);
                outputConfigurations.add(new OutputConfiguration(surface));
            }

            SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, getActivity().getMainExecutor(), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d("liaoww", "onConfigured ");
                    try {
                        mSession = session;
                        //设置对焦模式为视频模式下的自动对焦
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

                        // 人脸检测模式
                        mPreviewBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mFaceModeInfo.first);

                        if (mDigitalZoomRect != null) {
                            //变焦
                            mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mDigitalZoomRect);
                        }
                        session.setRepeatingRequest(mPreviewBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
                                for (Face face : faces) {
                                    RectF out = new RectF();
                                    mFace2SurfaceMatrix.mapRect(out, new RectF(face.getBounds()));
                                    Log.d("liaoww", face.toString() + " ----- out  : " + out.toString());
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
            });
            cameraDevice.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void createCameraRecorderSession(CameraDevice cameraDevice, List<Surface> surfaces) {
        try {
            closeSession();
            List<OutputConfiguration> outputConfigurations = new ArrayList<>();
            //TEMPLATE_PREVIEW : 创建预览的请求
            //TEMPLATE_STILL_CAPTURE: 创建一个适合于静态图像捕获的请求，图像质量优先于帧速率
            //TEMPLATE_RECORD : 创建视频录制的请求 将会使用标准帧率
            //TEMPLATE_VIDEO_SNAPSHOT : 创建视视频录制时截屏的请求
            //TEMPLATE_ZERO_SHUTTER_LAG : 创建一个适用于零快门延迟的请求。在不影响预览帧率的情况下最大化图像质量
            //TEMPLATE_MANUAL : 创建一个基本捕获请求，这种请求中所有的自动控制都是禁用的(自动曝光，自动白平衡、自动焦点)
            mPreviewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                mPreviewBuilder.addTarget(surface);
                outputConfigurations.add(new OutputConfiguration(surface));
            }

            SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, getActivity().getMainExecutor(), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d("liaoww", "onConfigured ");
                    try {
                        mSession = session;
                        //设置对焦模式为视频模式下的自动对焦
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

                        // 人脸检测模式关闭
                        mPreviewBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF);

                        if (mDigitalZoomRect != null) {
                            //变焦
                            mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mDigitalZoomRect);
                        }
                        session.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
                        if (mEncoderRunning) {
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

            // 人脸检测模式
            builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mEncoderRunning ? CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF : mFaceModeInfo.first);

            //变焦
            if (mDigitalZoomRect != null) {
                //变焦
                builder.set(CaptureRequest.SCALER_CROP_REGION, mDigitalZoomRect);
            }

            //给请求添加surface 作为图像输出目标
            for (Surface surface : surfaces) {
                builder.addTarget(surface);
            }

            // AE/AF区域设置通过setRepeatingRequest不断发请求
            mSession.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
                    for (Face face : faces) {
                        RectF out = new RectF();
                        mFace2SurfaceMatrix.mapRect(out, new RectF(face.getBounds()));
                        float right = Math.abs(out.left - mSensorAreaRect.bottom);
                        float bottom = Math.abs(out.top - mSensorAreaRect.right);
                        float left = Math.abs(out.right - mSensorAreaRect.bottom);
                        float top = Math.abs(out.bottom - mSensorAreaRect.right);
                        mFocusView.updateFaceRect(left, top, right, bottom, mSensorAreaRect);
                    }
                }
            }, mHandler);
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

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /**
     * 数码变焦
     *
     * @param zoom 0-1f ，从不变焦到最大zoomMax
     */
    private void digitalZoom(float zoom) {
        if (mPreviewBuilder != null && mSession != null) {
            mDigitalZoomRect = CameraUtil.getZoomRect(zoom, mDigitalZoomMax, mSensorAreaRect);
            mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mDigitalZoomRect);
            try {
                mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
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
        if (isResumed() && !mEncoderRunning) {
            closeCamera();
            releaseHandler();
            startPreview(mSurfaceWidth, mSurfaceHeight);
        }
    }
}
