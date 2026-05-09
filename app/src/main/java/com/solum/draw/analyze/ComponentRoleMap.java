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

public final class ComponentRoleMap {
    public static final String BACKGROUND = "BACKGROUND";
    public static final String MAIN_SYMBOL = "MAIN_SYMBOL";
    public static final String INNER_CUT = "INNER_CUT";
    public static final String GLOW = "GLOW";
    public static final String EDGE_HIGHLIGHT = "EDGE_HIGHLIGHT";
    public static final String TEXT = "TEXT";
    public static final String NOISE_IGNORE = "NOISE_IGNORE";

    public final List<Item> items;
    public final String drawPlan;

    private ComponentRoleMap(List<Item> items, String drawPlan) {
        this.items = items;
        this.drawPlan = drawPlan;
    }

    public static ComponentRoleMap build(Bitmap source, AnalysisLayers layers, ImageAnalysis analysis) {
        Bitmap small = Bitmap.createScaledBitmap(source, layers.width, layers.height, true);
        ArrayList<Item> out = new ArrayList<>();
        AnalysisLayers.Component largest = null;
        for (AnalysisLayers.Component c : layers.components) {
            if (largest == null || c.area > largest.area) largest = c;
        }
        int id = 1;
        for (AnalysisLayers.Component c : layers.components) {
            if (id > 64) break;
            float areaRatio = c.area / (float)Math.max(1, layers.width * layers.height);
            int avg = avgColor(small, c.rect);
            float bright = brightness(avg);
            float sat = saturation(avg);
            float center = centerScore(c.rect, layers.width, layers.height);
            boolean isLargest = c == largest;
            String role = classify(c, isLargest, areaRatio, bright, sat, center, layers, analysis);
            int priority = priority(role);
            String stage = stage(role);
            out.add(new Item(id, role, c.rect, c.area, areaRatio, avg, bright, sat, center, priority, stage));
            id++;
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                int p = a.priority - b.priority;
                return p != 0 ? p : Float.compare(b.areaRatio, a.areaRatio);
            }
        });
        return new ComponentRoleMap(out, buildDrawPlan(out, analysis));
    }

    private static String classify(AnalysisLayers.Component c, boolean isLargest, float areaRatio, float bright, float sat,
            float center, AnalysisLayers layers, ImageAnalysis analysis) {
        if (analysis.realTextRatio > 0.16f && analysis.textLineCount >= 2 && c.isTextCandidate) return TEXT;
        if (isLargest && center > 0.25f && areaRatio > 0.006f) return MAIN_SYMBOL;
        if (c.isLarge && center > 0.18f && analysis.logoScore > 0.25f) return MAIN_SYMBOL;
        if (bright < 0.12f && center > 0.18f && analysis.logoScore > 0.35f && areaRatio > 0.0008f) return INNER_CUT;
        if (bright > 0.52f && areaRatio < 0.018f && center > 0.08f) return EDGE_HIGHLIGHT;
        if (sat > 0.15f && bright > 0.18f && areaRatio < 0.045f && analysis.logoScore > 0.25f) return GLOW;
        if (areaRatio < 0.0007f) return NOISE_IGNORE;
        if (c.isTextCandidate && analysis.textLineCount == 0) return EDGE_HIGHLIGHT;
        return NOISE_IGNORE;
    }

    public Bitmap makeRoleBitmap(Bitmap source, AnalysisLayers layers) {
        Bitmap base = Bitmap.createScaledBitmap(source, layers.width, layers.height, true).copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(base);
        Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        dim.setColor(0xAA000000);
        canvas.drawRect(0, 0, base.getWidth(), base.getHeight(), dim);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.0f);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE);
        t.setTextSize(9f);
        t.setFakeBoldText(true);
        for (Item item : items) {
            if (item.role.equals(NOISE_IGNORE)) continue;
            p.setColor(color(item.role));
            canvas.drawRect(item.rect, p);
            canvas.drawText(item.id + " " + item.role, item.rect.left + 2, Math.max(12, item.rect.top + 10), t);
        }
        return base;
    }

    public Bitmap makeDrawOrderBitmap(Bitmap source, AnalysisLayers layers) {
        Bitmap base = Bitmap.createScaledBitmap(source, layers.width, layers.height, true).copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(base);
        Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
        wash.setColor(0xBB05070C);
        canvas.drawRect(0, 0, base.getWidth(), base.getHeight(), wash);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE);
        t.setTextSize(10f);
        t.setFakeBoldText(true);
        int n = 1;
        for (Item item : items) {
            if (item.role.equals(NOISE_IGNORE) || item.role.equals(TEXT)) continue;
            p.setColor(color(item.role));
            canvas.drawRect(item.rect, p);
            canvas.drawText(n + ":" + item.stage, item.rect.left + 2, Math.max(12, item.rect.top + 10), t);
            n++;
            if (n > 18) break;
        }
        return base;
    }

    public String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"drawPlan\": \"").append(esc(drawPlan)).append("\",\n  \"components\": [\n");
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (i > 0) b.append(",\n");
            b.append("    {\"id\":").append(it.id)
                    .append(",\"role\":\"").append(it.role).append("\"")
                    .append(",\"bbox\":[").append((int)it.rect.left).append(',').append((int)it.rect.top).append(',').append((int)it.rect.right).append(',').append((int)it.rect.bottom).append(']')
                    .append(",\"areaRatio\":").append(num(it.areaRatio))
                    .append(",\"avgColor\":\"").append(hex(it.avgColor)).append("\"")
                    .append(",\"brightness\":").append(num(it.brightness))
                    .append(",\"saturation\":").append(num(it.saturation))
                    .append(",\"priority\":").append(it.priority)
                    .append(",\"drawStage\":\"").append(it.stage).append("\"}");
        }
        b.append("\n  ]\n}");
        return b.toString();
    }

    public String roleSummaryHtml() {
        StringBuilder b = new StringBuilder();
        b.append("<table><tr><th>#</th><th>role</th><th>stage</th><th>area</th><th>color</th></tr>");
        int shown = 0;
        for (Item it : items) {
            if (it.role.equals(NOISE_IGNORE) && shown > 20) continue;
            b.append("<tr><td>").append(it.id).append("</td><td>").append(it.role).append("</td><td>").append(it.stage)
                    .append("</td><td>").append(Math.round(it.areaRatio * 10000f) / 100f).append("%</td><td>").append(hex(it.avgColor)).append("</td></tr>");
            shown++;
            if (shown > 40) break;
        }
        b.append("</table>");
        return b.toString();
    }

    private static String buildDrawPlan(List<Item> items, ImageAnalysis a) {
        if (a.genre.equals("logo_icon_flat")) return "1 background -> 2 glow/accent mass -> 3 main symbol -> 4 inner cuts -> 5 edge highlights -> 6 polish/noise cleanup";
        if (a.realTextRatio > 0.16f && a.textLineCount >= 2) return "1 background/panels -> 2 blocks -> 3 verified text components -> 4 icons -> 5 polish";
        return "1 background -> 2 large masses -> 3 main components -> 4 contours -> 5 details -> 6 polish";
    }

    private static int avgColor(Bitmap bmp, RectF r) {
        long rr = 0, gg = 0, bb = 0, n = 0;
        int x0 = clamp((int)r.left, 0, bmp.getWidth() - 1), x1 = clamp((int)r.right, 0, bmp.getWidth());
        int y0 = clamp((int)r.top, 0, bmp.getHeight() - 1), y1 = clamp((int)r.bottom, 0, bmp.getHeight());
        for (int y = y0; y < y1; y += 2) for (int x = x0; x < x1; x += 2) { int c = bmp.getPixel(x, y); rr += Color.red(c); gg += Color.green(c); bb += Color.blue(c); n++; }
        if (n == 0) return Color.BLACK;
        return Color.rgb((int)(rr / n), (int)(gg / n), (int)(bb / n));
    }

    private static int priority(String role) { if (role.equals(BACKGROUND)) return 1; if (role.equals(GLOW)) return 2; if (role.equals(MAIN_SYMBOL)) return 3; if (role.equals(INNER_CUT)) return 4; if (role.equals(EDGE_HIGHLIGHT)) return 5; if (role.equals(TEXT)) return 6; return 9; }
    private static String stage(String role) { if (role.equals(GLOW)) return "glow_mass"; if (role.equals(MAIN_SYMBOL)) return "main_symbol"; if (role.equals(INNER_CUT)) return "inner_cuts"; if (role.equals(EDGE_HIGHLIGHT)) return "edge_highlights"; if (role.equals(TEXT)) return "verified_text"; if (role.equals(BACKGROUND)) return "background"; return "ignore"; }
    private static int color(String role) { if (role.equals(MAIN_SYMBOL)) return 0xFF00E5FF; if (role.equals(INNER_CUT)) return 0xFFFFAA00; if (role.equals(GLOW)) return 0xFFB388FF; if (role.equals(EDGE_HIGHLIGHT)) return 0xFF69F0AE; if (role.equals(TEXT)) return 0xFFFF4D6D; return 0xFF666666; }
    private static float brightness(int c) { return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 25500f; }
    private static float saturation(int c) { int max = Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c))); int min = Math.min(Color.red(c), Math.min(Color.green(c), Color.blue(c))); return (max - min) / 255f; }
    private static float centerScore(RectF r, int w, int h) { float dx = Math.abs(r.centerX() - w * .5f) / (w * .5f); float dy = Math.abs(r.centerY() - h * .5f) / (h * .5f); return Math.max(0f, Math.min(1f, 1f - (dx + dy) * .5f)); }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static String hex(int c) { return String.format("#%06X", 0xFFFFFF & c); }
    private static String num(float v) { return String.format(java.util.Locale.US, "%.4f", v); }
    private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }

    public static final class Item {
        public final int id; public final String role; public final RectF rect; public final int area; public final float areaRatio; public final int avgColor; public final float brightness; public final float saturation; public final float centerScore; public final int priority; public final String stage;
        Item(int id, String role, RectF rect, int area, float areaRatio, int avgColor, float brightness, float saturation, float centerScore, int priority, String stage) {
            this.id = id; this.role = role; this.rect = rect; this.area = area; this.areaRatio = areaRatio; this.avgColor = avgColor; this.brightness = brightness; this.saturation = saturation; this.centerScore = centerScore; this.priority = priority; this.stage = stage;
        }
    }
}
