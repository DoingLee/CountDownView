package com.doing.countdowntimer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Doing on 2016/8/11 0011.
 *
 *  绘图：
 * 1、画大外圆
 * 2、画小内圆
 * 3、画进度环（当前剩余的总秒数/倒计时设置的总秒数 = 进度环度数/360度）
 * 4、画倒计时显示（剩余“分钟:时间”）
 * 5、倒计时实现：使用Timer逐秒计时，invalidate()更新界面
 * 6、倒计时结束，调用回调接口执行回调动作
 */
public class CountDownView extends View
{
    private int mTotalSeconds; //倒计时设置的总秒数
    private int mCurSeconds;   //当前剩余的总秒数

    private int mTotalMin;  //总分钟
    private int mTotalSec;  //总秒数
    private int mCurMin;    //显示的当前剩余分钟
    private int mCurSec;    //显示的当前剩余秒数

    private int mCurRingDegree; //倒计时环的度数

    private int mMaxCircleRadius; //外圆半径 （与XML设置的radius对应）
    private int mRingThickness; //环厚度 （与XML设置的ring_thickness对应）
    private int mRingInterval; //环与内外圆之间的间隙
    private int mMinCircleRadius; //内圆半径
    private int mCountTextSize;

    private int mMaxCircleColor; //外圆背景颜色
    private int mMinCircleColor; //内圆半径颜色
    private int mRingColor;  //环进度条颜色
    private int mCountTextColor; //

    private Paint mMaxCirclePaint;
    private Paint mMinCirclePaint;
    private Paint mRingPaint;
    private Paint mTextPaint;

    private static int UPDATE_VIEW = 100; //1s计时完成
    private static int COUNT_DOWN_FINISH = 101; //倒计时结束
    private Handler mViewHandler;
    private OnCountDownFinishListener mOnCountDownFinishListener;

    private RectF mRingRectF; //用于装载圆环的矩形

    private int mViewWidth;
    private int mViewHeight;
    private int mViewCenterX;
    private int mViewCenterY;

    public CountDownView(Context context) {
        this(context, null);
    }

    public CountDownView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CountDownView(Context context, AttributeSet attrs, int defStyleAttr){
        super(context,attrs,defStyleAttr);

        //自定义属性
        TypedArray attr = context.obtainStyledAttributes(attrs, R.styleable.CountDownView);
        mTotalSeconds = attr.getInt(R.styleable.CountDownView_seconds,5); //没有设置就默认1min * 60 = 60s

        float ringThickness = attr.getDimension(R.styleable.CountDownView_ring_thickness, context.getResources().getDimension(R.dimen.ringThickness));
        float maxCircleRadio = attr.getDimension(R.styleable.CountDownView_radius, context.getResources().getDimension(R.dimen.maxCircleRadio));
        int ringColor = attr.getColor(R.styleable.CountDownView_ring_color, context.getResources().getColor(R.color.green));
        float countTextSize = attr.getDimension(R.styleable.CountDownView_count_text_size, context.getResources().getDimension(R.dimen.countTextSize));
        int countTextColor = attr.getColor(R.styleable.CountDownView_count_text_color, context.getResources().getColor(R.color.green));
        // dp to px
        mRingThickness = DisplayUtils.dip2px(getContext(),ringThickness);
        mMaxCircleRadius = DisplayUtils.dip2px(getContext(), maxCircleRadio);
        mRingColor = DisplayUtils.dip2px(getContext(), ringColor);
        mCountTextSize = DisplayUtils.sp2px(getContext(), countTextSize);
        mCountTextColor = DisplayUtils.sp2px(getContext(), countTextColor);
        attr.recycle();

        init();
    }

