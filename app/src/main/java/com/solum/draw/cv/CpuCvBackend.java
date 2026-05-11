package com.solum.draw.cv;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class CpuCvBackend implements CvBackend {
    private static final int GRID_W = 96;
    private static final int MAX_GRID_H = 160;

    @Override public String name() { return "cpu_fallback_cv"; }
    @Override public boolean isNativeAccelerated() { return false; }

    @Override public CvResult analyze(Bitmap source) {
        if (source == null) return new CvResult(0, 0, 0f, 0f, 0, new ArrayList<CvRegion>(), name(), "no source image");
        int sw = source.getWidth();
        int sh = source.getHeight();
        int gw = GRID_W;
        int gh = Math.max(32, Math.min(MAX_GRID_H, Math.round(gw * sh / (float)Math.max(1, sw))));
        int[] luma = new int[gw * gh];
        int sum = 0;
        for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++) {
            int sx = Math.min(sw - 1, Math.max(0, Math.round((x + .5f) * sw / gw)));
            int sy = Math.min(sh - 1, Math.max(0, Math.round((y + .5f) * sh / gh)));
            int lum = luma(source.getPixel(sx, sy));
            luma[y * gw + x] = lum;
            sum += lum;
        }
        int avg = sum / Math.max(1, gw * gh);
        boolean[] edge = new boolean[gw * gh];
        boolean[] mask = new boolean[gw * gh];
        int edges = 0;
        for (int y = 1; y < gh - 1; y++) for (int x = 1; x < gw - 1; x++) {
            int id = y * gw + x;
            int e = Math.abs(luma[id + 1] - luma[id - 1]) + Math.abs(luma[id + gw] - luma[id - gw]);
            boolean isEdge = e > 30;
            boolean salient = isEdge || Math.abs(luma[id] - avg) > 34;
            edge[id] = isEdge;
            mask[id] = salient;
            if (isEdge) edges++;
        }
        mask = morphClose(mask, gw, gh);
        boolean[] boundary = boundary(mask, gw, gh);
        int contour = 0;
        for (boolean v : boundary) if (v) contour++;
        List<CvRegion> regions = regions(mask, edge, gw, gh);
        Collections.sort(regions, new Comparator<CvRegion>() { @Override public int compare(CvRegion a, CvRegion b) { return Float.compare(b.importance, a.importance); } });
        String summary = "CPU fallback: edges=" + edges + ", contours=" + contour + ", islands=" + regions.size();
        return new CvResult(gw, gh, edges / (float)(gw * gh), contour / (float)(gw * gh), regions.size(), regions, name(), summary);
    }

    private static List<CvRegion> regions(boolean[] mask, boolean[] edge, int gw, int gh) {
        boolean[] seen = new boolean[mask.length];
        ArrayList<CvRegion> out = new ArrayList<>();
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        for (int y = 1; y < gh - 1; y++) for (int x = 1; x < gw - 1; x++) {
            int start = y * gw + x;
            if (!mask[start] || seen[start]) continue;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(start);
            seen[start] = true;
            int minX = x, maxX = x, minY = y, maxY = y, area = 0, edgeCount = 0;
            while (!q.isEmpty()) {
                int id = q.removeFirst();
                int px = id % gw;
                int py = id / gw;
                area++;
                if (edge[id]) edgeCount++;
                if (px < minX) minX = px;
                if (px > maxX) maxX = px;
                if (py < minY) minY = py;
                if (py > maxY) maxY = py;
                for (int k = 0; k < 4; k++) {
                    int nx = px + dx[k], ny = py + dy[k];
                    if (nx <= 0 || ny <= 0 || nx >= gw - 1 || ny >= gh - 1) continue;
                    int nid = ny * gw + nx;
                    if (!seen[nid] && mask[nid]) { seen[nid] = true; q.add(nid); }
                }
            }
            if (area < 6) continue;
            float left = minX / (float)gw;
            float top = minY / (float)gh;
            float width = Math.max(1, maxX - minX + 1) / (float)gw;
            float height = Math.max(1, maxY - minY + 1) / (float)gh;
            float cx = left + width * .5f;
            float cy = top + height * .5f;
            float center = 1f - Math.min(.85f, Math.abs(cx - .5f) + Math.abs(cy - .55f) * .65f);
            float density = area / (float)Math.max(1, (maxX - minX + 1) * (maxY - minY + 1));
            float importance = density * .45f + center * .25f + Math.min(.30f, edgeCount / (float)Math.max(1, area));
            out.add(new CvRegion("island", left, top, width, height, area, importance));
        }
        return out;
    }

    private static boolean[] morphClose(boolean[] src, int w, int h) {
        boolean[] dil = new boolean[src.length];
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int id = y * w + x;
            boolean v = false;
            for (int yy = -1; yy <= 1 && !v; yy++) for (int xx = -1; xx <= 1; xx++) if (src[id + yy * w + xx]) { v = true; break; }
            dil[id] = v;
        }
        boolean[] ero = new boolean[src.length];
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int id = y * w + x;
            boolean v = true;
            for (int yy = -1; yy <= 1 && v; yy++) for (int xx = -1; xx <= 1; xx++) if (!dil[id + yy * w + xx]) { v = false; break; }
            ero[id] = v;
        }
        return ero;
    }

    private static boolean[] boundary(boolean[] mask, int w, int h) {
        boolean[] b = new boolean[mask.length];
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int id = y * w + x;
            if (!mask[id]) continue;
            b[id] = !mask[id + 1] || !mask[id - 1] || !mask[id + w] || !mask[id - w];
        }
        return b;
    }

    private static int luma(int c) { return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100; }
}
