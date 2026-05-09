package com.solum.draw.preview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.View;
import com.solum.draw.planner.StrokeAction;
import com.solum.draw.planner.StrokePlan;

public final class StrokePreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap sourceImage;
    private StrokePlan plan;

    public StrokePreviewView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.rgb(16, 18, 24));
    }

    public void setSourceImage(Bitmap sourceImage) {
        this.sourceImage = sourceImage;
        invalidate();
    }

    public void setPlan(StrokePlan plan) {
        this.plan = plan;
        invalidate();
    }

    public Rect currentImageRect() {
        if (sourceImage == null) {
            return new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        }
        return fitRect(sourceImage.getWidth(), sourceImage.getHeight(), Math.max(1, getWidth()), Math.max(1, getHeight()));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Rect dst = currentImageRect();
        if (sourceImage != null) {
            paint.setAlpha(64);
            canvas.drawBitmap(sourceImage, null, dst, paint);
            paint.setAlpha(255);
        }

        if (plan != null) {
            canvas.save();
            canvas.clipRect(dst);
            canvas.translate(dst.left, dst.top);
            drawPlan(canvas, plan);
            canvas.restore();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(26f);
        canvas.drawText("SolumDraw preview", 24f, 40f, paint);
    }

    private void drawPlan(Canvas canvas, StrokePlan plan) {
        for (StrokeAction action : plan.actions) {
            if (isSuppressedPreviewStroke(action)) {
                continue;
            }
            paint.setColor(action.color);
            paint.setStrokeWidth(action.size);
            paint.setStyle(Paint.Style.STROKE);

            if (action.path.size() == 1) {
                paint.setStyle(Paint.Style.FILL);
                PointF p = action.path.get(0);
                canvas.drawCircle(p.x, p.y, action.size * 2.0f, paint);
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
        }
    }

    private static boolean isSuppressedPreviewStroke(StrokeAction action) {
        int r = Color.red(action.color);
        int g = Color.green(action.color);
        int b = Color.blue(action.color);
        int luma = (r * 30 + g * 59 + b * 11) / 100;
        return luma < 16 && action.stage.startsWith("SCULPTOR");
    }

    private static Rect fitRect(int imageWidth, int imageHeight, int viewWidth, int viewHeight) {
        float scale = Math.min(viewWidth / (float) imageWidth, viewHeight / (float) imageHeight);
        int width = Math.round(imageWidth * scale);
        int height = Math.round(imageHeight * scale);
        int left = (viewWidth - width) / 2;
        int top = (viewHeight - height) / 2;
        return new Rect(left, top, left + width, top + height);
    }
}
