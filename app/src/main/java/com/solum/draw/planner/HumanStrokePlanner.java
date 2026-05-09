package com.solum.draw.planner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class HumanStrokePlanner {
    private static final int SMALL_SIZE = 96;

    private HumanStrokePlanner() {}

    public static StrokePlan build(Bitmap source, DrawMode mode, int canvasWidth, int canvasHeight) {
        Bitmap small = Bitmap.createScaledBitmap(source, SMALL_SIZE, SMALL_SIZE, true);
        List<StrokeAction> actions = new ArrayList<>();

        actions.add(singlePoint("SCULPTOR_BACKGROUND", averageColor(small), 72f, canvasWidth * 0.5f, canvasHeight * 0.5f));

        List<Integer> palette = extractPalette(small, mode == DrawMode.HUMAN_NATURAL ? 14 : 20);
        for (int i = 0; i < palette.size(); i++) {
            int color = palette.get(i);
            String stage = stageForIndex(i);
            float brush = brushForIndex(i);
            int maxPoints = maxPointsForIndex(i, mode);
            List<PointF> points = sampleColorPoints(small, color, maxPoints, canvasWidth, canvasHeight);
            if (points.size() > 1) {
                actions.add(new StrokeAction(stage, color, brush, humanOrder(points, mode, color)));
            }
        }

        return new StrokePlan(source.getWidth(), source.getHeight(), mode.name(), actions);
    }

    private static StrokeAction singlePoint(String stage, int color, float size, float x, float y) {
        List<PointF> path = new ArrayList<>();
        path.add(new PointF(x, y));
        return new StrokeAction(stage, color, size, path);
    }

    private static String stageForIndex(int index) {
        if (index < 3) return "SCULPTOR_BIG_MASS";
        if (index < 7) return "POTTER_FORM_REFINE";
        if (index < 12) return "GRINDER_MID_DETAIL";
        return "POLISHER_FINAL_ACCENT";
    }

    private static float brushForIndex(int index) {
        if (index < 3) return 18f;
        if (index < 7) return 11f;
        if (index < 12) return 6f;
        return 3f;
    }

    private static int maxPointsForIndex(int index, DrawMode mode) {
        int base = mode == DrawMode.PRINTER_DEBUG ? 34 : 22;
        return base + index * 2;
    }

    private static int averageColor(Bitmap bitmap) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 3) {
            for (int x = 0; x < bitmap.getWidth(); x += 3) {
                int c = bitmap.getPixel(x, y);
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                n++;
            }
        }
        if (n == 0) n = 1;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    private static List<Integer> extractPalette(Bitmap bitmap, int maxColors) {
        Map<Integer, Integer> buckets = new HashMap<>();
        for (int y = 0; y < bitmap.getHeight(); y += 2) {
            for (int x = 0; x < bitmap.getWidth(); x += 2) {
                int c = bitmap.getPixel(x, y);
                int r = (Color.red(c) / 32) * 32;
                int g = (Color.green(c) / 32) * 32;
                int b = (Color.blue(c) / 32) * 32;
                int q = Color.rgb(r, g, b);
                Integer old = buckets.get(q);
                buckets.put(q, old == null ? 1 : old + 1);
            }
        }

        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(buckets.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });

        List<Integer> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : list) {
            if (result.size() >= maxColors) break;
            result.add(entry.getKey());
        }
        return result;
    }

    private static List<PointF> sampleColorPoints(Bitmap bitmap, int target, int maxPoints, int canvasWidth, int canvasHeight) {
        List<PointF> points = new ArrayList<>();
        int threshold = 42 * 42 * 3;
        for (int y = 2; y < bitmap.getHeight() - 2; y += 3) {
            for (int x = 2; x < bitmap.getWidth() - 2; x += 3) {
                int c = bitmap.getPixel(x, y);
                if (distanceSquared(c, target) < threshold) {
                    points.add(new PointF((x / (float) bitmap.getWidth()) * canvasWidth, (y / (float) bitmap.getHeight()) * canvasHeight));
                }
            }
        }
        Collections.shuffle(points, new Random(1337 + target));
        if (points.size() > maxPoints) {
            return new ArrayList<>(points.subList(0, maxPoints));
        }
        return points;
    }

    private static int distanceSquared(int a, int b) {
        int dr = Color.red(a) - Color.red(b);
        int dg = Color.green(a) - Color.green(b);
        int db = Color.blue(a) - Color.blue(b);
        return dr * dr + dg * dg + db * db;
    }

    private static List<PointF> humanOrder(List<PointF> input, DrawMode mode, int seed) {
        List<PointF> pool = new ArrayList<>(input);
        List<PointF> out = new ArrayList<>();
        if (pool.isEmpty()) return out;

        Random random = new Random(77L + seed + pool.size());
        PointF current = pool.remove(0);
        out.add(current);

        while (!pool.isEmpty()) {
            int bestIndex = 0;
            float bestDistance = Float.MAX_VALUE;
            for (int i = 0; i < pool.size(); i++) {
                PointF p = pool.get(i);
                float dx = p.x - current.x;
                float dy = p.y - current.y;
                float d = dx * dx + dy * dy;
                if (mode == DrawMode.HUMAN_NATURAL) {
                    d *= 0.85f + random.nextFloat() * 0.35f;
                }
                if (d < bestDistance) {
                    bestDistance = d;
                    bestIndex = i;
                }
            }
            current = pool.remove(bestIndex);
            if (mode != DrawMode.PRINTER_DEBUG) {
                current = new PointF(current.x + (random.nextFloat() - 0.5f) * 2.4f, current.y + (random.nextFloat() - 0.5f) * 2.4f);
            }
            out.add(current);
        }
        return out;
    }
}
