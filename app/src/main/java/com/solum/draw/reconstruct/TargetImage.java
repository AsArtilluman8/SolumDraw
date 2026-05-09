package com.solum.draw.reconstruct;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class TargetImage {
    private final Bitmap bitmap;
    private final int width;
    private final int height;

    public TargetImage(Bitmap source, int maxSide) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        float scale = Math.min(1.0f, maxSide / (float) Math.max(srcW, srcH));
        int outW = Math.max(1, Math.round(srcW * scale));
        int outH = Math.max(1, Math.round(srcH * scale));
        this.bitmap = Bitmap.createScaledBitmap(source, outW, outH, true);
        this.width = bitmap.getWidth();
        this.height = bitmap.getHeight();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int colorAt(int x, int y) {
        return bitmap.getPixel(clamp(x, 0, width - 1), clamp(y, 0, height - 1));
    }

    public int lumaAt(int x, int y) {
        int c = colorAt(x, y);
        return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100;
    }

    public Bitmap bitmap() {
        return bitmap;
    }

    public String summary() {
        return "target=" + width + "x" + height;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
