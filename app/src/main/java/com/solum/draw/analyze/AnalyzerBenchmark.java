package com.solum.draw.analyze;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

public final class AnalyzerBenchmark {
    public static final String INPUT_DIR = "SolumDrawTestImages";

    private AnalyzerBenchmark() {}

    public static Result run(Context context) throws Exception {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File inputDir = new File(downloads, INPUT_DIR);
        if (!inputDir.exists()) inputDir.mkdirs();

        File outDir = new File(downloads, "SolumDrawAnalyzerReport_" + timestamp());
        outDir.mkdirs();
        File reportsDir = new File(outDir, "reports");
        reportsDir.mkdirs();

        File[] files = inputDir.listFiles();
        List<ImageAnalysis> analyses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (files != null) {
            int index = 1;
            for (File file : files) {
                if (!isImage(file)) continue;
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
                    if (bitmap == null) throw new IllegalArgumentException("decode returned null");
                    ImageAnalysis analysis = ImageAnalyzer.analyze(bitmap, file.getName());
                    analyses.add(analysis);
                    writeText(new File(reportsDir, String.format(Locale.US, "%03d_%s.json", index, safeName(file.getName()))), analysis.toJson());
                    index++;
                } catch (Exception e) {
                    errors.add(file.getName() + ": " + e.getMessage());
                }
            }
        }

        String summary = summaryJson(analyses, errors, inputDir);
        writeText(new File(outDir, "summary.json"), summary);
        writeText(new File(outDir, "summary.txt"), summaryText(analyses, errors, inputDir));
        File zip = new File(downloads, outDir.getName() + ".zip");
        zipDir(outDir, zip);
        return new Result(inputDir.getAbsolutePath(), zip.getAbsolutePath(), analyses.size(), errors.size());
    }

    private static String summaryJson(List<ImageAnalysis> analyses, List<String> errors, File inputDir) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"inputDir\": \"").append(inputDir.getAbsolutePath()).append("\",\n");
        b.append("  \"count\": ").append(analyses.size()).append(",\n");
        b.append("  \"errors\": ").append(errors.size()).append(",\n");
        b.append("  \"items\": [\n");
        for (int i = 0; i < analyses.size(); i++) {
            ImageAnalysis a = analyses.get(i);
            if (i > 0) b.append(",\n");
            b.append("    {\"name\":\"").append(a.name).append("\",\"genre\":\"").append(a.genre)
                    .append("\",\"confidence\":").append(String.format(Locale.US, "%.4f", a.confidence))
                    .append(",\"strategy\":\"").append(a.strategy).append("\"}");
        }
        b.append("\n  ],\n  \"errorList\": [");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) b.append(",");
            b.append("\"").append(errors.get(i).replace("\"", "'")).append("\"");
        }
        b.append("]\n}");
        return b.toString();
    }

    private static String summaryText(List<ImageAnalysis> analyses, List<String> errors, File inputDir) {
        StringBuilder b = new StringBuilder();
        b.append("SolumDraw Analyzer Benchmark\n");
        b.append("Input: ").append(inputDir.getAbsolutePath()).append("\n");
        b.append("Images: ").append(analyses.size()).append(" Errors: ").append(errors.size()).append("\n\n");
        for (ImageAnalysis a : analyses) {
            b.append(a.name).append(" | ").append(a.shortSummary()).append(" | ").append(a.strategy).append("\n");
        }
        if (!errors.isEmpty()) {
            b.append("\nErrors:\n");
            for (String e : errors) b.append("- ").append(e).append("\n");
        }
        return b.toString();
    }

    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".bmp");
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static void writeText(File file, String text) throws Exception {
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        w.write(text);
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

    public static final class Result {
        public final String inputDir;
        public final String zipPath;
        public final int images;
        public final int errors;

        public Result(String inputDir, String zipPath, int images, int errors) {
            this.inputDir = inputDir;
            this.zipPath = zipPath;
            this.images = images;
            this.errors = errors;
        }
    }
}
