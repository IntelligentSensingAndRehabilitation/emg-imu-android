package org.sralab.emgimu.visualization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;

public class GimbalView extends View {

    private final String TAG = GimbalView.class.getSimpleName();

    Paint paint1 = new Paint();
    Paint paint2 = new Paint();
    Paint paint3 = new Paint();

    float width = 15.0f;

    private void init() {
        paint1.setColor(Color.BLACK);
        paint1.setStrokeWidth(width);
        paint2.setColor(Color.RED);
        paint2.setStrokeWidth(width);
        paint3.setColor(Color.BLUE);
        paint3.setStrokeWidth(width);
    }

    public GimbalView(Context context) {
        super(context);
        init();
    }

    public GimbalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GimbalView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void updateQuat(float [] q) {
        p1.x = 1-2.0f*(q[2]*q[2]+q[3]*q[3]);
        p1.y = 2*(q[1]*q[2]+q[0]*q[3]);
        p1.z = 2*(q[1]*q[3]-q[0]*q[2]);
        p2.x = 2*(q[1]*q[2] - q[0]*q[3]);
        p2.y = 1-2.0f*(q[1]*q[1]+q[3]*q[3]);
        p2.z = 2*(q[2]*q[3] + q[0]*q[1]);
        p3.x = 2*(q[1]*q[3] + q[0]*q[2]);
        p3.y = 2*(q[2]*q[3] - q[0]*q[1]);
        p3.z = 1-2.0f*(q[1]*q[1]+q[2]*q[2]);
        invalidate();
    }

    private Point3 p1 = new Point3(1.0f, 0.0f, 0.0f);
    private Point3 p2 = new Point3(0.0f, 1.0f, 0.0f);
    private Point3 p3 = new Point3(0.0f, 0.0f, 1.0f);

    private class Point3 {
        public float x;
        public float y;
        public float z;

       public Point3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Point project() {
            float F = Math.min(getHeight(), getWidth()) * 0.5f;

            float cx = getWidth() / 2.0f;
            float cy = getHeight() / 2.0f;
            float cz = 2f;

            Point p = new Point();
            p.x = (int) (this.y * F / (cz + this.x) + cx);
            p.y = (int) (-this.z * F / (cz + this.x) + cy);
            return p;
        }

        public void draw(Canvas canvas, Paint paint)
        {
            float cx = getWidth() / 2.0f;
            float cy = getHeight() / 2.0f;

            Point p = this.project();

            canvas.drawLine(cx, cy, p.x, p.y, paint);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        p1.draw(canvas, paint1);
        p2.draw(canvas, paint2);
        p3.draw(canvas, paint3);
    }
}
