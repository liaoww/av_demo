package com.liaoww.media.view.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FocusView extends View {
    private int mEffectiveWidth = -1;

    private int mEffectiveHeight = -1;

    private float mFocusFrameWidth = DEFAULT_FRAME_WIDTH;

    private static final float DEFAULT_FRAME_WIDTH = 256;
    private static final float DEFAULT_FRAME_PAINT_WIDTH = 4f;
    private static final float DEFAULT_FRAME_RADIUS = 14f;
    private static final float DEFAULT_FRAME_STROKE_PAINT_WIDTH = DEFAULT_FRAME_PAINT_WIDTH + 4f;
    private static final int DEFAULT_FRAME_COLOR = Color.parseColor("#FFFFFF");
    private static final int DEFAULT_FRAME_STROKE_COLOR = Color.parseColor("#DCDCDC");

    private static final float DEFAULT_ZOOM_TEXT_PAINT_STROKE_WIDTH = 4f;
    private static final float DEFAULT_ZOOM_TEXT_SIZE = 80f;
    private float mDownEventX = 0f;
    private float mDownEventY = 0f;
    private long mDownEventTime = 0L;
    private Paint mFramePaint;
    private Paint mFrameStrokePaint;
    private Paint mZoomPaint;
    private boolean mFocusAnimationEnable = false;

    private ValueAnimator mFocusAnimation;

    private SparseArray<FocusListener> listeners;

    private Handler mHandler;

    volatile boolean mHandlerRunning = false;

    boolean mFaceFrameEnable = false;

    private int mTouchMotionType = 1;//手势模式 1为单指，2为多指

    //一次按下抬起，双指移动距离
    private float mPointerSpacing = -1;

    //距上一次双指按下，缩放了多少倍
    private float mScale = 1;

    //缩放尺寸
    private float mZoomSize = 0f;

    private boolean mZoomSizeEnable = false;

    private float faceLeft, faceTop, faceRight, faceBottom;
    private float lastFaceLeft, lastFaceTop, lastFaceRight, lastFaceBottom;


    public FocusView(Context context) {
        super(context);
        init();
    }

    public FocusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FocusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFocusAnimationEnable) {
            //绘制对焦框
            float offsetX = mDownEventX;
            float offsetY = mDownEventY;
            if ((offsetX - mFocusFrameWidth / 2) < 0) {
                //焦点框超过了有效范围最左侧，向右偏移x坐标
                offsetX = offsetX + 0 - (offsetX - mFocusFrameWidth / 2);
            }
            if ((offsetY - mFocusFrameWidth / 2) < 0) {
                //焦点框超过了有效范围最上侧，向下偏移y坐标
                offsetY = offsetY + 0 - (offsetY - mFocusFrameWidth / 2);
            }

            if ((offsetX + mFocusFrameWidth / 2) > mEffectiveWidth) {
                //焦点框超过了有效范围最右侧，向左偏移x坐标
                offsetX = offsetX - ((offsetX + mFocusFrameWidth / 2) - mEffectiveWidth);
            }

            if ((offsetY + mFocusFrameWidth / 2) > mEffectiveHeight) {
                //焦点框超过了有效范围最下侧，向上偏移y坐标
                offsetY = offsetY - ((offsetY + mFocusFrameWidth / 2) - mEffectiveHeight);
            }
            canvas.drawRoundRect(
                    offsetX - mFocusFrameWidth / 2,
                    offsetY - mFocusFrameWidth / 2,
                    offsetX + mFocusFrameWidth / 2,
                    offsetY + mFocusFrameWidth / 2,
                    DEFAULT_FRAME_RADIUS,
                    DEFAULT_FRAME_RADIUS,
                    mFrameStrokePaint);

            canvas.drawRoundRect(
                    offsetX - mFocusFrameWidth / 2,
                    offsetY - mFocusFrameWidth / 2,
                    offsetX + mFocusFrameWidth / 2,
                    offsetY + mFocusFrameWidth / 2,
                    DEFAULT_FRAME_RADIUS,
                    DEFAULT_FRAME_RADIUS,
                    mFramePaint);

        } else if (mFaceFrameEnable) {
            //人脸识别框和点击对焦框互斥
            canvas.drawRoundRect(faceLeft, faceTop, faceRight, faceBottom, DEFAULT_FRAME_RADIUS, DEFAULT_FRAME_RADIUS, mFrameStrokePaint);
            canvas.drawRoundRect(faceLeft, faceTop, faceRight, faceBottom, DEFAULT_FRAME_RADIUS, DEFAULT_FRAME_RADIUS, mFramePaint);
        }


        if (mZoomSizeEnable) {
            //绘制缩放比例文字，保留一位小数
            canvas.drawText(String.format("%.1f", mZoomSize) + "X", getWidth() / 2f, getHeight() / 2f, mZoomPaint);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                //单指
                mTouchMotionType = 1;
                mDownEventX = event.getX();
                mDownEventY = event.getY();
                mDownEventTime = SystemClock.currentThreadTimeMillis();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                //多指
                mTouchMotionType = 2;
                mPointerSpacing = getPointerSpacing(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTouchMotionType == 2) {
                    if (event.getPointerCount() >= 2) {
                        //执行缩放
                        float currentSpacing = getPointerSpacing(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                        notifyZoomListener(currentSpacing / mPointerSpacing - mScale);
                        mScale = currentSpacing / mPointerSpacing;
                        mZoomSizeEnable = true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTouchMotionType == 1) {
                    //单指触发对焦
                    if (isClick(event.getX(), event.getY())) {
                        doFrameAnimation();
                        notifyTouchFocusListener(event.getX(), event.getY());
                    }
                }
                mPointerSpacing = -1;
                mScale = 1;
                mZoomSizeEnable = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                postInvalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                mPointerSpacing = -1;
                mScale = 1;
                mZoomSizeEnable = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                postInvalidate();
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 根据实际surface大小，更新焦点框的有效范围
     */
    public void updateEffectiveArea(int width, int height) {
        mEffectiveWidth = width;
        mEffectiveHeight = height;
        getLayoutParams().width = width;
        getLayoutParams().height = height;
        requestLayout();
    }

    public interface FocusListener {
        void onFocus(float x, float y);

        void onZoom(float zoomOffset);
    }

    public void addTouchFocusListener(FocusListener listener) {
        if (listeners == null) {
            listeners = new SparseArray<>();
        }
        listeners.put(listener.hashCode(), listener);
    }

    public void removeTouchFocusListener(FocusListener listener) {
        if (listeners != null) {
            listeners.remove(listener.hashCode());
        }
    }

    public void release() {
        if (mFocusAnimation != null) {
            mFocusAnimation.cancel();
        }
        if (listeners != null) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.remove(i);
            }
        }
        listeners = null;
        mHandler.removeCallbacksAndMessages(null);
        mHandler = null;
    }


    public void updateFaceRect(float left, float top, float right, float bottom, RectF sensorRect) {
        mFaceFrameEnable = true;
        faceLeft = getWidth() * (left / sensorRect.bottom);
        faceTop = getHeight() * (top / sensorRect.right);
        faceRight = getWidth() * (right / sensorRect.bottom);
        faceBottom = getHeight() * (bottom / sensorRect.right);
        postInvalidate();
        doFaceTimeout();
    }

    public void setZoomSize(float size) {
        mZoomSize = size;
        postInvalidate();
    }

    // 触碰两点间距离
    private float getPointerSpacing(float pointOneX, float pointOneY, float pointTwoX, float pointTwoY) {
        //通过三角函数得到两点间的距离
        float x = pointOneX - pointTwoX;
        float y = pointOneY - pointTwoY;
        return (float) Math.sqrt(x * x + y * y);
    }

    private void doFaceTimeout() {
        if (!mHandlerRunning) {
            //记录一下当前人脸识别坐标，和300毫秒之后判断是否有变化
            lastFaceLeft = faceLeft;
            lastFaceTop = faceTop;
            lastFaceRight = faceRight;
            lastFaceBottom = faceBottom;
            mHandlerRunning = true;
            mHandler.postDelayed(() -> {
                mHandlerRunning = false;
                if (lastFaceLeft == faceLeft && lastFaceTop == faceTop & lastFaceRight == faceRight && lastFaceBottom == faceBottom) {
                    //人脸识别超时期间 未有新的坐标输入
                    //隐藏识别框
                    mFaceFrameEnable = false;
                    postInvalidate();
                } else {
                    //有新的坐标输入，进行下一次检测
                    doFaceTimeout();
                }
            }, 300);
        }
    }

    private void notifyTouchFocusListener(float x, float y) {
        if (listeners != null) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.valueAt(i).onFocus(x, y);
            }
        }
    }

    private void notifyZoomListener(float zoom) {
        if (listeners != null) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.valueAt(i).onZoom(zoom);
            }
        }
    }


    private void init() {
        mFramePaint = new Paint();
        mFramePaint.setColor(DEFAULT_FRAME_COLOR);
        mFramePaint.setStrokeWidth(DEFAULT_FRAME_PAINT_WIDTH);
        mFramePaint.setStyle(Paint.Style.STROKE);
        mFramePaint.setAntiAlias(true);

        mFrameStrokePaint = new Paint();
        mFrameStrokePaint.setColor(DEFAULT_FRAME_STROKE_COLOR);
        mFrameStrokePaint.setStrokeWidth(DEFAULT_FRAME_STROKE_PAINT_WIDTH);
        mFrameStrokePaint.setStyle(Paint.Style.STROKE);
        mFrameStrokePaint.setAntiAlias(true);

        mZoomPaint = new Paint();
        mZoomPaint.setColor(DEFAULT_FRAME_COLOR);
        mZoomPaint.setStrokeWidth(DEFAULT_ZOOM_TEXT_PAINT_STROKE_WIDTH);
        mZoomPaint.setTextAlign(Paint.Align.CENTER);
        mZoomPaint.setTextSize(DEFAULT_ZOOM_TEXT_SIZE);
        mZoomPaint.setAntiAlias(true);

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
            }
        };
    }

    private ValueAnimator setUpFocusAnimation() {
        //对焦框的略微缩紧动画 最后在最大值停留一下
        ValueAnimator animator = ValueAnimator.ofFloat(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_WIDTH * 0.7f, DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_WIDTH);
        animator.setDuration(800);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());//先加速后减速非线性动画
        animator.addUpdateListener(animation -> {
            mFocusFrameWidth = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mFocusAnimationEnable = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mFocusAnimationEnable = false;
            }
        });
        return animator;
    }

    private void doFrameAnimation() {
        if (mFocusAnimation == null) {
            mFocusAnimation = setUpFocusAnimation();
        }
        mFocusAnimation.start();
    }

    private boolean isClick(float x, float y) {
        //点击偏移不超过10像素且间隔小于100毫秒，判定为点击
        return (Math.abs(x - mDownEventX) < 10 && Math.abs(y - mDownEventY) < 10 && (SystemClock.currentThreadTimeMillis() - mDownEventTime) < 100);
    }
}