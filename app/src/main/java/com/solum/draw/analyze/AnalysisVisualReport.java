package com.solum.draw.analyze;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Environment;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AnalysisVisualReport {
    private static final int MAX_SIDE = 900;

    private AnalysisVisualReport() {}

    public static Result writeSingle(Bitmap source, ImageAnalysis analysis) throws Exception {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloads, "SolumDrawSingleAnalysis_" + timestamp());
        dir.mkdirs();
        AnalysisLayers layers = AnalysisLayers.build(source);
        ComponentRoleMap roles = ComponentRoleMap.build(source, layers, analysis);
        UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(source);
        DrawingIntentAnalysis intent = DrawingIntentAnalysis.build(analysis, layers, roles, ui);
        Bitmap original = scaleMax(source, MAX_SIDE);
        Bitmap overlay = overlay(source, analysis, layers, roles, ui, intent);
        Bitmap attention = scaleMax(layers.makeAttentionBitmap(source), MAX_SIDE);
        Bitmap edges = scaleMax(layers.makeEdgeBitmap(), MAX_SIDE);
        Bitmap components = scaleMax(layers.makeComponentsBitmap(), MAX_SIDE);
        Bitmap roleMap = scaleMax(roles.makeRoleBitmap(source, layers), MAX_SIDE);
        Bitmap drawOrder = scaleMax(roles.makeDrawOrderBitmap(source, layers), MAX_SIDE);
        Bitmap uiMap = scaleMax(ui.makeUiMap(source), MAX_SIDE);
        Bitmap priority = scaleMax(layers.makePriorityBitmap(source), MAX_SIDE);
        Bitmap histogram = histogramBitmap(layers);
        Bitmap palette = paletteBitmap(layers);
        Bitmap side = sideBySide(original, overlay, analysis);
        savePng(original, new File(dir, "original.png"));
        savePng(overlay, new File(dir, "analysis_overlay.png"));
        savePng(attention, new File(dir, "01_attention_map.png"));
        savePng(edges, new File(dir, "02_edges_sobel.png"));
        savePng(components, new File(dir, "03_components_map.png"));
        savePng(roleMap, new File(dir, "04_role_map.png"));
        savePng(drawOrder, new File(dir, "05_draw_order_map.png"));
        savePng(uiMap, new File(dir, "06_ui_layout_map.png"));
        savePng(histogram, new File(dir, "07_histogram.png"));
        savePng(palette, new File(dir, "08_palette_kmeans.png"));
        savePng(priority, new File(dir, "09_priority_overlay.png"));
        savePng(side, new File(dir, "analysis_side_by_side.png"));
        writeText(new File(dir, "analysis.json"), analysis.toJson());
        writeText(new File(dir, "layers.json"), layers.metricsJson());
        writeText(new File(dir, "component_roles.json"), roles.toJson());
        writeText(new File(dir, "ui_layout.json"), ui.toJson());
        writeText(new File(dir, "drawing_intent.json"), intent.toJson());
        writeText(new File(dir, "analysis_report.html"), html(analysis, layers, roles, ui, intent,
                original, overlay, attention, edges, components, roleMap, drawOrder, uiMap, histogram, palette, priority, side));
        File zip = new File(downloads, dir.getName() + ".zip");
        zipDir(dir, zip);
        return new Result(dir.getAbsolutePath(), new File(dir, "analysis_report.html").getAbsolutePath(), zip.getAbsolutePath());
    }

    public static Bitmap overlay(Bitmap source, ImageAnalysis a) {
        AnalysisLayers layers = AnalysisLayers.build(source);
        ComponentRoleMap roles = ComponentRoleMap.build(source, layers, a);
        UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(source);
        return overlay(source, a, layers, roles, ui, DrawingIntentAnalysis.build(a, layers, roles, ui));
    }

    public static Bitmap overlay(Bitmap source, ImageAnalysis a, AnalysisLayers layers) {
        ComponentRoleMap roles = ComponentRoleMap.build(source, layers, a);
        UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(source);
        return overlay(source, a, layers, roles, ui, DrawingIntentAnalysis.build(a, layers, roles, ui));
    }

    private static Bitmap overlay(Bitmap source, ImageAnalysis a, AnalysisLayers layers, ComponentRoleMap roles, UiLayoutAnalysis ui, DrawingIntentAnalysis intent) {
        Bitmap bmp = scaleMax(source, 720).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(bmp);
        float sx = bmp.getWidth() / (float) layers.width;
        float sy = bmp.getHeight() / (float) layers.height;
        drawBanner(c, bmp.getWidth(), a, ui, intent);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(3f, bmp.getWidth() / 180f));
        Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBg.setColor(0xCC000000);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(Math.max(16f, bmp.getWidth() / 30f));
        text.setFakeBoldText(true);
        int shown = 0;
        if (ui.isUiScreenshot && intent.uiEvidence >= 0.48f) {
            Bitmap ref = scaleMax(source, 256);
            float usx = bmp.getWidth() / (float) ref.getWidth();
            float usy = bmp.getHeight() / (float) ref.getHeight();
            for (UiLayoutAnalysis.Region r : ui.regions) {
                drawMark(c, stroke, labelBg, text, scale(r.rect, usx, usy), r.role, uiColor(r.role), bmp.getWidth());
                shown++;
                if (shown >= 10) break;
            }
            return bmp;
        }
        for (ComponentRoleMap.Item item : roles.items) {
            if (item.role.equals(ComponentRoleMap.NOISE_IGNORE)) continue;
            drawMark(c, stroke, labelBg, text, scale(item.rect, sx, sy), item.role, roleColor(item.role), bmp.getWidth());
            shown++;
            if (shown >= 12) break;
        }
        return bmp;
    }

    public static String writeBenchOverlay(File dir, Bitmap source, ImageAnalysis analysis, String name) throws Exception {
        File visualDir = new File(dir, "visuals");
        visualDir.mkdirs();
        File out = new File(visualDir, safe(name) + "_overlay.png");
        savePng(overlay(source, analysis), out);
        return "visuals/" + out.getName();
    }

    private static void drawMark(Canvas c, Paint stroke, Paint labelBg, Paint text, RectF rect, String label, int color, int maxW) {
        stroke.setColor(color);
        c.drawRect(rect, stroke);
        float tw = text.measureText(label);
        float y = Math.max(text.getTextSize() + 8f, rect.top + text.getTextSize() + 8f);
        c.drawRoundRect(new RectF(rect.left, y - text.getTextSize() - 8f, Math.min(maxW, rect.left + tw + 16f), y + 5f), 8f, 8f, labelBg);
        c.drawText(label, rect.left + 8f, y - 4f, text);
    }

    private static int roleColor(String role) {
        if (role.equals(ComponentRoleMap.MAIN_SYMBOL)) return 0xFF00E5FF;
        if (role.equals(ComponentRoleMap.INNER_CUT)) return 0xFFFFAA00;
        if (role.equals(ComponentRoleMap.GLOW)) return 0xFFB388FF;
        if (role.equals(ComponentRoleMap.EDGE_HIGHLIGHT)) return 0xFF69F0AE;
        if (role.equals(ComponentRoleMap.TEXT)) return 0xFFFF4D6D;
        return 0xFF666666;
    }

    private static int uiColor(String role) {
        if (role.equals(UiLayoutAnalysis.TOP_HUD)) return 0xFF00E5FF;
        if (role.equals(UiLayoutAnalysis.LEFT_PANEL)) return 0xFFB388FF;
        if (role.equals(UiLayoutAnalysis.RIGHT_GIZMO)) return 0xFFFFAA00;
        if (role.equals(UiLayoutAnalysis.BOTTOM_TOOLBAR)) return 0xFF69F0AE;
        if (role.equals(UiLayoutAnalysis.SCENE_VIEWPORT)) return 0xFF4D7CFF;
        if (role.equals(UiLayoutAnalysis.BRUSH_CURSOR)) return 0xFFFF4D6D;
        if (role.equals(UiLayoutAnalysis.TEXT_LABELS)) return 0xFFFFFFFF;
        return 0xFF888888;
    }

    private static Bitmap histogramBitmap(AnalysisLayers l) {
        int w = 640, h = 180;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF0F1118);
        int max = 1;
        for (int v : l.histogram) max = Math.max(max, v);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int bw = Math.max(1, w / l.histogram.length);
        for (int i = 0; i < l.histogram.length; i++) {
            float ratio = l.histogram[i] / (float) max;
            int barH = Math.round(ratio * (h - 34));
            p.setColor(i < 20 ? 0xFF4D7CFF : (i > 46 ? 0xFF00FF88 : 0xFF888888));
            c.drawRect(i * bw, h - barH - 14, i * bw + bw - 1, h - 14, p);
        }
        p.setColor(Color.WHITE); p.setTextSize(18f); p.setFakeBoldText(true);
        c.drawText("BRIGHTNESS HISTOGRAM / 64 buckets", 12, 24, p);
        return out;
    }

    private static Bitmap paletteBitmap(AnalysisLayers l) {
        int w = 640, h = 180;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF0F1118);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE); t.setTextSize(18f); t.setFakeBoldText(true);
        c.drawText("K-MEANS PALETTE / 6 clusters", 12, 24, t);
        int x = 12;
        for (AnalysisLayers.ColorCluster cc : l.clusters) {
            int sw = Math.max(34, Math.round(520 * cc.ratio));
            p.setColor(cc.color);
            c.drawRoundRect(new RectF(x, 52, Math.min(w - 12, x + sw), 128), 10f, 10f, p);
            t.setTextSize(14f); t.setFakeBoldText(false);
            c.drawText(cc.hex() + " " + Math.round(cc.ratio * 100f) + "%", x, 154, t);
            x += sw + 8;
            if (x > w - 80) break;
        }
        return out;
    }

    private static Bitmap sideBySide(Bitmap original, Bitmap overlay, ImageAnalysis a) {
        int w = original.getWidth() + overlay.getWidth();
        int h = Math.max(original.getHeight(), overlay.getHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF11151C); c.drawBitmap(original, 0, 0, null); c.drawBitmap(overlay, original.getWidth(), 0, null);
        return out;
    }

    private static String html(ImageAnalysis a, AnalysisLayers l, ComponentRoleMap roles, UiLayoutAnalysis ui, DrawingIntentAnalysis intent,
            Bitmap original, Bitmap overlay, Bitmap attention, Bitmap edges, Bitmap components, Bitmap roleMap, Bitmap drawOrder, Bitmap uiMap,
            Bitmap histogram, Bitmap palette, Bitmap priority, Bitmap side) {
        StringBuilder b = new StringBuilder();
        b.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>");
        b.append("body{font-family:monospace;background:#080808;color:#ececec;padding:14px}h1{color:#00ff88}.sl{color:#00ff88;letter-spacing:.22em;font-size:11px;margin:22px 0 8px;text-transform:uppercase}.card{background:#111;border:1px solid #252525;border-radius:10px;padding:12px;margin:10px 0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:10px}img{width:100%;border-radius:8px;background:#000}.tag{display:inline-block;border:1px solid #00ff8840;color:#00ff88;border-radius:4px;padding:5px 8px;margin:3px;font-size:11px}.metric{display:grid;grid-template-columns:1fr 1fr;gap:6px}.m{background:#0f0f0f;border:1px solid #1c1c1c;border-radius:6px;padding:8px}.json{white-space:pre-wrap;font-size:11px;overflow:auto}table{width:100%;border-collapse:collapse;font-size:11px}td,th{border:1px solid #272727;padding:5px;text-align:left}ol,ul{padding-left:20px}</style></head><body>");
        b.append("<h1>SolumDraw / Visual Analysis</h1>");
        b.append("<div class=card><b>").append(esc(a.genre)).append("</b> conf ").append(pct(a.confidence)).append("<br><b>Intent:</b> ").append(esc(intent.primaryIntent)).append(" / ").append(esc(intent.strategy)).append("<br>").append(esc(firstImpression(a, ui, intent))).append("</div>");
        b.append("<div class=sl>WHAT I SEE</div><div class=card>");
        for (String tag : tags(a, ui, intent)) b.append("<span class=tag>").append(esc(tag)).append("</span>");
        b.append("</div>");
        b.append("<div class=sl>DRAWING INTENT</div><div class=card>").append(intent.html()).append("</div>");
        b.append("<div class=sl>VISUAL LAYERS / EMBEDDED BASE64</div><div class=grid>");
        card(b, "Original", original); card(b, "Final overlay", overlay); card(b, "Attention map", attention); card(b, "Sobel edges", edges); card(b, "Components", components); card(b, "Role map", roleMap); card(b, "Draw order", drawOrder); card(b, "UI layout", uiMap); card(b, "Histogram", histogram); card(b, "K-Means palette", palette); card(b, "Priority overlay", priority); card(b, "Side by side", side);
        b.append("</div>");
        b.append("<div class=sl>FEATURE VECTOR</div><div class='card metric'>");
        metric(b,"edge",a.edgeDensity); metric(b,"detail",a.detailDensity); metric(b,"realText",a.realTextRatio); metric(b,"glyph",a.glyphRatio); metric(b,"logo",a.logoScore); metric(b,"ui",ui.uiScore); metric(b,"panels",ui.panelScore); metric(b,"toolbar",ui.toolbarScore); metric(b,"scene",ui.sceneScore); metric(b,"saliency",a.saliencyDensity); metric(b,"centralObject",a.centralObjectRatio); metric(b,"symmetry",a.symmetryVertical); metric(b,"components",a.componentCount/100f); metric(b,"textCand",a.textComponentCount/100f); metric(b,"textLines",a.textLineCount/10f); metric(b,"largestComp",a.largestComponentRatio);
        b.append("</div>");
        b.append("<div class=sl>UI LAYOUT</div><div class=card><pre>").append(esc(ui.toJson())).append("</pre></div>");
        b.append("<div class=sl>COMPONENT ROLES</div><div class=card>").append(roles.roleSummaryHtml()).append("</div>");
        b.append("<div class=sl>WHY PREDICTED</div><div class=card>").append(esc(a.strategy)).append("<br>warnings: ").append(esc(a.warnings)).append("</div>");
        b.append("<div class=sl>JSON</div><div class='card json'>").append(esc(a.toJson())).append("\n\n").append(esc(l.metricsJson())).append("\n\n").append(esc(roles.toJson())).append("\n\n").append(esc(ui.toJson())).append("\n\n").append(esc(intent.toJson())).append("</div>");
        b.append("</body></html>");
        return b.toString();
    }

    private static String firstImpression(ImageAnalysis a, UiLayoutAnalysis ui, DrawingIntentAnalysis intent) {
        if (intent.primaryIntent.contains("ui")) return "Drawing route: layout first. Draw viewport/app content, panels, cards/buttons, icons, text labels, then polish.";
        if (intent.primaryIntent.contains("character")) return "Drawing route: silhouette first. Preserve body/head outline before details.";
        if (intent.primaryIntent.contains("portrait")) return "Drawing route: mass first with face priority.";
        if (intent.primaryIntent.contains("logo")) return "Drawing route: shape first with crisp edges and visible cuts.";
        if (intent.primaryIntent.contains("scene")) return "Drawing route: scene masses first, details last.";
        if (intent.primaryIntent.contains("lineart")) return "Drawing route: contour-first line art.";
        return "Drawing route: general semantic layers, not printer scanlines.";
    }

    private static List<String> tags(ImageAnalysis a, UiLayoutAnalysis ui, DrawingIntentAnalysis intent) {
        ArrayList<String> t = new ArrayList<>();
        t.add(a.genre); t.add("intent:" + intent.primaryIntent); t.add("budget:" + intent.estimatedUsefulActions); t.add("noiseIgnore:" + intent.noiseIgnoreLevel + "/5");
        if (ui.isUiScreenshot) { t.add("uiScore:" + pct(ui.uiScore)); t.add(ui.subgenre); t.add("panels:" + pct(ui.panelScore)); t.add("scene:" + pct(ui.sceneScore)); }
        t.add("ev.ui:" + pct(intent.uiEvidence)); t.add("ev.char:" + pct(intent.characterEvidence)); t.add("ev.scene:" + pct(intent.sceneEvidence)); t.add("ev.logo:" + pct(intent.logoEvidence));
        if (a.realTextRatio > .16f && a.textLineCount >= 2) t.add("verified-text");
        if (a.componentCount > 0) t.add("components:" + a.componentCount);
        return t;
    }

    private static void card(StringBuilder b, String title, Bitmap img) { b.append("<div class=card><b>").append(esc(title)).append("</b><br><img src=\"").append(dataUri(img)).append("\"></div>"); }
    private static void metric(StringBuilder b, String name, float v) { b.append("<div class=m>").append(esc(name)).append("<br><b>").append(pct(v)).append("</b></div>"); }
    private static RectF scale(RectF r, float sx, float sy) { return new RectF(r.left * sx, r.top * sy, r.right * sx, r.bottom * sy); }
    private static void drawBanner(Canvas c, int w, ImageAnalysis a, UiLayoutAnalysis ui, DrawingIntentAnalysis intent) { Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(0xCC11151C); c.drawRect(0,0,w,126,bg); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.WHITE); p.setTextSize(22f); p.setFakeBoldText(true); c.drawText(a.genre+" "+pct(a.confidence),12,28,p); p.setTextSize(14f); p.setFakeBoldText(false); c.drawText("intent "+intent.primaryIntent+" budget "+intent.estimatedUsefulActions+" noise "+intent.noiseIgnoreLevel+"/5",12,52,p); c.drawText("edge "+pct(a.edgeDensity)+" text "+pct(a.realTextRatio)+" glyph "+pct(a.glyphRatio)+" logo "+pct(a.logoScore),12,76,p); c.drawText("ui "+pct(ui.uiScore)+" panels "+pct(ui.panelScore)+" toolbar "+pct(ui.toolbarScore)+" scene "+pct(ui.sceneScore),12,98,p); c.drawText("ev ui/char/scene/logo "+pct(intent.uiEvidence)+"/"+pct(intent.characterEvidence)+"/"+pct(intent.sceneEvidence)+"/"+pct(intent.logoEvidence),12,120,p); }
    private static Bitmap scaleMax(Bitmap source, int maxSide) { float s = Math.min(1f, maxSide / (float)Math.max(source.getWidth(), source.getHeight())); return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth()*s)), Math.max(1, Math.round(source.getHeight()*s)), true); }
    private static void savePng(Bitmap bmp, File file) throws Exception { FileOutputStream out = new FileOutputStream(file); bmp.compress(Bitmap.CompressFormat.PNG, 92, out); out.close(); }
    private static String dataUri(Bitmap bmp) { ByteArrayOutputStream out = new ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 86, out); return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP); }
    private static void writeText(File file, String text) throws Exception { OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); w.write(text); w.close(); }
    private static void zipDir(File dir, File zipFile) throws Exception { ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile)); addDir(zip, dir, dir.getAbsolutePath().length()+1); zip.close(); }
    private static void addDir(ZipOutputStream zip, File file, int rootLen) throws Exception { if(file.isDirectory()){ File[] kids=file.listFiles(); if(kids!=null) for(File kid:kids) addDir(zip,kid,rootLen); } else { zip.putNextEntry(new ZipEntry(file.getAbsolutePath().substring(rootLen))); FileInputStream in=new FileInputStream(file); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) zip.write(buf,0,n); in.close(); zip.closeEntry(); } }
    private static String pct(float v) { return Math.round(v*100f)+"%"; }
    private static String timestamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private static String safe(String s) { return s == null ? "image" : s.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String esc(String s) { return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }

    public static final class Result { public final String dirPath; public final String htmlPath; public final String zipPath; Result(String dirPath,String htmlPath,String zipPath){this.dirPath=dirPath;this.htmlPath=htmlPath;this.zipPath=zipPath;} }
}
