package com.solum.draw.analyze;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AnalysisLayers {
    public final int width;
    public final int height;
    public final float[] gray;
    public final float[] blur;
    public final float[] edges;
    public final float[] saliency;
    public final int[] histogram;
    public final List<ColorCluster> clusters;
    public final List<Region> attentionRegions;
    public final RectF foregroundBox;
    public final float centralObjectRatio;
    public final float edgeDensity;
    public final float saliencyDensity;
    public final float symmetryVertical;
    public final float logoScore;
    public final float glyphScore;
    public final float realTextScore;

    private AnalysisLayers(int width, int height, float[] gray, float[] blur, float[] edges, float[] saliency,
            int[] histogram, List<ColorCluster> clusters, List<Region> attentionRegions, RectF foregroundBox,
            float centralObjectRatio, float edgeDensity, float saliencyDensity, float symmetryVertical,
            float logoScore, float glyphScore, float realTextScore) {
        this.width = width;
        this.height = height;
        this.gray = gray;
        this.blur = blur;
        this.edges = edges;
        this.saliency = saliency;
        this.histogram = histogram;
        this.clusters = clusters;
        this.attentionRegions = attentionRegions;
        this.foregroundBox = foregroundBox;
        this.centralObjectRatio = centralObjectRatio;
        this.edgeDensity = edgeDensity;
        this.saliencyDensity = saliencyDensity;
        this.symmetryVertical = symmetryVertical;
        this.logoScore = logoScore;
        this.glyphScore = glyphScore;
        this.realTextScore = realTextScore;
    }

    public static AnalysisLayers build(Bitmap source) {
        Bitmap bmp = scaleMax(source, 192);
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float[] gray = grayLinear(bmp);
        float[] blur = gaussian(gray, w, h, 2);
        float[] edges = sobel(blur, w, h);
        float[] saliency = saliency(gray, w, h);
        int[] histogram = histogram(gray);
        List<ColorCluster> clusters = kmeans(bmp, 6, 10);
        RectF fg = foregroundBox(bmp, clusters);
        float central = centralRatio(fg, w, h);
        float edgeDensity = density(edges, 0.12f);
        float saliencyDensity = density(saliency, 0.35f);
        float symmetry = verticalSymmetry(edges, w, h);
        float realText = textRows(edges, w, h);
        float glyph = glyphScore(fg, central, edgeDensity, realText, symmetry, w, h);
        float logo = clamp01(glyph * 0.65f + symmetry * 0.20f + (clusters.isEmpty() ? 0f : clusters.get(0).ratio) * 0.35f - realText * 0.45f);
        List<Region> attention = attentionRegions(saliency, edges, w, h, 6);
        return new AnalysisLayers(w, h, gray, blur, edges, saliency, histogram, clusters, attention, fg, central,
                edgeDensity, saliencyDensity, symmetry, logo, glyph, realText);
    }

    public Bitmap makeAttentionBitmap(Bitmap source) {
        Bitmap base = Bitmap.createScaledBitmap(source, width, height, true).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(base);
        Paint dim = new Paint();
        dim.setColor(0x99000000);
        c.drawRect(0, 0, width, height, dim);
        Paint heat = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                float v = Math.max(saliency[y * width + x], edges[y * width + x] * 0.75f);
                if (v < 0.18f) continue;
                int a = Math.min(190, (int)(v * 220));
                heat.setColor(Color.argb(a, 0, 255, 136));
                c.drawRect(x, y, x + 2, y + 2, heat);
            }
        }
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f);
        ring.setColor(0xFFFFCC00);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTextSize(11f);
        label.setFakeBoldText(true);
        for (int i = 0; i < attentionRegions.size(); i++) {
            Region r = attentionRegions.get(i);
            c.drawOval(r.rect, ring);
            c.drawText(String.valueOf(i + 1), r.rect.centerX(), r.rect.centerY(), label);
        }
        return base;
    }

    public Bitmap makeEdgeBitmap() {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float e = edges[y * width + x];
                int a = e > 0.10f ? Math.min(255, (int)(e * 255)) : 0;
                out.setPixel(x, y, Color.argb(a, 0, 255, 136));
            }
        }
        return out;
    }

    public Bitmap makePriorityBitmap(Bitmap source) {
        Bitmap out = Bitmap.createScaledBitmap(source, width, height, true).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0xFF00E5FF);
        c.drawRect(foregroundBox, p);
        p.setColor(0xFFFFCC00);
        for (Region r : attentionRegions) c.drawOval(r.rect, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(10f);
        p.setFakeBoldText(true);
        p.setColor(Color.WHITE);
        c.drawText("1 BG", 6, 14, p);
        c.drawText("2 MAIN", foregroundBox.left + 4, Math.max(28, foregroundBox.top + 14), p);
        c.drawText("3 EDGES", Math.max(6, foregroundBox.right - 56), Math.min(height - 8, foregroundBox.bottom - 8), p);
        return out;
    }

    public String metricsJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        kv(b, "layerWidth", width, true);
        kv(b, "layerHeight", height, true);
        kv(b, "edgeDensity", edgeDensity, true);
        kv(b, "saliencyDensity", saliencyDensity, true);
        kv(b, "centralObjectRatio", centralObjectRatio, true);
        kv(b, "symmetryVertical", symmetryVertical, true);
        kv(b, "logoScore", logoScore, true);
        kv(b, "glyphScore", glyphScore, true);
        kv(b, "realTextScore", realTextScore, false);
        b.append("\n}");
        return b.toString();
    }

    private static float[] grayLinear(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        float[] g = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = bmp.getPixel(x, y);
            float r = gamma(Color.red(c) / 255f), gr = gamma(Color.green(c) / 255f), b = gamma(Color.blue(c) / 255f);
            g[y * w + x] = (0.2126f * r + 0.7152f * gr + 0.0722f * b);
        }
        return g;
    }

    private static float gamma(float v) { return v <= 0.04045f ? v / 12.92f : (float)Math.pow((v + 0.055f) / 1.055f, 2.4); }

    private static float[] gaussian(float[] src, int w, int h, int r) {
        float[] tmp = new float[w * h];
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float s = 0, wt = 0;
            for (int dx = -r; dx <= r; dx++) { int nx = clamp(x + dx, 0, w - 1); float gw = (float)Math.exp(-(dx * dx) / (2f * r * r)); s += src[y * w + nx] * gw; wt += gw; }
            tmp[y * w + x] = s / Math.max(0.0001f, wt);
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float s = 0, wt = 0;
            for (int dy = -r; dy <= r; dy++) { int ny = clamp(y + dy, 0, h - 1); float gw = (float)Math.exp(-(dy * dy) / (2f * r * r)); s += tmp[ny * w + x] * gw; wt += gw; }
            out[y * w + x] = s / Math.max(0.0001f, wt);
        }
        return out;
    }

    private static float[] sobel(float[] g, int w, int h) {
        float[] e = new float[w * h];
        float max = 0f;
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            float gx = -g[(y-1)*w+x-1] + g[(y-1)*w+x+1] - 2*g[y*w+x-1] + 2*g[y*w+x+1] - g[(y+1)*w+x-1] + g[(y+1)*w+x+1];
            float gy = -g[(y-1)*w+x-1] - 2*g[(y-1)*w+x] - g[(y-1)*w+x+1] + g[(y+1)*w+x-1] + 2*g[(y+1)*w+x] + g[(y+1)*w+x+1];
            float v = (float)Math.sqrt(gx * gx + gy * gy);
            e[y * w + x] = v;
            if (v > max) max = v;
        }
        if (max > 0f) for (int i = 0; i < e.length; i++) e[i] /= max;
        return e;
    }

    private static float[] saliency(float[] g, int w, int h) {
        int bs = Math.max(6, Math.min(w, h) / 14);
        float[] sal = new float[w * h];
        float max = 0f;
        for (int by = 0; by < h; by += bs) for (int bx = 0; bx < w; bx += bs) {
            float sum = 0, sum2 = 0; int cnt = 0;
            for (int y = by; y < Math.min(h, by + bs); y++) for (int x = bx; x < Math.min(w, bx + bs); x++) { float v = g[y * w + x]; sum += v; sum2 += v * v; cnt++; }
            float mean = sum / Math.max(1, cnt);
            float variance = Math.max(0f, sum2 / Math.max(1, cnt) - mean * mean);
            if (variance > max) max = variance;
            for (int y = by; y < Math.min(h, by + bs); y++) for (int x = bx; x < Math.min(w, bx + bs); x++) sal[y * w + x] = variance;
        }
        if (max > 0f) for (int i = 0; i < sal.length; i++) sal[i] /= max;
        return sal;
    }

    private static int[] histogram(float[] gray) {
        int[] h = new int[64];
        for (float v : gray) h[clamp((int)(v * 63f), 0, 63)]++;
        return h;
    }

    private static List<ColorCluster> kmeans(Bitmap bmp, int k, int iterations) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int step = Math.max(1, Math.min(w, h) / 72);
        ArrayList<int[]> samples = new ArrayList<>();
        for (int y = 0; y < h; y += step) for (int x = 0; x < w; x += step) { int c = bmp.getPixel(x, y); samples.add(new int[]{Color.red(c), Color.green(c), Color.blue(c)}); }
        ArrayList<float[]> centers = new ArrayList<>();
        for (int i = 0; i < k; i++) { int[] s = samples.get((i * samples.size()) / k); centers.add(new float[]{s[0], s[1], s[2]}); }
        int[] assign = new int[samples.size()];
        for (int it = 0; it < iterations; it++) {
            float[][] sums = new float[k][3]; int[] counts = new int[k];
            for (int i = 0; i < samples.size(); i++) {
                int[] s = samples.get(i); int best = 0; float bestD = Float.MAX_VALUE;
                for (int c = 0; c < k; c++) { float[] cc = centers.get(c); float d = dist2(s[0], s[1], s[2], cc); if (d < bestD) { bestD = d; best = c; } }
                assign[i] = best; counts[best]++; sums[best][0] += s[0]; sums[best][1] += s[1]; sums[best][2] += s[2];
            }
            for (int c = 0; c < k; c++) if (counts[c] > 0) centers.set(c, new float[]{sums[c][0] / counts[c], sums[c][1] / counts[c], sums[c][2] / counts[c]});
        }
        int[] counts = new int[k];
        for (int a : assign) counts[a]++;
        ArrayList<ColorCluster> out = new ArrayList<>();
        for (int c = 0; c < k; c++) { float[] cc = centers.get(c); out.add(new ColorCluster(Color.rgb(clamp((int)cc[0],0,255), clamp((int)cc[1],0,255), clamp((int)cc[2],0,255)), counts[c] / (float)Math.max(1, samples.size()))); }
        Collections.sort(out, new Comparator<ColorCluster>() { @Override public int compare(ColorCluster a, ColorCluster b) { return Float.compare(b.ratio, a.ratio); } });
        return out;
    }

    private static RectF foregroundBox(Bitmap bmp, List<ColorCluster> clusters) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int bg = clusters.isEmpty() ? bmp.getPixel(0, 0) : clusters.get(0).color;
        int minX = w, minY = h, maxX = 0, maxY = 0, count = 0;
        for (int y = 0; y < h; y += 2) for (int x = 0; x < w; x += 2) { int c = bmp.getPixel(x, y); if (colorDist(c, bg) > 70) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); count++; } }
        if (count < 30) return new RectF(w * .2f, h * .2f, w * .8f, h * .8f);
        return new RectF(Math.max(0, minX - 4), Math.max(0, minY - 4), Math.min(w, maxX + 4), Math.min(h, maxY + 4));
    }

    private static List<Region> attentionRegions(float[] sal, float[] edges, int w, int h, int maxRegions) {
        int cellsX = 8, cellsY = 8;
        ArrayList<Region> list = new ArrayList<>();
        for (int cy = 0; cy < cellsY; cy++) for (int cx = 0; cx < cellsX; cx++) {
            int x0 = cx * w / cellsX, x1 = (cx + 1) * w / cellsX, y0 = cy * h / cellsY, y1 = (cy + 1) * h / cellsY;
            float s = 0; int n = 0;
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { int i = y * w + x; s += sal[i] * 0.65f + edges[i] * 0.35f; n++; }
            list.add(new Region(new RectF(x0, y0, x1, y1), s / Math.max(1, n)));
        }
        Collections.sort(list, new Comparator<Region>() { @Override public int compare(Region a, Region b) { return Float.compare(b.score, a.score); } });
        if (list.size() > maxRegions) return new ArrayList<>(list.subList(0, maxRegions));
        return list;
    }

    private static float verticalSymmetry(float[] e, int w, int h) {
        float diff = 0, sum = 0;
        for (int y = 0; y < h; y += 2) for (int x = 0; x < w / 2; x += 2) { float a = e[y * w + x], b = e[y * w + (w - 1 - x)]; diff += Math.abs(a - b); sum += Math.max(a, b); }
        return clamp01(1f - diff / Math.max(0.0001f, sum));
    }

    private static float textRows(float[] edges, int w, int h) {
        int rows = 0;
        for (int y = 0; y < h; y += 3) { int hits = 0; for (int x = 0; x < w; x += 2) if (edges[y * w + x] > 0.16f) hits++; if (hits > w / 18 && hits < w / 3) rows++; }
        return clamp01(rows / (float)Math.max(1, h / 3));
    }

    private static float glyphScore(RectF fg, float central, float edgeDensity, float realText, float symmetry, int w, int h) {
        float area = (fg.width() * fg.height()) / Math.max(1f, w * h);
        return clamp01(central * 0.45f + area * 0.25f + edgeDensity * 0.8f + symmetry * 0.20f - realText * 0.60f);
    }

    private static float centralRatio(RectF r, int w, int h) {
        RectF center = new RectF(w * .25f, h * .25f, w * .75f, h * .75f);
        RectF inter = new RectF(Math.max(r.left, center.left), Math.max(r.top, center.top), Math.min(r.right, center.right), Math.min(r.bottom, center.bottom));
        if (inter.left >= inter.right || inter.top >= inter.bottom) return 0f;
        return clamp01((inter.width() * inter.height()) / Math.max(1f, r.width() * r.height()));
    }

    private static float density(float[] a, float threshold) { int n = 0; for (float v : a) if (v > threshold) n++; return n / (float)Math.max(1, a.length); }
    private static float dist2(int r, int g, int b, float[] c) { float dr = r - c[0], dg = g - c[1], db = b - c[2]; return dr * dr + dg * dg + db * db; }
    private static int colorDist(int a, int b) { return Math.abs(Color.red(a)-Color.red(b)) + Math.abs(Color.green(a)-Color.green(b)) + Math.abs(Color.blue(a)-Color.blue(b)); }
    private static Bitmap scaleMax(Bitmap source, int maxSide) { float s = Math.min(1f, maxSide / (float)Math.max(source.getWidth(), source.getHeight())); return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth()*s)), Math.max(1, Math.round(source.getHeight()*s)), true); }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static void kv(StringBuilder b, String k, int v, boolean comma) { b.append("  \"").append(k).append("\": ").append(v); if (comma) b.append(","); b.append("\n"); }
    private static void kv(StringBuilder b, String k, float v, boolean comma) { b.append("  \"").append(k).append("\": ").append(String.format(java.util.Locale.US, "%.4f", v)); if (comma) b.append(","); b.append("\n"); }

    public static final class Region { public final RectF rect; public final float score; Region(RectF rect, float score) { this.rect = rect; this.score = score; } }
    public static final class ColorCluster { public final int color; public final float ratio; ColorCluster(int color, float ratio) { this.color = color; this.ratio = ratio; } public String hex() { return String.format("#%06X", 0xFFFFFF & color); } }
}
