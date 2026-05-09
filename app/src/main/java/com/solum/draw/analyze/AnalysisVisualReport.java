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
        Bitmap original = scaleMax(source, MAX_SIDE);
        Bitmap overlay = overlay(source, analysis);
        Bitmap side = sideBySide(original, overlay, analysis);
        File originalFile = new File(dir, "original.png");
        File overlayFile = new File(dir, "analysis_overlay.png");
        File sideFile = new File(dir, "analysis_side_by_side.png");
        File jsonFile = new File(dir, "analysis.json");
        File htmlFile = new File(dir, "analysis_report.html");
        savePng(original, originalFile);
        savePng(overlay, overlayFile);
        savePng(side, sideFile);
        writeText(jsonFile, analysis.toJson());
        writeText(htmlFile, html(analysis));
        File zip = new File(downloads, dir.getName() + ".zip");
        zipDir(dir, zip);
        return new Result(dir.getAbsolutePath(), htmlFile.getAbsolutePath(), zip.getAbsolutePath());
    }

    public static Bitmap overlay(Bitmap source, ImageAnalysis a) {
        Bitmap bmp = scaleMax(source, 720).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(bmp);
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        drawBanner(c, w, a);
        List<Mark> marks = marks(bmp, a);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(3f, w / 180f));
        Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBg.setColor(0xCC000000);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(Math.max(16f, w / 30f));
        text.setFakeBoldText(true);
        for (Mark m : marks) {
            stroke.setColor(m.color);
            c.drawRect(m.rect, stroke);
            float tw = text.measureText(m.label);
            float y = Math.max(text.getTextSize() + 8f, m.rect.top + text.getTextSize() + 8f);
            c.drawRoundRect(new RectF(m.rect.left, y - text.getTextSize() - 8f, Math.min(w, m.rect.left + tw + 16f), y + 5f), 8f, 8f, labelBg);
            c.drawText(m.label, m.rect.left + 8f, y - 4f, text);
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

    private static List<Mark> marks(Bitmap bmp, ImageAnalysis a) {
        ArrayList<Mark> out = new ArrayList<>();
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        out.add(new Mark("OBJECT?", objectBox(bmp), 0xFF00E5FF));
        if (a.skinRatio > 0.08f) out.add(new Mark("FACE/SKIN", skinBox(bmp), 0xFFFFC46B));
        if (a.textRatio > 0.08f) out.add(new Mark("TEXT?", textBox(bmp), 0xFFFF4D6D));
        if (bottomTextScore(bmp) > 0.10f) out.add(new Mark("BOTTOM_TEXT?", new RectF(0, h * 0.78f, w, h), 0xFFFFAA00));
        if (centerEdgeScore(bmp) > 0.12f) out.add(new Mark("WATERMARK?", new RectF(w * 0.25f, h * 0.38f, w * 0.75f, h * 0.62f), 0xFFB388FF));
        if (a.detailDensity > 0.30f) out.add(new Mark("DETAIL", new RectF(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f), 0xFF69F0AE));
        if (a.paletteCompactness > 0.18f || a.edgeDensity < 0.12f) out.add(new Mark("BACKGROUND", new RectF(0, 0, w, h), 0x8888AAFF));
        return out;
    }

    private static RectF objectBox(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int border = Math.max(2, Math.min(w, h) / 20);
        long rr = 0, gg = 0, bb = 0, n = 0;
        for (int y = 0; y < h; y += 4) for (int x = 0; x < w; x += 4) if (x < border || y < border || x >= w - border || y >= h - border) { int c = bmp.getPixel(x, y); rr += Color.red(c); gg += Color.green(c); bb += Color.blue(c); n++; }
        if (n == 0) n = 1;
        int ar = (int)(rr / n), ag = (int)(gg / n), ab = (int)(bb / n);
        int minX = w, minY = h, maxX = 0, maxY = 0, count = 0;
        for (int y = 0; y < h; y += 3) for (int x = 0; x < w; x += 3) { int c = bmp.getPixel(x, y); int d = Math.abs(Color.red(c)-ar)+Math.abs(Color.green(c)-ag)+Math.abs(Color.blue(c)-ab); if (d > 70) { minX=Math.min(minX,x); minY=Math.min(minY,y); maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); count++; } }
        if (count < 20) return new RectF(w * .2f, h * .2f, w * .8f, h * .8f);
        return new RectF(Math.max(0, minX-8), Math.max(0, minY-8), Math.min(w, maxX+8), Math.min(h, maxY+8));
    }

    private static RectF skinBox(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int minX = w, minY = h, maxX = 0, maxY = 0, count = 0;
        for (int y = 0; y < h; y += 3) for (int x = 0; x < w; x += 3) { int c = bmp.getPixel(x, y); if (skin(Color.red(c), Color.green(c), Color.blue(c))) { minX=Math.min(minX,x); minY=Math.min(minY,y); maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); count++; } }
        if (count < 20) return new RectF(w * .25f, h * .15f, w * .75f, h * .65f);
        return new RectF(Math.max(0, minX-10), Math.max(0, minY-10), Math.min(w, maxX+10), Math.min(h, maxY+10));
    }

    private static RectF textBox(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int minX = w, minY = h, maxX = 0, maxY = 0, count = 0;
        for (int y = 2; y < h - 2; y += 4) for (int x = 2; x < w - 2; x += 4) if (edge(bmp,x,y) > 110) { minX=Math.min(minX,x); minY=Math.min(minY,y); maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); count++; }
        if (count < 20) return new RectF(w * .1f, h * .7f, w * .9f, h * .95f);
        return new RectF(Math.max(0, minX-8), Math.max(0, minY-8), Math.min(w, maxX+8), Math.min(h, maxY+8));
    }

    private static float bottomTextScore(Bitmap bmp) { return edgeScore(bmp, 0, (int)(bmp.getHeight()*.78f), bmp.getWidth(), bmp.getHeight()); }
    private static float centerEdgeScore(Bitmap bmp) { return edgeScore(bmp, (int)(bmp.getWidth()*.25f), (int)(bmp.getHeight()*.38f), (int)(bmp.getWidth()*.75f), (int)(bmp.getHeight()*.62f)); }
    private static float edgeScore(Bitmap bmp, int x0, int y0, int x1, int y1) { int total=0, hit=0; for(int y=Math.max(2,y0);y<Math.min(bmp.getHeight()-2,y1);y+=3) for(int x=Math.max(2,x0);x<Math.min(bmp.getWidth()-2,x1);x+=3){ total++; if(edge(bmp,x,y)>95) hit++; } return total==0?0f:hit/(float)total; }
    private static int edge(Bitmap bmp, int x, int y) { return Math.abs(luma(bmp.getPixel(x+1,y))-luma(bmp.getPixel(x-1,y))) + Math.abs(luma(bmp.getPixel(x,y+1))-luma(bmp.getPixel(x,y-1))); }
    private static int luma(int c) { return (Color.red(c)*30 + Color.green(c)*59 + Color.blue(c)*11) / 100; }
    private static boolean skin(int r, int g, int b) { return r > 75 && g > 40 && b > 25 && r > g && g > b && (r - b) > 35 && Math.abs(r - g) > 10; }

    private static void drawBanner(Canvas c, int w, ImageAnalysis a) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(0xCC11151C); c.drawRect(0,0,w,74,bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.WHITE); p.setTextSize(22f); p.setFakeBoldText(true);
        c.drawText(a.genre + "  " + pct(a.confidence), 12, 28, p);
        p.setTextSize(15f); p.setFakeBoldText(false);
        c.drawText("edge " + pct(a.edgeDensity) + "  detail " + pct(a.detailDensity) + "  skin " + pct(a.skinRatio) + "  text " + pct(a.textRatio), 12, 54, p);
    }

    private static Bitmap sideBySide(Bitmap original, Bitmap overlay, ImageAnalysis a) {
        int w = original.getWidth() + overlay.getWidth(); int h = Math.max(original.getHeight(), overlay.getHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(out); c.drawColor(0xFF11151C); c.drawBitmap(original,0,0,null); c.drawBitmap(overlay,original.getWidth(),0,null); return out;
    }

    private static String html(ImageAnalysis a) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>body{font-family:sans-serif;background:#11151c;color:#e8edf7;padding:16px}img{max-width:100%;border-radius:12px;background:#000}.card{background:#18202c;border:1px solid #2b3548;border-radius:14px;padding:12px;margin:12px 0}.json{white-space:pre-wrap;font-family:monospace;font-size:12px}</style></head><body><h1>SolumDraw visual analysis</h1><div class=card><b>" + esc(a.genre) + "</b> confidence " + pct(a.confidence) + "<br>strategy: " + esc(a.strategy) + "<br>warnings: " + esc(a.warnings) + "</div><div class=card><h2>Original</h2><img src=original.png></div><div class=card><h2>Overlay</h2><img src=analysis_overlay.png></div><div class=card><h2>Side by side</h2><img src=analysis_side_by_side.png></div><div class='card json'>" + esc(a.toJson()) + "</div></body></html>";
    }

    private static Bitmap scaleMax(Bitmap source, int maxSide) { float s = Math.min(1f, maxSide / (float)Math.max(source.getWidth(), source.getHeight())); return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth()*s)), Math.max(1, Math.round(source.getHeight()*s)), true); }
    private static void savePng(Bitmap bmp, File file) throws Exception { FileOutputStream out = new FileOutputStream(file); bmp.compress(Bitmap.CompressFormat.PNG, 92, out); out.close(); }
    private static void writeText(File file, String text) throws Exception { OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); w.write(text); w.close(); }
    private static void zipDir(File dir, File zipFile) throws Exception { ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile)); addDir(zip, dir, dir.getAbsolutePath().length()+1); zip.close(); }
    private static void addDir(ZipOutputStream zip, File file, int rootLen) throws Exception { if(file.isDirectory()){ File[] kids=file.listFiles(); if(kids!=null) for(File kid:kids) addDir(zip,kid,rootLen); } else { zip.putNextEntry(new ZipEntry(file.getAbsolutePath().substring(rootLen))); FileInputStream in=new FileInputStream(file); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) zip.write(buf,0,n); in.close(); zip.closeEntry(); } }
    private static String pct(float v) { return Math.round(v*100f)+"%"; }
    private static String timestamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private static String safe(String s) { return s == null ? "image" : s.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String esc(String s) { return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }

    private static final class Mark { final String label; final RectF rect; final int color; Mark(String label, RectF rect, int color){this.label=label;this.rect=rect;this.color=color;} }
    public static final class Result { public final String dirPath; public final String htmlPath; public final String zipPath; Result(String dirPath,String htmlPath,String zipPath){this.dirPath=dirPath;this.htmlPath=htmlPath;this.zipPath=zipPath;} }
}
