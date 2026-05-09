package com.solum.draw.planner;

import android.graphics.Point;
import java.util.ArrayList;
import java.util.List;

public final class ShapeRegion {
    public final int color;
    public final int minX;
    public final int minY;
    public final int maxX;
    public final int maxY;
    public final int pixelCount;
    public final List<Point> samples;

    public ShapeRegion(int color, int minX, int minY, int maxX, int maxY, int pixelCount, List<Point> samples) {
        this.color = color;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.pixelCount = pixelCount;
        this.samples = new ArrayList<>(samples);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int area() {
        return Math.max(1, width() * height());
    }

    public float density() {
        return pixelCount / (float) area();
    }

    public boolean isLargeMass(int imageWidth, int imageHeight) {
        int imageArea = Math.max(1, imageWidth * imageHeight);
        return pixelCount > imageArea * 0.035f || area() > imageArea * 0.08f;
    }
}
