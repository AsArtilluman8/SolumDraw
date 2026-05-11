package com.solum.draw.analyze;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AnalysisVisualReport {
    private static final int MAX_SIDE = 900;
    private static final int KEEP_SINGLE_FOLDERS = 20;

    private AnalysisVisualReport() {}

    public static Result writeSingle(Bitmap source, ImageAnalysis analysis) throws Exception {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File reportsRoot = new File(downloads, "SolumDrawReports");
        File singleRoot = new File(reportsRoot, "Single");
        singleRoot.mkdirs();

        File dir = new File(singleRoot, "SolumDrawSingleAnalysis_" + timestamp());
        dir.mkdirs();

        AnalysisLayers layers = AnalysisLayers.build(source);
        ComponentRoleMap roles = ComponentRoleMap.build(source, layers, analysis);
        UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(source);
        DrawingIntentAnalysis intent = DrawingIntentAnalysis.build(analysis, layers, roles, ui);

        Bitmap original = scaleMax(source, MAX_SIDE);
        Bitmap overlay = overlay(source, analysis, layers, roles, ui);
        Bitmap attention = scaleMax(layers.makeAttentionBitmap(source), MAX_SIDE);
        Bitmap edges = scaleMax(layers.makeEdgeBitmap(), MAX_SIDE);
        Bitmap components = scaleMax(layers.makeComponentsBitmap(), MAX_SIDE);
        Bitmap roleMap = scaleMax(roles.makeRoleBitmap(source, layers), MAX_SIDE);
        Bitmap drawOrder = scaleMax(roles.makeDrawOrderBitmap(source, layers), MAX_SIDE);
        Bitmap uiMap = scaleMax(ui.makeUiMap(source), MAX_SIDE);
        Bitmap priority = scaleMax(layers.makePriorityBitmap(source), MAX_SIDE);

        savePng(original, new File(dir, "original.png"));
        savePng(overlay, new File(dir, "analysis_overlay.png"));
        savePng(attention, new File(dir, "01_attention_map.png"));
        savePng(edges, new File(dir, "02_edges_sobel.png"));
        savePng(components, new File(dir, "03_components_map.png"));
        savePng(roleMap, new File(dir, "04_role_map.png"));
        savePng(drawOrder, new File(dir, "05_draw_order_map.png"));
        savePng(uiMap, new File(dir, "06_ui_layout_map.png"));
        savePng(priority, new File(dir, "09_priority_overlay.png"));

        writeText(new File(dir, "analysis.json"), analysis.toJson());
        writeText(new File(dir, "layers.json"), layers.metricsJson());
        writeText(new File(dir, "component_roles.json"), roles.toJson());
        writeText(new File(dir, "ui_layout.json"), ui.toJson());
        writeText(new File(dir, "drawing_intent.json"), intent.toJson());
        writeText(new File(dir, "analysis_report.html"), html(analysis, intent));
        writeText(new File(dir, "README_RU.txt"), ruReadme(analysis, intent));

        File zip = new File(downloads, dir.getName() + ".zip");
        zipDir(dir, zip);
        cleanupOldFolders(singleRoot, KEEP_SINGLE_FOLDERS);
        return new Result(dir.getAbsolutePath(), new File(dir, "analysis_report.html").getAbsolutePath(), zip.getAbsolutePath());
    }

    public static Bitmap overlay(Bitmap source, ImageAnalysis analysis) {
        AnalysisLayers layers = AnalysisLayers.build(source);
        ComponentRoleMap roles = ComponentRoleMap.build(source, layers, analysis);
        UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(source);
        return overlay(source, analysis, layers, roles, ui);
    }

    public static Bitmap overlay(Bitmap source, ImageAnalysis analysis, AnalysisLayers layers) {
        ComponentRoleMap roles = ComponentRoleMap.build(source, layers, analysis);
        UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(source);
        return overlay(source, analysis, layers, roles, ui);
    }

    public static String writeBenchOverlay(File dir, Bitmap source, ImageAnalysis analysis, String name) throws Exception {
        File visualDir = new File(dir, "visuals");
        visualDir.mkdirs();
        File out = new File(visualDir, safe(name) + "_overlay.png");
        savePng(overlay(source, analysis), out);
        return "visuals/" + out.getName();
    }

    private static Bitmap overlay(Bitmap source, ImageAnalysis analysis, AnalysisLayers layers, ComponentRoleMap roles, UiLayoutAnalysis ui) {
        Bitmap bmp = scaleMax(source, 720).copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sx = bmp.getWidth() / (float)Math.max(1, layers.width);
        float sy = bmp.getHeight() / (float)Math.max(1, layers.height);

        paint.setColor(0xAA081018);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, bmp.getWidth(), Math.min(126, bmp.getHeight()), paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(16f, bmp.getWidth() / 34f));
        paint.setFakeBoldText(true);
        canvas.drawText(analysis.genre + " " + Math.round(analysis.confidence * 100f) + "%", 12, 30, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(12f, bmp.getWidth() / 52f));
        canvas.drawText("edge " + pct(analysis.edgeDensity) + " text " + pct(analysis.realTextRatio) + " glyph " + pct(analysis.glyphRatio) + " logo " + pct(analysis.logoScore), 12, 58, paint);
        canvas.drawText("ui " + pct(ui.uiScore) + " panels " + pct(ui.panelScore) + " toolbar " + pct(ui.toolbarScore) + " scene " + pct(ui.sceneScore), 12, 84, paint);

        int shown = 0;
        if (ui.isUiScreenshot && ui.uiScore > 0.48f) {
            Bitmap ref = scaleMax(source, 256);
            float usx = bmp.getWidth() / (float)Math.max(1, ref.getWidth());
            float usy = bmp.getHeight() / (float)Math.max(1, ref.getHeight());
            for (UiLayoutAnalysis.Region r : ui.regions) {
                drawBox(canvas, scale(r.rect, usx, usy), r.role, uiColor(r.role), bmp.getWidth());
                if (++shown >= 10) break;
            }
        } else {
            for (ComponentRoleMap.Item item : roles.items) {
                if (item.role.equals(ComponentRoleMap.NOISE_IGNORE)) continue;
                drawBox(canvas, scale(item.rect, sx, sy), item.role, roleColor(item.role), bmp.getWidth());
                if (++shown >= 12) break;
            }
        }
        return bmp;
    }

    private static void drawBox(Canvas canvas, RectF rect, String label, int color, int maxW) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, maxW / 320f));
        p.setColor(color);
        canvas.drawRoundRect(rect, 10f, 10f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC000000);
        p.setTextSize(Math.max(13f, maxW / 52f));
        float tw = p.measureText(label);
        float y = Math.max(24f, rect.top + 22f);
        canvas.drawRoundRect(new RectF(rect.left, y - 22f, Math.min(maxW, rect.left + tw + 18f), y + 6f), 8f, 8f, p);
        p.setColor(Color.WHITE);
        canvas.drawText(label, rect.left + 8f, y, p);
    }

    private static int roleColor(String role) {
        if (role.equals(ComponentRoleMap.MAIN_SYMBOL)) return 0xFF22E6F2;
        if (role.equals(ComponentRoleMap.INNER_CUT)) return 0xFFFFAA00;
        if (role.equals(ComponentRoleMap.GLOW)) return 0xFF9B6BFF;
        if (role.equals(ComponentRoleMap.EDGE_HIGHLIGHT)) return 0xFF69F0AE;
        if (role.equals(ComponentRoleMap.TEXT)) return 0xFFFF4D6D;
        return 0xFF666666;
    }

    private static int uiColor(String role) {
        if (role.equals(UiLayoutAnalysis.TOP_HUD)) return 0xFF22E6F2;
        if (role.equals(UiLayoutAnalysis.LEFT_PANEL)) return 0xFF9B6BFF;
        if (role.equals(UiLayoutAnalysis.RIGHT_GIZMO)) return 0xFFFFAA00;
        if (role.equals(UiLayoutAnalysis.BOTTOM_TOOLBAR)) return 0xFF69F0AE;
        if (role.equals(UiLayoutAnalysis.SCENE_VIEWPORT)) return 0xFF4D7CFF;
        if (role.equals(UiLayoutAnalysis.BRUSH_CURSOR)) return 0xFFFF4D6D;
        return 0xFFFFFFFF;
    }

    private static String html(ImageAnalysis analysis, DrawingIntentAnalysis intent) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><style>body{background:#071018;color:#e9f7ff;font-family:sans-serif;padding:16px}.card{border:1px solid #22e6f2;border-radius:12px;padding:12px;margin:10px 0;background:#0b1420}a{color:#22e6f2}</style></head><body>"
                + "<h1>SolumDraw отчёт</h1>"
                + "<div class=card><b>Класс:</b> " + esc(analysis.genre) + " " + Math.round(analysis.confidence * 100f) + "%<br>"
                + "<b>План:</b> " + esc(analysis.strategy) + "<br>"
                + "<b>Причины:</b> " + esc(analysis.warnings) + "</div>"
                + "<div class=card>Открой картинки рядом с этим HTML: original, overlay, attention, edges, components, role map, draw order, ui layout.</div>"
                + "</body></html>";
    }

    private static String ruReadme(ImageAnalysis analysis, DrawingIntentAnalysis intent) {
        return "SolumDraw Single Analysis\n\n"
                + "Папка отчёта теперь лежит не в корне Download, а в Download/SolumDrawReports/Single/.\n"
                + "ZIP всё ещё лежит прямо в Download, чтобы его было легко отправить.\n\n"
                + "Класс: " + analysis.genre + " " + Math.round(analysis.confidence * 100f) + "%\n"
                + "План: " + analysis.strategy + "\n"
                + "Причины: " + analysis.warnings + "\n";
    }

    private static Bitmap scaleMax(Bitmap source, int maxSide) {
        float s = Math.min(1f, maxSide / (float)Math.max(source.getWidth(), source.getHeight()));
        return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * s)), Math.max(1, Math.round(source.getHeight() * s)), true);
    }

    private static RectF scale(RectF r, float sx, float sy) { return new RectF(r.left * sx, r.top * sy, r.right * sx, r.bottom * sy); }

    private static void savePng(Bitmap bmp, File file) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
        out.close();
    }

    private static void writeText(File file, String text) throws Exception {
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        w.write(text == null ? "" : text);
        w.close();
    }

    private static void zipDir(File dir, File zipFile) throws Exception {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));
        addDir(zip, dir, dir.getAbsolutePath().length() + 1);
        zip.close();
    }

    private static void addDir(ZipOutputStream zip, File file, int rootLen) throws Exception {
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) for (File kid : kids) addDir(zip, kid, rootLen);
        } else {
            zip.putNextEntry(new ZipEntry(file.getAbsolutePath().substring(rootLen)));
            FileInputStream in = new FileInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
            in.close();
            zip.closeEntry();
        }
    }

    private static void cleanupOldFolders(File root, int keep) {
        File[] dirs = root.listFiles();
        if (dirs == null || dirs.length <= keep) return;
        java.util.Arrays.sort(dirs, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        for (int i = keep; i < dirs.length; i++) deleteRec(dirs[i]);
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    private static String pct(float v) { return Math.round(v * 100f) + "%"; }
    private static String timestamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private static String safe(String s) { return s == null ? "image" : s.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    public static final class Result {
        public final String dirPath;
        public final String htmlPath;
        public final String zipPath;
        Result(String dirPath, String htmlPath, String zipPath) { this.dirPath = dirPath; this.htmlPath = htmlPath; this.zipPath = zipPath; }
    }
}
