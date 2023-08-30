package com.liaoww.media.view.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

public class CropImageView extends androidx.appcompat.widget.AppCompatImageView {
    private static final int DEFAULT_FRAME_COLOR = Color.parseColor("#FFFFFF");
    private static final int DEFAULT_FRAME_CORNER_COLOR = Color.parseColor("#FFFFFF");
    private static final float DEFAULT_FRAME_PAINT_WIDTH = 10f;
    private static final float DEFAULT_FRAME_TOUCH_OFFSET = 48f;
    private static final int DEFAULT_FRAME_TOUCH_TOP = 1;//触摸上边框
    private static final int DEFAULT_FRAME_TOUCH_BOTTOM = 2;//触摸下边框
    private static final int DEFAULT_FRAME_TOUCH_LEFT = 3;//触摸左边框
    private static final int DEFAULT_FRAME_TOUCH_RIGHT = 4;//触摸右边框
    private static final int DEFAULT_FRAME_TOUCH_TOP_LEFT = 5;//触摸左上角
    private static final int DEFAULT_FRAME_TOUCH_TOP_RIGHT = 6;//触摸右上角
    private static final int DEFAULT_FRAME_TOUCH_BOTTOM_LEFT = 7;//触摸左下角
    private static final int DEFAULT_FRAME_TOUCH_BOTTOM_RIGHT = 8;//触摸右下角

    private Paint mFramePaint;
    private Paint mFrameCornerCirclePaint;

    private int mDefaultFrameWidth;
    private int mDefaultFrameHeight;
    private float mDownX = -1f;
    private float mDownY = -1f;
    private float mTopMax = -1f;
    private float mBottomMax = -1f;
    private float mLeftMax = -1f;
    private float mRightMax = -1f;

    private int mCurrentState = -1;

    private final RectF mRect = new RectF();
    private final RectF mFixRect = new RectF();
    private final Matrix matrix = new Matrix();


