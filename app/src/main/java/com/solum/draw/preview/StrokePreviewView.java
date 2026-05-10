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
    private static final int MODE_ROUTE = 1;
    private static final int MODE_CONTOUR = 2;
    private static final int MODE_WHITE = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap sourceImage;
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
        paint.setAlpha(previewMode == MODE_CONTOUR ? 88 : 190);
        canvas.drawBitmap(sourceImage, null, dst, paint);
        paint.setAlpha(255);
        if (previewMode == MODE_CONTOUR) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(115, 0, 0, 0));
            canvas.drawRect(dst, paint);
        }
    }

    private void drawRouteOverlay(Canvas canvas, Rect dst) {
        int[][] pts;
        String[] names;
        if (routeKind.equals("person")) {
            pts = new int[][] {{50,16},{50,38},{50,58},{50,28},{58,72}};
            names = new String[] {"фон", "силуэт", "тело", "лицо", "детали"};
        } else if (routeKind.equals("ui")) {
            pts = new int[][] {{50,12},{30,36},{56,38},{72,54},{44,68}};
            names = new String[] {"фон", "панели", "карточки", "иконки", "текст"};
        } else if (routeKind.equals("logo")) {
            pts = new int[][] {{50,18},{50,36},{50,50},{50,64},{50,78}};
            names = new String[] {"фон", "знак", "вырезы", "glow", "края"};
        } else if (routeKind.equals("scene")) {
            pts = new int[][] {{50,16},{50,38},{50,62},{34,58},{66,58}};
            names = new String[] {"фон", "массы", "объект", "тени", "детали"};
        } else {
            pts = new int[][] {{50,18},{50,45},{50,68},{36,58},{66,58}};
            names = new String[] {"фон", "форма", "объект", "тени", "детали"};
        }
        for (int i = 0; i < pts.length; i++) {
            float x = dst.left + dst.width() * pts[i][0] / 100f;
            float y = dst.top + dst.height() * pts[i][1] / 100f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(230, 6, 14, 22));
            canvas.drawCircle(x, y, 24f, paint);
            paint.setColor(Color.rgb(34, 230, 242));
            canvas.drawCircle(x, y, 19f, paint);
            paint.setColor(Color.rgb(5, 10, 15));
            paint.setTextSize(20f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(i + 1), x, y + 7f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(17f);
            paint.setColor(Color.WHITE);
            canvas.drawText(names[i], x + 26f, y + 6f, paint);
        }
    }

    private void drawContourOverlay(Canvas canvas, Rect dst) {
        if (sourceImage == null || dst.width() < 8 || dst.height() < 8) return;
        int step = Math.max(7, Math.min(dst.width(), dst.height()) / 64);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.8f);
        paint.setColor(Color.rgb(34, 230, 242));
        int sw = sourceImage.getWidth();
        int sh = sourceImage.getHeight();
        for (int y = dst.top + step; y < dst.bottom - step; y += step) {
            Path p = new Path();
            boolean active = false;
            int run = 0;
            for (int x = dst.left + step; x < dst.right - step; x += step) {
                int ix = Math.min(sw - 2, Math.max(1, (int)((x - dst.left) * sw / (float)dst.width())));
                int iy = Math.min(sh - 2, Math.max(1, (int)((y - dst.top) * sh / (float)dst.height())));
                int e = Math.abs(luma(sourceImage.getPixel(ix - 1, iy)) - luma(sourceImage.getPixel(ix + 1, iy)))
                      + Math.abs(luma(sourceImage.getPixel(ix, iy - 1)) - luma(sourceImage.getPixel(ix, iy + 1)));
                if (e > 62) {
                    if (!active) { p.moveTo(x, y); active = true; run = 1; }
                    else { p.lineTo(x, y); run++; }
                } else if (active) {
                    if (run >= 3) canvas.drawPath(p, paint);
                    p.reset(); active = false; run = 0;
                }
            }
            if (active && run >= 3) canvas.drawPath(p, paint);
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
