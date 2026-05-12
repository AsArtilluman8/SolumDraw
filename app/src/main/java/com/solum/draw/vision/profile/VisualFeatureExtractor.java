package com.solum.draw.vision.profile;

import android.graphics.Bitmap;

import java.util.HashSet;
import java.util.Set;

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
        int[] qColors = new int[w * h];
        float satSum = 0f;
        int coloredCount = 0;
        int[] hueBins = new int[36];
        int glowPixels = 0;

        Set<Integer> palette = new HashSet<>();

        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int r = (c >> 16) & 255;
            int g = (c >> 8) & 255;
            int b = c & 255;

            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000;

            int qr = r >> 4;
            int qg = g >> 4;
            int qb = b >> 4;
            int q = (qr << 8) | (qg << 4) | qb;
            qColors[i] = q;
            palette.add(q);

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
        out.textDensity = edge.textDensity;
        out.softEdgeRatio = edge.softEdgeRatio;

        out.sharpness = computeSharpness(gray, w, h);
        out.tileRepetition = computeTileRepetition(argb, w, h);
        out.symmetryScore = computeSymmetry(gray, w, h);
        out.pixelGridScore = computePixelGridScore(out.edgeDensity, out.colorEntropy, out.saturation, palette.size());

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

        boolean[] strongEdge = new boolean[w * h];
        int strongCount = 0;
        int mediumCount = 0;
        final int mediumThreshold = 25;
        final int strongThreshold = 55;

        int textRuns = 0;

        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            int rowRun = 0;

            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                int gx = gray[idx + 1] - gray[idx - 1];
                int gy = gray[idx + w] - gray[idx - w];
                int mag = Math.abs(gx) + Math.abs(gy);

                if (mag > mediumThreshold) mediumCount++;

                if (mag > strongThreshold) {
                    strongEdge[idx] = true;
                    strongCount++;
                    rowRun++;
                } else {
                    if (rowRun >= 4 && rowRun <= Math.max(8, w / 4)) textRuns++;
                    rowRun = 0;
                }
            }

            if (rowRun >= 4 && rowRun <= Math.max(8, w / 4)) textRuns++;
        }

        float rawStrong = strongCount / (float) Math.max(1, (w - 2) * (h - 2));
        float rawMedium = mediumCount / (float) Math.max(1, (w - 2) * (h - 2));
        st.edgeDensity = clamp01(rawStrong);
        st.softEdgeRatio = clamp01((rawMedium - rawStrong) / Math.max(0.0001f, rawMedium));

        int longRuns = 0;
        int minHRun = Math.max(8, Math.round(w * 0.15f));
        int minVRun = Math.max(8, Math.round(h * 0.15f));

        for (int y = 0; y < h; y++) {
            int run = 0;
            for (int x = 0; x < w; x++) {
                if (strongEdge[y * w + x]) run++;
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
                if (strongEdge[y * w + x]) run++;
                else {
                    if (run >= minVRun) longRuns++;
                    run = 0;
                }
            }
            if (run >= minVRun) longRuns++;
        }

        st.hardLineScore = clamp01(longRuns / 80f);
        st.textDensity = clamp01(textRuns / (float) Math.max(1, h * 3));
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
        return clamp01((float) (Math.log1p(Math.max(0.0, variance)) / Math.log1p(20000.0)));
    }

    private static float computeTileRepetition(int[] argb, int w, int h) {
        if (w < 32 || h < 32) return 0f;

        final int grid = 4;
        int[] avg = new int[grid * grid];

        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                int x0 = gx * w / grid;
                int x1 = (gx + 1) * w / grid;
                int y0 = gy * h / grid;
                int y1 = (gy + 1) * h / grid;

                long sr = 0, sg = 0, sb = 0;
                int n = 0;

                for (int y = y0; y < y1; y += 2) {
                    for (int x = x0; x < x1; x += 2) {
                        int c = argb[y * w + x];
                        sr += (c >> 16) & 255;
                        sg += (c >> 8) & 255;
                        sb += c & 255;
                        n++;
                    }
                }

                if (n <= 0) n = 1;
                int r = (int) (sr / n);
                int g = (int) (sg / n);
                int b = (int) (sb / n);
                avg[gy * grid + gx] = (r << 16) | (g << 8) | b;
            }
        }

        float similarity = 0f;
        int pairs = 0;

        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                int i = gy * grid + gx;
                if (gx + 1 < grid) {
                    similarity += colorSimilarity(avg[i], avg[gy * grid + gx + 1]);
                    pairs++;
                }
                if (gy + 1 < grid) {
                    similarity += colorSimilarity(avg[i], avg[(gy + 1) * grid + gx]);
                    pairs++;
                }
            }
        }

        return clamp01(similarity / Math.max(1, pairs));
    }

    private static float computeSymmetry(int[] gray, int w, int h) {
        if (w < 4 || h < 4) return 0f;

        long diff = 0;
        long n = 0;

        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w / 2; x += 2) {
                int a = gray[y * w + x];
                int b = gray[y * w + (w - 1 - x)];
                diff += Math.abs(a - b);
                n++;
            }
        }

        float meanDiff = diff / (float) Math.max(1, n);
        return clamp01(1f - meanDiff / 96f);
    }

    private static float computePixelGridScore(float edgeDensity, float colorEntropy, float saturation, int paletteSize) {
        float paletteScore = clamp01((96f - Math.min(96f, paletteSize)) / 96f);
        float edgeScore = clamp01(edgeDensity / 0.45f);
        float entropyScore = clamp01((0.65f - colorEntropy) / 0.65f);
        float satScore = saturation > 0.12f ? 1f : 0.5f;
        return clamp01(paletteScore * 0.35f + edgeScore * 0.30f + entropyScore * 0.25f + satScore * 0.10f);
    }

    private static float colorSimilarity(int ca, int cb) {
        int ar = (ca >> 16) & 255;
        int ag = (ca >> 8) & 255;
        int ab = ca & 255;
        int br = (cb >> 16) & 255;
        int bg = (cb >> 8) & 255;
        int bb = cb & 255;
        float mae = (Math.abs(ar - br) + Math.abs(ag - bg) + Math.abs(ab - bb)) / 3f;
        return clamp01(1f - mae / 128f);
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
        float textDensity = 0f;
        float softEdgeRatio = 0f;
    }
}
