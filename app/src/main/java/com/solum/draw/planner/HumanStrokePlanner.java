package com.solum.draw.planner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class HumanStrokePlanner {
    private static final int SMALL_SIZE = 112;
    private static final int MAX_REGIONS = 42;

    private HumanStrokePlanner() {}

    public static StrokePlan build(Bitmap source, DrawMode mode, int canvasWidth, int canvasHeight) {
        Bitmap small = Bitmap.createScaledBitmap(source, SMALL_SIZE, SMALL_SIZE, true);
        List<StrokeAction> actions = new ArrayList<>();

        actions.add(singlePoint("SCULPTOR_BACKGROUND", averageColor(small), 76f, canvasWidth * 0.5f, canvasHeight * 0.5f));

        List<ShapeRegion> regions = ShapeExtractor.extract(small, regionLimit(mode));
        Collections.sort(regions, new Comparator<ShapeRegion>() {
            @Override public int compare(ShapeRegion a, ShapeRegion b) {
                int stageA = stageRank(a, small.getWidth(), small.getHeight());
                int stageB = stageRank(b, small.getWidth(), small.getHeight());
                if (stageA != stageB) return stageA - stageB;
                return b.pixelCount - a.pixelCount;
            }
        });

        int index = 0;
        for (ShapeRegion region : regions) {
            String stage = stageForRegion(region, small.getWidth(), small.getHeight(), index);
            float brush = brushForRegion(region, small.getWidth(), small.getHeight(), index);
            List<PointF> path = regionPath(region, canvasWidth, canvasHeight, small.getWidth(), small.getHeight(), mode, index);
            if (path.size() > 1) {
                actions.add(new StrokeAction(stage, region.color, brush, path));
            }
            index++;
        }

        return new StrokePlan(source.getWidth(), source.getHeight(), mode.name(), actions);
    }

    private static int regionLimit(DrawMode mode) {
        if (mode == DrawMode.PRINTER_DEBUG) return 56;
        if (mode == DrawMode.HUMAN_FAST) return 34;
        return MAX_REGIONS;
    }

    private static StrokeAction singlePoint(String stage, int color, float size, float x, float y) {
        List<PointF> path = new ArrayList<>();
        path.add(new PointF(x, y));
        return new StrokeAction(stage, color, size, path);
    }

    private static int stageRank(ShapeRegion region, int imageWidth, int imageHeight) {
        if (region.isLargeMass(imageWidth, imageHeight)) return 0;
        if (region.pixelCount > imageWidth * imageHeight * 0.012f || region.density() > 0.42f) return 1;
        if (region.pixelCount > 18) return 2;
        return 3;
    }

    private static String stageForRegion(ShapeRegion region, int imageWidth, int imageHeight, int index) {
        int rank = stageRank(region, imageWidth, imageHeight);
        if (rank == 0) return "SCULPTOR_REGION_MASS";
        if (rank == 1) return "POTTER_REGION_REFINE";
        if (rank == 2) return "GRINDER_REGION_DETAIL";
        return "POLISHER_REGION_ACCENT";
    }

    private static float brushForRegion(ShapeRegion region, int imageWidth, int imageHeight, int index) {
        int rank = stageRank(region, imageWidth, imageHeight);
        if (rank == 0) return Math.max(12f, Math.min(28f, Math.max(region.width(), region.height()) * 0.16f));
        if (rank == 1) return Math.max(7f, Math.min(16f, Math.max(region.width(), region.height()) * 0.11f));
        if (rank == 2) return 5f;
        return 3f;
    }

    private static List<PointF> regionPath(ShapeRegion region, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight, DrawMode mode, int seedIndex) {
        List<PointF> points = new ArrayList<>();

        if (region.isLargeMass(imageWidth, imageHeight)) {
            addBoxSweep(points, region, canvasWidth, canvasHeight, imageWidth, imageHeight);
        }

        int stride = sampleStride(region, mode);
        int count = 0;
        for (Point sample : region.samples) {
            if ((count++ % stride) != 0) continue;
            points.add(toCanvas(sample.x, sample.y, canvasWidth, canvasHeight, imageWidth, imageHeight));
        }

        if (points.size() < 3) {
            points.add(toCanvas(region.minX, region.minY, canvasWidth, canvasHeight, imageWidth, imageHeight));
            points.add(toCanvas(region.maxX, region.minY, canvasWidth, canvasHeight, imageWidth, imageHeight));
            points.add(toCanvas(region.maxX, region.maxY, canvasWidth, canvasHeight, imageWidth, imageHeight));
            points.add(toCanvas(region.minX, region.maxY, canvasWidth, canvasHeight, imageWidth, imageHeight));
        }

        return humanOrder(points, mode, region.color + seedIndex * 31);
    }

    private static void addBoxSweep(List<PointF> points, ShapeRegion region, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight) {
        int midY = (region.minY + region.maxY) / 2;
        int q1Y = region.minY + Math.max(1, region.height() / 4);
        int q3Y = region.maxY - Math.max(1, region.height() / 4);
        points.add(toCanvas(region.minX, q1Y, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.maxX, q1Y, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.maxX, midY, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.minX, midY, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.minX, q3Y, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.maxX, q3Y, canvasWidth, canvasHeight, imageWidth, imageHeight));
    }

    private static int sampleStride(ShapeRegion region, DrawMode mode) {
        if (mode == DrawMode.PRINTER_DEBUG) return 1;
        if (region.pixelCount > 220) return 3;
        if (mode == DrawMode.HUMAN_FAST) return 3;
        return 2;
    }

    private static PointF toCanvas(int x, int y, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight) {
        return new PointF((x / (float) imageWidth) * canvasWidth, (y / (float) imageHeight) * canvasHeight);
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
                    d *= 0.82f + random.nextFloat() * 0.42f;
                }
                if (d < bestDistance) {
                    bestDistance = d;
                    bestIndex = i;
                }
            }
            current = pool.remove(bestIndex);
            if (mode != DrawMode.PRINTER_DEBUG) {
                current = new PointF(current.x + (random.nextFloat() - 0.5f) * 2.2f, current.y + (random.nextFloat() - 0.5f) * 2.2f);
            }
            out.add(current);
        }
        return out;
    }
}
