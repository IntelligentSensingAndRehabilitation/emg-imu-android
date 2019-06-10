package org.sralab.emgimu.controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class GameView extends View {

    private static final String TAG = GameView.class.getSimpleName();

    Paint paint;

    protected float output_x = 0.5f;
    protected float output_y = 0.5f;

    protected float goal_x = 0.25f;
    protected float goal_y = 0.25f;

    public GameView(Context context)
    {
        super(context);
        paint = new Paint();
    }

    public GameView(Context context, AttributeSet attrs){
        super(context, attrs);
        paint = new Paint();
    }

    private void drawCircle(Canvas canvas, String color, float x, float y) {
        int nx = (int) (x * getWidth());
        int ny = (int) (y * getHeight());
        int radius = 10;

        // Use Color.parseColor to define HTML colors
        paint.setColor(Color.parseColor(color));
        canvas.drawCircle(nx, ny, radius, paint);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawPaint(paint);

        drawCircle(canvas, "#000000", this.goal_x, this.goal_y);
        drawCircle(canvas, "#0000FF", this.output_x, this.output_y);
    }

    private int measureDimension(int desiredSize, int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            result = desiredSize;
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }

        if (result < desiredSize) {
            Log.e(TAG, "View is too small and the content might get cut");
        }

        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = getSuggestedMinimumWidth() + getPaddingLeft() + getPaddingRight();
        int desiredHeight = getSuggestedMinimumHeight() + getPaddingTop() + getPaddingBottom();

        setMeasuredDimension(measureDimension(desiredWidth, widthMeasureSpec),
                measureDimension(desiredHeight, heightMeasureSpec));
    }

    public void setOutputCoordinate(float x, float y) {
        this.output_x = x;
        this.output_y = y;

        invalidate();
    }

    public void setGoalCoordinate(float x, float y) {
        this.goal_x = x;
        this.goal_y = y;

        invalidate();
    }
}
