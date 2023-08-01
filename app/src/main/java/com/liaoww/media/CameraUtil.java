package com.liaoww.media;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraUtil {

    public static byte[] toYuvImage(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // Order of U/V channel guaranteed, read more:
        // https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888
        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        Log.d("liaoww", "yPixelStride : " + yPlane.getPixelStride());
        Log.d("liaoww", "yRowStride : " + yPlane.getRowStride());
        Log.d("liaoww", "ySize : " + yBuffer.remaining());

        Log.d("liaoww", "uPixelStride : " + uPlane.getPixelStride());
        Log.d("liaoww", "uRowStride : " + uPlane.getRowStride());
        Log.d("liaoww", "uSize : " + uBuffer.remaining());

        Log.d("liaoww", "vPixelStride : " + vPlane.getPixelStride());
        Log.d("liaoww", "vRowStride : " + vPlane.getRowStride());
        Log.d("liaoww", "vSize : " + vBuffer.remaining());

        // Full size Y channel and quarter size U+V channels.
        int numPixels = (int) (width * height * 3 / 2);
        byte[] nv21 = new byte[numPixels];
        int index = 0;

        // Copy Y channel.
        int yRowStride = yPlane.getRowStride();
        int yPixelStride = yPlane.getPixelStride();
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                nv21[index++] = yBuffer.get(y * yRowStride + x * yPixelStride);
            }
        }

        // Copy VU data; NV21 format is expected to have YYYYVU packaging.
        // The U/V planes are guaranteed to have the same row stride and pixel stride.
        int uvRowStride = uPlane.getRowStride();
        int uvPixelStride = uPlane.getPixelStride();
        int uvWidth = width / 2;
        int uvHeight = height / 2;

        for (int y = 0; y < uvHeight; ++y) {
            for (int x = 0; x < uvWidth; ++x) {
                int bufferIndex = (y * uvRowStride) + (x * uvPixelStride);
                // V channel.
                nv21[index++] = vBuffer.get(bufferIndex);
                // U channel.
                nv21[index++] = uBuffer.get(bufferIndex);
            }
        }
        return nv21;
    }

    public static byte[] rotateYUVDegree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    public static byte[] rotateYUVDegree270(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = imageWidth - 1; x >= 0; x--) {
            for (int y = 0; y < imageHeight; y++) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }// Rotate the U and V color components
        i = imageWidth * imageHeight;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i++;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i++;
            }
        }
        return yuv;
    }

    public static byte[] rotateYUVDegree270AndMirror(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate and mirror the Y luma
        int i = 0;
        int maxY = 0;
        for (int x = imageWidth - 1; x >= 0; x--) {
            maxY = imageWidth * (imageHeight - 1) + x * 2;
            for (int y = 0; y < imageHeight; y++) {
                yuv[i] = data[maxY - (y * imageWidth + x)];
                i++;
            }
        }
        // Rotate and mirror the U and V color components
        int uvSize = imageWidth * imageHeight;
        i = uvSize;
        int maxUV = 0;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            maxUV = imageWidth * (imageHeight / 2 - 1) + x * 2 + uvSize;
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[maxUV - 2 - (y * imageWidth + x - 1)];
                i++;
                yuv[i] = data[maxUV - (y * imageWidth + x)];
                i++;
            }
        }
        return yuv;
    }


    public static byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer yBuffer = getBufferWithoutPadding(image.getPlanes()[0].getBuffer(), image.getWidth(), image.getPlanes()[0].getRowStride(), image.getHeight(), false);
        ByteBuffer vBuffer;
        //part1 获得真正的消除padding的ybuffer和ubuffer。需要对P格式和SP格式做不同的处理。如果是P格式的话只能逐像素去做，性能会降低。
        if (image.getPlanes()[2].getPixelStride() == 1) { //如果为true，说明是P格式。
            vBuffer = getuvBufferWithoutPaddingP(image.getPlanes()[1].getBuffer(), image.getPlanes()[2].getBuffer(), width, height, image.getPlanes()[1].getRowStride(), image.getPlanes()[1].getPixelStride());
        } else {
            vBuffer = getBufferWithoutPadding(image.getPlanes()[2].getBuffer(), image.getWidth(), image.getPlanes()[2].getRowStride(), image.getHeight() / 2, true);
        }

        //part2 将y数据和uv的交替数据（除去最后一个v值）赋值给nv21
        int ySize = yBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21;
        int byteSize = width * height * 3 / 2;
        nv21 = new byte[byteSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);

        //part3 最后一个像素值的u值是缺失的，因此需要从u平面取一下。
        ByteBuffer uPlane = image.getPlanes()[1].getBuffer();
        byte lastValue = uPlane.get(uPlane.capacity() - 1);
        nv21[byteSize - 1] = lastValue;
        return nv21;
    }

    //Planar格式（P）的处理
    private static ByteBuffer getuvBufferWithoutPaddingP(ByteBuffer uBuffer, ByteBuffer vBuffer, int width, int height, int rowStride, int pixelStride) {
        int pos = 0;
        byte[] byteArray = new byte[height * width / 2];
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                byteArray[pos++] = vBuffer.get(vuPos);
                byteArray[pos++] = uBuffer.get(vuPos);
            }
        }
        ByteBuffer bufferWithoutPaddings = ByteBuffer.allocate(byteArray.length);
        // 数组放到buffer中
        bufferWithoutPaddings.put(byteArray);
        //重置 limit 和postion 值否则 buffer 读取数据不对
        bufferWithoutPaddings.flip();
        return bufferWithoutPaddings;
    }

    //Semi-Planar格式（SP）的处理和y通道的数据
    private static ByteBuffer getBufferWithoutPadding(ByteBuffer buffer, int width, int rowStride, int times, boolean isVbuffer) {
        if (width == rowStride) return buffer;  //没有buffer,不用处理。
        int bufferPos = buffer.position();
        int cap = buffer.capacity();
        byte[] byteArray = new byte[times * width];
        int pos = 0;
        //对于y平面，要逐行赋值的次数就是height次。对于uv交替的平面，赋值的次数是height/2次
        for (int i = 0; i < times; i++) {
            buffer.position(bufferPos);
            //part 1.1 对于u,v通道,会缺失最后一个像u值或者v值，因此需要特殊处理，否则会crash
            if (isVbuffer && i == times - 1) {
                width = width - 1;
            }
            buffer.get(byteArray, pos, width);
            bufferPos += rowStride;
            pos = pos + width;
        }

        //nv21数组转成buffer并返回
        ByteBuffer bufferWithoutPaddings = ByteBuffer.allocate(byteArray.length);
        // 数组放到buffer中
        bufferWithoutPaddings.put(byteArray);
        //重置 limit 和postion 值否则 buffer 读取数据不对
        bufferWithoutPaddings.flip();
        return bufferWithoutPaddings;
    }

    /**
     * 通过宽高比筛选正确的分辨率
     *
     * @param choices
     * @return
     */
    public static Size chooseVideoSize(Size[] choices, Size aspectRatio, Size range) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * aspectRatio.getWidth() / aspectRatio.getHeight()) {
                if (range != null) {
                    //如果设置了范围，必须满足范围
                    if (size.getWidth() <= range.getWidth() && size.getHeight() <= range.getHeight()) {
                        return size;
                    }
                } else {
                    return size;
                }
            }
        }
        Log.e("liaoww", "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e("liaoww", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static CameraManager fetchCameraManager(Activity activity) {
        return (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    }

    public static String findCameraId(CameraManager cameraManager, int facingId) {
        String frontCameraId = "";
        if (cameraManager != null) {
            try {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    Log.d("liaoww", "cameraId : " + cameraId);
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == facingId) {
                        //前置摄像头
                        frontCameraId = cameraId;
                        break;
                    }
                }

            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            Log.d("liaoww", "fontCameraId : " + frontCameraId);
        }
        return frontCameraId;
    }

    public static Size findTargetSize(CameraManager cameraManager, String cameraId, Size aspectRatio, int target) {
        return findTargetSizeInRange(cameraManager, cameraId, aspectRatio, target, null);
    }

    public static Size findTargetSizeInRange(CameraManager cameraManager, String cameraId, Size aspectRatio, int target, Size range) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            return CameraUtil.chooseVideoSize(map.getOutputSizes(target), aspectRatio, range);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Size findTargetSize(StreamConfigurationMap map, Size aspectRatio, int target) {
        return CameraUtil.chooseVideoSize(map.getOutputSizes(target), aspectRatio, null);
    }


    public static <T> Size findTargetSize(CameraManager cameraManager, String cameraId, Size aspectRatio, Class<T> target) {
        return findTargetSize(cameraManager, cameraId, aspectRatio, null, target);
    }

    public static <T> Size findTargetSize(CameraManager cameraManager, String cameraId, Size aspectRatio, Size range, Class<T> target) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            return CameraUtil.chooseVideoSize(map.getOutputSizes(target), aspectRatio, range);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Size findPreviewSize(Size aspectRatio, CameraManager cameraManager, String cameraId, Size minSize) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            return CameraUtil.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), minSize.getWidth(), minSize.getHeight(), aspectRatio);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Size findPreviewSize(StreamConfigurationMap map, Size aspectRatio, Size minSize) {
        return CameraUtil.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), minSize.getWidth(), minSize.getHeight(), aspectRatio);
    }

    public static boolean findFlashAvailable(CameraCharacteristics characteristics) {
        return characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
    }

    public static int findSensorOrientation(CameraManager cameraManager, String cameraId) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Rect findSensorActiveArraySize(CameraCharacteristics characteristics) {
        return characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    }

    public static Matrix previewToCameraTransform(boolean mirrorX, int sensorOrientation,
                                                  RectF previewRect, RectF driverRecF) {
        Matrix transform = new Matrix();
        //如果是前置摄像头，进行水平翻转
        transform.setScale(mirrorX ? -1 : 1, 1);
        //旋转角度
        transform.postRotate(-sensorOrientation);
        transform.mapRect(previewRect);
        Matrix fill = new Matrix();
        //使用填充模式
        fill.setRectToRect(previewRect, driverRecF, Matrix.ScaleToFit.FILL);
        transform.setConcat(fill, transform);
        return transform;
    }

    public static RectF toCameraSpace(RectF source, Matrix matrix) {
        RectF result = new RectF();
        matrix.mapRect(result, source);
        return result;
    }

    public static void configureTransform(Activity activity, TextureView textureView, Size size, int viewWidth, int viewHeight) {
        if (null == textureView || null == size || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, size.getHeight(), size.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / size.getHeight(), (float) viewWidth / size.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    public static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }


    public static String saveJpeg2File(byte[] data, String path) {
        String realPath = path + "-" + System.currentTimeMillis() + ".jpg";
        try (FileOutputStream fileOutputStream = new FileOutputStream(new File(realPath))) {
            fileOutputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return realPath;
    }

    public static void testFFmpeg() {
        String mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/output.mp4";
        String mTest = Environment.getExternalStorageDirectory().getAbsolutePath() + "/akiyo_cif.yuv";

        new Thread(() -> {
            try (FileInputStream input = new FileInputStream(mTest)) {
                int width = 352;
                int height = 288;
                int size = width * height * 3 / 2;
                byte[] buf = new byte[size];
                while (input.read(buf) > 0) {
                    FFmpeg.yuv2Mp4(mPath, buf, size, width, height);
                }
                input.close();
                FFmpeg.releaseEncoder();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}
