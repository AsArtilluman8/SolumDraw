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
    private static final int MODE_ROUTE = 2;
    private static final int MODE_CONTOUR = 3;
    private static final int MODE_WHITE = 4;

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
        if (overlay != null) this.previewMode = MODE_ROUTE;
        invalidate();
    }

    public void setPlan(StrokePlan plan) {
        this.plan = plan;
        invalidate();
    }

    public String togglePreviewMode() {
        if (sourceImage != null || analysisOverlay != null) {
            previewMode = (previewMode + 1) % 5;
            if (previewMode == MODE_ANALYSIS && analysisOverlay == null) previewMode = MODE_ROUTE;
        } else {
            previewMode = MODE_WHITE;
        }
        invalidate();
        return previewModeName();
    }

    public String previewModeName() {
        if (previewMode == MODE_ANALYSIS && analysisOverlay != null) return "Старый overlay";
        if (previewMode == MODE_ROUTE) return "Маршрут 1-2-3";
        if (previewMode == MODE_CONTOUR) return "Контуры";
        if (previewMode == MODE_WHITE) return "Белый холст";
        return "Исходник";
    }

    public Rect currentImageRect() {
        Bitmap image = sourceImage != null ? sourceImage : analysisOverlay;
        if (image == null) return new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        return fitRect(image.getWidth(), image.getHeight(), Math.max(1, getWidth()), Math.max(1, getHeight()));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Rect dst = currentImageRect();
        drawPreviewSurface(canvas, dst);
        if (previewMode == MODE_ROUTE) drawRouteOverlay(canvas, dst);
        if (previewMode == MODE_CONTOUR) drawContourOverlay(canvas, dst);

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

    private void drawPreviewSurface(Canvas canvas, Rect dst) {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawColor(Color.rgb(16, 18, 24));

        if (previewMode == MODE_WHITE || sourceImage == null) {
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

        paint.setAlpha(previewMode == MODE_CONTOUR ? 92 : 170);
        canvas.drawBitmap(sourceImage, null, dst, paint);
        paint.setAlpha(255);

        if (previewMode == MODE_CONTOUR) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(92, 0, 0, 0));
            canvas.drawRect(dst, paint);
        }
    }

    private void drawRouteOverlay(Canvas canvas, Rect dst) {
        int[][] pts = new int[][] {
                {50, 18}, {50, 48}, {50, 74}, {36, 60}, {66, 60}
        };
        String[] names = new String[] {"фон", "форма", "объект", "тени", "детали"};
        for (int i = 0; i < pts.length; i++) {
            float x = dst.left + dst.width() * pts[i][0] / 100f;
            float y = dst.top + dst.height() * pts[i][1] / 100f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(220, 20, 24, 32));
            canvas.drawCircle(x, y, 24f, paint);
            paint.setColor(Color.rgb(40, 220, 255));
            canvas.drawCircle(x, y, 20f, paint);
            paint.setColor(Color.rgb(12, 16, 20));
            paint.setTextSize(22f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(i + 1), x, y + 8f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(19f);
            paint.setColor(Color.WHITE);
            canvas.drawText(names[i], x + 28f, y + 7f, paint);
        }
    }

    private void drawContourOverlay(Canvas canvas, Rect dst) {
        if (sourceImage == null || dst.width() < 8 || dst.height() < 8) return;
        int step = Math.max(8, Math.min(dst.width(), dst.height()) / 48);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(Color.rgb(40, 230, 255));
        int sw = sourceImage.getWidth();
        int sh = sourceImage.getHeight();
        for (int y = dst.top + step; y < dst.bottom - step; y += step) {
            Path p = new Path();
            boolean active = false;
            for (int x = dst.left + step; x < dst.right - step; x += step) {
                int ix = Math.min(sw - 2, Math.max(1, (int)((x - dst.left) * sw / (float)dst.width())));
                int iy = Math.min(sh - 2, Math.max(1, (int)((y - dst.top) * sh / (float)dst.height())));
                int c1 = sourceImage.getPixel(ix - 1, iy);
                int c2 = sourceImage.getPixel(ix + 1, iy);
                int c3 = sourceImage.getPixel(ix, iy - 1);
                int c4 = sourceImage.getPixel(ix, iy + 1);
                int e = Math.abs(luma(c1) - luma(c2)) + Math.abs(luma(c3) - luma(c4));
                if (e > 70) {
                    if (!active) { p.moveTo(x, y); active = true; }
                    else p.lineTo(x, y);
                } else if (active) {
                    canvas.drawPath(p, paint);
                    p.reset();
                    active = false;
                }
            }
            if (active) canvas.drawPath(p, paint);
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

    private static int luma(int c) { return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100; }

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
