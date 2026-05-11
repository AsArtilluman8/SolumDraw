package com.solum.draw.vision;

import android.content.Context;
import android.os.Environment;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DatasetBenchmarkRunner {
    public static final class Item {
        public final File image;
        public final File json;
        public final String expected;
        public final String description;

        Item(File image, File json, String expected, String description) {
            this.image = image;
            this.json = json;
            this.expected = expected == null ? "unknown" : expected;
            this.description = description == null ? "" : description;
        }
    }

    public static final class Report {
        public final ArrayList<Item> items;
        public final File jsonReport;
        public final File csvReport;
        public final File htmlReport;
        public final File zipReport;
        public final String summary;

        Report(ArrayList<Item> items, File jsonReport, File csvReport, File htmlReport, File zipReport, String summary) {
            this.items = items;
            this.jsonReport = jsonReport;
            this.csvReport = csvReport;
            this.htmlReport = htmlReport;
            this.zipReport = zipReport;
            this.summary = summary;
        }
    }

    private DatasetBenchmarkRunner() {}

    public static Report buildBenchmarkPack(Context context) throws Exception {
        File download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File knownDataset = new File(download, "SolumDrawDataset_v1/SolumDrawDataset_v1");

        ArrayList<Item> items;
        if (knownDataset.exists()) {
            items = findDatasetItems(knownDataset);
        } else {
            items = findDatasetItems(download);
        }

        File outDir = new File(download, "SolumDrawBenchmark_" + stamp());
        if (!outDir.exists()) outDir.mkdirs();

        File jsonReport = new File(outDir, "benchmark_manifest.json");
        File csvReport = new File(outDir, "benchmark_manifest.csv");
        File htmlReport = new File(outDir, "benchmark_report.html");
        File zipReport = new File(download, outDir.getName() + ".zip");

        writeJsonReport(items, jsonReport);
        writeCsvReport(items, csvReport);
        writeHtmlReport(items, htmlReport);
        zipDir(outDir, zipReport);

        String summary =
                "Dataset Benchmark Pack готов\n" +
                "Dataset: " + (knownDataset.exists() ? knownDataset.getAbsolutePath() : download.getAbsolutePath()) + "\n" +
                "Найдено: " + items.size() + " image/json items\n" +
                "CSV: " + csvReport.getAbsolutePath() + "\n" +
                "JSON: " + jsonReport.getAbsolutePath() + "\n" +
                "HTML: " + htmlReport.getAbsolutePath() + "\n" +
                "ZIP: " + zipReport.getAbsolutePath();

        return new Report(items, jsonReport, csvReport, htmlReport, zipReport, summary);
    }

    private static ArrayList<Item> findDatasetItems(File root) {
        ArrayList<Item> out = new ArrayList<>();
        scan(root, out, 0);
        return out;
    }

    private static void scan(File dir, ArrayList<Item> out, int depth) {
        if (dir == null || !dir.exists() || depth > 10 || out.size() >= 2000) return;

        String low = dir.getAbsolutePath().toLowerCase(Locale.US);
        if (low.contains("/android/") || low.contains("/.gradle/") || low.contains("/build/")) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scan(f, out, depth + 1);
            } else if (isImage(f)) {
                File json = findJsonForImage(f);
                String expected = "unknown";
                String description = "";

                if (json != null && json.exists()) {
                    try {
                        String text = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
                        JSONObject o = new JSONObject(text);
                        expected = firstString(o, "true_class", "expected_genre", "genre", "type", "class", "category", "label", "expected", "main_type");
                        description = firstString(o, "notes", "description", "prompt", "caption", "text", "visual_evidence", "must_detect", "objects");
                    } catch (Throwable ignored) {}
                }

                out.add(new Item(f, json, expected, description));
            }
        }
    }

    private static File findJsonForImage(File image) {
        String name = image.getName();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;

        File dir = image.getParentFile();
        if (dir == null) return null;

        String[] variants = {
                base + ".json",
                base + ".meta.json",
                base + "_meta.json"
        };

        for (String v : variants) {
            File f = new File(dir, v);
            if (f.exists()) return f;
        }

        File[] jsons = dir.listFiles(new FileFilter() {
            @Override public boolean accept(File f) {
                return f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".json");
            }
        });

        if (jsons != null && jsons.length == 1) return jsons[0];
        return null;
    }

    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    private static String firstString(JSONObject o, String... keys) {
        for (String k : keys) {
            if (!o.has(k)) continue;
            Object v = o.opt(k);
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (s.length() > 0 && !"null".equalsIgnoreCase(s)) return s;
        }
        return "unknown";
    }

    private static void writeJsonReport(ArrayList<Item> items, File out) throws Exception {
        BufferedWriter w = new BufferedWriter(new FileWriter(out));
        w.write("{\n");
        w.write("  \"status\": \"dataset_manifest_v1\",\n");
        w.write("  \"count\": " + items.size() + ",\n");
        w.write("  \"items\": [\n");

        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            w.write("    {\n");
            w.write("      \"index\": " + (i + 1) + ",\n");
            w.write("      \"image\": " + q(it.image.getAbsolutePath()) + ",\n");
            w.write("      \"json\": " + q(it.json == null ? "" : it.json.getAbsolutePath()) + ",\n");
            w.write("      \"expected\": " + q(it.expected) + ",\n");
            w.write("      \"description\": " + q(cut(it.description, 700)) + "\n");
            w.write("    }" + (i + 1 == items.size() ? "\n" : ",\n"));
        }

        w.write("  ]\n");
        w.write("}\n");
        w.close();
    }

    private static void writeCsvReport(ArrayList<Item> items, File out) throws Exception {
        BufferedWriter w = new BufferedWriter(new FileWriter(out));
        w.write("index,expected,image,json,description\n");

        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            w.write(String.valueOf(i + 1));
            w.write(",");
            w.write(csv(it.expected));
            w.write(",");
            w.write(csv(it.image.getAbsolutePath()));
            w.write(",");
            w.write(csv(it.json == null ? "" : it.json.getAbsolutePath()));
            w.write(",");
            w.write(csv(cut(it.description, 500)));
            w.write("\n");
        }

        w.close();
    }

    private static void writeHtmlReport(ArrayList<Item> items, File out) throws Exception {
        BufferedWriter w = new BufferedWriter(new FileWriter(out));
        w.write("<!doctype html><html><head><meta charset='utf-8'>");
        w.write("<meta name='viewport' content='width=device-width,initial-scale=1'>");
        w.write("<title>SolumDraw Dataset Benchmark</title>");
        w.write("<style>");
        w.write("body{background:#071018;color:#dff;font-family:Arial,sans-serif;margin:18px}");
        w.write("h1{color:#22e6f2;letter-spacing:4px}.box{border:1px solid #137987;border-radius:12px;padding:12px;margin:10px 0;background:#0b1822}");
        w.write("table{border-collapse:collapse;width:100%;font-size:12px}td,th{border:1px solid #164955;padding:6px;vertical-align:top}");
        w.write("th{color:#22e6f2;background:#10222d}.small{font-size:12px;color:#9cc;word-break:break-all}.tag{color:#ffd166}");
        w.write("</style></head><body>");
        w.write("<h1>SOLUM DRAW · DATASET BENCHMARK</h1>");
        w.write("<div class='box'>Найдено <b>" + items.size() + "</b> image/json items. HTML без больших preview, чтобы не раздувать отчёт.</div>");
        w.write("<table><tr><th>#</th><th>expected</th><th>image</th><th>json</th><th>description</th></tr>");

        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            w.write("<tr>");
            w.write("<td>" + (i + 1) + "</td>");
            w.write("<td class='tag'>" + esc(it.expected) + "</td>");
            w.write("<td class='small'>" + esc(it.image.getAbsolutePath()) + "</td>");
            w.write("<td class='small'>" + esc(it.json == null ? "" : it.json.getAbsolutePath()) + "</td>");
            w.write("<td class='small'>" + esc(cut(it.description, 260)) + "</td>");
            w.write("</tr>");
        }

        w.write("</table></body></html>");
        w.close();
    }

    private static void zipDir(File dir, File zip) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));
        File[] files = dir.listFiles();
        if (files != null) {
            byte[] buf = new byte[8192];
            for (File f : files) {
                if (!f.isFile()) continue;
                zos.putNextEntry(new ZipEntry(f.getName()));
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                in.close();
                zos.closeEntry();
            }
        }
        zos.close();
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static String q(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private static String csv(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\"";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
