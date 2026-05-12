package com.solum.draw.analyze;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import com.solum.draw.vision.MlKitVisionProbe;
import com.solum.draw.vision.VisionDecisionEngine;
import com.solum.draw.vision.VisionDecisionPostProcessor;
import com.solum.draw.vision.VisionResult;
import com.solum.draw.vision.profile.DatasetClasses;
import com.solum.draw.vision.profile.AxisScorer;
import com.solum.draw.vision.profile.ImageAxes;
import com.solum.draw.vision.profile.ImageProfile;
import com.solum.draw.vision.profile.ShadowClassRouter;
import com.solum.draw.vision.profile.VisualFeatureExtractor;
import com.solum.draw.vision.profile.VisualFeatureVector;
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
    
    private static final int QUICK_BENCH_LIMIT = 30;
    private static int benchLimitForNextRun = 0;

public static final String INPUT_DIR = "SolumDrawTestImages";
    public static final String DATASET_DIR = "SolumDrawDataset_v1/SolumDrawDataset_v1";

    private AnalyzerBenchmark() {}

    public interface Progress {
        void onStart(String datasetPath, int total, int labelsFound);
        void onItem(int index, int total, String name, int top1, int top3, int missingLabels);
    }

    public static Result run(Context context) throws Exception { return run(context, null); }

    public static Result runQuick(Context context) throws Exception { return runQuick(context, null); }

    public static Result runQuick(Context context, Progress progress) throws Exception {
        int old = benchLimitForNextRun;
        benchLimitForNextRun = QUICK_BENCH_LIMIT;
        try {
            return run(context, progress);
        } finally {
            benchLimitForNextRun = old;
        }
    }

    public static Result run(Context context, Progress progress) throws Exception {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dataset = pickDataset(downloads);
        File outDir = new File(downloads, "SolumDrawAnalyzerReport_" + timestamp());
        outDir.mkdirs();

        List<BenchImage> images = new ArrayList<>();
        collectImages(dataset, dataset, images);
        Collections.sort(images, new Comparator<BenchImage>() { @Override public int compare(BenchImage a, BenchImage b) { return a.relativeName.compareTo(b.relativeName); } });
        if (benchLimitForNextRun > 0 && images.size() > benchLimitForNextRun) {
            images = new ArrayList<BenchImage>(images.subList(0, benchLimitForNextRun));
        }

        int labelsFound = 0;
        for (BenchImage img : images) if (img.expected.length() > 0) labelsFound++;
        if (progress != null) progress.onStart(dataset.getAbsolutePath(), images.size(), labelsFound);

        List<ItemResult> items = new ArrayList<>();
        List<ProfileRow> profileRows = new ArrayList<>();
        List<AxisRow> axisRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int top1 = 0, top3 = 0, missingLabels = 0;

        for (int i = 0; i < images.size(); i++) {
            BenchImage img = images.get(i);
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(img.file));
                if (bitmap == null) throw new IllegalArgumentException("decode returned null");
                VisualFeatureVector features = VisualFeatureExtractor.extract(bitmap);
                ImageAnalysis analysis = ImageAnalyzer.analyze(bitmap, img.relativeName);
                String oldPredicted = SceneArtHeuristic.correctedGenre(analysis);
                String note = SceneArtHeuristic.note(analysis);

                VisionResult ml = MlKitVisionProbe.analyzeBlocking(bitmap, 9000L);
                String hint = analysis.genre + " " + analysis.warnings + " " + note + " old=" + oldPredicted;
                VisionDecisionEngine.Decision decision = VisionDecisionEngine.analyze(ml.labels, ml.objects, hint, "");
                decision = VisionDecisionPostProcessor.refine(decision, ml.labels, ml.objects, hint);

                String predicted = decision.datasetClass;
                List<String> candidates = sanitizeTop3(top3FromDecision(decision, analysis, predicted), predicted);

                boolean hasExpected = img.expected.length() > 0;
                boolean ok1 = hasExpected && img.expected.equals(predicted);
                boolean ok3 = hasExpected && contains(candidates, img.expected);
                if (!hasExpected) missingLabels++;
                if (ok1) top1++;
                if (ok3) top3++;
                items.add(new ItemResult(img.relativeName, img.expected, img.secondary, analysis, oldPredicted, predicted, decision.topLine, decision.reason, ml.provider, note, candidates, ok1, ok3, hasExpected));
                profileRows.add(new ProfileRow(img.relativeName, img.expected, DatasetClasses.axisOf(img.expected), predicted, DatasetClasses.axisOf(predicted), features, analysis, candidates, ok1, ok3, hasExpected));
                ImageProfile axisProfile = new ImageProfile();
                axisProfile.imagePath = img.relativeName;
                axisProfile.features = features;
                axisProfile.rawPredicted = predicted;
                AxisScorer.score(axisProfile, ml.labels, ml.objects, hint);
                ShadowClassRouter.route(axisProfile, predicted, candidates);
                axisRows.add(new AxisRow(img.relativeName, img.expected, predicted, candidates, axisProfile));
            } catch (Exception e) {
                errors.add(img.relativeName + "\t" + e.getClass().getSimpleName() + "\t" + String.valueOf(e.getMessage()));
            }
            if (progress != null) progress.onItem(i + 1, images.size(), img.relativeName, top1, top3, missingLabels);
        }

        touchImageProfileFoundation();
        Stats stats = buildStats(items);
        writeText(new File(outDir, "benchmark_results.csv"), resultsCsv(items));
        writeText(new File(outDir, "profile_features.csv"), profileFeaturesCsv(profileRows));
        writeText(new File(outDir, "profile_predictions.jsonl"), profilePredictionsJsonl(profileRows));
        writeText(new File(outDir, "feature_summary.json"), featureSummaryJson(profileRows));
        writeText(new File(outDir, "profile_axes.csv"), profileAxesCsv(axisRows));
        writeText(new File(outDir, "axis_distribution.md"), axisDistributionMd(axisRows));
        writeText(new File(outDir, "old_vs_shadow.csv"), oldVsShadowCsv(axisRows));
        writeText(new File(outDir, "shadow_router_report.md"), shadowRouterReportMd(axisRows));
        writeText(new File(outDir, "mistakes.csv"), mistakesCsv(items));
        writeText(new File(outDir, "predictions.jsonl"), predictionsJsonl(items));
        writeText(new File(outDir, "benchmark_summary.json"), summaryJson(dataset, images.size(), labelsFound, top1, top3, missingLabels, errors, stats));
        writeText(new File(outDir, "BENCHMARK_REPORT.md"), reportMd(dataset, images.size(), labelsFound, top1, top3, missingLabels, errors, stats));
        writeText(new File(outDir, "summary.txt"), reportMd(dataset, images.size(), labelsFound, top1, top3, missingLabels, errors, stats));
        writeText(new File(outDir, "errors.txt"), errorsText(errors));
        writeText(new File(outDir, "benchmark_guards.md"), benchmarkGuardsMd(items, errors));
        writeText(new File(outDir, "axis_report.md"), axisReportMd(items));
        writeText(new File(outDir, "axis_report.csv"), axisReportCsv(items));
        writeText(new File(outDir, "class_report.csv"), classReportCsv(items));
        writeText(new File(outDir, "fix_suggestions.md"), fixSuggestionsMd(items, errors));
        writeText(new File(outDir, "calibration_hints.md"), calibrationHintsMd(items));
        writeText(new File(outDir, "calibration_rules.json"), calibrationRulesJson(items));

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

    private static List<String> top3Candidates(ImageAnalysis a, String predicted) {
        ArrayList<String> c = new ArrayList<>();
        add(c, predicted);
        String g = predicted;
        if (SceneArtHeuristic.likelyFalseUi(a)) { add(c, "landscape_environment"); add(c, "digital_painting_concept"); add(c, "photo_general"); }
        else if (g.contains("ui")) { add(c, "ui_screenshot"); add(c, "game_ui_hud"); add(c, "diagram_chart"); }
        else if (g.contains("anime")) { add(c, "anime_manga"); add(c, "cartoon_comic"); add(c, "lineart_sketch"); }
        else if (g.contains("portrait") || a.skinRatio > 0.08f) { add(c, "portrait_character"); add(c, "human_body_fullbody"); add(c, "photo_general"); }
        else if (g.contains("logo")) { add(c, "logo_icon"); add(c, "vector_flat"); add(c, "transparent_layered"); }
        else if (g.contains("sketch")) { add(c, "lineart_sketch"); add(c, "pencil_drawing"); add(c, "ink_wash"); }
        else if (g.contains("vector")) { add(c, "vector_flat"); add(c, "isometric_art"); add(c, "cartoon_comic"); }
        else if (g.contains("landscape") || g.contains("painting") || g.contains("photo")) { add(c, "digital_painting_concept"); add(c, "landscape_environment"); add(c, "photo_general"); }
        else { add(c, "photo_general"); add(c, "digital_painting_concept"); add(c, "texture_pattern"); }
        while (c.size() > 3) c.remove(c.size() - 1);
        return c;
    }


    private static List<String> top3FromDecision(VisionDecisionEngine.Decision d, ImageAnalysis a, String predicted) {
        ArrayList<String> c = new ArrayList<>();
        if (d != null && d.topLine != null && d.topLine.length() > 0) {
            String[] parts = d.topLine.split("\\|");
            for (String part : parts) {
                String name = part.trim();
                int sp = name.indexOf(' ');
                if (sp > 0) name = name.substring(0, sp).trim();
                add(c, name);
                if (c.size() >= 3) break;
            }
        }
        add(c, predicted);
        if (c.size() < 3) {
            for (String x : top3Candidates(a, predicted)) add(c, x);
        }
        while (c.size() > 3) c.remove(c.size() - 1);
        return c;
    }

    private static void add(List<String> list, String v) { if (v != null && v.length() > 0 && !list.contains(v)) list.add(v); }

    private static List<String> sanitizeTop3(List<String> raw, String predicted) {
        ArrayList<String> out = new ArrayList<>();

        addValid(out, predicted);

        if (raw != null) {
            for (String x : raw) {
                addValid(out, x);
            }
        }

        // Axis-aware safe fillers. These are real dataset classes only.
        if (predicted != null) {
            String axis = DatasetClasses.axisOf(predicted);
            for (String cls : DatasetClasses.ALL) {
                if (out.size() >= 3) break;
                if (axis.equals(DatasetClasses.axisOf(cls))) addValid(out, cls);
            }
        }

        for (String cls : DatasetClasses.ALL) {
            if (out.size() >= 3) break;
            addValid(out, cls);
        }

        while (out.size() > 3) out.remove(out.size() - 1);
        return out;
    }

    private static void addValid(List<String> out, String cls) {
        if (cls == null) return;
        cls = cls.trim();
        if (DatasetClasses.isForbidden(cls)) return;
        if (!DatasetClasses.isValid(cls)) return;
        if (!out.contains(cls)) out.add(cls);
    }

    private static boolean contains(List<String> list, String v) { for (String s : list) if (s.equals(v)) return true; return false; }

    private static Stats buildStats(List<ItemResult> items) {
        Stats s = new Stats();
        for (ItemResult it : items) {
            inc(s.predicted, it.predicted);
            if (it.hasExpected) {
                inc(s.expected, it.expected);
                inc(s.confusion, it.expected + " -> " + it.predicted);
                if (!it.ok1) s.wrong++;
            } else s.missing++;
        }
        return s;
    }

    private static String resultsCsv(List<ItemResult> items) {
        StringBuilder b = new StringBuilder("file,true_class,secondary_classes,old_predicted,predicted,router_top,ml_provider,top3,top1_ok,top3_ok,confidence,note,router_reason\n");
        for (ItemResult it : items) b.append(csv(it.name)).append(',').append(csv(it.expected)).append(',').append(csv(it.secondary)).append(',').append(csv(it.analysis.genre)).append(',').append(csv(it.predicted)).append(',').append(csv(join(it.top3))).append(',').append(it.ok1).append(',').append(it.ok3).append(',').append(num(it.analysis.confidence)).append(',').append(csv(it.note)).append('\n');
        return b.toString();
    }

    private static String mistakesCsv(List<ItemResult> items) {
        StringBuilder b = new StringBuilder("file,true_class,old_predicted,predicted,router_top,top3,confidence,note,warnings,router_reason\n");
        for (ItemResult it : items) if (it.hasExpected && !it.ok1) b.append(csv(it.name)).append(',').append(csv(it.expected)).append(',').append(csv(it.analysis.genre)).append(',').append(csv(it.predicted)).append(',').append(csv(join(it.top3))).append(',').append(num(it.analysis.confidence)).append(',').append(csv(it.note)).append(',').append(csv(it.analysis.warnings)).append('\n');
        return b.toString();
    }

    private static String predictionsJsonl(List<ItemResult> items) {
        StringBuilder b = new StringBuilder();
        for (ItemResult it : items) b.append("{\"file\":\"").append(esc(it.name)).append("\",\"true_class\":\"").append(esc(it.expected)).append("\",\"raw_predicted\":\"").append(esc(it.analysis.genre)).append("\",\"predicted\":\"").append(esc(it.predicted)).append("\",\"top3\":\"").append(esc(join(it.top3))).append("\",\"top1_ok\":").append(it.ok1).append(",\"top3_ok\":").append(it.ok3).append("}\n");
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
        b.append("  \"top1_accuracy\": ").append(num(top1 / (float)Math.max(1, labels))).append(",\n");
        b.append("  \"top3_accuracy\": ").append(num(top3 / (float)Math.max(1, labels))).append(",\n");
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
        b.append("## Смысл\n\n0% плохо, 100% идеально. Patch 27G: Bench теперь использует ML Kit + общий VisionDecisionEngine. old_predicted оставлен только для сравнения.\n\n");
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

    private static String axisReportMd(List<ItemResult> items) {
        AxisStats st = buildAxisStats(items);
        StringBuilder b = new StringBuilder();
        b.append("# SolumDraw Multi-axis Benchmark\n\n");
        b.append("Strict top1 = папка dataset точно совпала с predicted.\n");
        b.append("Axis top1 = совпал тип задачи: style/content/ui/architecture/scene/etc.\n\n");
        b.append("- Items: ").append(st.total).append("\n");
        b.append("- Strict top1: ").append(st.strictTop1).append(" / ").append(st.total).append(" = ").append(pctText(st.strictTop1, st.total)).append("\n");
        b.append("- Axis top1: ").append(st.axisTop1).append(" / ").append(st.total).append(" = ").append(pctText(st.axisTop1, st.total)).append("\n");
        b.append("- Off-axis: ").append(st.offAxis).append("\n\n");

        b.append("## By true axis\n\n");
        for (Map.Entry<String,Integer> e : sorted(st.axisTotal)) {
            String axis = e.getKey();
            int total = e.getValue();
            int ok = get(st.axisCorrect, axis);
            b.append("- ").append(axis).append(": ").append(ok).append("/").append(total)
                    .append(" = ").append(pctText(ok, total)).append("\n");
        }

        b.append("\n## Frequent axis confusions\n\n");
        for (Map.Entry<String,Integer> e : sorted(st.axisConfusions)) {
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }

        b.append("\n## Meaning\n\n");
        b.append("Если strict низкий, но axis выше — анализатор часто понимает общий тип картинки, но не угадывает точную папку dataset.\n");
        b.append("Если и axis низкий — проблема в правилах/ML/fallback для этой группы.\n");
        return b.toString();
    }

    private static String axisReportCsv(List<ItemResult> items) {
        StringBuilder b = new StringBuilder();
        b.append("file,true_class,true_axis,predicted,predicted_axis,strict_ok,axis_ok,top3,raw_predicted,confidence,note\n");
        for (ItemResult it : items) {
            String trueAxis = axisOf(it.expected);
            String predAxis = axisOf(it.predicted);
            boolean strict = it.hasExpected && it.expected.equals(it.predicted);
            boolean axisOk = it.hasExpected && trueAxis.equals(predAxis);
            b.append(csv(it.name)).append(',')
                    .append(csv(it.expected)).append(',')
                    .append(csv(trueAxis)).append(',')
                    .append(csv(it.predicted)).append(',')
                    .append(csv(predAxis)).append(',')
                    .append(strict).append(',')
                    .append(axisOk).append(',')
                    .append(csv(join(it.top3))).append(',')
                    .append(csv(it.analysis.genre)).append(',')
                    .append(num(it.analysis.confidence)).append(',')
                    .append(csv(it.note)).append('\n');
        }
        return b.toString();
    }

    private static AxisStats buildAxisStats(List<ItemResult> items) {
        AxisStats st = new AxisStats();
        st.total = items.size();
        for (ItemResult it : items) {
            if (!it.hasExpected) continue;
            String trueAxis = axisOf(it.expected);
            String predAxis = axisOf(it.predicted);
            inc(st.axisTotal, trueAxis);

            boolean strict = it.expected.equals(it.predicted);
            boolean axisOk = trueAxis.equals(predAxis);

            if (strict) st.strictTop1++;
            if (axisOk) {
                st.axisTop1++;
                inc(st.axisCorrect, trueAxis);
            } else {
                st.offAxis++;
                inc(st.axisConfusions, trueAxis + " -> " + predAxis);
            }
        }
        return st;
    }

    private static String axisOf(String cls) {
        if (cls == null) return "unknown";
        String c = cls.toLowerCase(Locale.US);

        if (c.contains("watercolor") || c.contains("oil_painting") || c.contains("ink_wash") ||
                c.contains("grayscale_ink") || c.contains("low_poly") || c.contains("pixel_art") ||
                c.contains("retro_halftone") || c.contains("abstract_art") || c.contains("digital_painting") ||
                c.contains("cartoon") || c.contains("anime") || c.contains("lineart") || c.contains("vector_flat")) {
            return "style_art";
        }

        if (c.contains("portrait") || c.contains("character") || c.contains("human_body") ||
                c.contains("fashion") || c.contains("clothing")) {
            return "character_body";
        }

        if (c.contains("animal") || c.contains("creature")) {
            return "animal_creature";
        }

        if (c.contains("architecture") || c.contains("building") || c.contains("hardsurface") ||
                c.contains("isometric")) {
            return "architecture_object";
        }

        if (c.contains("landscape") || c.contains("environment") || c.contains("space_scifi") ||
                c.contains("plant") || c.contains("flower")) {
            return "scene_environment";
        }

        if (c.contains("ui") || c.contains("hud") || c.contains("screenshot") ||
                c.contains("diagram") || c.contains("chart") || c.contains("logo") ||
                c.contains("icon") || c.contains("text_document")) {
            return "ui_document";
        }

        if (c.contains("pattern") || c.contains("texture") || c.contains("seamless") ||
                c.contains("transparent_layered")) {
            return "texture_pattern";
        }

        if (c.contains("vfx") || c.contains("glow") || c.contains("magic")) {
            return "vfx_fx";
        }

        if (c.contains("vehicle")) {
            return "vehicle";
        }

        if (c.contains("noisy") || c.contains("compressed")) {
            return "quality_noise";
        }

        if (c.contains("product") || c.contains("object")) {
            return "generic_object";
        }

        return "unknown";
    }
private static int get(Map<String,Integer> m, String k) {
        Integer v = m.get(k);
        return v == null ? 0 : v;
    }

    private static String pctText(int a, int b) {
        if (b <= 0) return "0%";
        return Math.round((a * 1000f) / b) / 10f + "%";
    }

    private static List<Map.Entry<String,Integer>> sorted(Map<String,Integer> m) {
        List<Map.Entry<String,Integer>> e = new ArrayList<>(m.entrySet());
        Collections.sort(e, new Comparator<Map.Entry<String,Integer>>() {
            @Override public int compare(Map.Entry<String,Integer> a, Map.Entry<String,Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        return e;
    }

    private static final class AxisStats {
        int total, strictTop1, axisTop1, offAxis;
        final Map<String,Integer> axisTotal = new HashMap<>();
        final Map<String,Integer> axisCorrect = new HashMap<>();
        final Map<String,Integer> axisConfusions = new HashMap<>();
    }



    private static String calibrationHintsMd(List<ItemResult> items) {
        Map<String,ClassStats> byClass = new HashMap<>();
        Map<String,Integer> exactConfusions = new HashMap<>();
        Map<String,Integer> axisConfusions = new HashMap<>();

        for (ItemResult it : items) {
            if (!it.hasExpected) continue;

            ClassStats st = byClass.get(it.expected);
            if (st == null) {
                st = new ClassStats();
                st.name = it.expected;
                st.axis = axisOf(it.expected);
                byClass.put(it.expected, st);
            }

            st.total++;
            if (it.expected.equals(it.predicted)) st.strictOk++;
            if (axisOf(it.expected).equals(axisOf(it.predicted))) st.axisOk++;
            inc(st.predictedTo, it.predicted);

            if (!it.expected.equals(it.predicted)) {
                inc(exactConfusions, it.expected + " -> " + it.predicted);
            }
            if (!axisOf(it.expected).equals(axisOf(it.predicted))) {
                inc(axisConfusions, axisOf(it.expected) + " -> " + axisOf(it.predicted));
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Calibration Hints\n\n");
        b.append("Это не финальный ML. Это карта, какие правила чинить первыми.\n\n");

        b.append("## Priority 1: strict низкий, axis высокий\n\n");
        b.append("Значит общий тип понятен, но точный жанр/папка выбран неверно.\n\n");
        int n = 0;
        for (Map.Entry<String,ClassStats> e : sortedClassStats(byClass)) {
            ClassStats st = e.getValue();
            if (st.total <= 0) continue;
            float strict = st.strictOk / (float) st.total;
            float axis = st.axisOk / (float) st.total;
            if (strict < 0.35f && axis >= 0.60f) {
                b.append("- ").append(st.name)
                        .append(": strict ").append(pctText(st.strictOk, st.total))
                        .append(", axis ").append(pctText(st.axisOk, st.total))
                        .append(", mostly predicted as: ").append(topMapInline(st.predictedTo, 5))
                        .append("\n");
                n++;
            }
        }
        if (n == 0) b.append("- none\n");

        b.append("\n## Priority 2: axis низкий\n\n");
        b.append("Значит правила вообще неправильно понимают тип изображения.\n\n");
        n = 0;
        for (Map.Entry<String,ClassStats> e : sortedClassStats(byClass)) {
            ClassStats st = e.getValue();
            if (st.total <= 0) continue;
            float axis = st.axisOk / (float) st.total;
            if (axis < 0.50f) {
                b.append("- ").append(st.name)
                        .append(": axis ").append(pctText(st.axisOk, st.total))
                        .append(", predicted as: ").append(topMapInline(st.predictedTo, 5))
                        .append("\n");
                n++;
            }
        }
        if (n == 0) b.append("- none\n");

        b.append("\n## Current obvious fixes from this run\n\n");
        b.append("- abstract_art should not collapse into watercolor_paint when shapes are non-object/non-character.\n");
        b.append("- anime_manga should beat ui_screenshot when skin/face/eyes/hair evidence exists.\n");
        b.append("- cartoon_comic needs its own class, not product_object/animal fallback.\n");
        b.append("- diagram_chart should be separated from lineart_sketch by layout/arrow/text/blocks evidence.\n");
        b.append("- ui_screenshot should require strong phone/app UI evidence, not just text-like regions.\n");

        b.append("\n## Top exact confusions\n\n");
        n = 0;
        for (Map.Entry<String,Integer> e : sorted(exactConfusions)) {
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            if (++n >= 20) break;
        }

        b.append("\n## Top axis confusions\n\n");
        n = 0;
        for (Map.Entry<String,Integer> e : sorted(axisConfusions)) {
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            if (++n >= 20) break;
        }

        return b.toString();
    }

    private static String calibrationRulesJson(List<ItemResult> items) {
        Map<String,ClassStats> byClass = new HashMap<>();

        for (ItemResult it : items) {
            if (!it.hasExpected) continue;
            ClassStats st = byClass.get(it.expected);
            if (st == null) {
                st = new ClassStats();
                st.name = it.expected;
                st.axis = axisOf(it.expected);
                byClass.put(it.expected, st);
            }
            st.total++;
            if (it.expected.equals(it.predicted)) st.strictOk++;
            if (axisOf(it.expected).equals(axisOf(it.predicted))) st.axisOk++;
            inc(st.predictedTo, it.predicted);
        }

        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"note\": \"Generated benchmark calibration hints. Do not treat as training data yet.\",\n");
        b.append("  \"classes\": [\n");

        int n = 0;
        for (Map.Entry<String,ClassStats> e : sortedClassStats(byClass)) {
            ClassStats st = e.getValue();
            if (n++ > 0) b.append(",\n");
            b.append("    {\n");
            b.append("      \"class\": \"").append(esc(st.name)).append("\",\n");
            b.append("      \"axis\": \"").append(esc(st.axis)).append("\",\n");
            b.append("      \"total\": ").append(st.total).append(",\n");
            b.append("      \"strict_ok\": ").append(st.strictOk).append(",\n");
            b.append("      \"axis_ok\": ").append(st.axisOk).append(",\n");
            b.append("      \"predicted_as\": \"").append(esc(topMapInline(st.predictedTo, 8))).append("\"\n");
            b.append("    }");
        }

        b.append("\n  ]\n");
        b.append("}\n");
        return b.toString();
    }

    private static String classReportCsv(List<ItemResult> items) {
        Map<String,ClassStats> m = new HashMap<>();
        for (ItemResult it : items) {
            if (!it.hasExpected) continue;
            ClassStats st = m.get(it.expected);
            if (st == null) {
                st = new ClassStats();
                st.name = it.expected;
                st.axis = axisOf(it.expected);
                m.put(it.expected, st);
            }
            st.total++;
            if (it.expected.equals(it.predicted)) st.strictOk++;
            if (axisOf(it.expected).equals(axisOf(it.predicted))) st.axisOk++;
            inc(st.predictedTo, it.predicted);
        }

        StringBuilder b = new StringBuilder();
        b.append("class,axis,total,strict_ok,strict_pct,axis_ok,axis_pct,top_wrong_predictions\n");
        for (Map.Entry<String,ClassStats> e : sortedClassStats(m)) {
            ClassStats st = e.getValue();
            b.append(csv(st.name)).append(',')
                    .append(csv(st.axis)).append(',')
                    .append(st.total).append(',')
                    .append(st.strictOk).append(',')
                    .append(csv(pctText(st.strictOk, st.total))).append(',')
                    .append(st.axisOk).append(',')
                    .append(csv(pctText(st.axisOk, st.total))).append(',')
                    .append(csv(topMapInline(st.predictedTo, 6))).append('\n');
        }
        return b.toString();
    }

    private static String fixSuggestionsMd(List<ItemResult> items, List<String> errors) {
        Map<String,ClassStats> byClass = new HashMap<>();
        Map<String,Integer> confusions = new HashMap<>();
        Map<String,Integer> axisConfusions = new HashMap<>();

        for (ItemResult it : items) {
            if (!it.hasExpected) continue;

            ClassStats st = byClass.get(it.expected);
            if (st == null) {
                st = new ClassStats();
                st.name = it.expected;
                st.axis = axisOf(it.expected);
                byClass.put(it.expected, st);
            }

            st.total++;
            if (it.expected.equals(it.predicted)) st.strictOk++;
            if (axisOf(it.expected).equals(axisOf(it.predicted))) st.axisOk++;
            inc(st.predictedTo, it.predicted);

            if (!it.expected.equals(it.predicted)) {
                inc(confusions, it.expected + " -> " + it.predicted);
            }
            if (!axisOf(it.expected).equals(axisOf(it.predicted))) {
                inc(axisConfusions, axisOf(it.expected) + " -> " + axisOf(it.predicted));
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# SolumDraw Fix Suggestions\n\n");
        b.append("Цель отчета: показать, что чинить первым, без ручного просмотра 200+ картинок.\n\n");

        b.append("## Runtime errors\n\n");
        if (errors == null || errors.isEmpty()) {
            b.append("- none\n");
        } else {
            for (String e : errors) b.append("- ").append(e).append("\n");
        }

        b.append("\n## Worst classes by strict accuracy\n\n");
        int n = 0;
        for (Map.Entry<String,ClassStats> e : sortedClassStats(byClass)) {
            ClassStats st = e.getValue();
            if (st.total <= 0) continue;
            int wrong = st.total - st.strictOk;
            if (wrong <= 0) continue;
            b.append("- ").append(st.name)
                    .append(": strict ").append(st.strictOk).append("/").append(st.total)
                    .append(" = ").append(pctText(st.strictOk, st.total))
                    .append(", axis ").append(st.axisOk).append("/").append(st.total)
                    .append(" = ").append(pctText(st.axisOk, st.total))
                    .append(", predicted as: ").append(topMapInline(st.predictedTo, 5))
                    .append("\n");
            if (++n >= 15) break;
        }
        if (n == 0) b.append("- none\n");

        b.append("\n## Top exact confusions\n\n");
        n = 0;
        for (Map.Entry<String,Integer> e : sorted(confusions)) {
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            if (++n >= 20) break;
        }
        if (n == 0) b.append("- none\n");

        b.append("\n## Top axis confusions\n\n");
        n = 0;
        for (Map.Entry<String,Integer> e : sorted(axisConfusions)) {
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            if (++n >= 20) break;
        }
        if (n == 0) b.append("- none\n");

        b.append("\n## Next patch priority\n\n");
        b.append("1. Если class strict низкий, но axis высокий — чинить dataset router/top3 mapping.\n");
        b.append("2. Если axis низкий — чинить VisionDecisionEngine правила признаков.\n");
        b.append("3. Если много Runtime errors — чинить ImageAnalyzer crash/fallback.\n");
        b.append("4. Если UI/document путается со style_art — усилить text/panel/icon признаки.\n");
        b.append("5. Если style_art путается с character_body — разделить style и content как две независимые оси.\n");
        return b.toString();
    }

    private static List<Map.Entry<String,ClassStats>> sortedClassStats(Map<String,ClassStats> m) {
        List<Map.Entry<String,ClassStats>> e = new ArrayList<>(m.entrySet());
        Collections.sort(e, new Comparator<Map.Entry<String,ClassStats>>() {
            @Override public int compare(Map.Entry<String,ClassStats> a, Map.Entry<String,ClassStats> b) {
                ClassStats aa = a.getValue();
                ClassStats bb = b.getValue();
                int aw = aa.total - aa.strictOk;
                int bw = bb.total - bb.strictOk;
                if (bw != aw) return bw - aw;
                return aa.name.compareTo(bb.name);
            }
        });
        return e;
    }

    private static String topMapInline(Map<String,Integer> m, int limit) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Map.Entry<String,Integer> e : sorted(m)) {
            if (n > 0) b.append(" | ");
            b.append(e.getKey()).append(":").append(e.getValue());
            if (++n >= limit) break;
        }
        if (n == 0) return "none";
        return b.toString();
    }

    private static final class ClassStats {
        String name = "";
        String axis = "";
        int total = 0;
        int strictOk = 0;
        int axisOk = 0;
        final Map<String,Integer> predictedTo = new HashMap<>();
    }


    private static void touchImageProfileFoundation() {
        // Patch 27Q compile guard only. No prediction behavior change.
        ImageProfile ignored = new ImageProfile();
        ignored.resetShadow();
    }




    private static String oldVsShadowCsv(List<AxisRow> rows) {
        StringBuilder b = new StringBuilder();
        b.append("file,true_class,old_predicted,old_top3,shadow_predicted,shadow_top3,old_top1,shadow_top1,old_top3_ok,shadow_top3_ok,old_axis_ok,shadow_axis_ok,shadow_conf,styleAxis,contentAxis,purposeAxis,qualityAxis\n");
        for (AxisRow r : rows) {
            ImageProfile p = r.profile;
            boolean oldTop1 = r.trueClass != null && r.trueClass.equals(r.predicted);
            boolean shadowTop1 = r.trueClass != null && r.trueClass.equals(p.shadowFinalClass);
            boolean oldTop3 = listContains(r.oldTop3, r.trueClass);
            boolean shadowTop3 = listContains(p.shadowTop3, r.trueClass);
            boolean oldAxis = DatasetClasses.axisOf(r.trueClass).equals(DatasetClasses.axisOf(r.predicted));
            boolean shadowAxis = DatasetClasses.axisOf(r.trueClass).equals(DatasetClasses.axisOf(p.shadowFinalClass));

            b.append(csv(r.file)).append(',')
                    .append(csv(r.trueClass)).append(',')
                    .append(csv(r.predicted)).append(',')
                    .append(csv(join(r.oldTop3))).append(',')
                    .append(csv(p.shadowFinalClass)).append(',')
                    .append(csv(join(p.shadowTop3))).append(',')
                    .append(oldTop1).append(',')
                    .append(shadowTop1).append(',')
                    .append(oldTop3).append(',')
                    .append(shadowTop3).append(',')
                    .append(oldAxis).append(',')
                    .append(shadowAxis).append(',')
                    .append(num(p.shadowConfidence)).append(',')
                    .append(csv(String.valueOf(p.styleAxis))).append(',')
                    .append(csv(String.valueOf(p.contentAxis))).append(',')
                    .append(csv(String.valueOf(p.purposeAxis))).append(',')
                    .append(csv(String.valueOf(p.qualityAxis))).append('\n');
        }
        return b.toString();
    }

    private static String shadowRouterReportMd(List<AxisRow> rows) {
        int total = 0;
        int oldTop1 = 0, shadowTop1 = 0;
        int oldTop3 = 0, shadowTop3 = 0;
        int oldAxis = 0, shadowAxis = 0;
        Map<String,Integer> shadowDist = new HashMap<>();
        Map<String,Integer> top3Dist = new HashMap<>();

        for (AxisRow r : rows) {
            if (r.trueClass == null || r.trueClass.length() == 0) continue;
            ImageProfile p = r.profile;
            total++;
            if (r.trueClass.equals(r.predicted)) oldTop1++;
            if (r.trueClass.equals(p.shadowFinalClass)) shadowTop1++;
            if (listContains(r.oldTop3, r.trueClass)) oldTop3++;
            if (listContains(p.shadowTop3, r.trueClass)) shadowTop3++;
            if (DatasetClasses.axisOf(r.trueClass).equals(DatasetClasses.axisOf(r.predicted))) oldAxis++;
            if (DatasetClasses.axisOf(r.trueClass).equals(DatasetClasses.axisOf(p.shadowFinalClass))) shadowAxis++;
            inc(shadowDist, p.shadowFinalClass);
            for (String t : p.shadowTop3) inc(top3Dist, t);
        }

        String maxClass = "";
        int maxCount = 0;
        for (Map.Entry<String,Integer> e : shadowDist.entrySet()) {
            if (e.getValue() > maxCount) {
                maxClass = e.getKey();
                maxCount = e.getValue();
            }
        }

        int diversity = 0;
        for (String k : top3Dist.keySet()) {
            if (DatasetClasses.isValid(k) && !DatasetClasses.isForbidden(k)) diversity++;
        }

        float maxShare = total <= 0 ? 0f : maxCount / (float) total;

        StringBuilder b = new StringBuilder();
        b.append("# Shadow ClassRouter Report\n\n");
        b.append("Shadow router is report-only. It does not change real prediction.\n\n");
        b.append("- Total: ").append(total).append('\n');
        b.append("- Old top1: ").append(oldTop1).append('/').append(total).append(" = ").append(pctText(oldTop1, total)).append('\n');
        b.append("- Shadow top1: ").append(shadowTop1).append('/').append(total).append(" = ").append(pctText(shadowTop1, total)).append('\n');
        b.append("- Old top3: ").append(oldTop3).append('/').append(total).append(" = ").append(pctText(oldTop3, total)).append('\n');
        b.append("- Shadow top3: ").append(shadowTop3).append('/').append(total).append(" = ").append(pctText(shadowTop3, total)).append('\n');
        b.append("- Old axis: ").append(oldAxis).append('/').append(total).append(" = ").append(pctText(oldAxis, total)).append('\n');
        b.append("- Shadow axis: ").append(shadowAxis).append('/').append(total).append(" = ").append(pctText(shadowAxis, total)).append('\n');
        b.append("- Shadow top3 diversity: ").append(diversity).append('\n');
        b.append("- Shadow max class share: ").append(maxClass).append(" ").append(Math.round(maxShare * 1000f) / 10f).append("%\n\n");

        b.append("## Enable checklist\n\n");
        b.append(shadowTop1 >= oldTop1 ? "[PASS] " : "[FAIL] ").append("shadow_top1 >= old_top1\n");
        b.append(shadowTop3 >= oldTop3 ? "[PASS] " : "[FAIL] ").append("shadow_top3 >= old_top3\n");
        b.append(shadowAxis >= oldAxis ? "[PASS] " : "[FAIL] ").append("shadow_axis >= old_axis\n");
        b.append(diversity >= 12 ? "[PASS] " : "[FAIL] ").append("shadow_top3_diversity >= 12\n");
        b.append(maxShare <= 0.30f ? "[PASS] " : "[FAIL] ").append("shadow_sink_collapse <= 30%\n");

        b.append("\n## Shadow prediction distribution\n\n");
        for (Map.Entry<String,Integer> e : sorted(shadowDist)) {
            float share = total <= 0 ? 0f : e.getValue() / (float) total;
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue())
                    .append('/').append(total)
                    .append(" = ").append(Math.round(share * 1000f) / 10f).append("%\n");
        }

        return b.toString();
    }

    private static boolean listContains(List<String> list, String value) {
        if (list == null || value == null) return false;
        for (String s : list) if (value.equals(s)) return true;
        return false;
    }

    private static String profileAxesCsv(List<AxisRow> rows) {
        StringBuilder b = new StringBuilder();
        b.append("file,true_class,predicted,styleAxis,styleConf,contentAxis,contentConf,purposeAxis,purposeConf,qualityAxis,qualityConf\n");
        for (AxisRow r : rows) {
            ImageProfile p = r.profile;
            b.append(csv(r.file)).append(',')
                    .append(csv(r.trueClass)).append(',')
                    .append(csv(r.predicted)).append(',')
                    .append(csv(String.valueOf(p.styleAxis))).append(',')
                    .append(num(p.styleAxisConf)).append(',')
                    .append(csv(String.valueOf(p.contentAxis))).append(',')
                    .append(num(p.contentAxisConf)).append(',')
                    .append(csv(String.valueOf(p.purposeAxis))).append(',')
                    .append(num(p.purposeAxisConf)).append(',')
                    .append(csv(String.valueOf(p.qualityAxis))).append(',')
                    .append(num(p.qualityAxisConf)).append('\n');
        }
        return b.toString();
    }

    private static String axisDistributionMd(List<AxisRow> rows) {
        Map<String,Integer> style = new HashMap<>();
        Map<String,Integer> content = new HashMap<>();
        Map<String,Integer> purpose = new HashMap<>();
        Map<String,Integer> quality = new HashMap<>();

        int lowStyle = 0, lowContent = 0, lowPurpose = 0, lowQuality = 0;

        for (AxisRow r : rows) {
            ImageProfile p = r.profile;
            inc(style, String.valueOf(p.styleAxis));
            inc(content, String.valueOf(p.contentAxis));
            inc(purpose, String.valueOf(p.purposeAxis));
            inc(quality, String.valueOf(p.qualityAxis));

            if (p.styleAxisConf < 0.25f) lowStyle++;
            if (p.contentAxisConf < 0.25f) lowContent++;
            if (p.purposeAxisConf < 0.25f) lowPurpose++;
            if (p.qualityAxisConf < 0.25f) lowQuality++;
        }

        StringBuilder b = new StringBuilder();
        b.append("# AxisScorer Debug Distribution\n\n");
        b.append("AxisScorer is shadow/debug only. It does not change prediction.\n\n");
        axisGuardBlock(b, "StyleAxis", style, rows.size(), lowStyle);
        axisGuardBlock(b, "ContentAxis", content, rows.size(), lowContent);
        axisGuardBlock(b, "PurposeAxis", purpose, rows.size(), lowPurpose);
        axisGuardBlock(b, "QualityAxis", quality, rows.size(), lowQuality);
        return b.toString();
    }

    private static void axisGuardBlock(StringBuilder b, String name, Map<String,Integer> counts, int total, int lowConf) {
        b.append("## ").append(name).append("\n\n");
        String maxName = "";
        int max = 0;
        int unknown = 0;

        for (Map.Entry<String,Integer> e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                maxName = e.getKey();
            }
            if ("UNKNOWN".equals(e.getKey())) unknown = e.getValue();
        }

        float maxShare = total <= 0 ? 0f : max / (float) total;
        float unknownShare = total <= 0 ? 0f : unknown / (float) total;
        float lowShare = total <= 0 ? 0f : lowConf / (float) total;

        b.append(maxShare > 0.60f ? "[WARN] " : "[PASS] ")
                .append("AXIS_COLLAPSE: max=")
                .append(maxName).append(" ")
                .append(Math.round(maxShare * 1000f) / 10f).append("%, threshold <= 60%\n");

        b.append(unknownShare > 0.40f ? "[WARN] " : "[PASS] ")
                .append("UNKNOWN_RATE: ")
                .append(Math.round(unknownShare * 1000f) / 10f).append("%, threshold <= 40%\n");

        b.append(lowShare > 0.50f ? "[WARN] " : "[PASS] ")
                .append("LOW_CONF_RATE: ")
                .append(Math.round(lowShare * 1000f) / 10f).append("%, threshold <= 50%\n\n");

        for (Map.Entry<String,Integer> e : sorted(counts)) {
            float share = total <= 0 ? 0f : e.getValue() / (float) total;
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue())
                    .append(" / ").append(total)
                    .append(" = ").append(Math.round(share * 1000f) / 10f).append("%\n");
        }
        b.append('\n');
    }

    private static final class AxisRow {
        final String file;
        final String trueClass;
        final String predicted;
        final List<String> oldTop3;
        final ImageProfile profile;

        AxisRow(String file, String trueClass, String predicted, List<String> oldTop3, ImageProfile profile) {
            this.file = file;
            this.trueClass = trueClass;
            this.predicted = predicted;
            this.oldTop3 = oldTop3 == null ? new ArrayList<String>() : oldTop3;
            this.profile = profile;
        }
    }

    private static String profileFeaturesCsv(List<ProfileRow> rows) {
        StringBuilder b = new StringBuilder();
        b.append("file,true_class,true_axis,predicted,predicted_axis,")
                .append(VisualFeatureVector.csvHeader())
                .append(",top1_ok,top3_ok,confidence,top3\n");
        for (ProfileRow r : rows) {
            b.append(csv(r.file)).append(',')
                    .append(csv(r.trueClass)).append(',')
                    .append(csv(r.trueAxis)).append(',')
                    .append(csv(r.predicted)).append(',')
                    .append(csv(r.predictedAxis)).append(',')
                    .append(r.features.toCsvRow()).append(',')
                    .append(r.top1Ok).append(',')
                    .append(r.top3Ok).append(',')
                    .append(num(r.analysis.confidence)).append(',')
                    .append(csv(join(r.top3))).append('\n');
        }
        return b.toString();
    }

    private static String profilePredictionsJsonl(List<ProfileRow> rows) {
        StringBuilder b = new StringBuilder();
        for (ProfileRow r : rows) {
            b.append("{")
                    .append("\"file\":\"").append(esc(r.file)).append("\",")
                    .append("\"true_class\":\"").append(esc(r.trueClass)).append("\",")
                    .append("\"true_axis\":\"").append(esc(r.trueAxis)).append("\",")
                    .append("\"predicted\":\"").append(esc(r.predicted)).append("\",")
                    .append("\"predicted_axis\":\"").append(esc(r.predictedAxis)).append("\",")
                    .append("\"features\":{")
                    .append("\"edgeDensity\":").append(num(r.features.edgeDensity)).append(',')
                    .append("\"sharpness\":").append(num(r.features.sharpness)).append(',')
                    .append("\"saturation\":").append(num(r.features.saturation)).append(',')
                    .append("\"colorEntropy\":").append(num(r.features.colorEntropy)).append(',')
                    .append("\"glowScore\":").append(num(r.features.glowScore)).append(',')
                    .append("\"hardLineScore\":").append(num(r.features.hardLineScore)).append(',')
                    .append("\"tileRepetition\":").append(num(r.features.tileRepetition)).append(',')
                    .append("\"pixelGridScore\":").append(num(r.features.pixelGridScore)).append(',')
                    .append("\"textDensity\":").append(num(r.features.textDensity)).append(',')
                    .append("\"symmetryScore\":").append(num(r.features.symmetryScore)).append(',')
                    .append("\"softEdgeRatio\":").append(num(r.features.softEdgeRatio)).append(',')
                    .append("\"computeMs\":").append(r.features.computeTimeMs)
                    .append("},")
                    .append("\"top1_ok\":").append(r.top1Ok).append(',')
                    .append("\"top3_ok\":").append(r.top3Ok).append(',')
                    .append("\"top3\":\"").append(esc(join(r.top3))).append("\"")
                    .append("}\n");
        }
        return b.toString();
    }

    private static String featureSummaryJson(List<ProfileRow> rows) {
        FeatureAgg edge = new FeatureAgg();
        FeatureAgg sharp = new FeatureAgg();
        FeatureAgg sat = new FeatureAgg();
        FeatureAgg entropy = new FeatureAgg();
        FeatureAgg glow = new FeatureAgg();
        FeatureAgg hard = new FeatureAgg();
        FeatureAgg tile = new FeatureAgg();
        FeatureAgg pixel = new FeatureAgg();
        FeatureAgg text = new FeatureAgg();
        FeatureAgg symmetry = new FeatureAgg();
        FeatureAgg soft = new FeatureAgg();
        FeatureAgg ms = new FeatureAgg();

        for (ProfileRow r : rows) {
            edge.add(r.features.edgeDensity);
            sharp.add(r.features.sharpness);
            sat.add(r.features.saturation);
            entropy.add(r.features.colorEntropy);
            glow.add(r.features.glowScore);
            hard.add(r.features.hardLineScore);
            tile.add(r.features.tileRepetition);
            pixel.add(r.features.pixelGridScore);
            text.add(r.features.textDensity);
            symmetry.add(r.features.symmetryScore);
            soft.add(r.features.softEdgeRatio);
            ms.add((float) r.features.computeTimeMs);
        }

        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"count\": ").append(rows.size()).append(",\n");
        appendFeatureJson(b, "edgeDensity", edge, true);
        appendFeatureJson(b, "sharpness", sharp, true);
        appendFeatureJson(b, "saturation", sat, true);
        appendFeatureJson(b, "colorEntropy", entropy, true);
        appendFeatureJson(b, "glowScore", glow, true);
        appendFeatureJson(b, "hardLineScore", hard, true);
        appendFeatureJson(b, "tileRepetition", tile, true);
        appendFeatureJson(b, "pixelGridScore", pixel, true);
        appendFeatureJson(b, "textDensity", text, true);
        appendFeatureJson(b, "symmetryScore", symmetry, true);
        appendFeatureJson(b, "softEdgeRatio", soft, true);
        appendFeatureJson(b, "computeMs", ms, false);
        b.append("\n}\n");
        return b.toString();
    }

    private static void appendFeatureJson(StringBuilder b, String name, FeatureAgg a, boolean comma) {
        b.append("  \"").append(name).append("\": {")
                .append("\"mean\": ").append(num(a.mean()))
                .append(", \"min\": ").append(num(a.minValue()))
                .append(", \"max\": ").append(num(a.maxValue()))
                .append(", \"zeroRate\": ").append(num(a.zeroRate()))
                .append("}");
        if (comma) b.append(',');
        b.append('\n');
    }

    private static final class ProfileRow {
        final String file;
        final String trueClass;
        final String trueAxis;
        final String predicted;
        final String predictedAxis;
        final VisualFeatureVector features;
        final ImageAnalysis analysis;
        final List<String> top3;
        final boolean top1Ok;
        final boolean top3Ok;
        final boolean hasExpected;

        ProfileRow(String file, String trueClass, String trueAxis, String predicted, String predictedAxis, VisualFeatureVector features, ImageAnalysis analysis, List<String> top3, boolean top1Ok, boolean top3Ok, boolean hasExpected) {
            this.file = file;
            this.trueClass = trueClass;
            this.trueAxis = trueAxis;
            this.predicted = predicted;
            this.predictedAxis = predictedAxis;
            this.features = features;
            this.analysis = analysis;
            this.top3 = top3;
            this.top1Ok = top1Ok;
            this.top3Ok = top3Ok;
            this.hasExpected = hasExpected;
        }
    }

    private static final class FeatureAgg {
        int n = 0;
        int zero = 0;
        float sum = 0f;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        void add(float v) {
            if (Float.isNaN(v) || Float.isInfinite(v)) v = 0f;
            n++;
            sum += v;
            if (v == 0f) zero++;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        float mean() { return n <= 0 ? 0f : sum / n; }
        float minValue() { return n <= 0 ? 0f : min; }
        float maxValue() { return n <= 0 ? 0f : max; }
        float zeroRate() { return n <= 0 ? 0f : zero / (float) n; }
    }

    private static String benchmarkGuardsMd(List<ItemResult> items, List<String> errors) {
        GuardStats g = buildGuardStats(items, errors);
        StringBuilder b = new StringBuilder();

        b.append("# SolumDraw Benchmark Guards\n\n");
        b.append("This report detects benchmark regressions before they become real prediction changes.\n\n");

        guardLine(b, "SINK_COLLAPSE",
                g.maxClassShare <= 0.30f,
                "max class share = " + guardPct(g.maxClassShare) + " (" + g.maxClassName + "), threshold <= 30%");

        guardLine(b, "FORBIDDEN_IN_TOP3",
                g.forbiddenTop3 == 0,
                "forbidden tokens in top3 = " + g.forbiddenTop3);

        guardLine(b, "TOP3_DIVERSITY",
                g.top3Diversity >= 12,
                "unique top3 classes = " + g.top3Diversity + ", threshold >= 12");

        guardLine(b, "TOP3_INVALID_CLASS",
                g.invalidTop3 == 0,
                "invalid top3 classes = " + g.invalidTop3);

        guardLine(b, "ANALYZER_SKIP_RATE",
                g.errorRate <= 0.15f,
                "skipped analyzer images = " + g.errors + "/" + g.total + " = " + guardPct(g.errorRate) + ", threshold <= 15%; non-blocking for shadow-router gate");

        b.append("\n## Class distribution\n\n");
        for (Map.Entry<String,Integer> e : guardSorted(g.predictedCounts)) {
            float share = g.total <= 0 ? 0f : e.getValue() / (float) g.total;
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue())
                    .append(" / ").append(g.total)
                    .append(" = ").append(guardPct(share)).append("\n");
        }

        b.append("\n## Top3 unique classes\n\n");
        for (Map.Entry<String,Integer> e : guardSorted(g.top3Counts)) {
            b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }

        if (!g.forbiddenExamples.isEmpty()) {
            b.append("\n## Forbidden top3 examples\n\n");
            for (String x : g.forbiddenExamples) b.append("- ").append(x).append("\n");
        }

        if (!g.invalidExamples.isEmpty()) {
            b.append("\n## Invalid top3 examples\n\n");
            for (String x : g.invalidExamples) b.append("- ").append(x).append("\n");
        }

        b.append("\n## Meaning\n\n");
        b.append("- FAIL means this benchmark result should not be merged into prediction logic.\n- ANALYZER_SKIP_RATE is a warning-style guard: skipped old-analyzer images do not block shadow-router if top3/axis/sink guards pass.\n");
        b.append("- WARN/FAIL on sink collapse means one class is absorbing too much of the dataset.\n");
        b.append("- FORBIDDEN_IN_TOP3 catches internal tokens such as calibrated/fallback/unknown.\n");
        return b.toString();
    }

    private static GuardStats buildGuardStats(List<ItemResult> items, List<String> errors) {
        GuardStats g = new GuardStats();
        g.total = items == null ? 0 : items.size();
        g.errors = errors == null ? 0 : errors.size();
        g.errorRate = g.total <= 0 ? 0f : g.errors / (float) g.total;

        if (items != null) {
            for (ItemResult it : items) {
                String predicted = it.predicted == null ? "" : it.predicted;
                guardInc(g.predictedCounts, predicted);

                if (it.top3 != null) {
                    for (String t : it.top3) {
                        String token = t == null ? "" : t.trim();
                        guardInc(g.top3Counts, token);

                        if (DatasetClasses.isForbidden(token)) {
                            g.forbiddenTop3++;
                            if (g.forbiddenExamples.size() < 30) {
                                g.forbiddenExamples.add(it.name + " -> " + token);
                            }
                        }

                        if (!DatasetClasses.isForbidden(token) && !DatasetClasses.isValid(token)) {
                            g.invalidTop3++;
                            if (g.invalidExamples.size() < 30) {
                                g.invalidExamples.add(it.name + " -> " + token);
                            }
                        }
                    }
                }
            }
        }

        g.top3Diversity = 0;
        for (String k : g.top3Counts.keySet()) {
            if (!DatasetClasses.isForbidden(k) && DatasetClasses.isValid(k)) g.top3Diversity++;
        }

        for (Map.Entry<String,Integer> e : g.predictedCounts.entrySet()) {
            float share = g.total <= 0 ? 0f : e.getValue() / (float) g.total;
            if (share > g.maxClassShare) {
                g.maxClassShare = share;
                g.maxClassName = e.getKey();
            }
        }

        return g;
    }

    private static void guardLine(StringBuilder b, String name, boolean pass, String details) {
        b.append(pass ? "[PASS] " : "[FAIL] ")
                .append(name)
                .append(": ")
                .append(details)
                .append("\n");
    }

    private static String guardPct(float v) {
        return Math.round(v * 1000f) / 10f + "%";
    }

    private static void guardInc(Map<String,Integer> map, String key) {
        Integer v = map.get(key);
        map.put(key, v == null ? 1 : v + 1);
    }

    private static List<Map.Entry<String,Integer>> guardSorted(Map<String,Integer> m) {
        List<Map.Entry<String,Integer>> e = new ArrayList<>(m.entrySet());
        Collections.sort(e, new Comparator<Map.Entry<String,Integer>>() {
            @Override public int compare(Map.Entry<String,Integer> a, Map.Entry<String,Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        return e;
    }

    private static final class GuardStats {
        int total;
        int errors;
        float errorRate;
        int forbiddenTop3;
        int invalidTop3;
        int top3Diversity;
        float maxClassShare;
        String maxClassName = "";
        final Map<String,Integer> predictedCounts = new HashMap<>();
        final Map<String,Integer> top3Counts = new HashMap<>();
        final List<String> forbiddenExamples = new ArrayList<>();
        final List<String> invalidExamples = new ArrayList<>();
    }

    private static String errorsText(List<String> errors) {
        StringBuilder b = new StringBuilder();
        b.append("errors=").append(errors.size()).append("\n");
        for (String e : errors) b.append(e).append("\n");
        return b.toString();
    }

    private static String readText(File f) { try { BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8")); StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line).append('\n'); r.close(); return b.toString(); } catch (Exception e) { return ""; } }
    private static String jsonString(String json, String key) { String q = "\"" + key + "\""; int i = json.indexOf(q); if (i < 0) return ""; int c = json.indexOf(':', i); if (c < 0) return ""; int a = json.indexOf('"', c + 1); if (a < 0) return ""; int b = json.indexOf('"', a + 1); if (b < 0) return ""; return json.substring(a + 1, b); }
    private static String jsonArrayInline(String json, String key) { String q = "\"" + key + "\""; int i = json.indexOf(q); if (i < 0) return ""; int a = json.indexOf('[', i); int b = json.indexOf(']', a); if (a < 0 || b < 0) return ""; return json.substring(a + 1, b).replace("\"", "").replace("\n", " ").trim(); }
    private static boolean isImageName(String name) { String n = name.toLowerCase(Locale.US); return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".bmp"); }
    private static String timestamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private static String num(float v) { return String.format(Locale.US, "%.4f", v); }
    private static String join(List<String> v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.size(); i++) { if (i > 0) b.append('|'); b.append(v.get(i)); } return b.toString(); }
    private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
    private static String csv(String s) { return "\"" + (s == null ? "" : s.replace("\"", "\"\"").replace("\n", " ")) + "\""; }
    private static void writeText(File file, String text) throws Exception { OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); w.write(text); w.close(); }
    private static void zipDir(File dir, File zipFile) throws Exception { ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile)); addDir(zip, dir, dir.getAbsolutePath().length() + 1); zip.close(); }
    private static void addDir(ZipOutputStream zip, File file, int rootLen) throws Exception { if (file.isDirectory()) { File[] kids = file.listFiles(); if (kids != null) for (File kid : kids) addDir(zip, kid, rootLen); } else { zip.putNextEntry(new ZipEntry(file.getAbsolutePath().substring(rootLen))); FileInputStream in = new FileInputStream(file); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) zip.write(buf, 0, n); in.close(); zip.closeEntry(); } }

    private static final class BenchImage { final String relativeName; final File file; final String expected; final String secondary; private BenchImage(String relativeName, File file, String expected, String secondary) { this.relativeName = relativeName; this.file = file; this.expected = expected; this.secondary = secondary; } static BenchImage fromFile(File root, File file) { String rel = file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1).replace(File.separatorChar, '/'); File side = new File(file.getParentFile(), stripExt(file.getName()) + ".json"); String json = readText(side); String trueClass = jsonString(json, "true_class"); if (trueClass.length() == 0 && file.getParentFile() != null) trueClass = file.getParentFile().getName(); String secondary = jsonArrayInline(json, "secondary_classes"); return new BenchImage(rel, file, trueClass, secondary); } private static String stripExt(String n) { int i = n.lastIndexOf('.'); return i > 0 ? n.substring(0, i) : n; } }
    private static final class ItemResult { final String name, expected, secondary, oldPredicted, predicted, routerTop, routerReason, mlProvider, note; final ImageAnalysis analysis; final List<String> top3; final boolean ok1, ok3, hasExpected; ItemResult(String name, String expected, String secondary, ImageAnalysis analysis, String oldPredicted, String predicted, String routerTop, String routerReason, String mlProvider, String note, List<String> top3, boolean ok1, boolean ok3, boolean hasExpected) { this.name = name; this.expected = expected; this.secondary = secondary; this.analysis = analysis; this.oldPredicted = oldPredicted; this.predicted = predicted; this.routerTop = routerTop; this.routerReason = routerReason; this.mlProvider = mlProvider; this.note = note; this.top3 = top3; this.ok1 = ok1; this.ok3 = ok3; this.hasExpected = hasExpected; } }
    private static final class Stats { final Map<String,Integer> expected = new HashMap<>(); final Map<String,Integer> predicted = new HashMap<>(); final Map<String,Integer> confusion = new HashMap<>(); int wrong = 0; int missing = 0; }
    public static final class Result { public final String inputDir, zipPath; public final int images, errors, labelsFound, top1, top3, missingLabels; public Result(String inputDir, String zipPath, int images, int errors, int labelsFound, int top1, int top3, int missingLabels) { this.inputDir = inputDir; this.zipPath = zipPath; this.images = images; this.errors = errors; this.labelsFound = labelsFound; this.top1 = top1; this.top3 = top3; this.missingLabels = missingLabels; } }
}
