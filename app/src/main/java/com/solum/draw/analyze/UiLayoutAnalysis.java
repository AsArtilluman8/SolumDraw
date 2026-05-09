package com.solum.draw.analyze;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

public final class UiLayoutAnalysis {
    public static final String TOP_HUD = "TOP_HUD";
    public static final String LEFT_PANEL = "LEFT_PANEL";
    public static final String RIGHT_GIZMO = "RIGHT_GIZMO";
    public static final String BOTTOM_TOOLBAR = "BOTTOM_TOOLBAR";
    public static final String FLOATING_PANEL = "FLOATING_PANEL";
    public static final String SCENE_VIEWPORT = "SCENE_VIEWPORT";
    public static final String BRUSH_CURSOR = "BRUSH_CURSOR";
    public static final String TEXT_LABELS = "TEXT_LABELS";

    public final boolean isUiScreenshot;
    public final float uiScore;
    public final float panelScore;
    public final float textScore;
    public final float toolbarScore;
    public final float sceneScore;
    public final int textLineCount;
    public final List<Region> regions;
    public final String subgenre;

    private UiLayoutAnalysis(boolean isUiScreenshot, float uiScore, float panelScore, float textScore,
            float toolbarScore, float sceneScore, int textLineCount, List<Region> regions, String subgenre) {
        this.isUiScreenshot = isUiScreenshot;
        this.uiScore = uiScore;
        this.panelScore = panelScore;
        this.textScore = textScore;
        this.toolbarScore = toolbarScore;
        this.sceneScore = sceneScore;
        this.textLineCount = textLineCount;
        this.regions = regions;
        this.subgenre = subgenre;
    }

    public static UiLayoutAnalysis analyze(Bitmap source) {
        Bitmap bmp = scale(source, 256);
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        List<Region> regions = new ArrayList<>();

        float top = bandTextScore(bmp, 0, 0, w, Math.max(1, h / 8));
        float bottom = bandTextScore(bmp, 0, h * 5 / 6, w, h);
        float left = panelDarkScore(bmp, 0, h / 8, w / 3, h * 5 / 6);
        float right = panelDarkScore(bmp, w * 2 / 3, h / 8, w, h * 5 / 6);
        float centerScene = sceneTextureScore(bmp, w / 4, h / 5, w * 3 / 4, h * 4 / 5);
        int textLines = countTextRows(bmp);
        float rectScore = rectangleLineScore(bmp);
        float panelScore = clamp01(left * 0.45f + right * 0.18f + rectScore * 0.45f);
        float textScore = clamp01(textLines / 18f + top * 0.25f + bottom * 0.18f);
        float toolbarScore = clamp01(top * 0.45f + bottom * 0.50f + rectScore * 0.30f);
        float sceneScore = clamp01(centerScene);
        float uiScore = clamp01(panelScore * 0.34f + textScore * 0.32f + toolbarScore * 0.24f + sceneScore * 0.10f);
        boolean isUi = uiScore > 0.33f || (textLines >= 4 && rectScore > 0.16f) || (left > 0.28f && bottom > 0.16f);

        if (top > 0.10f) regions.add(new Region(TOP_HUD, new RectF(0, 0, w, Math.max(1, h / 8)), top, 1));
        if (left > 0.18f) regions.add(new Region(LEFT_PANEL, new RectF(0, h / 8, w / 3, h * 5 / 6), left, 2));
        if (right > 0.10f) regions.add(new Region(RIGHT_GIZMO, new RectF(w * 2 / 3, h / 8, w, h * 5 / 6), right, 3));
        if (bottom > 0.10f) regions.add(new Region(BOTTOM_TOOLBAR, new RectF(0, h * 5 / 6, w, h), bottom, 4));
        regions.add(new Region(SCENE_VIEWPORT, new RectF(w / 5, h / 6, w * 4 / 5, h * 5 / 6), sceneScore, 5));
        RectF brush = findCircleLikeCenter(bmp);
        if (brush != null) regions.add(new Region(BRUSH_CURSOR, brush, 0.50f, 6));
        if (textLines > 0) regions.add(new Region(TEXT_LABELS, new RectF(0, 0, w, h), textScore, 7));

        String sub = isUi ? (sceneScore > 0.24f ? "game_engine_editor_overlay" : "mobile_app_ui") : "none";
        return new UiLayoutAnalysis(isUi, uiScore, panelScore, textScore, toolbarScore, sceneScore, textLines, regions, sub);
    }

