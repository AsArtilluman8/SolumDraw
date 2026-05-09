package com.solum.draw.planner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class EdgeExtractor {
    private EdgeExtractor() {}

    public static List<EdgeSegment> extract(Bitmap bitmap, int maxSegments) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        List<EdgeSegment> segments = new ArrayList<>();

        for (int y = 2; y < height - 2; y += 3) {
            List<Point> run = new ArrayList<>();
            int runStrength = 0;
            int runColor = Color.WHITE;

            for (int x = 2; x < width - 2; x += 2) {
                int strength = edgeStrength(bitmap, x, y);
                if (strength > 38) {
                    if (run.isEmpty()) {
                        runColor = edgeColor(bitmap.getPixel(x, y));
                    }
                    run.add(new Point(x, y));
                    runStrength += strength;
                } else {
                    if (run.size() >= 3) {
                        segments.add(new EdgeSegment(runColor, runStrength / run.size(), run));
                    }
                    run = new ArrayList<>();
                    runStrength = 0;
                }
            }
            if (run.size() >= 3) {
                segments.add(new EdgeSegment(runColor, runStrength / run.size(), run));
            }
        }

        Collections.sort(segments, new Comparator<EdgeSegment>() {
            @Override public int compare(EdgeSegment a, EdgeSegment b) {
                int scoreA = a.strength * Math.min(24, a.points.size());
                int scoreB = b.strength * Math.min(24, b.points.size());
                return scoreB - scoreA;
            }
        });

        if (segments.size() > maxSegments) {
            return new ArrayList<>(segments.subList(0, maxSegments));
        }
        return segments;
    }

    private static int edgeStrength(Bitmap bitmap, int x, int y) {
        int l = luma(bitmap.getPixel(x - 1, y));
        int r = luma(bitmap.getPixel(x + 1, y));
        int u = luma(bitmap.getPixel(x, y - 1));
        int d = luma(bitmap.getPixel(x, y + 1));
        return Math.abs(r - l) + Math.abs(d - u);
    }

    private static int luma(int color) {
        return (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100;
    }

    private static int edgeColor(int color) {
        int l = luma(color);
        if (l > 160) {
            return Color.rgb(235, 220, 200);
        }
        if (l > 90) {
            return Color.rgb(165, 125, 95);
        }
        return Color.rgb(45, 38, 34);
    }
}
