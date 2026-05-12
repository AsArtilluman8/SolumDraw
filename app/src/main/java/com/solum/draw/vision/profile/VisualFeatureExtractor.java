package com.solum.draw.vision.profile;

import android.graphics.Bitmap;

public final class VisualFeatureExtractor {
    private static final int MAX_SIDE = 256;

    private VisualFeatureExtractor() {}

    public static VisualFeatureVector extract(Bitmap source) {
        long t0 = System.currentTimeMillis();
        VisualFeatureVector out = new VisualFeatureVector();

        if (source == null || source.getWidth() <= 1 || source.getHeight() <= 1) {
            out.computeTimeMs = System.currentTimeMillis() - t0;
            return out;
        }

        Bitmap bitmap = downscale(source, MAX_SIDE);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        out.analysisWidth = w;
        out.analysisHeight = h;

        int[] argb = new int[w * h];
        bitmap.getPixels(argb, 0, w, 0, 0, w, h);

        int[] gray = new int[w * h];
        float satSum = 0f;
        int coloredCount = 0;
        int[] hueBins = new int[36];
        int glowPixels = 0;

        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int r = (c >> 16) & 255;
            int g = (c >> 8) & 255;
            int b = c & 255;

            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000;

            float sat = max == 0 ? 0f : (max - min) / (float) max;
            satSum += sat;

            if (sat > 0.10f) {
                coloredCount++;
                int hue = hueDeg(r, g, b, max, min);
                int bin = Math.max(0, Math.min(35, hue / 10));
                hueBins[bin]++;
            }

            int brightness = (r + g + b) / 3;
            if (brightness > 180 && max > 200 && sat > 0.30f) {
                glowPixels++;
            }
        }

        out.saturation = clamp01(satSum / Math.max(1, argb.length));
        out.colorEntropy = hueEntropy(hueBins, coloredCount);
        out.glowScore = clamp01((glowPixels / (float) Math.max(1, argb.length)) / 0.15f);

        EdgeStats edge = computeEdges(gray, w, h);
        out.edgeDensity = edge.edgeDensity;
        out.hardLineScore = edge.hardLineScore;
        out.sharpness = computeSharpness(gray, w, h);

        out.computeTimeMs = System.currentTimeMillis() - t0;
        return out;
    }

    private static Bitmap downscale(Bitmap src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxSide) return src;

        float scale = maxSide / (float) longest;
        int nw = Math.max(2, Math.round(w * scale));
        int nh = Math.max(2, Math.round(h * scale));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static EdgeStats computeEdges(int[] gray, int w, int h) {
        EdgeStats st = new EdgeStats();
        if (w < 3 || h < 3) return st;

        boolean[] edge = new boolean[w * h];
        int edgeCount = 0;
        final int threshold = 30;

        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                int gx = gray[idx + 1] - gray[idx - 1];
                int gy = gray[idx + w] - gray[idx - w];
                int mag = Math.abs(gx) + Math.abs(gy);
                if (mag > threshold) {
                    edge[idx] = true;
                    edgeCount++;
                }
            }
        }

        float raw = edgeCount / (float) Math.max(1, (w - 2) * (h - 2));
        st.edgeDensity = clamp01(raw / 0.50f);

        int longRuns = 0;
        int minHRun = Math.max(8, Math.round(w * 0.15f));
        int minVRun = Math.max(8, Math.round(h * 0.15f));

        for (int y = 0; y < h; y++) {
            int run = 0;
            for (int x = 0; x < w; x++) {
                if (edge[y * w + x]) run++;
                else {
                    if (run >= minHRun) longRuns++;
                    run = 0;
                }
            }
            if (run >= minHRun) longRuns++;
        }

        for (int x = 0; x < w; x++) {
            int run = 0;
            for (int y = 0; y < h; y++) {
                if (edge[y * w + x]) run++;
                else {
                    if (run >= minVRun) longRuns++;
                    run = 0;
                }
            }
            if (run >= minVRun) longRuns++;
        }

        st.hardLineScore = clamp01(longRuns / 20f);
        return st;
    }

    private static float computeSharpness(int[] gray, int w, int h) {
        if (w < 3 || h < 3) return 0f;

        int n = 0;
        double sum = 0.0;
        double sum2 = 0.0;

        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                int lap = Math.abs(4 * gray[idx] - gray[idx - 1] - gray[idx + 1] - gray[idx - w] - gray[idx + w]);
                sum += lap;
                sum2 += lap * lap;
                n++;
            }
        }

        if (n <= 0) return 0f;
        double mean = sum / n;
        double variance = (sum2 / n) - (mean * mean);
        return clamp01((float) (variance / 1000.0));
    }

    private static float hueEntropy(int[] bins, int total) {
        if (total <= 0) return 0f;
        double e = 0.0;
        for (int count : bins) {
            if (count <= 0) continue;
            double p = count / (double) total;
            e -= p * (Math.log(p) / Math.log(2.0));
        }
        return clamp01((float) (e / (Math.log(36.0) / Math.log(2.0))));
    }

    private static int hueDeg(int r, int g, int b, int max, int min) {
        int delta = max - min;
        if (delta == 0) return 0;

        float h;
        if (max == r) h = 60f * (((g - b) / (float) delta) % 6f);
        else if (max == g) h = 60f * (((b - r) / (float) delta) + 2f);
        else h = 60f * (((r - g) / (float) delta) + 4f);

        if (h < 0f) h += 360f;
        return Math.max(0, Math.min(359, Math.round(h)));
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static final class EdgeStats {
        float edgeDensity = 0f;
        float hardLineScore = 0f;
    }
}
