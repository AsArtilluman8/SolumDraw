package com.solum.draw.planner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;

public final class RasterSimilarityPlanner {
    private RasterSimilarityPlanner() {}

    public static StrokePlan build(Bitmap source, DrawMode mode, int canvasWidth, int canvasHeight) {
        int maxLong = mode == DrawMode.HUMAN_FAST ? 300 : 420;
        float scale = Math.min(maxLong / (float) Math.max(1, source.getWidth()),
                               maxLong / (float) Math.max(1, source.getHeight()));
        int w = Math.max(32, Math.round(source.getWidth() * scale));
        int h = Math.max(32, Math.round(source.getHeight() * scale));

        Bitmap bmp = Bitmap.createScaledBitmap(source, w, h, true);
        ArrayList<StrokeAction> actions = new ArrayList<>();

        addMassRuns(actions, bmp, w, h, canvasWidth, canvasHeight, mode);
        addDenseColorStrokes(actions, bmp, w, h, canvasWidth, canvasHeight, mode);
        addEdgeStrokes(actions, bmp, w, h, canvasWidth, canvasHeight, mode, false);
        addEdgeStrokes(actions, bmp, w, h, canvasWidth, canvasHeight, mode, true);

        bmp.recycle();
        return new StrokePlan(source.getWidth(), source.getHeight(), mode.name(), actions);
    }

    private static void addMassRuns(ArrayList<StrokeAction> out, Bitmap bmp, int w, int h, int cw, int ch, DrawMode mode) {
        int stepY = mode == DrawMode.HUMAN_FAST ? 14 : 10;
        int minRun = mode == DrawMode.HUMAN_FAST ? 9 : 6;

        for (int y = 0; y < h; y += stepY) {
            int startX = -1;
            int lastX = -1;
            int colorSumR = 0, colorSumG = 0, colorSumB = 0, count = 0;

            for (int x = 0; x < w; x++) {
                int px = bmp.getPixel(x, y);
                if (isSkippableWhite(px)) {
                    if (startX >= 0) {
                        emitRun(out, "SCULPTOR_RASTER_MASS", startX, lastX, y, avg(colorSumR, colorSumG, colorSumB, count), 7.5f, w, h, cw, ch);
                    }
                    startX = -1;
                    lastX = -1;
                    colorSumR = colorSumG = colorSumB = count = 0;
                    continue;
                }

                if (startX < 0) startX = x;
                lastX = x;
                colorSumR += Color.red(px);
                colorSumG += Color.green(px);
                colorSumB += Color.blue(px);
                count++;

                if (lastX - startX >= Math.max(minRun, w / 7)) {
                    emitRun(out, "SCULPTOR_RASTER_MASS", startX, lastX, y, avg(colorSumR, colorSumG, colorSumB, count), 7.5f, w, h, cw, ch);
                    startX = -1;
                    lastX = -1;
                    colorSumR = colorSumG = colorSumB = count = 0;
                }
            }

            if (startX >= 0) {
                emitRun(out, "SCULPTOR_RASTER_MASS", startX, lastX, y, avg(colorSumR, colorSumG, colorSumB, count), 7.5f, w, h, cw, ch);
            }
        }
    }

    private static void addDenseColorStrokes(ArrayList<StrokeAction> out, Bitmap bmp, int w, int h, int cw, int ch, DrawMode mode) {
        int step = mode == DrawMode.HUMAN_FAST ? 8 : 5;
        int max = mode == DrawMode.HUMAN_FAST ? 520 : 900;
        int added = 0;

        for (int y = step / 2; y < h - step; y += step) {
            boolean reverse = ((y / step) % 2) == 1;
            if (!reverse) {
                for (int x = step / 2; x < w - step; x += step) {
                    if (emitDetail(out, bmp, x, y, step, w, h, cw, ch)) added++;
                    if (added >= max) return;
                }
            } else {
                for (int x = w - step - 1; x >= step / 2; x -= step) {
                    if (emitDetail(out, bmp, x, y, step, w, h, cw, ch)) added++;
                    if (added >= max) return;
                }
            }
        }
    }

