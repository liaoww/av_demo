package com.liaoww.media.view.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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

    private float mFrameWidth = 256;

    private static final float DEFAULT_FRAME_WIDTH = 256;
    private float mPaintStrokeWidth = 4f;
    private int mPaintColor = Color.parseColor("#ffffff");
    private float mDownEventX = 0f;
    private float mDownEventY = 0f;
    private long mDownEventTime = 0L;
    private Paint mFramePaint;
    private boolean mAnimationEnable = false;

    private ValueAnimator mFocusAnimation;

    private SparseArray<FocusListener> listeners;

    private Handler mHandler;

    volatile boolean mHandlerRunning = false;

    boolean mFaceFrameEnable = false;


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
        if (mAnimationEnable) {
            float offsetX = mDownEventX;
            float offsetY = mDownEventY;
            if ((offsetX - mFrameWidth / 2) < 0) {
                //焦点框超过了有效范围最左侧，向右偏移x坐标
                offsetX = offsetX + 0 - (offsetX - mFrameWidth / 2);
            }
            if ((offsetY - mFrameWidth / 2) < 0) {
                //焦点框超过了有效范围最上侧，向下偏移y坐标
                offsetY = offsetY + 0 - (offsetY - mFrameWidth / 2);
            }

            if ((offsetX + mFrameWidth / 2) > mEffectiveWidth) {
                //焦点框超过了有效范围最右侧，向左偏移x坐标
                offsetX = offsetX - ((offsetX + mFrameWidth / 2) - mEffectiveWidth);
            }

            if ((offsetY + mFrameWidth / 2) > mEffectiveHeight) {
                //焦点框超过了有效范围最下侧，向上偏移y坐标
                offsetY = offsetY - ((offsetY + mFrameWidth / 2) - mEffectiveHeight);
            }
            canvas.drawRoundRect(
                    offsetX - mFrameWidth / 2,
                    offsetY - mFrameWidth / 2,
                    offsetX + mFrameWidth / 2,
                    offsetY + mFrameWidth / 2,
                    8,
                    8,
                    mFramePaint);
        }else if(mFaceFrameEnable){
            canvas.drawRect(faceLeft, faceTop, faceRight, faceBottom, mFramePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownEventX = event.getX();
                mDownEventY = event.getY();
                mDownEventTime = SystemClock.currentThreadTimeMillis();
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                if (isClick(event.getX(), event.getY())) {
                    doFrameAnimation();
                    notifyTouchFocusListener(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_CANCEL:
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


    float faceLeft, faceTop, faceRight, faceBottom;
    float lastFaceLeft, lastFaceTop, lastFaceRight, lastFaceBottom;

    public void updateFaceRect(float left, float top, float right, float bottom, RectF sensorRect) {
        mFaceFrameEnable = true;
        faceLeft = getWidth() * (left / sensorRect.bottom);
        faceTop = getHeight() * (top / sensorRect.right);
        faceRight = getWidth() * (right / sensorRect.bottom);
        faceBottom = getHeight() * (bottom / sensorRect.right);
        postInvalidate();
        doFaceTimeout();
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


    private void init() {
        mFramePaint = new Paint();
        mFramePaint.setColor(mPaintColor);
        mFramePaint.setStrokeWidth(mPaintStrokeWidth);
        mFramePaint.setStyle(Paint.Style.STROKE);
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
            }
        };
    }

    private ValueAnimator setUpFocusAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_WIDTH * 0.7f, DEFAULT_FRAME_WIDTH);
        animator.setDuration(500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            mFrameWidth = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mAnimationEnable = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mAnimationEnable = false;
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