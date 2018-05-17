package org.sralab.emgimu.mve;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import org.sralab.emgimu.config.R;

/**
 * TODO: document your custom view class.
 */
public class EmgPowerView extends View {
    private final String TAG = EmgPowerView.class.getSimpleName();

    private TextPaint mTextPaint;
    private int mTextColor;
    private float mTextHeight, mTextWidth;
    private float mTextDimension = 50; // TODO: use a default from R.dimen...

    private int mBarColor; // defaults to R.color.orange below but can be stylelized
    private Paint mBarPaint;
    private int mLineColor; // defaults to R.color.red below but can be stylelized
    private float mLineWidth = 10; // TODO: use a default from R.dimen...
    private Paint mLinePaint;

    public EmgPowerView(Context context) {
        super(context);
        init(null, 0);
    }

    public EmgPowerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public EmgPowerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private double mMin = 100;
    private double mMax = 4900;
    private double mPwr = 2500;
    private final double mMaxHeightPwr = 5000; // The range of the graph
    public void setMinPower(double p)
    {
        mMin = p;
    }
    public void setMaxPower(double p)
    {
        mMax = p;
    }
    public void setCurrentPower(double p)
    {
        mPwr = p;
    }

    /**
     * Converts an EMG power into a screen height, where the height
     * coordinates increase down.
     */
    private int pwrToHeight(double p) {
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int contentHeight = getHeight() - paddingTop - paddingBottom;

        double pwrFrac = Math.max(Math.min(p / mMaxHeightPwr, 1), 0);
        return (int) (contentHeight * (1-pwrFrac) + paddingTop);
    }

    private double heightToPwr(double h) {
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int contentHeight = getHeight() - paddingTop - paddingBottom;

        return mMaxHeightPwr * (1 - (h - paddingTop) / contentHeight);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.EmgPowerView, defStyle, 0);

        // TODO: get pulling in SRALab colors working
        mBarColor = a.getColor(
                R.styleable.EmgPowerView_barColor,
                ContextCompat.getColor(getContext(), R.color.orange));

        mLineColor = a.getColor(
                R.styleable.EmgPowerView_lineColor,
                ContextCompat.getColor(getContext(), R.color.light_orange));

        mLineWidth = a.getDimension(
                R.styleable.EmgPowerView_lineWidth,
                mLineWidth);

        mTextDimension = a.getDimension(
                R.styleable.EmgPowerView_textDimension,
                mTextDimension);

        mTextColor = a.getColor(
                R.styleable.EmgPowerView_textColor,
                ContextCompat.getColor(getContext(), R.color.light_orange));

        /*if (a.hasValue(R.styleable.EmgPowerView_exampleDrawable)) {
            mExampleDrawable = a.getDrawable(
                    R.styleable.EmgPowerView_exampleDrawable);
            mExampleDrawable.setCallback(this);
        }*/

        a.recycle();

        mBarPaint = new Paint();
        mBarPaint.setColor(mBarColor);
        mLinePaint = new Paint();
        mLinePaint.setColor(mLineColor);
        mLinePaint.setStrokeWidth(mLineWidth);

        // Set up a default TextPaint object
        mTextPaint = new TextPaint();
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements();

        mDetector = new GestureDetector(this.getContext(), new mListener());
    }

    private String maxDescribeString() {
        return Integer.toString((int) mMax);
    }
    private void invalidateTextPaintAndMeasurements() {
        mBarPaint.setColor(mBarColor);
        mLinePaint.setColor(mLineColor);

        mTextPaint.setTextSize(mTextDimension);
        mTextPaint.setColor(mTextColor);
        mTextWidth = mTextPaint.measureText(maxDescribeString());

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();

        int contentWidth = getWidth() - paddingLeft - paddingRight;

        int lineLeft = paddingLeft;
        int lineRight = (int) (paddingLeft + contentWidth * 0.45);
        int barLeft = (int) (paddingLeft + contentWidth * 0.5);
        int barRight = paddingLeft + contentWidth;

        Rect rectangle = new Rect(barLeft, pwrToHeight(0),
                barRight, pwrToHeight(mPwr));
        canvas.drawRect(rectangle, mBarPaint);

        int minHeight = pwrToHeight(mMin);
        canvas.drawLine(lineLeft, minHeight, lineRight, minHeight, mLinePaint);

        int maxHeight = pwrToHeight(mMax);
        canvas.drawLine(lineLeft, maxHeight, lineRight, maxHeight, mLinePaint);

        // Draw the text.
        float stringHeight = maxHeight - mTextHeight;
        if (maxHeight < mTextDimension * 2) {
            // if bar close to top of screen, move text below it
            stringHeight = maxHeight + mTextDimension;
        }
        canvas.drawText(maxDescribeString(),
                (lineLeft + lineRight) / 2,
                stringHeight,
                mTextPaint);
    }

    private boolean mAllowInput = false;
    public void enableInput(boolean allow) {
        mAllowInput = allow;
    }

    private GestureDetector mDetector;
    class mListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            if (mAllowInput == false)
                return false;

            // Ignore gestures on the bar graph side of screen
            if (e.getX() > getWidth() / 2)
                return false;

            // Ignore gestures if they do not start near the current bar
            if (Math.abs(e.getY() - pwrToHeight(mMax)) > 100)
                return false;
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            //Log.d(TAG, "onScroll: " + e1 + " " + e2 + " " + distanceX + " " + distanceY);

            double h = e2.getY();
            double p = heightToPwr(h);
            mMax = p;
            invalidate();
            return true;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = mDetector.onTouchEvent(event);
        if (!result) {
           if (event.getAction() == MotionEvent.ACTION_UP) {
               Log.d(TAG, "Valid scroll completed.");
               // TODO: need to have a callback listener that can catch this to notify controller
           } else {
               Log.d(TAG, "onTouchEvent not handled: " + event);
           }
        }
        return result;
    }
}
