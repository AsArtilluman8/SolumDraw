package com.solum.draw.vision;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class VisionRegionMap {
    private static final int GRID_W = 128;
    private static final int MAX_GRID_H = 192;

    public final int gridWidth;
    public final int gridHeight;
    public final boolean[] edge;
    public final boolean[] boundary;
    public final boolean[] subjectMask;
    public final List<Region> regions;
    public final Region mainRegion;
    private final List<float[]> contourLines;

    private VisionRegionMap(int gridWidth, int gridHeight, boolean[] edge, boolean[] boundary, boolean[] subjectMask, List<Region> regions, Region mainRegion, List<float[]> contourLines) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.edge = edge;
        this.boundary = boundary;
        this.subjectMask = subjectMask;
        this.regions = regions;
        this.mainRegion = mainRegion;
        this.contourLines = contourLines;
    }

    public static VisionRegionMap analyze(Bitmap src) {
        if (src == null) return empty();

        int sw = src.getWidth();
        int sh = src.getHeight();
        int gw = GRID_W;
        int gh = Math.max(48, Math.round(gw * sh / (float)Math.max(1, sw)));
        gh = Math.min(MAX_GRID_H, gh);
        int count = gw * gh;

        int[] lum = new int[count];
        int[] sat = new int[count];
        int sumL = 0;
        int sumS = 0;

        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int px = Math.min(sw - 1, Math.max(0, Math.round((x + 0.5f) * sw / gw)));
                int py = Math.min(sh - 1, Math.max(0, Math.round((y + 0.5f) * sh / gh)));
                int c = src.getPixel(px, py);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int mx = Math.max(r, Math.max(g, b));
                int mn = Math.min(r, Math.min(g, b));
                int id = y * gw + x;
                lum[id] = (r * 30 + g * 59 + b * 11) / 100;
                sat[id] = mx - mn;
                sumL += lum[id];
                sumS += sat[id];
            }
        }

        int avgL = sumL / Math.max(1, count);
        int avgS = sumS / Math.max(1, count);

        boolean[] edge = new boolean[count];
        boolean[] mask = new boolean[count];

        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int id = y * gw + x;
                int gx =
                        -lum[id - gw - 1] + lum[id - gw + 1]
                      - 2 * lum[id - 1] + 2 * lum[id + 1]
                      - lum[id + gw - 1] + lum[id + gw + 1];

                int gy =
                        -lum[id - gw - 1] - 2 * lum[id - gw] - lum[id - gw + 1]
                      + lum[id + gw - 1] + 2 * lum[id + gw] + lum[id + gw + 1];

                int mag = Math.abs(gx) + Math.abs(gy);
                boolean isEdge = mag > 96;
                boolean salient = isEdge || Math.abs(lum[id] - avgL) > 30 || Math.abs(sat[id] - avgS) > 38;

                edge[id] = isEdge;
                mask[id] = salient;
            }
        }

        mask = morphClose(mask, gw, gh, 2);
        mask = morphOpen(mask, gw, gh, 1);

        boolean[] boundary = boundary(mask, gw, gh);
        List<Region> regions = regions(mask, edge, gw, gh);
        Collections.sort(regions, new Comparator<Region>() {
            @Override public int compare(Region a, Region b) {
                return Float.compare(b.importance, a.importance);
            }
        });

        Region main = pickMain(regions);
        List<float[]> lines = contourLines(boundary, gw, gh);

        return new VisionRegionMap(gw, gh, edge, boundary, mask, regions, main, lines);
    }

    public List<float[]> contourPolylines() {
        return contourLines;
    }

    public List<RoutePoint> route() {
        ArrayList<RoutePoint> out = new ArrayList<>();
        Region main = mainRegion;
        Region detail = pickDetail(main);
        out.add(new RoutePoint(1, "фон", 0.50f, 0.16f));
        out.add(new RoutePoint(2, "масса", main.cx, clamp(main.top + main.h * 0.25f, 0.08f, 0.92f)));
        out.add(new RoutePoint(3, "объект", main.cx, main.cy));
        out.add(new RoutePoint(4, "тени", clamp(main.cx - main.w * 0.23f, 0.06f, 0.94f), clamp(main.cy + main.h * 0.24f, 0.08f, 0.94f)));
        out.add(new RoutePoint(5, "детали", detail.cx, detail.cy));
        return out;
    }

    public List<Region> displayRegions() {
        ArrayList<Region> out = new ArrayList<>();
        for (Region r : regions) {
            if (out.size() >= 12) break;
            if (r.w * r.h > 0.82f) continue;
            if (r.area >= 8) out.add(r);
        }
        if (out.isEmpty()) out.add(mainRegion);
        return out;
    }

    public boolean isBoundary(int x, int y) {
        if (x < 0 || y < 0 || x >= gridWidth || y >= gridHeight) return false;
        return boundary[y * gridWidth + x];
    }

    private Region pickDetail(Region main) {
        for (Region r : regions) {
            if (r != main && r.area > 8 && r.w * r.h < main.w * main.h * 0.75f) return r;
        }
        return new Region("детали", clamp(main.cx + main.w * 0.25f, 0.08f, 0.92f), clamp(main.cy - main.h * 0.08f, 0.08f, 0.92f), main.w * .3f, main.h * .2f, main.area / 4, main.importance * .5f, main.left, main.top);
    }

    private static Region pickMain(List<Region> list) {
        for (Region r : list) {
            if (r.w * r.h < 0.74f && r.area > 12) return r;
        }
        if (!list.isEmpty()) return list.get(0);
        return new Region("объект", .5f, .55f, .25f, .25f, 0, .1f, .375f, .425f);
    }

    private static List<Region> regions(boolean[] mask, boolean[] edge, int gw, int gh) {
        boolean[] seen = new boolean[mask.length];
        ArrayList<Region> out = new ArrayList<>();
        int[] dx = {1,-1,0,0};
        int[] dy = {0,0,1,-1};

        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int start = y * gw + x;
                if (!mask[start] || seen[start]) continue;

                ArrayDeque<Integer> q = new ArrayDeque<>();
                q.add(start);
                seen[start] = true;

                int minX = x, maxX = x, minY = y, maxY = y;
                int area = 0;
                int edgeCount = 0;

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
                        int nx = px + dx[k];
                        int ny = py + dy[k];
                        if (nx <= 0 || ny <= 0 || nx >= gw - 1 || ny >= gh - 1) continue;
                        int nid = ny * gw + nx;
                        if (!seen[nid] && mask[nid]) {
                            seen[nid] = true;
                            q.add(nid);
                        }
                    }
                }

                if (area < 6) continue;

                float left = minX / (float)gw;
                float top = minY / (float)gh;
                float w = Math.max(1, maxX - minX + 1) / (float)gw;
                float h = Math.max(1, maxY - minY + 1) / (float)gh;
                float cx = left + w * .5f;
                float cy = top + h * .5f;
                float center = 1f - Math.min(.85f, Math.abs(cx - .5f) + Math.abs(cy - .55f) * .65f);
                float density = area / (float)Math.max(1, (maxX - minX + 1) * (maxY - minY + 1));
                float imp = density * .42f + center * .28f + Math.min(.30f, edgeCount / (float)Math.max(1, area));
                out.add(new Region("область", cx, cy, w, h, area, imp, left, top));
            }
        }
        return out;
    }

    private static List<float[]> contourLines(boolean[] boundary, int gw, int gh) {
        ArrayList<float[]> lines = new ArrayList<>();
        boolean[] used = new boolean[boundary.length];
        int[] dx = {1,1,0,-1,-1,-1,0,1};
        int[] dy = {0,1,1,1,0,-1,-1,-1};

        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int start = y * gw + x;
                if (!boundary[start] || used[start]) continue;

                ArrayList<Float> pts = new ArrayList<>();
                ArrayDeque<Integer> q = new ArrayDeque<>();
                q.add(start);
                used[start] = true;

                int guard = 0;
                while (!q.isEmpty() && guard++ < 900) {
                    int id = q.removeFirst();
                    int px = id % gw;
                    int py = id / gw;

                    if (pts.size() < 420) {
                        pts.add((px + .5f) / gw);
                        pts.add((py + .5f) / gh);
                    }

                    for (int k = 0; k < 8; k++) {
                        int nx = px + dx[k];
                        int ny = py + dy[k];
                        if (nx <= 0 || ny <= 0 || nx >= gw - 1 || ny >= gh - 1) continue;
                        int nid = ny * gw + nx;
                        if (boundary[nid] && !used[nid]) {
                            used[nid] = true;
                            q.add(nid);
                        }
                    }
                }

                if (pts.size() >= 8) {
                    float[] arr = new float[pts.size()];
                    for (int i = 0; i < pts.size(); i++) arr[i] = pts.get(i);
                    lines.add(simplify(arr, 0.008f));
                    if (lines.size() >= 48) return lines;
                }
            }
        }

        return lines;
    }

    private static float[] simplify(float[] src, float eps) {
        if (src.length <= 16) return src;
        ArrayList<Float> out = new ArrayList<>();
        float lastX = -10f;
        float lastY = -10f;

        for (int i = 0; i < src.length - 1; i += 2) {
            float x = src[i];
            float y = src[i + 1];
            float dx = x - lastX;
            float dy = y - lastY;
            if (out.isEmpty() || dx * dx + dy * dy > eps * eps) {
                out.add(x);
                out.add(y);
                lastX = x;
                lastY = y;
            }
        }

        float[] r = new float[out.size()];
        for (int i = 0; i < out.size(); i++) r[i] = out.get(i);
        return r;
    }

    private static boolean[] morphClose(boolean[] src, int w, int h, int passes) {
        boolean[] out = src;
        for (int i = 0; i < passes; i++) out = dilate(out, w, h);
        for (int i = 0; i < passes; i++) out = erode(out, w, h);
        return out;
    }

    private static boolean[] morphOpen(boolean[] src, int w, int h, int passes) {
        boolean[] out = src;
        for (int i = 0; i < passes; i++) out = erode(out, w, h);
        for (int i = 0; i < passes; i++) out = dilate(out, w, h);
        return out;
    }

    private static boolean[] dilate(boolean[] src, int w, int h) {
        boolean[] dst = new boolean[src.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int id = y * w + x;
                boolean v = false;
                for (int yy = -1; yy <= 1 && !v; yy++) {
                    for (int xx = -1; xx <= 1; xx++) {
                        if (src[id + yy * w + xx]) { v = true; break; }
                    }
                }
                dst[id] = v;
            }
        }
        return dst;
    }

    private static boolean[] erode(boolean[] src, int w, int h) {
        boolean[] dst = new boolean[src.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int id = y * w + x;
                boolean v = true;
                for (int yy = -1; yy <= 1 && v; yy++) {
                    for (int xx = -1; xx <= 1; xx++) {
                        if (!src[id + yy * w + xx]) { v = false; break; }
                    }
                }
                dst[id] = v;
            }
        }
        return dst;
    }

    private static boolean[] boundary(boolean[] mask, int w, int h) {
        boolean[] b = new boolean[mask.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int id = y * w + x;
                if (!mask[id]) continue;
                b[id] = !mask[id + 1] || !mask[id - 1] || !mask[id + w] || !mask[id - w];
            }
        }
        return b;
    }

    private static VisionRegionMap empty() {
        boolean[] one = new boolean[1];
        Region r = new Region("объект", .5f, .5f, .2f, .2f, 0, .1f, .4f, .4f);
        ArrayList<Region> list = new ArrayList<>();
        list.add(r);
        return new VisionRegionMap(1, 1, one, one, one, list, r, new ArrayList<float[]>());
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static final class Region {
        public final String label;
        public final float cx, cy, w, h, left, top;
        public final int area;
        public final float importance;

        Region(String label, float cx, float cy, float w, float h, int area, float importance, float left, float top) {
            this.label = label;
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.area = area;
            this.importance = importance;
            this.left = left;
            this.top = top;
        }

        public RectF rectIn(RectF dst) {
            return new RectF(
                    dst.left + left * dst.width(),
                    dst.top + top * dst.height(),
                    dst.left + (left + w) * dst.width(),
                    dst.top + (top + h) * dst.height()
            );
        }
    }

    public static final class RoutePoint {
        public final int index;
        public final String label;
        public final float x, y;

        RoutePoint(int index, String label, float x, float y) {
            this.index = index;
            this.label = label;
            this.x = x;
            this.y = y;
        }
    }
}
