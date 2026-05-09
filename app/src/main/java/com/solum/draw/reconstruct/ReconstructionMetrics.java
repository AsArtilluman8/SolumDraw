package com.solum.draw.reconstruct;

import android.graphics.Color;

public final class ReconstructionMetrics {
    public final long totalError;
    public final int averageError;
    public final int width;
    public final int height;

    public ReconstructionMetrics(long totalError, int averageError, int width, int height) {
        this.totalError = totalError;
        this.averageError = averageError;
        this.width = width;
        this.height = height;
    }

    public static ReconstructionMetrics compare(TargetImage target, VirtualCanvas canvas) {
        int width = Math.min(target.width(), canvas.width());
        int height = Math.min(target.height(), canvas.height());
        long total = 0;
        int count = Math.max(1, width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                total += colorDistance(target.colorAt(x, y), canvas.colorAt(x, y));
            }
        }
        return new ReconstructionMetrics(total, (int) (total / count), width, height);
    }

    public String summary() {
        return "reconstructMetrics=" + width + "x" + height + " totalError=" + totalError + " averageError=" + averageError;
    }

    private static int colorDistance(int a, int b) {
        int dr = Color.red(a) - Color.red(b);
        int dg = Color.green(a) - Color.green(b);
        int db = Color.blue(a) - Color.blue(b);
        return Math.abs(dr) + Math.abs(dg) + Math.abs(db);
    }
}
