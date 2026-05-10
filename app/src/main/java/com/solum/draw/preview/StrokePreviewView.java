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
    private static final int MODE_SOURCE = 0;
    private static final int MODE_ANALYSIS = 1;
    private static final int MODE_WHITE = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap sourceImage;
    private Bitmap analysisOverlay;
    private StrokePlan plan;
    private int previewMode = MODE_SOURCE;

    public StrokePreviewView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.rgb(16, 18, 24));
    }

    public void setSourceImage(Bitmap sourceImage) {
        this.sourceImage = sourceImage;
        this.analysisOverlay = null;
        this.previewMode = MODE_SOURCE;
        invalidate();
    }

    public void setAnalysisOverlay(Bitmap overlay) {
        this.analysisOverlay = overlay;
        if (overlay != null) this.previewMode = MODE_ANALYSIS;
        invalidate();
    }

    public void setPlan(StrokePlan plan) {
        this.plan = plan;
        invalidate();
    }

    public String togglePreviewMode() {
        if (analysisOverlay != null) {
            previewMode = (previewMode + 1) % 3;
        } else {
            previewMode = previewMode == MODE_WHITE ? MODE_SOURCE : MODE_WHITE;
        }
        invalidate();
        return previewModeName();
    }

    public String previewModeName() {
        if (previewMode == MODE_ANALYSIS && analysisOverlay != null) return "Анализ / overlay";
        if (previewMode == MODE_WHITE) return "Белый холст";
        return "Исходник";
    }

    public Rect currentImageRect() {
        Bitmap image = displayImage();
        if (image == null) {
            return new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        }
        return fitRect(image.getWidth(), image.getHeight(), Math.max(1, getWidth()), Math.max(1, getHeight()));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Rect dst = currentImageRect();
        drawPreviewSurface(canvas, dst);

        if (plan != null) {
            canvas.save();
            canvas.clipRect(dst);
            canvas.translate(dst.left, dst.top);
            drawPlan(canvas, plan);
            canvas.restore();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(previewMode == MODE_WHITE ? Color.rgb(20, 24, 32) : Color.WHITE);
        paint.setTextSize(24f);
        canvas.drawText("SolumDraw — " + previewModeName(), 24f, 40f, paint);
    }

    private Bitmap displayImage() {
        if (previewMode == MODE_ANALYSIS && analysisOverlay != null) return analysisOverlay;
        return sourceImage;
    }

    private void drawPreviewSurface(Canvas canvas, Rect dst) {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawColor(Color.rgb(16, 18, 24));

        if (previewMode == MODE_WHITE || displayImage() == null) {
            paint.setColor(Color.rgb(238, 238, 232));
            canvas.drawRect(dst, paint);
            paint.setColor(Color.rgb(42, 44, 50));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawRect(dst, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }

        if (previewMode == MODE_ANALYSIS && analysisOverlay != null) {
            paint.setAlpha(255);
            canvas.drawBitmap(analysisOverlay, null, dst, paint);
            return;
        }

        if (sourceImage != null) {
            paint.setAlpha(96);
            canvas.drawBitmap(sourceImage, null, dst, paint);
            paint.setAlpha(255);
        }
    }

    private void drawPlan(Canvas canvas, StrokePlan plan) {
        for (StrokeAction action : plan.actions) {
            if (previewMode == MODE_SOURCE && isSuppressedPreviewStroke(action)) continue;
            int color = previewMode == MODE_WHITE ? contrastForWhiteCanvas(action.color) : action.color;
            paint.setColor(color);
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

    private static int contrastForWhiteCanvas(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        int luma = (r * 30 + g * 59 + b * 11) / 100;
        if (luma > 210) return Color.rgb(80, 80, 80);
        return color;
    }

    private static boolean isSuppressedPreviewStroke(StrokeAction action) {
        int r = Color.red(action.color), g = Color.green(action.color), b = Color.blue(action.color);
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