    public Bitmap makeUiMap(Bitmap source) {
        Bitmap bmp = scale(source, 720).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(bmp);
        Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
        wash.setColor(0x77000000);
        c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), wash);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(3f, bmp.getWidth() / 160f));
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE);
        t.setTextSize(Math.max(14f, bmp.getWidth() / 36f));
        t.setFakeBoldText(true);
        Bitmap ref = scale(source, 256);
        float sx = bmp.getWidth() / (float) ref.getWidth();
        float sy = bmp.getHeight() / (float) ref.getHeight();
        for (Region r : regions) {
            p.setColor(color(r.role));
            RectF rr = new RectF(r.rect.left * sx, r.rect.top * sy, r.rect.right * sx, r.rect.bottom * sy);
            c.drawRect(rr, p);
            c.drawText(r.role + " " + Math.round(r.score * 100f) + "%", rr.left + 5, Math.max(18, rr.top + t.getTextSize() + 4), t);
        }
        return bmp;
    }

    public String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"isUiScreenshot\": ").append(isUiScreenshot).append(",\n");
        b.append("  \"subgenre\": \"").append(subgenre).append("\",\n");
        b.append("  \"uiScore\": ").append(num(uiScore)).append(",\n");
        b.append("  \"panelScore\": ").append(num(panelScore)).append(",\n");
        b.append("  \"textScore\": ").append(num(textScore)).append(",\n");
        b.append("  \"toolbarScore\": ").append(num(toolbarScore)).append(",\n");
        b.append("  \"sceneScore\": ").append(num(sceneScore)).append(",\n");
        b.append("  \"textLineCount\": ").append(textLineCount).append(",\n");
        b.append("  \"regions\": [");
        for (int i = 0; i < regions.size(); i++) {
            Region r = regions.get(i);
            if (i > 0) b.append(",");
            b.append("{\"role\":\"").append(r.role).append("\",\"score\":").append(num(r.score))
                    .append(",\"bbox\": [").append((int)r.rect.left).append(',').append((int)r.rect.top).append(',')
                    .append((int)r.rect.right).append(',').append((int)r.rect.bottom).append("]}");
        }
        b.append("]\n}");
        return b.toString();
    }

    public String summary() {
        return "uiScore=" + pct(uiScore) + " panels=" + pct(panelScore) + " text=" + pct(textScore)
                + " toolbar=" + pct(toolbarScore) + " scene=" + pct(sceneScore) + " lines=" + textLineCount;
    }

    private static float bandTextScore(Bitmap b, int x0, int y0, int x1, int y1) {
        int rows = 0;
        for (int y = Math.max(1, y0 + 1); y < Math.min(b.getHeight() - 1, y1 - 1); y += 2) {
            int hits = 0;
            for (int x = Math.max(1, x0 + 1); x < Math.min(b.getWidth() - 1, x1 - 1); x += 2) {
                int e = edgeAt(b, x, y);
                int l = luma(b.getPixel(x, y));
                if (e > 58 && l > 70) hits++;
            }
            int span = Math.max(1, (x1 - x0) / 2);
            if (hits > span * 0.025f && hits < span * 0.60f) rows++;
        }
        return clamp01(rows / 18f);
    }

    private static int countTextRows(Bitmap b) {
        int rows = 0;
        for (int y = 2; y < b.getHeight() - 2; y += 3) {
            int runs = 0;
            boolean inRun = false;
            for (int x = 2; x < b.getWidth() - 2; x += 2) {
                boolean hit = edgeAt(b, x, y) > 62 && luma(b.getPixel(x, y)) > 75;
                if (hit && !inRun) { runs++; inRun = true; }
                if (!hit) inRun = false;
            }
            if (runs >= 4 && runs <= 45) rows++;
        }
        return rows;
    }

    private static float panelDarkScore(Bitmap b, int x0, int y0, int x1, int y1) {
        int dark = 0, total = 0, edge = 0;
        for (int y = y0; y < y1; y += 3) for (int x = x0; x < x1; x += 3) {
            int l = luma(b.getPixel(x, y));
            if (l < 80) dark++;
            if (x > 0 && y > 0 && x < b.getWidth() - 1 && y < b.getHeight() - 1 && edgeAt(b, x, y) > 45) edge++;
            total++;
        }
        float darkRatio = dark / (float)Math.max(1, total);
        float edgeRatio = edge / (float)Math.max(1, total);
        return clamp01(darkRatio * 0.55f + edgeRatio * 1.30f);
    }

    private static float rectangleLineScore(Bitmap b) {
        int hLines = 0, vLines = 0;
        for (int y = 2; y < b.getHeight() - 2; y += 4) {
            int hits = 0;
            for (int x = 2; x < b.getWidth() - 2; x += 3) if (edgeAt(b, x, y) > 55) hits++;
            if (hits > b.getWidth() / 28) hLines++;
        }
        for (int x = 2; x < b.getWidth() - 2; x += 4) {
            int hits = 0;
            for (int y = 2; y < b.getHeight() - 2; y += 3) if (edgeAt(b, x, y) > 55) hits++;
            if (hits > b.getHeight() / 28) vLines++;
        }
        return clamp01((hLines + vLines) / 45f);
    }

    private static float sceneTextureScore(Bitmap b, int x0, int y0, int x1, int y1) {
        int varied = 0, total = 0;
        for (int y = y0 + 2; y < y1 - 2; y += 4) for (int x = x0 + 2; x < x1 - 2; x += 4) {
            int c = b.getPixel(x, y);
            int l = luma(c);
            int e = edgeAt(b, x, y);
            int sat = saturationInt(c);
            if ((e > 35 && sat > 10) || (l > 70 && l < 210 && sat > 20)) varied++;
            total++;
        }
        return clamp01(varied / (float)Math.max(1, total));
    }

    private static RectF findCircleLikeCenter(Bitmap b) {
        int cx = b.getWidth() / 2, cy = b.getHeight() / 2;
        int r = Math.min(b.getWidth(), b.getHeight()) / 10;
        int edge = 0, total = 0;
        for (int y = cy - r; y <= cy + r; y += 2) for (int x = cx - r; x <= cx + r; x += 2) {
            if (x <= 1 || y <= 1 || x >= b.getWidth() - 1 || y >= b.getHeight() - 1) continue;
            int dx = x - cx, dy = y - cy;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (Math.abs(d - r * 0.75f) < 3f) {
                if (edgeAt(b, x, y) > 35) edge++;
                total++;
            }
        }
        if (total > 0 && edge / (float)total > 0.18f) return new RectF(cx - r, cy - r, cx + r, cy + r);
        return null;
    }

    private static int edgeAt(Bitmap b, int x, int y) {
        int lx = luma(b.getPixel(Math.min(b.getWidth() - 1, x + 1), y)) - luma(b.getPixel(Math.max(0, x - 1), y));
        int ly = luma(b.getPixel(x, Math.min(b.getHeight() - 1, y + 1))) - luma(b.getPixel(x, Math.max(0, y - 1)));
        return Math.abs(lx) + Math.abs(ly);
    }

    private static int luma(int c) { return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100; }
    private static int saturationInt(int c) { int max = Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c))); int min = Math.min(Color.red(c), Math.min(Color.green(c), Color.blue(c))); return max - min; }
    private static Bitmap scale(Bitmap source, int maxSide) { float s = Math.min(1f, maxSide / (float)Math.max(source.getWidth(), source.getHeight())); return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * s)), Math.max(1, Math.round(source.getHeight() * s)), true); }
    private static int color(String role) { if (role.equals(TOP_HUD)) return 0xFF00E5FF; if (role.equals(LEFT_PANEL)) return 0xFFB388FF; if (role.equals(RIGHT_GIZMO)) return 0xFFFFAA00; if (role.equals(BOTTOM_TOOLBAR)) return 0xFF69F0AE; if (role.equals(BRUSH_CURSOR)) return 0xFFFF4D6D; if (role.equals(TEXT_LABELS)) return 0xFFFFFFFF; return 0xFF4D7CFF; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static String num(float v) { return String.format(java.util.Locale.US, "%.4f", v); }
    private static String pct(float v) { return Math.round(v * 100f) + "%"; }

    public static final class Region {
        public final String role;
        public final RectF rect;
        public final float score;
        public final int priority;
        Region(String role, RectF rect, float score, int priority) { this.role = role; this.rect = rect; this.score = score; this.priority = priority; }
    }
}
