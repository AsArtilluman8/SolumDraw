package com.solum.draw.analyze;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        Map<String, String> expected = readExpected(new File(inputDir, "expected.json"));
        List<BenchImage> images = new ArrayList<>();
        collectFileImages(inputDir, images);
        int fileScanCount = images.size();
        if (images.isEmpty()) {
            collectMediaStoreImages(context, images);
        }

        List<ItemResult> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int expectedCount = 0;
        int correct = 0;
        int index = 1;
        for (BenchImage image : images) {
            try {
                Bitmap bitmap = image.decode(context.getContentResolver());
                if (bitmap == null) throw new IllegalArgumentException("decode returned null");
                ImageAnalysis analysis = ImageAnalyzer.analyze(bitmap, image.relativeName);
                String exp = expected.get(image.relativeName);
                boolean hasExpected = exp != null && exp.length() > 0;
                boolean ok = hasExpected && exp.equals(analysis.genre);
                if (hasExpected) expectedCount++;
                if (ok) correct++;
                items.add(new ItemResult(image.relativeName, exp == null ? "" : exp, analysis, ok));
                writeText(new File(reportsDir, String.format(Locale.US, "%03d_%s.json", index, safeName(image.relativeName))), analysis.toJson());
                index++;
            } catch (Exception e) {
                errors.add(image.relativeName + ": " + e.getMessage());
            }
        }

        String summary = summaryJson(items, errors, inputDir, expectedCount, correct, fileScanCount, images.size());
        writeText(new File(outDir, "summary.json"), summary);
        writeText(new File(outDir, "summary.txt"), summaryText(items, errors, inputDir, expectedCount, correct, fileScanCount, images.size()));
        File zip = new File(downloads, outDir.getName() + ".zip");
        zipDir(outDir, zip);
        return new Result(inputDir.getAbsolutePath(), zip.getAbsolutePath(), items.size(), errors.size());
    }

    private static void collectFileImages(File root, List<BenchImage> out) {
        File[] kids = root.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) collectFileImages(f, out);
            else if (isImageName(f.getName())) out.add(BenchImage.fromFile(root, f));
        }
    }

    private static void collectMediaStoreImages(Context context, List<BenchImage> out) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[] {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
        };
        String selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = new String[] { "%Download/" + INPUT_DIR + "%" };
        Cursor cursor = null;
        try {
            cursor = resolver.query(collection, projection, selection, selectionArgs, MediaStore.Images.Media.DATE_ADDED + " ASC");
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int relCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                String relPath = cursor.getString(relCol);
                if (!isImageName(name)) continue;
                String relativeName = mediaRelativeName(relPath, name);
                Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                out.add(BenchImage.fromUri(relativeName, uri));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static String mediaRelativeName(String relativePath, String name) {
        String p = relativePath == null ? "" : relativePath.replace('\\', '/');
        int idx = p.indexOf("Download/" + INPUT_DIR + "/");
        if (idx >= 0) {
            String sub = p.substring(idx + ("Download/" + INPUT_DIR + "/").length());
            return sub + name;
        }
        idx = p.indexOf(INPUT_DIR + "/");
        if (idx >= 0) {
            String sub = p.substring(idx + (INPUT_DIR + "/").length());
            return sub + name;
        }
        return name;
    }

    private static Map<String, String> readExpected(File expectedFile) {
        Map<String, String> out = new HashMap<>();
        if (!expectedFile.exists()) return out;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(expectedFile), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.contains(":")) continue;
                String[] parts = line.split(":", 2);
                String k = cleanJsonString(parts[0]);
                String v = cleanJsonString(parts[1]);
                if (k.length() > 0 && v.length() > 0) out.put(k, v);
            }
            r.close();
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String cleanJsonString(String s) {
        s = s.trim();
        if (s.endsWith(",")) s = s.substring(0, s.length() - 1).trim();
        if (s.startsWith("\"")) s = s.substring(1);
        if (s.endsWith("\"")) s = s.substring(0, s.length() - 1);
        return s.replace("\\/", "/").trim();
    }

    private static String summaryJson(List<ItemResult> items, List<String> errors, File inputDir, int expectedCount, int correct, int fileScanCount, int finalScanCount) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"inputDir\": \"").append(inputDir.getAbsolutePath()).append("\",\n");
        b.append("  \"fileScanCount\": ").append(fileScanCount).append(",\n");
        b.append("  \"finalScanCount\": ").append(finalScanCount).append(",\n");
        b.append("  \"count\": ").append(items.size()).append(",\n");
        b.append("  \"errors\": ").append(errors.size()).append(",\n");
        b.append("  \"expectedCount\": ").append(expectedCount).append(",\n");
        b.append("  \"correct\": ").append(correct).append(",\n");
        b.append("  \"accuracy\": ").append(expectedCount == 0 ? "0" : String.format(Locale.US, "%.4f", correct / (float) expectedCount)).append(",\n");
        b.append("  \"items\": [\n");
        for (int i = 0; i < items.size(); i++) {
            ItemResult item = items.get(i);
            ImageAnalysis a = item.analysis;
            if (i > 0) b.append(",\n");
            b.append("    {\"name\":\"").append(item.name).append("\",\"expected\":\"").append(item.expected)
                    .append("\",\"predicted\":\"").append(a.genre)
                    .append("\",\"ok\":").append(item.ok)
                    .append(",\"confidence\":").append(String.format(Locale.US, "%.4f", a.confidence))
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

    private static String summaryText(List<ItemResult> items, List<String> errors, File inputDir, int expectedCount, int correct, int fileScanCount, int finalScanCount) {
        StringBuilder b = new StringBuilder();
        b.append("SolumDraw Analyzer Benchmark\n");
        b.append("Input: ").append(inputDir.getAbsolutePath()).append("\n");
        b.append("File scan: ").append(fileScanCount).append(" Final scan: ").append(finalScanCount).append("\n");
        b.append("Images: ").append(items.size()).append(" Errors: ").append(errors.size()).append("\n");
        b.append("Expected: ").append(expectedCount).append(" Correct: ").append(correct).append(" Accuracy: ")
                .append(expectedCount == 0 ? "n/a" : Math.round(100f * correct / expectedCount) + "%").append("\n\n");
        for (ItemResult item : items) {
            b.append(item.ok ? "OK " : "BAD").append(" | ").append(item.name)
                    .append(" | expected=").append(item.expected)
                    .append(" predicted=").append(item.analysis.shortSummary())
                    .append(" | ").append(item.analysis.strategy).append("\n");
        }
        if (!errors.isEmpty()) {
            b.append("\nErrors:\n");
            for (String e : errors) b.append("- ").append(e).append("\n");
        }
        return b.toString();
    }

    private static boolean isImageName(String name) {
        String n = name.toLowerCase(Locale.US);
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

    private static final class BenchImage {
        final String relativeName;
        final File file;
        final Uri uri;

        private BenchImage(String relativeName, File file, Uri uri) {
            this.relativeName = relativeName;
            this.file = file;
            this.uri = uri;
        }

        static BenchImage fromFile(File root, File file) {
            String r = file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1).replace(File.separatorChar, '/');
            return new BenchImage(r, file, null);
        }

        static BenchImage fromUri(String relativeName, Uri uri) {
            return new BenchImage(relativeName, null, uri);
        }

        Bitmap decode(ContentResolver resolver) throws Exception {
            if (file != null) return BitmapFactory.decodeStream(new FileInputStream(file));
            InputStream in = resolver.openInputStream(uri);
            try {
                return BitmapFactory.decodeStream(in);
            } finally {
                if (in != null) in.close();
            }
        }
    }

    private static final class ItemResult {
        final String name;
        final String expected;
        final ImageAnalysis analysis;
        final boolean ok;
        ItemResult(String name, String expected, ImageAnalysis analysis, boolean ok) {
            this.name = name;
            this.expected = expected;
            this.analysis = analysis;
            this.ok = ok;
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
