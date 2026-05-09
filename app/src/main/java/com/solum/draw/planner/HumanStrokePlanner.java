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
    private static final int SMALL_SIZE = 128;
    private static final int MAX_REGIONS = 58;

    private HumanStrokePlanner() {}

    public static StrokePlan build(Bitmap source, DrawMode mode, int canvasWidth, int canvasHeight) {
        Bitmap small = Bitmap.createScaledBitmap(source, SMALL_SIZE, SMALL_SIZE, true);
        List<StrokeAction> actions = new ArrayList<>();

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
        int largeMassCount = 0;
        for (ShapeRegion region : regions) {
            if (shouldSkipRegion(region, small.getWidth(), small.getHeight(), largeMassCount)) {
                index++;
                continue;
            }
            if (region.isLargeMass(small.getWidth(), small.getHeight())) {
                largeMassCount++;
            }

            String stage = stageForRegion(region, small.getWidth(), small.getHeight(), index);
            float brush = brushForRegion(region, small.getWidth(), small.getHeight(), index);
            List<PointF> path = regionPath(region, canvasWidth, canvasHeight, small.getWidth(), small.getHeight(), mode, index);
            if (path.size() > 1) {
                actions.add(new StrokeAction(stage, region.color, brush, path));
            }

            if (needsCoveragePass(region, small.getWidth(), small.getHeight(), mode, largeMassCount)) {
                List<PointF> coverage = coveragePath(region, canvasWidth, canvasHeight, small.getWidth(), small.getHeight(), mode, index);
                if (coverage.size() > 1) {
                    actions.add(new StrokeAction(stage + "_COVERAGE", soften(region.color), Math.max(brush * 0.62f, 4.5f), coverage));
                }
            }
            index++;
        }

        addEdgePass(actions, small, canvasWidth, canvasHeight, mode);
        return new StrokePlan(source.getWidth(), source.getHeight(), mode.name(), actions);
    }

    private static boolean shouldSkipRegion(ShapeRegion region, int imageWidth, int imageHeight, int largeMassCount) {
        int imageArea = imageWidth * imageHeight;
        if (largeMassCount >= 2 && region.isLargeMass(imageWidth, imageHeight)) return true;
        if (isNearFullWidthBand(region, imageWidth, imageHeight)) return true;
        if (region.pixelCount < 9 && region.area() < 24) return true;
        if (region.area() > imageArea * 0.22f && region.density() < 0.18f) return true;
        return false;
    }

    private static boolean isNearFullWidthBand(ShapeRegion region, int imageWidth, int imageHeight) {
        return region.width() > imageWidth * 0.72f && region.height() < imageHeight * 0.10f && region.density() > 0.48f;
    }

    private static void addEdgePass(List<StrokeAction> actions, Bitmap small, int canvasWidth, int canvasHeight, DrawMode mode) {
        List<EdgeSegment> edges = EdgeExtractor.extract(small, edgeLimit(mode));
        int index = 0;
        for (EdgeSegment edge : edges) {
            List<PointF> path = edgePath(edge, canvasWidth, canvasHeight, small.getWidth(), small.getHeight(), mode, index);
            if (path.size() < 2) continue;
            String stage = index < polishStart(mode) ? "GRINDER_EDGE" : "POLISHER_ACCENT_EDGE";
            float size = index < polishStart(mode) ? 3.0f : 2.0f;
            actions.add(new StrokeAction(stage, edge.color, size, path));
            index++;
        }
    }

    private static int edgeLimit(DrawMode mode) {
        if (mode == DrawMode.PRINTER_DEBUG) return 32;
        if (mode == DrawMode.HUMAN_FAST) return 18;
        return 24;
    }

    private static int polishStart(DrawMode mode) {
        if (mode == DrawMode.PRINTER_DEBUG) return 24;
        if (mode == DrawMode.HUMAN_FAST) return 13;
        return 16;
    }

    private static List<PointF> edgePath(EdgeSegment edge, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight, DrawMode mode, int seedIndex) {
        List<PointF> points = new ArrayList<>();
        int stride = mode == DrawMode.PRINTER_DEBUG ? 1 : 2;
        for (int i = 0; i < edge.points.size(); i += stride) {
            Point p = edge.points.get(i);
            points.add(toCanvas(p.x, p.y, canvasWidth, canvasHeight, imageWidth, imageHeight));
        }
        if (mode == DrawMode.HUMAN_NATURAL) {
            return humanWobble(points, edge.color + seedIndex * 71, 1.1f);
        }
        return points;
    }

    private static int regionLimit(DrawMode mode) {
        if (mode == DrawMode.PRINTER_DEBUG) return 72;
        if (mode == DrawMode.HUMAN_FAST) return 48;
        return MAX_REGIONS;
    }

    private static int stageRank(ShapeRegion region, int imageWidth, int imageHeight) {
        if (region.isLargeMass(imageWidth, imageHeight)) return 0;
        if (region.pixelCount > imageWidth * imageHeight * 0.009f || region.density() > 0.30f) return 1;
        if (region.pixelCount > 12) return 2;
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
        if (rank == 0) return Math.max(12f, Math.min(28f, Math.max(region.width(), region.height()) * 0.15f));
        if (rank == 1) return Math.max(7f, Math.min(16f, Math.max(region.width(), region.height()) * 0.10f));
        if (rank == 2) return 5.0f;
        return 3.0f;
    }

    private static boolean needsCoveragePass(ShapeRegion region, int imageWidth, int imageHeight, DrawMode mode, int largeMassCount) {
        if (mode == DrawMode.PRINTER_DEBUG) return false;
        if (isNearFullWidthBand(region, imageWidth, imageHeight)) return false;
        int rank = stageRank(region, imageWidth, imageHeight);
        return rank <= 1 && region.area() > 130 && largeMassCount <= 2;
    }

    private static List<PointF> regionPath(ShapeRegion region, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight, DrawMode mode, int seedIndex) {
        List<PointF> points = new ArrayList<>();

        if (region.isLargeMass(imageWidth, imageHeight) && !isNearFullWidthBand(region, imageWidth, imageHeight)) {
            addBoxSweep(points, region, canvasWidth, canvasHeight, imageWidth, imageHeight, mode);
        }

        int stride = sampleStride(region, mode);
        int count = 0;
        for (Point sample : region.samples) {
            if ((count++ % stride) != 0) continue;
            points.add(toCanvas(sample.x, sample.y, canvasWidth, canvasHeight, imageWidth, imageHeight));
        }

        if (!isNearFullWidthBand(region, imageWidth, imageHeight)) {
            addRegionCorners(points, region, canvasWidth, canvasHeight, imageWidth, imageHeight);
        }

        if (points.size() < 3) {
            addBoxSweep(points, region, canvasWidth, canvasHeight, imageWidth, imageHeight, mode);
        }

        return humanOrder(points, mode, region.color + seedIndex * 31);
    }

    private static List<PointF> coveragePath(ShapeRegion region, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight, DrawMode mode, int seedIndex) {
        List<PointF> points = new ArrayList<>();
        int lines = Math.max(2, Math.min(6, region.height() / 5));
        for (int i = 0; i < lines; i++) {
            int y = region.minY + Math.round((i + 0.5f) * region.height() / lines);
            if (i % 2 == 0) {
                points.add(toCanvas(region.minX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
                points.add(toCanvas(region.maxX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
            } else {
                points.add(toCanvas(region.maxX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
                points.add(toCanvas(region.minX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
            }
        }
        if (mode == DrawMode.HUMAN_NATURAL) {
            return humanWobble(points, region.color + seedIndex * 101, 1.4f);
        }
        return points;
    }

    private static void addBoxSweep(List<PointF> points, ShapeRegion region, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight, DrawMode mode) {
        int lines = Math.max(2, Math.min(5, region.height() / 6));
        for (int i = 0; i < lines; i++) {
            int y = region.minY + Math.round((i + 0.5f) * region.height() / lines);
            if (i % 2 == 0) {
                points.add(toCanvas(region.minX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
                points.add(toCanvas(region.maxX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
            } else {
                points.add(toCanvas(region.maxX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
                points.add(toCanvas(region.minX, y, canvasWidth, canvasHeight, imageWidth, imageHeight));
            }
        }
        if (mode == DrawMode.HUMAN_NATURAL) {
            int midX = (region.minX + region.maxX) / 2;
            points.add(toCanvas(midX, region.minY, canvasWidth, canvasHeight, imageWidth, imageHeight));
            points.add(toCanvas(midX, region.maxY, canvasWidth, canvasHeight, imageWidth, imageHeight));
        }
    }

    private static void addRegionCorners(List<PointF> points, ShapeRegion region, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight) {
        points.add(toCanvas(region.minX, region.minY, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.maxX, region.minY, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.maxX, region.maxY, canvasWidth, canvasHeight, imageWidth, imageHeight));
        points.add(toCanvas(region.minX, region.maxY, canvasWidth, canvasHeight, imageWidth, imageHeight));
    }

    private static int sampleStride(ShapeRegion region, DrawMode mode) {
        if (mode == DrawMode.PRINTER_DEBUG) return 1;
        if (mode == DrawMode.HUMAN_FAST) return region.pixelCount > 240 ? 3 : 2;
        return region.pixelCount > 320 ? 2 : 1;
    }

    private static PointF toCanvas(int x, int y, int canvasWidth, int canvasHeight, int imageWidth, int imageHeight) {
        return new PointF((x / (float) imageWidth) * canvasWidth, (y / (float) imageHeight) * canvasHeight);
    }

    private static int soften(int color) {
        int r = Math.min(255, Color.red(color) + 8);
        int g = Math.min(255, Color.green(color) + 8);
        int b = Math.min(255, Color.blue(color) + 8);
        return Color.rgb(r, g, b);
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

    private static List<PointF> humanWobble(List<PointF> input, int seed, float amount) {
        Random random = new Random(seed);
        List<PointF> out = new ArrayList<>();
        for (PointF p : input) {
            out.add(new PointF(p.x + (random.nextFloat() - 0.5f) * amount, p.y + (random.nextFloat() - 0.5f) * amount));
        }
        return out;
    }
}