    private static void addEdgeStrokes(ArrayList<StrokeAction> out, Bitmap bmp, int w, int h, int cw, int ch, DrawMode mode, boolean strongOnly) {
        int step = strongOnly ? 2 : (mode == DrawMode.HUMAN_FAST ? 4 : 3);
        int threshold = strongOnly ? 82 : 48;
        int max = strongOnly ? 360 : (mode == DrawMode.HUMAN_FAST ? 420 : 720);
        int added = 0;

        for (int y = 1; y < h - 1; y += step) {
            for (int x = 1; x < w - 1; x += step) {
                int px = bmp.getPixel(x, y);
                int dx = colorDiff(px, bmp.getPixel(x + 1, y));
                int dy = colorDiff(px, bmp.getPixel(x, y + 1));
                int d = Math.max(dx, dy);
                if (d < threshold) continue;
                if (isSkippableWhite(px) && d < threshold + 18) continue;

                String stage = strongOnly ? "POLISHER_RASTER_ACCENT" : "GRINDER_RASTER_EDGE";
                float size = strongOnly ? 1.7f : 2.4f;
                int color = d > 96 ? darken(px) : px;

                ArrayList<PointF> path = new ArrayList<>();
                path.add(toCanvas(x, y, w, h, cw, ch));
                if (dx >= dy) path.add(toCanvas(x + step, y, w, h, cw, ch));
                else path.add(toCanvas(x, y + step, w, h, cw, ch));
                out.add(new StrokeAction(stage, color, size, path));

                added++;
                if (added >= max) return;
            }
        }
    }

    private static boolean emitDetail(ArrayList<StrokeAction> out, Bitmap bmp, int x, int y, int step, int w, int h, int cw, int ch) {
        int px = bmp.getPixel(x, y);
        if (isSkippableWhite(px)) return false;

        ArrayList<PointF> path = new ArrayList<>();
        path.add(toCanvas(x, y, w, h, cw, ch));
        path.add(toCanvas(Math.min(w - 1, x + step), y, w, h, cw, ch));
        out.add(new StrokeAction("POTTER_RASTER_COLOR", px, Math.max(2.5f, step * 0.42f), path));
        return true;
    }

    private static void emitRun(ArrayList<StrokeAction> out, String stage, int x1, int x2, int y, int color, float size, int w, int h, int cw, int ch) {
        if (x2 <= x1) return;
        ArrayList<PointF> path = new ArrayList<>();
        path.add(toCanvas(x1, y, w, h, cw, ch));
        path.add(toCanvas(x2, y, w, h, cw, ch));
        out.add(new StrokeAction(stage, color, size, path));
    }

    private static PointF toCanvas(int x, int y, int w, int h, int cw, int ch) {
        return new PointF((x / (float) Math.max(1, w - 1)) * cw,
                          (y / (float) Math.max(1, h - 1)) * ch);
    }

    private static boolean isSkippableWhite(int c) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return r > 242 && g > 242 && b > 242 && (max - min) < 18;
    }

    private static int avg(int r, int g, int b, int n) {
        if (n <= 0) return Color.BLACK;
        return Color.rgb(clamp(r / n), clamp(g / n), clamp(b / n));
    }

    private static int darken(int c) {
        return Color.rgb(clamp((int)(Color.red(c) * 0.45f)),
                         clamp((int)(Color.green(c) * 0.45f)),
                         clamp((int)(Color.blue(c) * 0.45f)));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int colorDiff(int c1, int c2) {
        int r = Math.abs(Color.red(c1) - Color.red(c2));
        int g = Math.abs(Color.green(c1) - Color.green(c2));
        int b = Math.abs(Color.blue(c1) - Color.blue(c2));
        return (r + g + b) / 3;
    }
}
