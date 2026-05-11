package com.solum.draw.preview;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class VisionRegionMap {
    private static final int GRID_W = 72;
    private static final int GRID_H = 72;

    public final int gridWidth;
    public final int gridHeight;
    public final List<Region> regions;
    public final Region mainRegion;
    public final Region backgroundRegion;
    private final boolean[] edge;
    private final boolean[] salient;

    private VisionRegionMap(int gridWidth, int gridHeight, boolean[] edge, boolean[] salient, List<Region> regions, Region mainRegion, Region backgroundRegion) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.edge = edge;
        this.salient = salient;
        this.regions = regions;
        this.mainRegion = mainRegion;
        this.backgroundRegion = backgroundRegion;
    }

    public static VisionRegionMap analyze(Bitmap source) {
        if (source == null) return empty();
        int sw = source.getWidth();
        int sh = source.getHeight();
        int gw = GRID_W;
        int gh = Math.max(24, Math.round(GRID_W * sh / (float)Math.max(1, sw)));
        if (gh > GRID_H * 2) gh = GRID_H * 2;
        int[] luma = new int[gw * gh];
        int[] sat = new int[gw * gh];
        int sumL = 0;
        int sumS = 0;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int ix = Math.min(sw - 1, Math.max(0, Math.round((x + 0.5f) * sw / gw)));
                int iy = Math.min(sh - 1, Math.max(0, Math.round((y + 0.5f) * sh / gh)));
                int c = source.getPixel(ix, iy);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int lum = (r * 30 + g * 59 + b * 11) / 100;
                int mx = Math.max(r, Math.max(g, b));
                int mn = Math.min(r, Math.min(g, b));
                int id = y * gw + x;
                luma[id] = lum;
                sat[id] = mx - mn;
                sumL += lum;
                sumS += mx - mn;
            }
        }
        int avgL = sumL / Math.max(1, gw * gh);
        int avgS = sumS / Math.max(1, gw * gh);
        boolean[] edge = new boolean[gw * gh];
        boolean[] salient = new boolean[gw * gh];
        int edgeCount = 0;
        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int id = y * gw + x;
                int gx = Math.abs(luma[id + 1] - luma[id - 1]);
                int gy = Math.abs(luma[id + gw] - luma[id - gw]);
                int e = gx + gy;
                boolean isEdge = e > 34;
                boolean isSalient = isEdge || Math.abs(luma[id] - avgL) > 28 || Math.abs(sat[id] - avgS) > 34;
                edge[id] = isEdge;
                salient[id] = isSalient;
                if (isEdge) edgeCount++;
            }
        }
        if (edgeCount < gw * gh / 50) {
            for (int y = 1; y < gh - 1; y++) for (int x = 1; x < gw - 1; x++) {
                int id = y * gw + x;
                int e = Math.abs(luma[id + 1] - luma[id - 1]) + Math.abs(luma[id + gw] - luma[id - gw]);
                if (e > 20) { edge[id] = true; salient[id] = true; }
            }
        }
        List<Region> regions = connectedRegions(gw, gh, salient, edge);
        Collections.sort(regions, new Comparator<Region>() { @Override public int compare(Region a, Region b) { return Float.compare(b.importance, a.importance); } });
        Region main = regions.isEmpty() ? new Region("объект", 0.50f, 0.55f, 0.20f, 0.20f, 0, 0) : regions.get(0);
        Region background = new Region("фон", 0.50f, 0.16f, 1.0f, 0.24f, gw * gh, 0.35f);
        return new VisionRegionMap(gw, gh, edge, salient, regions, main, background);
    }

    public List<RoutePoint> routePoints() {
        ArrayList<RoutePoint> pts = new ArrayList<>();
        pts.add(new RoutePoint(1, "фон", backgroundRegion.cx, backgroundRegion.cy));
        Region main = mainRegion;
        pts.add(new RoutePoint(2, "масса", main.cx, clamp(main.top + main.h * 0.35f, 0.08f, 0.92f)));
        pts.add(new RoutePoint(3, "объект", main.cx, main.cy));
        Region shadow = pickLowerRegion(main);
        pts.add(new RoutePoint(4, "тень", shadow.cx, clamp(shadow.cy + shadow.h * 0.15f, 0.08f, 0.94f)));
        Region detail = pickDetailRegion(main);
        pts.add(new RoutePoint(5, "детали", detail.cx, detail.cy));
        return pts;
    }

    public List<Region> contourRegions() {
        ArrayList<Region> out = new ArrayList<>();
        int n = Math.min(8, regions.size());
        for (int i = 0; i < n; i++) {
            Region r = regions.get(i);
            if (r.area > 6 && r.w > 0.03f && r.h > 0.03f) out.add(r);
        }
        if (out.isEmpty()) out.add(mainRegion);
        return out;
    }

    public boolean isEdgeCell(int x, int y) {
        if (x < 0 || y < 0 || x >= gridWidth || y >= gridHeight) return false;
        return edge[y * gridWidth + x];
    }

    private Region pickLowerRegion(Region fallback) {
        for (Region r : regions) if (r.cy > fallback.cy && Math.abs(r.cx - fallback.cx) < 0.35f) return r;
        return fallback;
    }

    private Region pickDetailRegion(Region fallback) {
        for (Region r : regions) if (r != fallback && r.importance > fallback.importance * 0.25f) return r;
        return fallback;
    }

    private static List<Region> connectedRegions(int gw, int gh, boolean[] mask, boolean[] edge) {
        boolean[] seen = new boolean[gw * gh];
        ArrayList<Region> regions = new ArrayList<>();
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int start = y * gw + x;
                if (!mask[start] || seen[start]) continue;
                ArrayDeque<Integer> q = new ArrayDeque<>();
                q.add(start);
                seen[start] = true;
                int minX = x, maxX = x, minY = y, maxY = y, count = 0, edgeCount = 0;
                while (!q.isEmpty()) {
                    int id = q.removeFirst();
                    int px = id % gw;
                    int py = id / gw;
                    count++;
                    if (edge[id]) edgeCount++;
                    if (px < minX) minX = px; if (px > maxX) maxX = px;
                    if (py < minY) minY = py; if (py > maxY) maxY = py;
                    for (int k = 0; k < 4; k++) {
                        int nx = px + dx[k], ny = py + dy[k];
                        if (nx <= 0 || ny <= 0 || nx >= gw - 1 || ny >= gh - 1) continue;
                        int nid = ny * gw + nx;
                        if (!seen[nid] && mask[nid]) { seen[nid] = true; q.add(nid); }
                    }
                }
                if (count >= 5) {
                    float left = minX / (float)gw;
                    float top = minY / (float)gh;
                    float w = Math.max(1, maxX - minX + 1) / (float)gw;
                    float h = Math.max(1, maxY - minY + 1) / (float)gh;
                    float cx = left + w * 0.5f;
                    float cy = top + h * 0.5f;
                    float centerBias = 1.0f - Math.min(0.75f, Math.abs(cx - 0.5f) + Math.abs(cy - 0.54f) * 0.7f);
                    float area = count / (float)(gw * gh);
                    float importance = area * 1.8f + edgeCount / (float)Math.max(1, count) * 0.6f + centerBias * 0.22f;
                    regions.add(new Region("область", cx, cy, w, h, count, importance, left, top));
                }
            }
        }
        return regions;
    }

    private static VisionRegionMap empty() {
        boolean[] m = new boolean[1];
        Region r = new Region("объект", 0.5f, 0.5f, 0.2f, 0.2f, 0, 0);
        ArrayList<Region> list = new ArrayList<>(); list.add(r);
        return new VisionRegionMap(1, 1, m, m, list, r, r);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    public static final class Region {
        public final String label;
        public final float cx, cy, w, h, left, top;
        public final int area;
        public final float importance;
        Region(String label, float cx, float cy, float w, float h, int area, float importance) { this(label, cx, cy, w, h, area, importance, cx - w * 0.5f, cy - h * 0.5f); }
        Region(String label, float cx, float cy, float w, float h, int area, float importance, float left, float top) {
            this.label = label; this.cx = cx; this.cy = cy; this.w = w; this.h = h; this.area = area; this.importance = importance; this.left = left; this.top = top;
        }
        public RectF rectIn(RectF dst) { return new RectF(dst.left + left * dst.width(), dst.top + top * dst.height(), dst.left + (left + w) * dst.width(), dst.top + (top + h) * dst.height()); }
    }

    public static final class RoutePoint {
        public final int index;
        public final String label;
        public final float x, y;
        RoutePoint(int index, String label, float x, float y) { this.index = index; this.label = label; this.x = x; this.y = y; }
    }
}
