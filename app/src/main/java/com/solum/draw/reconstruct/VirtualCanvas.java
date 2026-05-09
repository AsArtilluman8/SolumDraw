package com.solum.draw.reconstruct;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import com.solum.draw.planner.StrokeAction;

public final class VirtualCanvas {
    private final Bitmap bitmap;
    private final Canvas canvas;
    private final Paint paint;
    private int appliedStrokes;

    public VirtualCanvas(int width, int height) {
        this.bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        this.canvas = new Canvas(bitmap);
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setStrokeCap(Paint.Cap.ROUND);
        this.paint.setStrokeJoin(Paint.Join.ROUND);
        clear(Color.rgb(238, 238, 232));
    }

    public void clear(int color) {
        canvas.drawColor(color);
        appliedStrokes = 0;
    }

    public void apply(StrokeAction action) {
        paint.setColor(action.color);
        paint.setStrokeWidth(action.size);
        paint.setStyle(Paint.Style.STROKE);

        if (action.path.size() == 1) {
            paint.setStyle(Paint.Style.FILL);
            PointF p = action.path.get(0);
            canvas.drawCircle(p.x, p.y, action.size * 2f, paint);
        } else if (action.path.size() > 1) {
            Path path = new Path();
            PointF first = action.path.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < action.path.size(); i++) {
                PointF p = action.path.get(i);
                path.lineTo(p.x, p.y);
            }
            canvas.drawPath(path, paint);
        }
        appliedStrokes++;
    }

    public int colorAt(int x, int y) {
        return bitmap.getPixel(clamp(x, 0, width() - 1), clamp(y, 0, height() - 1));
    }

    public int width() {
        return bitmap.getWidth();
    }

    public int height() {
        return bitmap.getHeight();
    }

    public int appliedStrokes() {
        return appliedStrokes;
    }

    public Bitmap bitmap() {
        return bitmap;
    }

    public String summary() {
        return "virtualCanvas=" + width() + "x" + height() + " appliedStrokes=" + appliedStrokes;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
