package com.solum.draw.preview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import com.solum.draw.planner.StrokeAction;
import com.solum.draw.planner.StrokePlan;
import com.solum.draw.vision.VisionRegionMap;
import java.util.List;

public final class StrokePreviewView extends View {
    private static final int MODE_SOURCE = 0;
    private static final int MODE_ANALYSIS = 1;
    private static final int MODE_ROUTE = 2;
    private static final int MODE_CONTOUR = 3;
    private static final int MODE_WHITE = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap sourceImage;
    private Bitmap analysisOverlay;
    private VisionRegionMap vision;
    private StrokePlan plan;
    private int previewMode = MODE_SOURCE;

    public StrokePreviewView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.rgb(4, 10, 17));
    }

    public void setSourceImage(Bitmap sourceImage) {
        this.sourceImage = sourceImage;
        this.analysisOverlay = null;
        this.vision = VisionRegionMap.analyze(sourceImage);
        this.previewMode = MODE_SOURCE;
        invalidate();
    }

    public void setAnalysisOverlay(Bitmap overlay) {
        this.analysisOverlay = overlay;
        if (overlay != null) this.previewMode = MODE_ANALYSIS;
        invalidate();
    }

    public void setPlan(StrokePlan plan) { this.plan = plan; invalidate(); }

    public String togglePreviewMode() {
        previewMode = (previewMode + 1) % 5;
        if (previewMode == MODE_ANALYSIS && analysisOverlay == null) previewMode = MODE_ROUTE;
        invalidate();
        return previewModeName();
    }

    public String previewModeName() {
        if (previewMode == MODE_ANALYSIS && analysisOverlay != null) return "Анализ";
        if (previewMode == MODE_ROUTE) return "Маршрут";
        if (previewMode == MODE_CONTOUR) return "Контуры";
        if (previewMode == MODE_WHITE) return "Холст";
        return "Исходник";
    }

    public Rect currentImageRect() {
        Bitmap image = displayImageBase();
        if (image == null) return new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        return fitRect(image.getWidth(), image.getHeight(), Math.max(1, getWidth()), Math.max(1, getHeight()));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Rect dst = currentImageRect();
        drawPreviewSurface(canvas, dst);
        if (previewMode == MODE_ROUTE) drawRoute(canvas, dst);
        if (previewMode == MODE_CONTOUR) drawContours(canvas, dst);
        if (plan != null) drawPlanLayer(canvas, dst);
        drawModeLabel(canvas);
    }

    private Bitmap displayImageBase() {
        if (previewMode == MODE_ANALYSIS && analysisOverlay != null) return analysisOverlay;
        return sourceImage;
    }

    private void drawPreviewSurface(Canvas canvas, Rect dst) {
        canvas.drawColor(Color.rgb(4, 10, 17));
        paint.setStyle(Paint.Style.FILL);
        if (previewMode == MODE_WHITE || displayImageBase() == null) {
            paint.setColor(Color.rgb(238, 238, 232));
            canvas.drawRect(dst, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.rgb(42, 44, 50));
            canvas.drawRect(dst, paint);
            return;
        }
        Bitmap img = displayImageBase();
        paint.setAlpha(previewMode == MODE_CONTOUR ? 96 : 220);
        canvas.drawBitmap(img, null, dst, paint);
        paint.setAlpha(255);
        if (previewMode == MODE_CONTOUR) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(118, 0, 0, 0));
            canvas.drawRect(dst, paint);
        }
    }

    private void drawRoute(Canvas canvas, Rect dst) {
        ensureVision();
        if (vision == null) return;
        List<VisionRegionMap.RoutePoint> points = vision.route();
        for (VisionRegionMap.RoutePoint p : points) {
            float x = dst.left + dst.width() * p.x;
            float y = dst.top + dst.height() * p.y;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(235, 2, 8, 14));
            canvas.drawCircle(x, y, 25f, paint);
            paint.setColor(Color.rgb(34, 230, 242));
            canvas.drawCircle(x, y, 20f, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(20f);
            paint.setFakeBoldText(true);
            paint.setColor(Color.rgb(3, 8, 12));
            canvas.drawText(String.valueOf(p.index), x, y + 7f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(16f);
            paint.setFakeBoldText(false);
            paint.setColor(Color.WHITE);
            canvas.drawText(p.label, x + 27f, y + 6f, paint);
        }
        drawRegionBoxes(canvas, dst, false);
    }

    private void drawContours(Canvas canvas, Rect dst) {
        ensureVision();
        if (vision == null) return;

        RectF df = new RectF(dst);

        paint.setStyle(Paint.Style.FILL);
        int idx = 0;
        for (VisionRegionMap.Region r : vision.displayRegions()) {
            RectF rr = r.rectIn(df);
            if (rr.width() < 12 || rr.height() < 12) continue;

            int alpha = idx == 0 ? 34 : 22;
            int color;
            switch (idx % 6) {
                case 0: color = Color.argb(alpha, 34, 230, 242); break;
                case 1: color = Color.argb(alpha, 255, 196, 72); break;
                case 2: color = Color.argb(alpha, 155, 107, 255); break;
                case 3: color = Color.argb(alpha, 80, 255, 150); break;
                case 4: color = Color.argb(alpha, 255, 90, 140); break;
                default: color = Color.argb(alpha, 120, 180, 255); break;
            }

            paint.setColor(color);
            canvas.drawRoundRect(rr, 10f, 10f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(idx == 0 ? 2.0f : 1.2f);
            paint.setColor(idx == 0 ? Color.argb(170, 34, 230, 242) : Color.argb(105, 230, 240, 255));
            canvas.drawRoundRect(rr, 10f, 10f, paint);
            paint.setStyle(Paint.Style.FILL);

            idx++;
            if (idx >= 8) break;
        }

        drawLocalContourSegments(canvas, dst, true);
        drawLocalContourSegments(canvas, dst, false);

        drawRoute(canvas, dst);
    }

    private void drawLocalContourSegments(Canvas canvas, Rect dst, boolean glow) {
        if (vision == null || vision.gridWidth <= 1 || vision.gridHeight <= 1) return;

        int gw = vision.gridWidth;
        int gh = vision.gridHeight;
        float sx = dst.width() / (float) gw;
        float sy = dst.height() / (float) gh;

        Path path = new Path();

        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int id = y * gw + x;
                if (!vision.subjectMask[id]) continue;

                float x0 = dst.left + x * sx;
                float y0 = dst.top + y * sy;
                float x1 = x0 + sx;
                float y1 = y0 + sy;

                if (!vision.subjectMask[id - 1]) {
                    path.moveTo(x0, y0);
                    path.lineTo(x0, y1);
                }
                if (!vision.subjectMask[id + 1]) {
                    path.moveTo(x1, y0);
                    path.lineTo(x1, y1);
                }
                if (!vision.subjectMask[id - gw]) {
                    path.moveTo(x0, y0);
                    path.lineTo(x1, y0);
                }
                if (!vision.subjectMask[id + gw]) {
                    path.moveTo(x0, y1);
                    path.lineTo(x1, y1);
                }
            }
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        if (glow) {
            paint.setStrokeWidth(5.0f);
            paint.setColor(Color.argb(58, 34, 230, 242));
        } else {
            paint.setStrokeWidth(2.0f);
            paint.setColor(Color.argb(235, 34, 230, 242));
        }

        canvas.drawPath(path, paint);
    }

    private void drawRegionBoxes(Canvas canvas, Rect dst, boolean strong) {
        if (vision == null) return;
        RectF df = new RectF(dst);
        int i = 0;
        for (VisionRegionMap.Region r : vision.displayRegions()) {
            RectF rr = r.rectIn(df);
            if (rr.width() < 16 || rr.height() < 16) continue;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strong ? 2.4f : 1.4f);
            paint.setColor(i == 0 ? Color.argb(210, 34, 230, 242) : Color.argb(120, 155, 107, 255));
            canvas.drawRoundRect(rr, 12f, 12f, paint);
            if (++i >= 8) break;
        }
    }

    private void drawPlanLayer(Canvas canvas, Rect dst) {
        canvas.save();
        canvas.clipRect(dst);
        canvas.translate(dst.left, dst.top);
        drawPlan(canvas, plan);
        canvas.restore();
    }

    private void drawModeLabel(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(22f);
        paint.setFakeBoldText(false);
        canvas.drawText("SolumDraw — " + previewModeName(), 18f, 34f, paint);
    }

    private void ensureVision() {
        if (vision == null && sourceImage != null) vision = VisionRegionMap.analyze(sourceImage);
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
        return luma > 210 ? Color.rgb(80, 80, 80) : color;
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