    public CropImageView(Context context) {
        super(context);
        init();
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public RectF getFixRect() {
        mFixRect.set(mRect);

        //(0,0)坐标点从view左上角平移到图片的左上角位置
        mFixRect.offset(-(getWidth() - mDefaultFrameWidth) / 2f, -(getHeight() - mDefaultFrameHeight) / 2f);

        //旋转
        if (getRotation() != 0) {
            Log.e("liaoww" , "rotation : " + getRotation());
            Log.e("liaoww" , "mFixRect  1 : " + mFixRect.toShortString());
            matrix.reset();
            matrix.setRotate(getRotation(), mDefaultFrameWidth / 2f, mDefaultFrameHeight / 2f);
            matrix.mapRect(mFixRect, mFixRect);
            Log.e("liaoww" , "mFixRect  2 : " + mFixRect.toShortString());
        }

        //镜像
        if (getRotationY() == 180) {
            matrix.reset();
            matrix.setScale(-1f, 1f, mDefaultFrameWidth / 2f, mDefaultFrameHeight / 2f);
            matrix.mapRect(mFixRect, mFixRect);
        }
        return mFixRect;
    }

    public int getAreaWidth() {
        return mDefaultFrameWidth;
    }

    public int getAreaHeight() {
        return mDefaultFrameHeight;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mRect != null) {
            canvas.drawRect(mRect.left, mRect.top, mRect.right, mRect.bottom, mFramePaint);
            if (mCurrentState == DEFAULT_FRAME_TOUCH_TOP) {
                canvas.drawCircle(mRect.left, mRect.top, 20f, mFrameCornerCirclePaint);
                canvas.drawCircle(mRect.right, mRect.top, 20f, mFrameCornerCirclePaint);
            } else if (mCurrentState == DEFAULT_FRAME_TOUCH_LEFT) {
                canvas.drawCircle(mRect.left, mRect.top, 20f, mFrameCornerCirclePaint);
                canvas.drawCircle(mRect.left, mRect.bottom, 20f, mFrameCornerCirclePaint);
            } else if (mCurrentState == DEFAULT_FRAME_TOUCH_RIGHT) {
                canvas.drawCircle(mRect.right, mRect.top, 20f, mFrameCornerCirclePaint);
                canvas.drawCircle(mRect.right, mRect.bottom, 20f, mFrameCornerCirclePaint);
            } else if (mCurrentState == DEFAULT_FRAME_TOUCH_BOTTOM) {
                canvas.drawCircle(mRect.left, mRect.bottom, 20f, mFrameCornerCirclePaint);
                canvas.drawCircle(mRect.right, mRect.bottom, 20f, mFrameCornerCirclePaint);
            } else if (mCurrentState == DEFAULT_FRAME_TOUCH_TOP_LEFT
                    || mCurrentState == DEFAULT_FRAME_TOUCH_TOP_RIGHT
                    || mCurrentState == DEFAULT_FRAME_TOUCH_BOTTOM_LEFT
                    || mCurrentState == DEFAULT_FRAME_TOUCH_BOTTOM_RIGHT) {
                canvas.drawCircle(mRect.left, mRect.top, 20f, mFrameCornerCirclePaint);
                canvas.drawCircle(mRect.right, mRect.top, 20f, mFrameCornerCirclePaint);
                canvas.drawCircle(mRect.left, mRect.bottom, 20f, mFrameCornerCirclePaint);
                canvas.drawCircle(mRect.right, mRect.bottom, 20f, mFrameCornerCirclePaint);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //单指
                float x = event.getX();
                float y = event.getY();
                if (x >= mRect.left - DEFAULT_FRAME_TOUCH_OFFSET && x <= mRect.left + DEFAULT_FRAME_TOUCH_OFFSET && y >= mRect.top - DEFAULT_FRAME_TOUCH_OFFSET && y <= mRect.top + DEFAULT_FRAME_TOUCH_OFFSET) {
                    //左上
                    mCurrentState = DEFAULT_FRAME_TOUCH_TOP_LEFT;
                    mDownX = event.getX();
                    mDownY = event.getY();
                } else if (x >= mRect.right - DEFAULT_FRAME_TOUCH_OFFSET && x <= mRect.right + DEFAULT_FRAME_TOUCH_OFFSET && y >= mRect.top - DEFAULT_FRAME_TOUCH_OFFSET && y <= mRect.top + DEFAULT_FRAME_TOUCH_OFFSET) {
                    //右上
                    mCurrentState = DEFAULT_FRAME_TOUCH_TOP_RIGHT;
                    mDownX = event.getX();
                    mDownY = event.getY();
                } else if (x >= mRect.left - DEFAULT_FRAME_TOUCH_OFFSET && x <= mRect.left + DEFAULT_FRAME_TOUCH_OFFSET && y <= mRect.bottom + DEFAULT_FRAME_TOUCH_OFFSET && y >= mRect.bottom - DEFAULT_FRAME_TOUCH_OFFSET) {
                    //左下
                    mCurrentState = DEFAULT_FRAME_TOUCH_BOTTOM_LEFT;
                    mDownX = event.getX();
                    mDownY = event.getY();
                } else if (x >= mRect.right - DEFAULT_FRAME_TOUCH_OFFSET && x <= mRect.right + DEFAULT_FRAME_TOUCH_OFFSET && y <= mRect.bottom + DEFAULT_FRAME_TOUCH_OFFSET && y >= mRect.bottom - DEFAULT_FRAME_TOUCH_OFFSET) {
                    //右下
                    mCurrentState = DEFAULT_FRAME_TOUCH_BOTTOM_RIGHT;
                    mDownX = event.getX();
                    mDownY = event.getY();
                } else if (x >= mRect.left && x <= mRect.right && y >= mRect.top - DEFAULT_FRAME_TOUCH_OFFSET && y <= mRect.top + DEFAULT_FRAME_TOUCH_OFFSET) {
                    //上边框
                    mCurrentState = DEFAULT_FRAME_TOUCH_TOP;
                    mDownY = event.getY();
                } else if (x >= mRect.left && x <= mRect.right && y >= mRect.bottom - DEFAULT_FRAME_TOUCH_OFFSET && y <= mRect.bottom + DEFAULT_FRAME_TOUCH_OFFSET) {
                    //下边框
                    mCurrentState = DEFAULT_FRAME_TOUCH_BOTTOM;
                    mDownY = event.getY();
                } else if (x >= mRect.left - DEFAULT_FRAME_TOUCH_OFFSET && x < mRect.left + DEFAULT_FRAME_TOUCH_OFFSET && y >= mRect.top && y <= mRect.bottom) {
                    //左边框
                    mCurrentState = DEFAULT_FRAME_TOUCH_LEFT;
                    mDownX = event.getX();
                } else if (x >= mRect.right - DEFAULT_FRAME_TOUCH_OFFSET && x < mRect.right + DEFAULT_FRAME_TOUCH_OFFSET && y >= mRect.top && y <= mRect.bottom) {
                    //右边框
                    mCurrentState = DEFAULT_FRAME_TOUCH_RIGHT;
                    mDownX = event.getX();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mDownX >= 0 || mDownY >= 0) {
                    switch (mCurrentState) {
                        case DEFAULT_FRAME_TOUCH_TOP:
                            if (mRect.bottom - Math.max(mTopMax, mRect.top + event.getY() - mDownY) >= getWidth() / 4f) {
                                //top和bottom之间间隔最小值限定到屏幕宽的1/4
                                mRect.top = Math.max(mTopMax, mRect.top + event.getY() - mDownY);
                                mDownY = event.getY();
                            }
                            break;
                        case DEFAULT_FRAME_TOUCH_BOTTOM:
                            if (Math.min(mRect.bottom + event.getY() - mDownY, mBottomMax) - mRect.top >= getWidth() / 4f) {
                                mRect.bottom = Math.min(mRect.bottom + event.getY() - mDownY, mBottomMax);
                                mDownY = event.getY();
                            }
                            break;
                        case DEFAULT_FRAME_TOUCH_LEFT:
                            //left和right之间间隔最小值限定到屏幕宽的1/4
                            if (mRect.right - Math.max(mRect.left + event.getX() - mDownX, mLeftMax) >= getWidth() / 4f) {
                                mRect.left = Math.max(mRect.left + event.getX() - mDownX, mLeftMax);
                                mDownX = event.getX();
                            }
                            break;
                        case DEFAULT_FRAME_TOUCH_RIGHT:
                            if (Math.min(mRect.right + event.getX() - mDownX, mRightMax) - mRect.left >= getWidth() / 4f) {
                                mRect.right = Math.min(mRect.right + event.getX() - mDownX, mRightMax);
                                mDownX = event.getX();
                            }
                            break;
                        case DEFAULT_FRAME_TOUCH_TOP_LEFT:
                            if (mRect.bottom - Math.max(mTopMax, mRect.top + event.getY() - mDownY) >= getWidth() / 4f) {
                                mRect.top = Math.max(mTopMax, mRect.top + event.getY() - mDownY);
                                mDownY = event.getY();
                            }
                            if (mRect.right - Math.max(mRect.left + event.getX() - mDownX, mLeftMax) >= getWidth() / 4f) {
                                mRect.left = Math.max(mRect.left + event.getX() - mDownX, mLeftMax);
                                mDownX = event.getX();
                            }
                            break;
                        case DEFAULT_FRAME_TOUCH_TOP_RIGHT:
                            if (mRect.bottom - Math.max(mTopMax, mRect.top + event.getY() - mDownY) >= getWidth() / 4f) {
                                mRect.top = Math.max(mTopMax, mRect.top + event.getY() - mDownY);
                                mDownY = event.getY();
                            }
                            if (Math.min(mRect.right + event.getX() - mDownX, mRightMax) - mRect.left >= getWidth() / 4f) {
                                mRect.right = Math.min(mRect.right + event.getX() - mDownX, mRightMax);
                                mDownX = event.getX();
                            }
                            break;
                        case DEFAULT_FRAME_TOUCH_BOTTOM_LEFT:
                            if (Math.min(mRect.bottom + event.getY() - mDownY, mBottomMax) - mRect.top >= getWidth() / 4f) {
                                mRect.bottom = Math.min(mRect.bottom + event.getY() - mDownY, mBottomMax);
                                mDownY = event.getY();
                            }
                            if (mRect.right - Math.max(mRect.left + event.getX() - mDownX, mLeftMax) >= getWidth() / 4f) {
                                mRect.left = Math.max(mRect.left + event.getX() - mDownX, mLeftMax);
                                mDownX = event.getX();
                            }
                            break;
                        case DEFAULT_FRAME_TOUCH_BOTTOM_RIGHT:
                            if (Math.min(mRect.bottom + event.getY() - mDownY, mBottomMax) - mRect.top >= getWidth() / 4f) {
                                mRect.bottom = Math.min(mRect.bottom + event.getY() - mDownY, mBottomMax);
                                mDownY = event.getY();
                            }
                            if (Math.min(mRect.right + event.getX() - mDownX, mRightMax) - mRect.left >= getWidth() / 4f) {
                                mRect.right = Math.min(mRect.right + event.getX() - mDownX, mRightMax);
                                mDownX = event.getX();
                            }
                            break;
                    }
                    postInvalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDownX = -1f;
                mDownY = -1f;
                mCurrentState = -1;
                break;
        }
        return true;
    }

    public void setFrameDefaultSize(int width, int height) {
        mDefaultFrameWidth = width;
        mDefaultFrameHeight = height;
        //设置裁剪框边界边界
        mTopMax = (getHeight() - mDefaultFrameHeight) / 2f + DEFAULT_FRAME_PAINT_WIDTH / 2;
        mBottomMax = (getHeight() + mDefaultFrameHeight) / 2f - DEFAULT_FRAME_PAINT_WIDTH / 2;
        mLeftMax = (getWidth() - mDefaultFrameWidth) / 2f + DEFAULT_FRAME_PAINT_WIDTH / 2;
        mRightMax = (getWidth() + mDefaultFrameWidth) / 2f - DEFAULT_FRAME_PAINT_WIDTH / 2;

        //默认留一点余量
        float frameWidth = (int) (mDefaultFrameWidth * 0.8);
        float frameHeight = (int) (mDefaultFrameHeight * 0.8);

        mRect.set(0, 0, frameWidth, frameHeight);
        mRect.offset((getWidth() - frameWidth) / 2f, (getHeight() - frameHeight) / 2f);
        postInvalidate();
    }

    private void init() {
        mFramePaint = new Paint();
        mFramePaint.setColor(DEFAULT_FRAME_COLOR);
        mFramePaint.setStrokeWidth(DEFAULT_FRAME_PAINT_WIDTH);
        mFramePaint.setStyle(Paint.Style.STROKE);
        mFramePaint.setAntiAlias(true);

        mFrameCornerCirclePaint = new Paint();
        mFrameCornerCirclePaint.setColor(DEFAULT_FRAME_CORNER_COLOR);
        mFrameCornerCirclePaint.setStyle(Paint.Style.FILL);
        mFrameCornerCirclePaint.setAntiAlias(true);
    }
}
