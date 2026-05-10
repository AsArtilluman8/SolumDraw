package com.solum.draw.analyze;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AnalyzerBenchmark {
    public static final String INPUT_DIR = "SolumDrawTestImages";
    public static final String DATASET_DIR = "SolumDrawDataset_v1/SolumDrawDataset_v1";

    private AnalyzerBenchmark() {}

    public interface Progress {
        void onStart(String datasetPath, int total, int labelsFound);
        void onItem(int index, int total, String name, int top1, int top3, int missingLabels);
    }

    public static Result run(Context context) throws Exception { return run(context, null); }

    public static Result run(Context context, Progress progress) throws Exception {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dataset = pickDataset(downloads);
        File outDir = new File(downloads, "SolumDrawAnalyzerReport_" + timestamp());
        outDir.mkdirs();

        List<BenchImage> images = new ArrayList<>();
        collectImages(dataset, dataset, images);
        Collections.sort(images, new Comparator<BenchImage>() { @Override public int compare(BenchImage a, BenchImage b) { return a.relativeName.compareTo(b.relativeName); } });

        int labelsFound = 0;
        for (BenchImage img : images) if (img.expected.length() > 0) labelsFound++;
        if (progress != null) progress.onStart(dataset.getAbsolutePath(), images.size(), labelsFound);

        List<ItemResult> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int top1 = 0, top3 = 0, missingLabels = 0;

        for (int i = 0; i < images.size(); i++) {
            BenchImage img = images.get(i);
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(img.file));
                if (bitmap == null) throw new IllegalArgumentException("decode returned null");
                ImageAnalysis analysis = ImageAnalyzer.analyze(bitmap, img.relativeName);
                List<String> candidates = top3Candidates(analysis);
                boolean hasExpected = img.expected.length() > 0;
                boolean ok1 = hasExpected && img.expected.equals(analysis.genre);
                boolean ok3 = hasExpected && contains(candidates, img.expected);
                if (!hasExpected) missingLabels++;
                if (ok1) top1++;
                if (ok3) top3++;
                items.add(new ItemResult(img.relativeName, img.expected, img.secondary, analysis, candidates, ok1, ok3, hasExpected));
            } catch (Exception e) {
                errors.add(img.relativeName + ": " + e.getMessage());
            }
            if (progress != null) progress.onItem(i + 1, images.size(), img.relativeName, top1, top3, missingLabels);
        }

        Stats stats = buildStats(items);
        writeText(new File(outDir, "benchmark_results.csv"), resultsCsv(items));
        writeText(new File(outDir, "mistakes.csv"), mistakesCsv(items));
        writeText(new File(outDir, "predictions.jsonl"), predictionsJsonl(items));
        writeText(new File(outDir, "benchmark_summary.json"), summaryJson(dataset, images.size(), labelsFound, top1, top3, missingLabels, errors, stats));
        writeText(new File(outDir, "BENCHMARK_REPORT.md"), reportMd(dataset, images.size(), labelsFound, top1, top3, missingLabels, errors, stats));
        writeText(new File(outDir, "summary.txt"), reportMd(dataset, images.size(), labelsFound, top1, top3, missingLabels, errors, stats));

        File zip = new File(downloads, outDir.getName() + ".zip");
        zipDir(outDir, zip);
        return new Result(dataset.getAbsolutePath(), zip.getAbsolutePath(), images.size(), errors.size(), labelsFound, top1, top3, missingLabels);
    }

    private static File pickDataset(File downloads) {
        File rich = new File(downloads, DATASET_DIR);
        if (rich.exists() && rich.isDirectory()) return rich;
        File old = new File(downloads, INPUT_DIR);
        if (!old.exists()) old.mkdirs();
        return old;
    }

    private static void collectImages(File root, File dir, List<BenchImage> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) collectImages(root, f, out);
            else if (isImageName(f.getName())) out.add(BenchImage.fromFile(root, f));
        }
    }

    private static List<String> top3Candidates(ImageAnalysis a) {
        ArrayList<String> c = new ArrayList<>();
        add(c, a.genre);
        String g = a.genre;
        if (g.contains("ui")) { add(c, "ui_screenshot"); add(c, "game_ui_hud"); add(c, "diagram_chart"); }
        else if (g.contains("anime")) { add(c, "anime_manga"); add(c, "cartoon_comic"); add(c, "lineart_sketch"); }
        else if (g.contains("portrait") || a.skinRatio > 0.08f) { add(c, "portrait_character"); add(c, "human_body_fullbody"); add(c, "photo_general"); }
        else if (g.contains("logo")) { add(c, "logo_icon"); add(c, "vector_flat"); add(c, "transparent_layered"); }
        else if (g.contains("sketch")) { add(c, "lineart_sketch"); add(c, "pencil_drawing"); add(c, "ink_wash"); }
        else if (g.contains("vector")) { add(c, "vector_flat"); add(c, "isometric_art"); add(c, "cartoon_comic"); }
        else if (g.contains("cinematic") || g.contains("wallpaper") || g.contains("photo")) { add(c, "digital_painting_concept"); add(c, "landscape_environment"); add(c, "photo_general"); }
        else { add(c, "photo_general"); add(c, "digital_painting_concept"); add(c, "texture_pattern"); }
        while (c.size() > 3) c.remove(c.size() - 1);
        return c;
    }

    private static void add(List<String> list, String v) { if (v != null && v.length() > 0 && !list.contains(v)) list.add(v); }
    private static boolean contains(List<String> list, String v) { for (String s : list) if (s.equals(v)) return true; return false; }

    private static Stats buildStats(List<ItemResult> items) {
        Stats s = new Stats();
        for (ItemResult it : items) {
            inc(s.predicted, it.analysis.genre);
            if (it.hasExpected) {
                inc(s.expected, it.expected);
                inc(s.confusion, it.expected + " -> " + it.analysis.genre);
                if (!it.ok1) s.wrong++;
            } else s.missing++;
        }
        return s;
    }

    private static String resultsCsv(List<ItemResult> items) {
        StringBuilder b = new StringBuilder("file,true_class,secondary_classes,predicted,top3,top1_ok,top3_ok,confidence\n");
        for (ItemResult it : items) b.append(csv(it.name)).append(',').append(csv(it.expected)).append(',').append(csv(it.secondary)).append(',').append(csv(it.analysis.genre)).append(',').append(csv(join(it.top3))).append(',').append(it.ok1).append(',').append(it.ok3).append(',').append(num(it.analysis.confidence)).append('\n');
        return b.toString();
    }

    private static String mistakesCsv(List<ItemResult> items) {
        StringBuilder b = new StringBuilder("file,true_class,predicted,top3,confidence,warnings\n");
        for (ItemResult it : items) if (it.hasExpected && !it.ok1) b.append(csv(it.name)).append(',').append(csv(it.expected)).append(',').append(csv(it.analysis.genre)).append(',').append(csv(join(it.top3))).append(',').append(num(it.analysis.confidence)).append(',').append(csv(it.analysis.warnings)).append('\n');
        return b.toString();
    }

    private static String predictionsJsonl(List<ItemResult> items) {
        StringBuilder b = new StringBuilder();
        for (ItemResult it : items) b.append("{\"file\":\"").append(esc(it.name)).append("\",\"true_class\":\"").append(esc(it.expected)).append("\",\"predicted\":\"").append(esc(it.analysis.genre)).append("\",\"top3\":\"").append(esc(join(it.top3))).append("\",\"top1_ok\":").append(it.ok1).append(",\"top3_ok\":").append(it.ok3).append("}\n");
        return b.toString();
    }

    private static String summaryJson(File dataset, int total, int labels, int top1, int top3, int missing, List<String> errors, Stats stats) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"dataset\": \"").append(esc(dataset.getAbsolutePath())).append("\",\n");
        b.append("  \"total\": ").append(total).append(",\n");
        b.append("  \"labels_found\": ").append(labels).append(",\n");
        b.append("  \"missing_labels\": ").append(missing).append(",\n");
        b.append("  \"top1_correct\": ").append(top1).append(",\n");
        b.append("  \"top3_correct\": ").append(top3).append(",\n");
        b.append("  \"top1_accuracy\": ").append(total == 0 ? "0" : num(top1 / (float)Math.max(1, labels))).append(",\n");
        b.append("  \"top3_accuracy\": ").append(total == 0 ? "0" : num(top3 / (float)Math.max(1, labels))).append(",\n");
        b.append("  \"errors\": ").append(errors.size()).append("\n}");
        return b.toString();
    }

    private static String reportMd(File dataset, int total, int labels, int top1, int top3, int missing, List<String> errors, Stats stats) {
        StringBuilder b = new StringBuilder();
        b.append("# SolumDraw Benchmark Report\n\n");
        b.append("Dataset: `").append(dataset.getAbsolutePath()).append("`\n\n");
        b.append("- Images: ").append(total).append('\n');
        b.append("- Labels found: ").append(labels).append('\n');
        b.append("- Missing labels: ").append(missing).append('\n');
        b.append("- Top1: ").append(top1).append(" / ").append(labels).append(" = ").append(Math.round(100f * top1 / Math.max(1, labels))).append("%\n");
        b.append("- Top3: ").append(top3).append(" / ").append(labels).append(" = ").append(Math.round(100f * top3 / Math.max(1, labels))).append("%\n");
        b.append("- Errors: ").append(errors.size()).append("\n\n");
        b.append("## Что это значит\n\n0% плохо, 100% идеально. Сейчас это тест понимания картинки, не тест рисования.\n\n");
        b.append("## Частые предсказания\n\n").append(mapMd(stats.predicted));
        b.append("\n## Частые ошибки\n\n").append(mapMd(stats.confusion));
        return b.toString();
    }

    private static String mapMd(Map<String,Integer> m) {
        StringBuilder b = new StringBuilder();
        List<Map.Entry<String,Integer>> e = new ArrayList<>(m.entrySet());
        Collections.sort(e, new Comparator<Map.Entry<String,Integer>>() { @Override public int compare(Map.Entry<String,Integer> a, Map.Entry<String,Integer> b) { return b.getValue() - a.getValue(); }});
        int n = 0; for (Map.Entry<String,Integer> x : e) { b.append("- ").append(x.getKey()).append(": ").append(x.getValue()).append('\n'); if (++n >= 30) break; }
        if (n == 0) b.append("none\n");
        return b.toString();
    }

    private static void inc(Map<String,Integer> map, String key) { Integer v = map.get(key); map.put(key, v == null ? 1 : v + 1); }

    private static String readText(File f) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line).append('\n'); r.close(); return b.toString();
        } catch (Exception e) { return ""; }
    }
    private static String jsonString(String json, String key) {
        String q = "\"" + key + "\""; int i = json.indexOf(q); if (i < 0) return ""; int c = json.indexOf(':', i); if (c < 0) return ""; int a = json.indexOf('"', c + 1); if (a < 0) return ""; int b = json.indexOf('"', a + 1); if (b < 0) return ""; return json.substring(a + 1, b);
    }
    private static String jsonArrayInline(String json, String key) {
        String q = "\"" + key + "\""; int i = json.indexOf(q); if (i < 0) return ""; int a = json.indexOf('[', i); int b = json.indexOf(']', a); if (a < 0 || b < 0) return ""; return json.substring(a + 1, b).replace("\"", "").replace("\n", " ").trim();
    }

    private static boolean isImageName(String name) { String n = name.toLowerCase(Locale.US); return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".bmp"); }
    private static String timestamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private static String num(float v) { return String.format(Locale.US, "%.4f", v); }
    private static String join(List<String> v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.size(); i++) { if (i > 0) b.append('|'); b.append(v.get(i)); } return b.toString(); }
    private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
    private static String csv(String s) { return "\"" + (s == null ? "" : s.replace("\"", "\"\"").replace("\n", " ")) + "\""; }
    private static void writeText(File file, String text) throws Exception { OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); w.write(text); w.close(); }
    private static void zipDir(File dir, File zipFile) throws Exception { ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile)); addDir(zip, dir, dir.getAbsolutePath().length() + 1); zip.close(); }
    private static void addDir(ZipOutputStream zip, File file, int rootLen) throws Exception { if (file.isDirectory()) { File[] kids = file.listFiles(); if (kids != null) for (File kid : kids) addDir(zip, kid, rootLen); } else { zip.putNextEntry(new ZipEntry(file.getAbsolutePath().substring(rootLen))); FileInputStream in = new FileInputStream(file); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) zip.write(buf, 0, n); in.close(); zip.closeEntry(); } }

    private static final class BenchImage {
        final String relativeName; final File file; final String expected; final String secondary;
        private BenchImage(String relativeName, File file, String expected, String secondary) { this.relativeName = relativeName; this.file = file; this.expected = expected; this.secondary = secondary; }
        static BenchImage fromFile(File root, File file) {
            String rel = file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1).replace(File.separatorChar, '/');
            File side = new File(file.getParentFile(), stripExt(file.getName()) + ".json");
            String json = readText(side);
            String trueClass = jsonString(json, "true_class");
            if (trueClass.length() == 0 && file.getParentFile() != null) trueClass = file.getParentFile().getName();
            String secondary = jsonArrayInline(json, "secondary_classes");
            return new BenchImage(rel, file, trueClass, secondary);
        }
        private static String stripExt(String n) { int i = n.lastIndexOf('.'); return i > 0 ? n.substring(0, i) : n; }
    }
    private static final class ItemResult { final String name, expected, secondary; final ImageAnalysis analysis; final List<String> top3; final boolean ok1, ok3, hasExpected; ItemResult(String name, String expected, String secondary, ImageAnalysis analysis, List<String> top3, boolean ok1, boolean ok3, boolean hasExpected) { this.name = name; this.expected = expected; this.secondary = secondary; this.analysis = analysis; this.top3 = top3; this.ok1 = ok1; this.ok3 = ok3; this.hasExpected = hasExpected; } }
    private static final class Stats { final Map<String,Integer> expected = new HashMap<>(); final Map<String,Integer> predicted = new HashMap<>(); final Map<String,Integer> confusion = new HashMap<>(); int wrong = 0; int missing = 0; }
    public static final class Result { public final String inputDir, zipPath; public final int images, errors, labelsFound, top1, top3, missingLabels; public Result(String inputDir, String zipPath, int images, int errors, int labelsFound, int top1, int top3, int missingLabels) { this.inputDir = inputDir; this.zipPath = zipPath; this.images = images; this.errors = errors; this.labelsFound = labelsFound; this.top1 = top1; this.top3 = top3; this.missingLabels = missingLabels; } }
}