    private void init(){
        //各种参数设置
        mCurSeconds = mTotalSeconds;
        mTotalMin = mTotalSeconds / 60;
        mTotalSec = mTotalSeconds % 60;
        mCurMin = mTotalMin;
        mCurSec = mTotalSec;
        mCurRingDegree = 360;

        mRingInterval = mRingThickness / 4;
        mMinCircleRadius = mMaxCircleRadius - mRingThickness - 2*mRingInterval;
        mMaxCircleColor = Color.GRAY;
        mMinCircleColor = Color.WHITE;

        mMaxCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMinCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mViewHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                if(msg.what == UPDATE_VIEW){
                    invalidate();  //更新界面
                }
                else if(msg.what == COUNT_DOWN_FINISH){
                    if(mOnCountDownFinishListener != null)
                        mOnCountDownFinishListener.onCountDownFinish(); //执行回调方法
                }
            }
        };
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //处理wrap_content：添加wrap_content默认值（这里是mMaxCircleRadio*2）
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        if (widthSpecMode == MeasureSpec.AT_MOST
                && heightSpecMode == MeasureSpec.AT_MOST) {
            setMeasuredDimension(mMaxCircleRadius*2, mMaxCircleRadius*2);
        } else if (widthSpecMode == MeasureSpec.AT_MOST) {
            setMeasuredDimension(mMaxCircleRadius*2, heightSpecSize);
        } else if (heightSpecMode == MeasureSpec.AT_MOST) {
            setMeasuredDimension(widthSpecSize, mMaxCircleRadius*2);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        mViewWidth = getMeasuredWidth();
        mViewHeight = getMeasuredHeight();
        mViewCenterX = mViewWidth/ 2;
        mViewCenterY = mViewHeight / 2;

        mRingRectF = new RectF(mViewCenterX - mMinCircleRadius - mRingInterval - mRingThickness/2,
                mViewCenterY - mMinCircleRadius - mRingInterval - mRingThickness/2,
                mViewCenterX + mMinCircleRadius + mRingInterval + mRingThickness/2,
                mViewCenterY + mMinCircleRadius + mRingInterval + mRingThickness/2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //处理padding（使xml中padding参数有效）
//        final int paddingLeft = getPaddingLeft();
//        final int paddingRight = getPaddingRight();
//        final int paddingTop = getPaddingTop();
//        final int paddingBottom = getPaddingBottom();
//        int width = getWidth() - paddingLeft - paddingRight;
//        int height = getHeight() - paddingTop - paddingBottom;

        drawMaxCircle(canvas);
        drawMinCircle(canvas);
        drawRing(canvas);
        drawCountText(canvas);
    }

    private void drawMinCircle(Canvas canvas){
        mMinCirclePaint.setColor(mMinCircleColor);
        canvas.drawCircle(mViewCenterX, mViewCenterY, mMinCircleRadius, mMinCirclePaint);
    }

    private void drawMaxCircle(Canvas canvas){
        mMaxCirclePaint.setColor(mMaxCircleColor);
        canvas.drawCircle(mViewCenterX, mViewCenterY, mMaxCircleRadius, mMaxCirclePaint);
    }

    private void drawRing(Canvas canvas){
        mRingPaint.setColor(mRingColor);
        mRingPaint.setStrokeWidth(mRingThickness);
        mRingPaint.setStyle(Paint.Style.STROKE);
        canvas.drawArc(mRingRectF, 270, mCurRingDegree, false, mRingPaint);
    }

    private void drawCountText(Canvas canvas){
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(mCountTextColor);
        mTextPaint.setTextSize(mCountTextSize);
        String text = mCurMin + ":" + mCurSec;
        canvas.drawText(text, mViewCenterX, mViewCenterY, mTextPaint);
    }

    /************************************* 公开调用设置方法  ******************************************/

    public void setCountSeconds(int seconds){
        mTotalSeconds = seconds;
        mCurSeconds = mTotalSeconds;
        mTotalMin = seconds / 60;
        mTotalSec = seconds % 60;
        mCurMin = mTotalMin;
        mCurSec = mTotalSec;
    }

    /**
     *
     * @param radius  外圆半径（dp）
     */
    public void setRadio(int radius){
        mMaxCircleRadius = DisplayUtils.dip2px(getContext(),radius);
    }

    /**
     *
     * @param ringThickness 环的厚度（dp）
     */
    public void setRingThickness(int ringThickness){
        mRingThickness = DisplayUtils.dip2px(getContext(),ringThickness);
    }

    /**
     *
     * @param ringColor 环的颜色
     */
    public void setRingColor(int ringColor){
        mRingColor = ringColor;
    }

    /**
     *
     * @param color 倒计时文字的颜色
     */
    public void setCountTextColor(int color){
        mCountTextColor = color;
    }

    /**
     *
     * @param size 倒计时文字的大小
     */
    public void setCountTextSize(int size){
        mCountTextSize = DisplayUtils.sp2px(getContext(),size);
    }

    public void setOnCountDownFinishListener(OnCountDownFinishListener onCountDownFinishListener){
        mOnCountDownFinishListener = onCountDownFinishListener;
    }

    /**
     * 开始倒计时
     */
    public void startCountDown(){
        final Timer timer = new Timer();
        TimerTask task = new TimerTask(){
            public void run() {
//                每计时1000ms结束后do something
                if(mCurSeconds > 0){
                    mCurSeconds --;
                    mCurSec = mCurSeconds % 60;
                    mCurMin = mCurSeconds / 60;
                    mCurRingDegree = 360 * mCurSeconds / mTotalSeconds;
                    Message message = Message.obtain();
                    message.what = UPDATE_VIEW;
                    mViewHandler.sendMessage(message);
                }
                else{
                    //倒计时结束
                    timer.cancel();
                    Message message = Message.obtain();
                    message.what = COUNT_DOWN_FINISH;
                    mViewHandler.sendMessage(message);
                }
            }
        };
        timer.schedule(task, 0, 1000); // 1000ms循环计时
    }

    /**
     * 监听倒计时结束的回调接口
     */
    public interface OnCountDownFinishListener{
        void onCountDownFinish();
    }
}
