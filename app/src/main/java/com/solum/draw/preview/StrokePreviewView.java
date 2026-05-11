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
import java.util.List;

public final class StrokePreviewView extends View {
    private static final int MODE_SOURCE = 0;
    private static final int MODE_ROUTE = 1;
    private static final int MODE_CONTOUR = 2;
    private static final int MODE_WHITE = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap sourceImage;
    private VisionRegionMap regionMap;
    private StrokePlan plan;
    private int previewMode = MODE_SOURCE;
    private String routeKind = "general";

    public StrokePreviewView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.rgb(6, 11, 18));
    }

    public void setRouteKind(String kind) { this.routeKind = kind == null ? "general" : kind; invalidate(); }
    public void showRoute() { this.previewMode = MODE_ROUTE; invalidate(); }

    public void setSourceImage(Bitmap sourceImage) {
        this.sourceImage = sourceImage;
        this.regionMap = VisionRegionMap.analyze(sourceImage);
        this.previewMode = MODE_SOURCE;
        invalidate();
    }

    public void setPlan(StrokePlan plan) { this.plan = plan; invalidate(); }

    public String togglePreviewMode() {
        previewMode = (previewMode + 1) % 4;
        invalidate();
        return previewModeName();
    }

    public String previewModeName() {
        if (previewMode == MODE_ROUTE) return "Маршрут";
        if (previewMode == MODE_CONTOUR) return "Контуры";
        if (previewMode == MODE_WHITE) return "Холст";
        return "Исходник";
    }

    public Rect currentImageRect() {
        if (sourceImage == null) return new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        return fitRect(sourceImage.getWidth(), sourceImage.getHeight(), Math.max(1, getWidth()), Math.max(1, getHeight()));
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
        paint.setColor(Color.WHITE);
        paint.setTextSize(22f);
        canvas.drawText("SolumDraw — " + previewModeName(), 18f, 34f, paint);
    }

    private void drawPreviewSurface(Canvas canvas, Rect dst) {
        canvas.drawColor(Color.rgb(6, 11, 18));
        paint.setStyle(Paint.Style.FILL);
        if (sourceImage == null) {
            paint.setColor(Color.rgb(238, 238, 232));
            canvas.drawRect(dst, paint);
            return;
        }
        if (previewMode == MODE_WHITE) {
            paint.setColor(Color.rgb(238, 238, 232));
            canvas.drawRect(dst, paint);
            paint.setColor(Color.rgb(42, 44, 50));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawRect(dst, paint);
            return;
        }
        paint.setAlpha(previewMode == MODE_CONTOUR ? 96 : 200);
        canvas.drawBitmap(sourceImage, null, dst, paint);
        paint.setAlpha(255);
        if (previewMode == MODE_CONTOUR) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(118, 0, 0, 0));
            canvas.drawRect(dst, paint);
        }
    }

    private void drawRouteOverlay(Canvas canvas, Rect dst) {
        if (regionMap == null) regionMap = VisionRegionMap.analyze(sourceImage);
        List<VisionRegionMap.RoutePoint> pts = regionMap.routePoints();
        for (VisionRegionMap.RoutePoint p : pts) {
            float x = dst.left + dst.width() * p.x;
            float y = dst.top + dst.height() * p.y;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(235, 6, 14, 22));
            canvas.drawCircle(x, y, 25f, paint);
            paint.setColor(Color.rgb(34, 230, 242));
            canvas.drawCircle(x, y, 20f, paint);
            paint.setColor(Color.rgb(5, 10, 15));
            paint.setTextSize(20f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(p.index), x, y + 7f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(17f);
            paint.setColor(Color.WHITE);
            canvas.drawText(p.label, x + 27f, y + 6f, paint);
        }
        drawRegionBoxes(canvas, dst, false);
    }

    private void drawContourOverlay(Canvas canvas, Rect dst) {
        if (sourceImage == null || dst.width() < 8 || dst.height() < 8) return;
        if (regionMap == null) regionMap = VisionRegionMap.analyze(sourceImage);
        drawRegionBoxes(canvas, dst, true);
        drawEdgeOutline(canvas, dst);
    }

    private void drawRegionBoxes(Canvas canvas, Rect dst, boolean strong) {
        if (regionMap == null) return;
        RectF dstF = new RectF(dst);
        List<VisionRegionMap.Region> regions = regionMap.contourRegions();
        int count = 0;
        for (VisionRegionMap.Region r : regions) {
            RectF rr = r.rectIn(dstF);
            if (rr.width() < 12 || rr.height() < 12) continue;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strong ? 2.4f : 1.6f);
            paint.setColor(count == 0 ? Color.argb(strong ? 210 : 120, 34, 230, 242) : Color.argb(strong ? 135 : 80, 155, 107, 255));
            canvas.drawRoundRect(rr, 12f, 12f, paint);
            count++;
        }
    }

    private void drawEdgeOutline(Canvas canvas, Rect dst) {
        int gw = regionMap.gridWidth;
        int gh = regionMap.gridHeight;
        float cw = dst.width() / (float)Math.max(1, gw);
        float ch = dst.height() / (float)Math.max(1, gh);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(220, 34, 230, 242));
        float dot = Math.max(1.1f, Math.min(cw, ch) * 0.42f);
        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                if (!regionMap.isEdgeCell(x, y)) continue;
                int neighbors = 0;
                if (regionMap.isEdgeCell(x + 1, y)) neighbors++;
                if (regionMap.isEdgeCell(x - 1, y)) neighbors++;
                if (regionMap.isEdgeCell(x, y + 1)) neighbors++;
                if (regionMap.isEdgeCell(x, y - 1)) neighbors++;
                if (regionMap.isEdgeCell(x + 1, y + 1)) neighbors++;
                if (regionMap.isEdgeCell(x - 1, y - 1)) neighbors++;
                if (regionMap.isEdgeCell(x + 1, y - 1)) neighbors++;
                if (regionMap.isEdgeCell(x - 1, y + 1)) neighbors++;
                if (neighbors >= 7) continue;
                float px = dst.left + (x + 0.5f) * cw;
                float py = dst.top + (y + 0.5f) * ch;
                canvas.drawCircle(px, py, dot, paint);
            }
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
    private static int contrastForWhiteCanvas(int color) { return luma(color) > 210 ? Color.rgb(80, 80, 80) : color; }
    private static boolean isSuppressedPreviewStroke(StrokeAction action) { return luma(action.color) < 16 && action.stage.startsWith("SCULPTOR"); }
    private static Rect fitRect(int imageWidth, int imageHeight, int viewWidth, int viewHeight) {
        float scale = Math.min(viewWidth / (float) imageWidth, viewHeight / (float) imageHeight);
        int width = Math.round(imageWidth * scale);
        int height = Math.round(imageHeight * scale);
        int left = (viewWidth - width) / 2;
        int top = (viewHeight - height) / 2;
        return new Rect(left, top, left + width, top + height);
    }
}
