package com.solum.draw.planner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RasterSimilarityPlanner {
    private static final int WHITE = Color.rgb(255, 255, 255);
    private RasterSimilarityPlanner() {}

    public static StrokePlan build(Bitmap source, DrawMode mode, int canvasWidth, int canvasHeight) {
        if (source == null) return new StrokePlan(1, 1, mode.name() + "_GARTIC_CORE_EMPTY", new ArrayList<StrokeAction>());

        int maxLong = mode == DrawMode.HUMAN_FAST ? 360 : 520;
        float scale = Math.min(maxLong / (float)Math.max(1, source.getWidth()), maxLong / (float)Math.max(1, source.getHeight()));
        int w = Math.max(24, Math.round(source.getWidth() * scale));
        int h = Math.max(24, Math.round(source.getHeight() * scale));
        Bitmap bmp = Bitmap.createScaledBitmap(source, w, h, true);

        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        bmp.recycle();

        int fullMax = mode == DrawMode.HUMAN_FAST ? 14 : 20;
        int coarseMax = mode == DrawMode.HUMAN_FAST ? 6 : 8;
        int[] fullPalette = buildAdaptivePalette(pixels, w, h, fullMax);
        int[] coarsePalette = coarsePaletteFromFull(fullPalette, coarseMax);
        int[] targetMap = new int[w * h];
        int[] coarseMap = new int[w * h];

        for (int k = 0; k < pixels.length; k++) {
            int c = pixels[k];
            if (isWhiteish(c) || Color.alpha(c) < 35) {
                targetMap[k] = 0;
                coarseMap[k] = 0;
            } else {
                targetMap[k] = nearestColor(c, fullPalette, false);
                coarseMap[k] = nearestColor(c, coarsePalette, true);
            }
        }

        int[] drawn = new int[w * h];
        for (int i = 0; i < drawn.length; i++) drawn[i] = WHITE;

        ArrayList<RawAction> raw = new ArrayList<RawAction>();
        boolean[] frozen;
        ArrayList<RawAction> stage;

        frozen = freezeMask(drawn, targetMap, 26);
        stage = buildStageActionsFromMap(coarseMap, coarsePalette, frozen, "SCULPTOR_COARSE", "coarse", 18, w, h);
        addAndApply(raw, stage, drawn, w, h);

        for (int pass = 0; pass < 4; pass++) {
            frozen = freezeMask(drawn, targetMap, 24);
            stage = buildSilhouetteClosure(drawn, targetMap, fullPalette, w, h);
            if (stage.isEmpty()) break;
            addAndApply(raw, stage, drawn, w, h);
        }

        frozen = freezeMask(drawn, targetMap, 22);
        stage = buildStageActionsFromMap(errorMap(drawn, targetMap, 28), fullPalette, frozen, "POTTER_FORM", "form", 8, w, h);
        addAndApply(raw, stage, drawn, w, h);

        frozen = freezeMask(drawn, targetMap, 20);
        stage = buildStageActionsFromMap(errorMap(drawn, targetMap, 24), fullPalette, frozen, "GRINDER_MID", "mid", 4, w, h);
        addAndApply(raw, stage, drawn, w, h);

        frozen = freezeMask(drawn, targetMap, 17);
        stage = buildStageActionsFromMap(errorMap(drawn, targetMap, 18), fullPalette, frozen, "POLISHER_DETAIL", "detail", 2, w, h);
        addAndApply(raw, stage, drawn, w, h);

        frozen = freezeMask(drawn, targetMap, 14);
        stage = buildStageActionsFromMap(errorMap(drawn, targetMap, 18), fullPalette, frozen, "POLISHER_RESIDUAL", "detail", 4, w, h);
        addAndApply(raw, stage, drawn, w, h);

        frozen = freezeMask(drawn, targetMap, 12);
        stage = buildTinyDetailPass(errorMap(drawn, targetMap, 12), fullPalette, w, h);
        addAndApply(raw, stage, drawn, w, h);

        ArrayList<StrokeAction> actions = new ArrayList<StrokeAction>();
        for (int i = 0; i < raw.size(); i++) actions.add(rawToStroke(raw.get(i), w, h, canvasWidth, canvasHeight));
        return new StrokePlan(source.getWidth(), source.getHeight(), mode.name() + "_GARTIC_CORE_V3", actions);
    }

    private static void addAndApply(ArrayList<RawAction> all, ArrayList<RawAction> stage, int[] drawn, int w, int h) {
        for (int i = 0; i < stage.size(); i++) {
            RawAction a = stage.get(i);
            all.add(a);
            applyStroke(drawn, w, h, a);
        }
    }

    private static int[] buildAdaptivePalette(int[] pixels, int w, int h, int maxColors) {
        Map<Integer, Bin> bins = new HashMap<Integer, Bin>();
        int sampleStep = 3;
        for (int y = 0; y < h; y += sampleStep) {
            for (int x = 0; x < w; x += sampleStep) {
                int c = pixels[y * w + x];
                if (isWhiteish(c) || Color.alpha(c) < 35) continue;
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                Bin bin = bins.get(key);
                if (bin == null) { bin = new Bin(); bins.put(key, bin); }
                int bonus = isSkinish(r, g, b) ? 3 : 1;
                bin.count += bonus;
                bin.sr += r * bonus; bin.sg += g * bonus; bin.sb += b * bonus;
                if (bonus > 1) bin.skin++;
            }
        }
        ArrayList<Pal> arr = new ArrayList<Pal>();
        for (Bin e : bins.values()) {
            if (e.count <= 0) continue;
            arr.add(new Pal(Color.rgb(clamp((int)(e.sr / e.count)), clamp((int)(e.sg / e.count)), clamp((int)(e.sb / e.count))), e.count, e.skin));
        }
        Collections.sort(arr, new Comparator<Pal>() { @Override public int compare(Pal a, Pal b) { return b.count - a.count; } });

        ArrayList<Pal> merged = new ArrayList<Pal>();
        for (int i = 0; i < arr.size(); i++) {
            Pal c = arr.get(i);
            boolean put = false;
            for (int j = 0; j < merged.size(); j++) {
                Pal m = merged.get(j);
                int th = (c.skin > 0 || m.skin > 0) ? 44 : 30;
                if (colorDist2(c.color, m.color) <= th * th) {
                    int total = m.count + c.count;
                    int r = Math.round((Color.red(m.color) * m.count + Color.red(c.color) * c.count) / (float) total);
                    int g = Math.round((Color.green(m.color) * m.count + Color.green(c.color) * c.count) / (float) total);
                    int b = Math.round((Color.blue(m.color) * m.count + Color.blue(c.color) * c.count) / (float) total);
                    m.color = Color.rgb(clamp(r), clamp(g), clamp(b));
                    m.count = total;
                    m.skin += c.skin;
                    put = true;
                    break;
                }
            }
            if (!put) merged.add(new Pal(c.color, c.count, c.skin));
            if (merged.size() > maxColors * 3) break;
        }
        Collections.sort(merged, new Comparator<Pal>() { @Override public int compare(Pal a, Pal b) { return b.count - a.count; } });
        if (merged.isEmpty()) return fixedPalette();
        int n = Math.min(maxColors, merged.size());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = merged.get(i).color;
        return out;
    }

    private static int[] coarsePaletteFromFull(int[] full, int maxColors) {
        ArrayList<Pal> merged = new ArrayList<Pal>();
        for (int i = 0; i < full.length; i++) {
            int c = full[i];
            boolean skin = isSkinish(Color.red(c), Color.green(c), Color.blue(c));
            boolean put = false;
            for (int j = 0; j < merged.size(); j++) {
                Pal m = merged.get(j);
                int th = (skin || m.skin > 0) ? 42 : 34;
                if (colorDist2(c, m.color) <= th * th) {
                    int r = (Color.red(m.color) + Color.red(c)) / 2;
                    int g = (Color.green(m.color) + Color.green(c)) / 2;
                    int b = (Color.blue(m.color) + Color.blue(c)) / 2;
                    m.color = Color.rgb(clamp(r), clamp(g), clamp(b));
                    if (skin) m.skin = 1;
                    put = true;
                    break;
                }
            }
            if (!put) merged.add(new Pal(c, 1, skin ? 1 : 0));
        }
        ArrayList<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < merged.size() && out.size() < Math.min(2, maxColors); i++) if (merged.get(i).skin > 0) out.add(merged.get(i).color);
        for (int i = 0; i < merged.size() && out.size() < maxColors; i++) if (merged.get(i).skin == 0) out.add(merged.get(i).color);
        if (out.isEmpty()) for (int i = 0; i < full.length && out.size() < maxColors; i++) out.add(full[i]);
        int[] arr = new int[out.size()];
        for (int i = 0; i < out.size(); i++) arr[i] = out.get(i);
        return arr.length == 0 ? fixedPalette() : arr;
    }

    private static int nearestColor(int c, int[] palette, boolean coarse) {
        if (palette == null || palette.length == 0) return c;
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        boolean skin = isSkinish(r, g, b);
        int best = palette[0];
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int p = palette[i];
            int pr = Color.red(p), pg = Color.green(p), pb = Color.blue(p);
            long d = sq(r - pr) + sq(g - pg) + sq(b - pb);
            if (skin) {
                boolean warm = pr >= pb && pg >= pb;
                boolean gray = Math.abs(pr - pg) < 18 && Math.abs(pg - pb) < 18;
                if (coarse) {
                    if (warm) d = (long)(d * 0.68f);
                    if (gray) d = (long)(d * 1.95f);
                } else {
                    if (warm) d = (long)(d * 0.84f);
                    if (gray) d = (long)(d * 1.35f);
                }
            }
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private static boolean[] freezeMask(int[] drawn, int[] target, int errThr) {
        boolean[] out = new boolean[target.length];
        for (int i = 0; i < target.length; i++) {
            if (target[i] == 0) continue;
            if (colorDiff(drawn[i], target[i]) <= errThr) out[i] = true;
        }
        return out;
    }

    private static int[] errorMap(int[] drawn, int[] target, int errThr) {
        int[] out = new int[target.length];
        for (int i = 0; i < target.length; i++) {
            int tc = target[i];
            if (tc == 0) continue;
            if (colorDiff(drawn[i], tc) > errThr) out[i] = tc;
        }
        return out;
    }

    private static ArrayList<RawAction> buildStageActionsFromMap(int[] map, int[] colors, boolean[] frozen, String stageName, String stage, int minArea, int w, int h) {
        ArrayList<RawAction> actions = new ArrayList<RawAction>();
        for (int ci = 0; ci < colors.length; ci++) {
            int color = colors[ci];
            ArrayList<Island> islands = extractIslands(map, color, minArea, w, h);
            Collections.sort(islands, new Comparator<Island>() { @Override public int compare(Island a, Island b) { return Float.compare(b.priority, a.priority); } });
            for (int ii = 0; ii < islands.size(); ii++) {
                Island isl = islands.get(ii);
                int brush = pickBrush(isl.pixels.size(), stage);
                ArrayList<ArrayList<int[]>> paths = choosePathsForStage(isl, brush, stage, w);
                for (int pi = 0; pi < paths.size(); pi++) {
                    ArrayList<int[]> pts = paths.get(pi);
                    if (pts == null || pts.isEmpty()) continue;
                    if (segmentFrozenRatio(pts, frozen, brush, w, h) > 0.92f) continue;
                    actions.add(new RawAction(stageName, color, brush, pts, isl.priority));
                }
            }
        }
        Collections.sort(actions, new Comparator<RawAction>() { @Override public int compare(RawAction a, RawAction b) { return Float.compare(b.priority, a.priority); } });
        return actions;
    }

    private static ArrayList<RawAction> buildSilhouetteClosure(int[] drawn, int[] target, int[] colors, int w, int h) {
        ArrayList<RawAction> actions = new ArrayList<RawAction>();
        for (int ci = 0; ci < colors.length; ci++) {
            int color = colors[ci];
            int[] map = new int[target.length];
            for (int k = 0; k < target.length; k++) {
                if (target[k] == color && isWhiteish(drawn[k])) map[k] = color;
            }
            ArrayList<Island> comps = extractIslands(map, color, 12, w, h);
            Collections.sort(comps, new Comparator<Island>() { @Override public int compare(Island a, Island b) { return Float.compare(b.priority, a.priority); } });
            int limit = Math.min(80, comps.size());
            for (int i = 0; i < limit; i++) {
                Island comp = comps.get(i);
                int brush = pickBrush(comp.pixels.size(), "form");
                ArrayList<ArrayList<int[]>> segs = buildSolidFillRoute(comp, Math.max(1, (int)Math.ceil(brush * 1.05f)), w);
                for (int s = 0; s < segs.size(); s++) actions.add(new RawAction("SCULPTOR_CLOSURE", color, brush, segs.get(s), comp.priority));
            }
        }
        Collections.sort(actions, new Comparator<RawAction>() { @Override public int compare(RawAction a, RawAction b) { return Float.compare(b.priority, a.priority); } });
        return actions;
    }

    private static ArrayList<RawAction> buildTinyDetailPass(int[] map, int[] colors, int w, int h) {
        ArrayList<RawAction> actions = new ArrayList<RawAction>();
        for (int ci = 0; ci < colors.length; ci++) {
            int color = colors[ci];
            ArrayList<Island> islands = extractIslands(map, color, 1, w, h);
            Collections.sort(islands, new Comparator<Island>() { @Override public int compare(Island a, Island b) { return Float.compare(b.priority, a.priority); } });
            int lim = Math.min(120, islands.size());
            for (int ii = 0; ii < lim; ii++) {
                Island isl = islands.get(ii);
                if (isl.pixels.size() > 10) continue;
                ArrayList<int[]> pts = new ArrayList<int[]>();
                for (int j = 0; j < isl.pixels.size(); j++) {
                    int p = isl.pixels.get(j);
                    pts.add(new int[] { p % w, p / w });
                }
                Collections.sort(pts, new Comparator<int[]>() { @Override public int compare(int[] a, int[] b) { return (a[1] - b[1]) != 0 ? a[1] - b[1] : a[0] - b[0]; } });
                if (!pts.isEmpty()) actions.add(new RawAction("POLISHER_TINY", color, 1, slicePts(pts, Math.min(4, pts.size())), isl.priority));
            }
        }
        Collections.sort(actions, new Comparator<RawAction>() { @Override public int compare(RawAction a, RawAction b) { return Float.compare(b.priority, a.priority); } });
        return actions;
    }

    private static ArrayList<Island> extractIslands(int[] map, int color, int minArea, int w, int h) {
        boolean[] seen = new boolean[map.length];
        ArrayList<Island> out = new ArrayList<Island>();
        int[] nb = new int[] {1, -1, w, -w};
        ArrayDeque<Integer> q = new ArrayDeque<Integer>();
        for (int s = 0; s < map.length; s++) {
            if (map[s] != color || seen[s]) continue;
            Island isl = new Island(w);
            q.clear();
            q.add(s); seen[s] = true;
            while (!q.isEmpty()) {
                int cur = q.removeFirst();
                int cx = cur % w;
                isl.add(cur, w);
                for (int di = 0; di < nb.length; di++) {
                    int d = nb[di];
                    int n = cur + d;
                    if (n < 0 || n >= map.length || seen[n] || map[n] != color) continue;
                    if (d == 1 && cx == w - 1) continue;
                    if (d == -1 && cx == 0) continue;
                    seen[n] = true;
                    q.add(n);
                }
            }
            if (isl.pixels.size() >= minArea) { isl.finish(w, h); out.add(isl); }
        }
        return out;
    }

    private static ArrayList<ArrayList<int[]>> choosePathsForStage(Island isl, int brush, String stage, int w) {
        boolean preferCol = isl.h > isl.wd * 1.15f;
        float fillRatio = stage.equals("coarse") ? 0.50f : (stage.equals("form") ? 0.72f : (stage.equals("mid") ? 0.62f : 0.88f));
        int step = Math.max(1, Math.round(brush * fillRatio));
        int maxPts = stage.equals("coarse") ? 38 : (stage.equals("form") ? 30 : 24);
        ArrayList<ArrayList<int[]>> base = buildSnakeBridgeRoute(isl, preferCol, step, 16, maxPts, w);
        ArrayList<ArrayList<int[]>> out = new ArrayList<ArrayList<int[]>>();
        for (int i = 0; i < base.size(); i++) out.add(densifyPath(base.get(i), Math.max(1, Math.round(brush * 0.50f))));
        return out;
    }

    private static ArrayList<ArrayList<int[]>> buildSnakeBridgeRoute(Island isl, boolean byCol, int step, int maxJoin, int maxPts, int w) {
        Map<Integer, ArrayList<Integer>> rows = new HashMap<Integer, ArrayList<Integer>>();
        for (int i = 0; i < isl.pixels.size(); i++) {
            int p = isl.pixels.get(i), x = p % w, y = p / w;
            int key = byCol ? x : y;
            int val = byCol ? y : x;
            ArrayList<Integer> list = rows.get(key);
            if (list == null) { list = new ArrayList<Integer>(); rows.put(key, list); }
            list.add(val);
        }
        ArrayList<Integer> keys = new ArrayList<Integer>(rows.keySet());
        Collections.sort(keys);
        ArrayList<ArrayList<int[]>> out = new ArrayList<ArrayList<int[]>>();
        ArrayList<int[]> path = new ArrayList<int[]>();
        int dir = 1, lastK = -1000000;
        for (int ki = 0; ki < keys.size(); ki++) {
            int key = keys.get(ki);
            if (key - lastK < step) continue;
            lastK = key;
            ArrayList<int[]> runs = runsFromValues(rows.get(key));
            if (dir < 0) Collections.reverse(runs);
            ArrayList<int[]> pts = new ArrayList<int[]>();
            for (int i = 0; i < runs.size(); i++) {
                int a = runs.get(i)[0], b = runs.get(i)[1];
                if (!byCol) {
                    pts.add(dir > 0 ? new int[] { a, key } : new int[] { b, key });
                    pts.add(dir > 0 ? new int[] { b, key } : new int[] { a, key });
                } else {
                    pts.add(dir > 0 ? new int[] { key, a } : new int[] { key, b });
                    pts.add(dir > 0 ? new int[] { key, b } : new int[] { key, a });
                }
            }
            if (pts.isEmpty()) continue;
            if (path.isEmpty()) path.addAll(pts);
            else {
                int[] lp = path.get(path.size() - 1), fp = pts.get(0);
                int d = Math.abs(lp[0] - fp[0]) + Math.abs(lp[1] - fp[1]);
                if (d <= maxJoin && path.size() + pts.size() <= maxPts) path.addAll(pts);
                else { flushPath(out, path); path = new ArrayList<int[]>(); path.addAll(pts); }
            }
            dir *= -1;
        }
        flushPath(out, path);
        return out;
    }

    private static ArrayList<ArrayList<int[]>> buildSolidFillRoute(Island isl, int step, int w) {
        Map<Integer, ArrayList<Integer>> rowMap = new HashMap<Integer, ArrayList<Integer>>();
        for (int i = 0; i < isl.pixels.size(); i++) {
            int p = isl.pixels.get(i), x = p % w, y = p / w;
            ArrayList<Integer> xs = rowMap.get(y);
            if (xs == null) { xs = new ArrayList<Integer>(); rowMap.put(y, xs); }
            xs.add(x);
        }
        ArrayList<Integer> ys = new ArrayList<Integer>(rowMap.keySet());
        Collections.sort(ys);
        ArrayList<ArrayList<int[]>> out = new ArrayList<ArrayList<int[]>>();
        ArrayList<int[]> path = new ArrayList<int[]>();
        int dir = 1, lastY = -1000000;
        for (int yi = 0; yi < ys.size(); yi++) {
            int y = ys.get(yi);
            if (y - lastY < step) continue;
            lastY = y;
            ArrayList<int[]> runs = runsFromValues(rowMap.get(y));
            if (dir < 0) Collections.reverse(runs);
            ArrayList<int[]> pts = new ArrayList<int[]>();
            for (int ri = 0; ri < runs.size(); ri++) {
                int a = runs.get(ri)[0], b = runs.get(ri)[1];
                pts.add(dir > 0 ? new int[] { a, y } : new int[] { b, y });
                pts.add(dir > 0 ? new int[] { b, y } : new int[] { a, y });
            }
            pts = densifyPath(pts, 1);
            if (path.isEmpty()) path.addAll(pts);
            else {
                int[] lp = path.get(path.size() - 1), fp = pts.get(0);
                double d = Math.sqrt(sq(lp[0] - fp[0]) + sq(lp[1] - fp[1]));
                if (d <= step * 3) path.addAll(pts);
                else { flushAny(out, path); path = new ArrayList<int[]>(); path.addAll(pts); }
            }
            dir *= -1;
        }
        flushAny(out, path);
        return out;
    }

    private static ArrayList<int[]> runsFromValues(ArrayList<Integer> vals) {
        Collections.sort(vals);
        ArrayList<int[]> runs = new ArrayList<int[]>();
        if (vals.isEmpty()) return runs;
        int s = vals.get(0), e = s;
        for (int i = 1; i < vals.size(); i++) {
            int v = vals.get(i);
            if (v <= e + 1) e = v;
            else { runs.add(new int[] { s, e }); s = e = v; }
        }
        runs.add(new int[] { s, e });
        return runs;
    }

    private static ArrayList<int[]> densifyPath(ArrayList<int[]> points, int maxGap) {
        if (points == null || points.size() < 2) return points;
        ArrayList<int[]> out = new ArrayList<int[]>();
        out.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            int[] a = points.get(i - 1), b = points.get(i);
            int dx = b[0] - a[0], dy = b[1] - a[1];
            double dist = Math.sqrt(dx * dx + dy * dy);
            int steps = Math.max(1, (int)Math.ceil(dist / Math.max(1, maxGap)));
            for (int s = 1; s <= steps; s++) out.add(new int[] { Math.round(a[0] + dx * s / (float)steps), Math.round(a[1] + dy * s / (float)steps) });
        }
        return out;
    }

    private static void flushPath(ArrayList<ArrayList<int[]>> out, ArrayList<int[]> path) { if (path.size() >= 2) out.add(new ArrayList<int[]>(path)); }
    private static void flushAny(ArrayList<ArrayList<int[]>> out, ArrayList<int[]> path) { if (path.size() >= 1) out.add(new ArrayList<int[]>(path)); }
    private static ArrayList<int[]> slicePts(ArrayList<int[]> in, int n) { ArrayList<int[]> out = new ArrayList<int[]>(); for (int i = 0; i < n && i < in.size(); i++) out.add(in.get(i)); return out; }

    private static float segmentFrozenRatio(ArrayList<int[]> pts, boolean[] frozen, int brush, int w, int h) {
        if (pts == null || pts.isEmpty()) return 1f;
        int hit = 0, total = 0, r = Math.max(1, Math.round(brush * 0.50f));
        for (int i = 0; i < pts.size(); i++) {
            int x = pts.get(i)[0], y = pts.get(i)[1];
            for (int yy = Math.max(0, y - r); yy <= Math.min(h - 1, y + r); yy++) {
                for (int xx = Math.max(0, x - r); xx <= Math.min(w - 1, x + r); xx++) {
                    total++;
                    if (frozen[yy * w + xx]) hit++;
                }
            }
        }
        return total > 0 ? hit / (float)total : 1f;
    }

    private static int pickBrush(int area, String stage) {
        if (stage.equals("coarse")) { if (area > 2200) return 7; if (area > 900) return 6; if (area > 220) return 5; return 4; }
        if (stage.equals("form")) { if (area > 1400) return 5; if (area > 420) return 4; return 3; }
        if (stage.equals("mid")) { if (area > 700) return 4; if (area > 140) return 3; return 2; }
        if (stage.equals("detail")) { if (area > 90) return 3; if (area > 20) return 2; return 1; }
        return area > 30 ? 2 : 1;
    }

    private static void applyStroke(int[] drawn, int w, int h, RawAction a) {
        if (a.points == null || a.points.isEmpty()) return;
        int radius = Math.max(0, Math.round(a.brush * 0.50f));
        if (a.points.size() == 1) { stamp(drawn, w, h, a.points.get(0)[0], a.points.get(0)[1], radius, a.color); return; }
        for (int i = 1; i < a.points.size(); i++) {
            int[] p0 = a.points.get(i - 1), p1 = a.points.get(i);
            int dx = p1[0] - p0[0], dy = p1[1] - p0[1];
            int steps = Math.max(Math.abs(dx), Math.abs(dy));
            steps = Math.max(1, steps);
            for (int s = 0; s <= steps; s++) {
                int x = Math.round(p0[0] + dx * s / (float)steps);
                int y = Math.round(p0[1] + dy * s / (float)steps);
                stamp(drawn, w, h, x, y, radius, a.color);
            }
        }
    }

    private static void stamp(int[] drawn, int w, int h, int cx, int cy, int r, int color) {
        for (int y = Math.max(0, cy - r); y <= Math.min(h - 1, cy + r); y++) {
            for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r * r + 1) drawn[y * w + x] = color;
            }
        }
    }

    private static StrokeAction rawToStroke(RawAction a, int w, int h, int cw, int ch) {
        ArrayList<PointF> path = new ArrayList<PointF>();
        for (int i = 0; i < a.points.size(); i++) path.add(toCanvas(a.points.get(i)[0], a.points.get(i)[1], w, h, cw, ch));
        return new StrokeAction(a.stage, a.color, Math.max(1f, a.brush), path);
    }

    private static PointF toCanvas(int x, int y, int w, int h, int cw, int ch) {
        return new PointF((x / (float)Math.max(1, w - 1)) * cw, (y / (float)Math.max(1, h - 1)) * ch);
    }

    private static boolean isWhiteish(int c) { return Color.red(c) > 246 && Color.green(c) > 246 && Color.blue(c) > 246; }
    private static boolean isSkinish(int r, int g, int b) { int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)); return r > 95 && g > 55 && b > 35 && r > g && g > b && (mx - mn) > 12 && (r - g) < 105; }
    private static int colorDiff(int a, int b) { return (Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b))) / 3; }
    private static int colorDist2(int a, int b) { return (int)(sq(Color.red(a) - Color.red(b)) + sq(Color.green(a) - Color.green(b)) + sq(Color.blue(a) - Color.blue(b))); }
    private static long sq(int v) { return (long)v * (long)v; }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static int[] fixedPalette() {
        return new int[] { Color.rgb(0,0,0), Color.rgb(102,102,102), Color.rgb(0,79,204), Color.rgb(170,170,170), Color.rgb(37,200,255), Color.rgb(1,116,31), Color.rgb(153,1,0), Color.rgb(150,65,18), Color.rgb(17,176,60), Color.rgb(255,0,18), Color.rgb(255,119,41), Color.rgb(177,112,28), Color.rgb(153,0,78), Color.rgb(203,89,86), Color.rgb(255,193,38), Color.rgb(255,0,144), Color.rgb(254,175,168) };
    }

    private static final class Bin { int count, skin; long sr, sg, sb; }
    private static final class Pal { int color, count, skin; Pal(int c, int n, int s) { color = c; count = n; skin = s; } }

    private static final class Island {
        final ArrayList<Integer> pixels = new ArrayList<Integer>();
        int minX, maxX, minY, maxY, wd, h;
        float priority;
        Island(int w) { minX = w; maxX = 0; minY = Integer.MAX_VALUE; maxY = 0; }
        void add(int p, int w) { pixels.add(p); int x = p % w, y = p / w; if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y; }
        void finish(int w, int hgt) {
            wd = maxX - minX + 1; h = maxY - minY + 1;
            float sx = 0, sy = 0;
            for (int i = 0; i < pixels.size(); i++) { int p = pixels.get(i); sx += p % w; sy += p / w; }
            float cx = sx / Math.max(1, pixels.size()), cy = sy / Math.max(1, pixels.size());
            float cx0 = w * 0.5f, cy0 = hgt * 0.5f;
            float maxDist = (float)Math.sqrt(cx0 * cx0 + cy0 * cy0);
            float dist = 1f - (float)Math.sqrt((cx - cx0) * (cx - cx0) + (cy - cy0) * (cy - cy0)) / Math.max(1f, maxDist);
            float compact = pixels.size() / (float)Math.max(1, wd * h);
            priority = pixels.size() * (0.60f + Math.max(0f, dist) * 0.25f + Math.min(1f, compact) * 0.15f);
        }
    }

    private static final class RawAction {
        final String stage; final int color; final int brush; final ArrayList<int[]> points; final float priority;
        RawAction(String s, int c, int b, ArrayList<int[]> p, float pr) { stage = s; color = c; brush = b; points = p; priority = pr; }
    }
}
