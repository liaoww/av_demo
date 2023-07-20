package com.liaoww.media.view.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

public class FocusView extends View {
    private int mEffectiveWidth = -1;

    private int mEffectiveHeight = -1;

    private float mEffectiveLeft = -1;
    private float mEffectiveTop = -1;
    private float mEffectiveRight = -1;
    private float mEffectiveBottom = -1;

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
            if ((offsetX - mFrameWidth / 2) < mEffectiveLeft) {
                //焦点框超过了有效范围最左侧，向右偏移x坐标
                offsetX = offsetX + mEffectiveLeft - (offsetX - mFrameWidth / 2);
            }
            if ((offsetY - mFrameWidth / 2) < mEffectiveTop) {
                //焦点框超过了有效范围最上侧，向下偏移y坐标
                offsetY = offsetY + mEffectiveTop - (offsetY - mFrameWidth / 2);
            }

            if ((offsetX + mFrameWidth / 2) > mEffectiveRight) {
                //焦点框超过了有效范围最右侧，向左偏移x坐标
                offsetX = offsetX - ((offsetX + mFrameWidth / 2) - mEffectiveRight);
            }

            if ((offsetY + mFrameWidth / 2) > mEffectiveBottom) {
                //焦点框超过了有效范围最下侧，向上偏移y坐标
                offsetY = offsetY - ((offsetY + mFrameWidth / 2) - mEffectiveBottom);
            }
            canvas.drawRoundRect(
                    offsetX - mFrameWidth / 2,
                    offsetY - mFrameWidth / 2,
                    offsetX + mFrameWidth / 2,
                    offsetY + mFrameWidth / 2,
                    8,
                    8,
                    mFramePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isInEffectiveArea(event.getX(), event.getY())) {
                    mDownEventX = event.getX();
                    mDownEventY = event.getY();
                    mDownEventTime = SystemClock.currentThreadTimeMillis();
                }
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
        Log.d("liaoww" , "updateEffectiveArea " + "w : " + getWidth() + "--- h : " + getHeight());
        mEffectiveWidth = width;
        mEffectiveHeight = height;
        mEffectiveLeft = (getWidth() - mEffectiveWidth) / 2f;
        mEffectiveTop = (getHeight() - mEffectiveHeight) / 2f;
        mEffectiveRight = getWidth() - ((getWidth() - mEffectiveWidth) / 2f);
        mEffectiveBottom = getHeight() - ((getHeight() - mEffectiveHeight) / 2f);
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
    }

    private ValueAnimator setUpFocusAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_WIDTH * 0.7f, DEFAULT_FRAME_WIDTH);
        animator.setDuration(500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            mFrameWidth = (float) animation.getAnimatedValue();
            invalidate();
        });
        return animator;
    }

    private void doFrameAnimation() {
        mAnimationEnable = true;
        if (mFocusAnimation == null) {
            mFocusAnimation = setUpFocusAnimation();
        }
        mFocusAnimation.start();
    }

    private boolean isClick(float x, float y) {
        //点击偏移不超过10像素且间隔小于100毫秒，判定为点击
        return (Math.abs(x - mDownEventX) < 10 && Math.abs(y - mDownEventY) < 10 && (SystemClock.currentThreadTimeMillis() - mDownEventTime) < 100);
    }

    private boolean isInEffectiveArea(float x, float y) {
        //判断点击的是否是画面有效区域
        return (x > mEffectiveLeft) &&
                (y > mEffectiveTop) &&
                (x < mEffectiveRight) &&
                (y < mEffectiveBottom);
    }
}