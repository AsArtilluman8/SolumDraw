package com.solum.draw.planner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ShapeExtractor {
    private static final int QUANT_STEP = 32;
    private static final int MAX_SAMPLES_PER_REGION = 96;

    private ShapeExtractor() {}

    public static List<ShapeRegion> extract(Bitmap bitmap, int maxRegions) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] labels = new int[width * height];
        for (int i = 0; i < labels.length; i++) labels[i] = quantize(bitmap.getPixel(i % width, i / width));

        boolean[] visited = new boolean[width * height];
        List<ShapeRegion> regions = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (visited[index]) continue;
                ShapeRegion region = flood(labels, visited, width, height, x, y);
                if (region.pixelCount >= 10 && region.width() >= 2 && region.height() >= 2) {
                    regions.add(region);
                }
            }
        }

        Collections.sort(regions, new Comparator<ShapeRegion>() {
            @Override public int compare(ShapeRegion a, ShapeRegion b) {
                return b.pixelCount - a.pixelCount;
            }
        });

        if (regions.size() > maxRegions) {
            return new ArrayList<>(regions.subList(0, maxRegions));
        }
        return regions;
    }

    private static ShapeRegion flood(int[] labels, boolean[] visited, int width, int height, int startX, int startY) {
        int target = labels[startY * width + startX];
        ArrayDeque<Point> queue = new ArrayDeque<>();
        queue.add(new Point(startX, startY));
        visited[startY * width + startX] = true;

        int minX = startX;
        int minY = startY;
        int maxX = startX;
        int maxY = startY;
        int count = 0;
        List<Point> samples = new ArrayList<>();

        while (!queue.isEmpty()) {
            Point p = queue.removeFirst();
            count++;
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
            if (samples.size() < MAX_SAMPLES_PER_REGION && (count % 3 == 0 || count < 12)) {
                samples.add(new Point(p.x, p.y));
            }

            visit(labels, visited, width, height, target, p.x + 1, p.y, queue);
            visit(labels, visited, width, height, target, p.x - 1, p.y, queue);
            visit(labels, visited, width, height, target, p.x, p.y + 1, queue);
            visit(labels, visited, width, height, target, p.x, p.y - 1, queue);
        }

        return new ShapeRegion(target, minX, minY, maxX, maxY, count, samples);
    }

    private static void visit(int[] labels, boolean[] visited, int width, int height, int target, int x, int y, ArrayDeque<Point> queue) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int index = y * width + x;
        if (visited[index]) return;
        if (labels[index] != target) return;
        visited[index] = true;
        queue.add(new Point(x, y));
    }

    private static int quantize(int color) {
        int r = (Color.red(color) / QUANT_STEP) * QUANT_STEP;
        int g = (Color.green(color) / QUANT_STEP) * QUANT_STEP;
        int b = (Color.blue(color) / QUANT_STEP) * QUANT_STEP;
        return Color.rgb(r, g, b);
    }
}
