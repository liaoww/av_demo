package com.liaoww.media.view.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 计时器view
 */
public class TimerView extends View {
    private static final int DEFAULT_CIRCLE_PAINT_COLOR = Color.parseColor("#ff0000");
    private static final float DEFAULT_CIRCLE_RADIUS = 14f;

    private static final int DEFAULT_TEXT_PAINT_COLOR = Color.parseColor("#ffffff");
    private static final int DEFAULT_TEXT_STROKE_PAINT_COLOR = Color.parseColor("#DCDCDC");
    private static final float DEFAULT_TEXT_PAINT_WIDTH = 2f;
    private static final float DEFAULT_TEXT_PAINT_SIZE = 100f;
    private static final int DEFAULT_TICK_INTERVAL = 500;//时钟闪烁间隔


    private Paint mCirclePaint;
    private Paint mTextPaint;
    private Paint mTextStrokePaint;

    private String mText;

    private SimpleDateFormat mFormat;

    private volatile boolean mTicking = false;

    private long mCurrentTime = 0L;

    private Date mDate;

    private Handler mHandler;

    public TimerView(Context context) {
        super(context);
        init();
    }

    public TimerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void start() {
        if (mTicking) {
            return;
        }
        if (mDate == null) {
            mDate = new Date();
        }
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        mText = "00:00";
        mTicking = true;
        mCurrentTime = 0L;
        mHandler.post(mTicker);
    }

    public void stop() {
        mTicking = false;
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    private void init() {
        mCirclePaint = new Paint();
        mCirclePaint.setColor(DEFAULT_CIRCLE_PAINT_COLOR);
        mCirclePaint.setStyle(Paint.Style.FILL);
        mCirclePaint.setAntiAlias(true);

        mTextPaint = new Paint();
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(DEFAULT_TEXT_PAINT_COLOR);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(DEFAULT_TEXT_PAINT_SIZE);
        mTextPaint.setAntiAlias(true);

        mTextStrokePaint = new Paint();
        mTextStrokePaint.setStyle(Paint.Style.STROKE);
        mTextStrokePaint.setColor(DEFAULT_TEXT_STROKE_PAINT_COLOR);
        mTextStrokePaint.setStrokeWidth(DEFAULT_TEXT_PAINT_WIDTH);
        mTextStrokePaint.setTextAlign(Paint.Align.CENTER);
        mTextStrokePaint.setTextSize(DEFAULT_TEXT_PAINT_SIZE);
        mTextStrokePaint.setAntiAlias(true);

        mFormat = new SimpleDateFormat("mm:ss");
        mHandler = new Handler(Looper.getMainLooper());
    }

    private final Runnable mTicker = new Runnable() {
        public void run() {
            if (!mTicking) {
                return;
            }
            mDate.setTime(mCurrentTime);
            mText = mFormat.format(mDate);
            mCurrentTime += DEFAULT_TICK_INTERVAL;
            postInvalidate();

            //每隔500毫秒 红点闪烁
            if (mHandler != null) {
                long now = SystemClock.uptimeMillis();
                long next = now + (DEFAULT_TICK_INTERVAL - now % DEFAULT_TICK_INTERVAL);
                mHandler.postAtTime(mTicker, next);
            }
        }
    };


    @Override
    protected void onDraw(Canvas canvas) {
        if (mText == null || mText.length() <= 0) {
            return;
        }
        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        float yDistance = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;
        float textWidth = getTextWidth(mTextPaint, mText);

        if ((mCurrentTime / DEFAULT_TICK_INTERVAL) % 2 == 1) {
            //红点闪烁 下一个整数秒时显示
            canvas.drawCircle(getWidth() / 2f - textWidth / 2 - DEFAULT_CIRCLE_RADIUS * 2, getHeight() / 2f, DEFAULT_CIRCLE_RADIUS, mCirclePaint);
        }
        canvas.drawText(mText, getWidth() / 2f, getHeight() / 2f + yDistance, mTextPaint);
        canvas.drawText(mText, getWidth() / 2f, getHeight() / 2f + yDistance, mTextStrokePaint);
    }

    private int getTextWidth(Paint paint, String str) {
        int iRet = 0;
        if (str != null && str.length() > 0) {
            int len = str.length();
            float[] widths = new float[len];
            paint.getTextWidths(str, widths);
            for (int j = 0; j < len; j++) {
                iRet += (int) Math.ceil(widths[j]);
            }
        }
        return iRet;
    }
}
