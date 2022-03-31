package org.sralab.emgimu.mve;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.core.content.ContextCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.sralab.emgimu.config.R;

/**
 * TODO: document your custom view class.
 */
public class EmgPowerView extends View {
    private final String TAG = EmgPowerView.class.getSimpleName();

    private TextPaint mTextPaint;
    private int mTextColor;
    private float mTextHeight;
    private float mTextDimension = 50; // TODO: use a default from R.dimen...

    private int mBarColor; // defaults to R.color.orange below but can be stylelized
    private Paint mBarPaint;
    private int mLineColor; // defaults to R.color.red below but can be stylelized
    private float mLineWidth = 10; // TODO: use a default from R.dimen...
    private Paint mLinePaint;

    private Rect rectangle;

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

    private float mMin = 0;
    private float mMax = 0;
    private float mPwr = 0;
    private float mThresh = 0;
    private float mMaxHeightPwr = Short.MAX_VALUE * 2; // The range of the graph
    public void setMinPower(Double p)
    {
        mMin = p.floatValue();
    }
    public void setMaxPower(Double p)
    {
        mMax = p.floatValue();
    }
    public void setCurrentPower(Double p)
    {
        mPwr = p.floatValue();
        //Log.d(TAG, "power = " + mPwr);
        invalidate();
    }
    public float getThreshold() { return mThresh; }
    public void setThreshold(float thresh) { mThresh = thresh; }
    public void setMaxRange(float maxPwr) { mMaxHeightPwr = maxPwr; }

    /**
     * Converts an EMG power into a screen height, where the height
     * coordinates increase down.
     */
    private int pwrToHeight(float p) {
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int contentHeight = getHeight() - paddingTop - paddingBottom;

        float pwrFrac = Math.max(Math.min(p / mMaxHeightPwr, 1f), 0f);
        return (int) (contentHeight * (1-pwrFrac) + paddingTop);
    }

    private float heightToPwr(float h) {
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int contentHeight = getHeight() - paddingTop - paddingBottom;

        return mMaxHeightPwr * (1f - (h - paddingTop) / contentHeight);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.EmgPowerView, defStyle, 0);

        // TODO: get pulling in SRALab colors working
        mBarColor = a.getColor(
                R.styleable.EmgPowerView_barColor,
                ContextCompat.getColor(getContext(), R.color.sral_red));

        mLineColor = a.getColor(
                R.styleable.EmgPowerView_lineColor,
                ContextCompat.getColor(getContext(), R.color.sral_orange));

        mLineWidth = a.getDimension(
                R.styleable.EmgPowerView_lineWidth,
                mLineWidth);

        mTextDimension = a.getDimension(
                R.styleable.EmgPowerView_textDimension,
                mTextDimension);

        mTextColor = a.getColor(
                R.styleable.EmgPowerView_textColor,
                ContextCompat.getColor(getContext(), R.color.sral_orange));

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

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int barLeft = (int) (paddingLeft + contentWidth * 0.36);
        int barRight = (int) (paddingLeft + contentWidth * 0.63);

        //rectangle = new Rect(barLeft, pwrToHeight(0),
        //        barRight, pwrToHeight(0));
        rectangle = new Rect(barLeft, pwrToHeight(2000), barRight, pwrToHeight(0));

        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements();

        mDetector = new GestureDetector(this.getContext(), new mListener());

    }

    private String maxDescribeString() {
        return Integer.toString((int) mMax);
    }

    private String minDescribeString() {
        return Integer.toString((int) mMin);
    }

    private String threshDescribeString() {
        return Integer.toString((int) mThresh);
    }

    private void invalidateTextPaintAndMeasurements() {
        mBarPaint.setColor(mBarColor);
        mLinePaint.setColor(mLineColor);

        mTextPaint.setTextSize(mTextDimension);
        mTextPaint.setColor(mTextColor);
        float mTextWidth = mTextPaint.measureText(maxDescribeString());

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
        int lineRight = (int) (paddingLeft + contentWidth * 0.3);
        int threshLeft = (int) (paddingLeft + contentWidth * 0.69);
        int threshRight = (int) (paddingLeft + contentWidth * 1.0);
        int barLeft = (int) (paddingLeft + contentWidth * 0.36);
        int barRight = (int) (paddingLeft + contentWidth * 0.63);

        rectangle.set(barLeft, pwrToHeight(mPwr), barRight, pwrToHeight(0));
        canvas.drawRect(rectangle, mBarPaint);

        int minHeight = pwrToHeight(mMin);
        canvas.drawLine(lineLeft, minHeight, lineRight, minHeight, mLinePaint);

        int maxHeight = pwrToHeight(mMax);
        canvas.drawLine(lineLeft, maxHeight, lineRight, maxHeight, mLinePaint);

        int threshHeight = pwrToHeight(mThresh);
        canvas.drawLine(threshLeft, threshHeight, threshRight, threshHeight, mLinePaint);

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

        stringHeight = minHeight + mTextDimension;
        /*if (maxHeight < mTextDimension * 2) {
            // if bar close to top of screen, move text below it
            stringHeight = maxHeight + mTextDimension;
        };*/
        canvas.drawText(minDescribeString(),
                (lineLeft + lineRight) / 2,
                stringHeight,
                mTextPaint);

        stringHeight = threshHeight + mTextDimension;
        canvas.drawText(threshDescribeString(),
                (threshLeft + threshRight) / 2,
                stringHeight,
                mTextPaint);

    }

    private boolean mAllowInput = true;
    public void enableInput(boolean allow) {
        mAllowInput = allow;
    }

    private GestureDetector mDetector;
    class mListener extends GestureDetector.SimpleOnGestureListener {

        // Track which side we are scrolling
        private int scrolling = 0;

        @Override
        public boolean onDown(MotionEvent e) {

            scrolling = 0;

            if (mAllowInput == false)
                return false;

            // Left side swipes are for current power
            if (e.getX() < getWidth() / 3) {

                // Ignore gestures if they do not start near the current bar
                if (Math.abs(e.getY() - pwrToHeight(mMax)) > 100)
                    return false;

                scrolling = 1;
            }


            // Right side swipes are for current power threshold
            if (e.getX() > getWidth() * 2 / 3) {

                // Ignore gestures if they do not start near the current bar
                if (Math.abs(e.getY() - pwrToHeight(mThresh)) > 100)
                    return false;

                scrolling = 2;
            }


            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            //Log.d(TAG, "onScroll: " + e1 + " " + e2 + " " + distanceX + " " + distanceY);

            float h = e2.getY();
            float p = heightToPwr(h);

            if (scrolling == 1) {
                mMax = p;
            } else if (scrolling == 2) {
                mThresh = p;
            }

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
               if (mListener != null) {
                   mListener.onMaxChanged(mMax);
               }
           } else {
               Log.d(TAG, "onTouchEvent not handled: " + event);
           }
        }
        return result;
    }

    OnMaxChangedEventListener mListener;

    public interface OnMaxChangedEventListener {
        void onMaxChanged(float newMax);
    }

    public void setOnMaxChangedEventListener(OnMaxChangedEventListener maxChangeEventListener) {
        mListener = maxChangeEventListener;
    }

}
