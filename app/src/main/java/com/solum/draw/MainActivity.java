package com.solum.draw;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.solum.draw.analyze.AnalysisVisualReport;
import com.solum.draw.analyze.AnalyzerBenchmark;
import com.solum.draw.analyze.ImageAnalysis;
import com.solum.draw.analyze.ImageAnalyzer;
import com.solum.draw.analyze.ImageFeatures;
import com.solum.draw.analyze.MultiEvidenceAnalyzer;
import com.solum.draw.debug.CrashLogger;
import com.solum.draw.debug.RuntimeLog;
import com.solum.draw.image.SafeBitmapLoader;
import com.solum.draw.planner.DrawMode;
import com.solum.draw.planner.HumanStrokePlanner;
import com.solum.draw.planner.StrokeAction;
import com.solum.draw.planner.StrokePlan;
import com.solum.draw.planner.StrokePlanJson;
import com.solum.draw.preview.StrokePreviewView;
import com.solum.draw.reconstruct.ErrorMap;
import com.solum.draw.reconstruct.ReconstructionMetrics;
import com.solum.draw.reconstruct.ResidualPlanner;
import com.solum.draw.reconstruct.TargetImage;
import com.solum.draw.reconstruct.VirtualCanvas;
import java.io.File;
import java.io.FileWriter;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 1001;
    private static final int REQUEST_BENCH_PERMISSION = 2002;
    private static final int BG = 0xFF060B12;
    private static final int CYAN = 0xFF22E6F2;
    private static final int VIOLET = 0xFF9B6BFF;
    private static final int TEXT = 0xFFE9F7FF;

    private TextView status;
    private StrokePreviewView previewView;
    private Bitmap sourceImage;
    private SafeBitmapLoader.Result lastImageInfo;
    private ImageAnalysis lastAnalysis;
    private MultiEvidenceAnalyzer.Decision lastDecision;
    private StrokePlan currentPlan;
    private String lastReconstructionSummary = "метрик пока нет";
    private volatile boolean backgroundBusy = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        CrashLogger.install(this);
        RuntimeLog.line("boot", "SolumDraw Patch 20B UI started");
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView title = new TextView(this);
        title.setText("S O L U M   D R A W  ·  E V I D E N C E");
        title.setTextColor(CYAN);
        title.setTextSize(14f);
        title.setPadding(14, 12, 14, 4);

        status = new TextView(this);
        status.setTextColor(TEXT);
        status.setTextSize(12.5f);
        status.setPadding(14, 10, 14, 10);
        status.setBackground(cardBg(0xAA0D1824, CYAN, 1));
        status.setText("Готово. Импорт → Анализ → Вид: маршрут/контуры/холст. Benchmark показывает прогресс.");

        LinearLayout topBar = row();
        LinearLayout drawBar = row();
        Button importButton = button("Импорт", CYAN);
        Button analyzeButton = button("Анализ", CYAN);
        Button infoButton = button("Инфо", VIOLET);
        Button viewButton = button("Вид", CYAN);
        Button benchButton = button("Bench", VIOLET);
        Button fastButton = button("Быстро", CYAN);
        Button naturalButton = button("Натур.", CYAN);
        Button exportButton = button("Экспорт", VIOLET);
        topBar.addView(importButton); topBar.addView(analyzeButton); topBar.addView(infoButton); topBar.addView(viewButton); topBar.addView(benchButton);
        drawBar.addView(fastButton); drawBar.addView(naturalButton); drawBar.addView(exportButton);

        previewView = new StrokePreviewView(this);
        root.addView(title);
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(topBar);
        root.addView(drawBar);
        root.addView(previewView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        importButton.setOnClickListener(v -> pickImage());
        analyzeButton.setOnClickListener(v -> analyzeCurrentImageVisual());
        infoButton.setOnClickListener(v -> showImageInfo());
        viewButton.setOnClickListener(v -> togglePreviewMode());
        benchButton.setOnClickListener(v -> runAnalyzerBenchmarkWithPermission());
        fastButton.setOnClickListener(v -> buildPlan(DrawMode.HUMAN_FAST));
        naturalButton.setOnClickListener(v -> buildPlan(DrawMode.HUMAN_NATURAL));
        exportButton.setOnClickListener(v -> exportPlan());
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 4, 8, 4);
        return row;
    }

    private Button button(String text, int accent) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setTextSize(10f);
        button.setAllCaps(false);
        button.setPadding(2, 2, 2, 2);
        button.setBackground(cardBg(0xBB0B1420, accent, 1));
        button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private GradientDrawable cardBg(int fill, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(16f);
        d.setStroke(strokeWidth, stroke);
        return d;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void togglePreviewMode() {
        String mode = previewView.togglePreviewMode();
        status.setText("Вид: " + mode + "\nРежимы: исходник → маршрут → контуры → холст.");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                if (uri == null) { status.setText("Импорт не удался: пустой путь."); return; }
                lastImageInfo = SafeBitmapLoader.load(getContentResolver(), uri);
                sourceImage = lastImageInfo.bitmap;
                previewView.setSourceImage(sourceImage);
                currentPlan = null;
                lastAnalysis = null;
                lastDecision = null;
                previewView.setPlan(null);
                status.setText("Картинка загружена\n" + lastImageInfo.summary() + "\nНажми Анализ.");
            } catch (Exception e) {
                CrashLogger.logHandledError("image_import", e);
                status.setText("Импорт не удался: " + e.getMessage());
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BENCH_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) runAnalyzerBenchmark();
            else status.setText("Нет разрешения на чтение картинок.");
        }
    }

    private boolean hasBenchImagePermission() {
        String permission = benchPermissionName();
        if (permission == null) return true;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String benchPermissionName() {
        if (Build.VERSION.SDK_INT >= 33) return Manifest.permission.READ_MEDIA_IMAGES;
        if (Build.VERSION.SDK_INT >= 23) return Manifest.permission.READ_EXTERNAL_STORAGE;
        return null;
    }

    private void runAnalyzerBenchmarkWithPermission() {
        if (!hasBenchImagePermission()) {
            String permission = benchPermissionName();
            status.setText("Bench нужен доступ к изображениям. Подтверди разрешение.");
            requestPermissions(new String[] { permission }, REQUEST_BENCH_PERMISSION);
            return;
        }
        runAnalyzerBenchmark();
    }

    private void analyzeCurrentImageVisual() {
        if (sourceImage == null) { status.setText("Сначала импортируй картинку."); return; }
        if (backgroundBusy) { status.setText("Занято. Дождись конца текущей задачи."); return; }
        backgroundBusy = true;
        status.setText("Анализ...\nСчитаю независимые evidence-баллы и строю маршрут.");
        new Thread(() -> {
            try {
                ImageFeatures features = ImageFeatures.build(sourceImage, "current_import");
                MultiEvidenceAnalyzer.Decision decision = MultiEvidenceAnalyzer.analyze(features);
                ImageAnalysis analysis = ImageAnalyzer.analyze(sourceImage, "current_import");
                lastDecision = decision;
                lastAnalysis = analysis;
                previewView.setRouteKind(routeKind(decision.top1));
                File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_analysis_current.json");
                FileWriter writer = new FileWriter(out);
                writer.write(analysis.toJson());
                writer.write("\n\n{\"top3\":\"" + decision.top3Pipe() + "\",\"evidence\":\"" + safe(decision.warnings) + "\"}\n");
                writer.close();
                AnalysisVisualReport.Result report = AnalysisVisualReport.writeSingle(sourceImage, analysis);
                String text = analysisText(decision, report.zipPath);
                runOnUiThread(() -> { previewView.showRoute(); status.setText(text); });
            } catch (Exception e) {
                CrashLogger.logHandledError("analyze_visual", e);
                runOnUiThread(() -> status.setText("Анализ не удался: " + e.getMessage()));
            } finally { backgroundBusy = false; }
        }).start();
    }

    private void runAnalyzerBenchmark() {
        if (backgroundBusy) { status.setText("Занято. Дождись конца текущей задачи."); return; }
        backgroundBusy = true;
        status.setText("Bench стартовал...\n0/?");
        new Thread(() -> {
            try {
                AnalyzerBenchmark.Result result = AnalyzerBenchmark.run(this, new AnalyzerBenchmark.Progress() {
                    @Override public void onStart(String datasetPath, int total, int labelsFound) {
                        runOnUiThread(() -> status.setText("Bench старт\nКартинок: " + total + " | меток: " + labelsFound + "\n" + datasetPath));
                    }
                    @Override public void onItem(int index, int total, String name, int top1, int top3, int missingLabels) {
                        if (index == 1 || index == total || index % 3 == 0) {
                            int pct = Math.round(index * 100f / Math.max(1, total));
                            runOnUiThread(() -> status.setText("Bench " + index + "/" + total + "  " + pct + "%\nTop1: " + top1 + " | Top3: " + top3 + " | без меток: " + missingLabels + "\n" + name));
                        }
                    }
                });
                String text = "Bench готов\nКартинок: " + result.images + " | Top1: " + pct(result.top1, result.labelsFound) + " | Top3: " + pct(result.top3, result.labelsFound) + "\nZIP: " + result.zipPath;
                runOnUiThread(() -> status.setText(text));
            } catch (Exception e) {
                CrashLogger.logHandledError("benchmark", e);
                runOnUiThread(() -> status.setText("Bench не удался: " + e.getMessage()));
            } finally { backgroundBusy = false; }
        }).start();
    }

    private void showImageInfo() {
        if (lastImageInfo == null) { status.setText("Нет картинки. Датасет: Download/" + AnalyzerBenchmark.DATASET_DIR); return; }
        Rect rect = previewView.currentImageRect();
        String analysisInfo = lastDecision == null ? "Анализ: ещё нет" : analysisText(lastDecision, "");
        status.setText(lastImageInfo.summary() + " | preview=" + rect.width() + "x" + rect.height() + "\n" + analysisInfo);
    }

    private void buildPlan(DrawMode mode) {
        if (sourceImage == null) { status.setText("Сначала импортируй картинку."); return; }
        if (lastAnalysis == null) { try { lastAnalysis = ImageAnalyzer.analyze(sourceImage, "current_import"); } catch (Exception ignored) {} }
        try {
            Rect imageRect = previewView.currentImageRect();
            int width = Math.max(1, imageRect.width());
            int height = Math.max(1, imageRect.height());
            long start = System.currentTimeMillis();
            StrokePlan basePlan = HumanStrokePlanner.build(sourceImage, mode, width, height);
            ResidualPlanner.Result residual = ResidualPlanner.addResidualStrokes(sourceImage, basePlan, mode, width, height);
            currentPlan = residual.plan;
            long ms = System.currentTimeMillis() - start;
            previewView.setPlan(currentPlan);
            lastReconstructionSummary = residual.summary() + " | " + runVirtualCanvasMetrics(currentPlan);
            status.setText("План " + mode.name() + "\nДействий: " + currentPlan.actions.size() + " | " + ms + "ms\n" + residual.summary());
        } catch (Exception e) { status.setText("План не построился: " + e.getMessage()); }
    }

    private String runVirtualCanvasMetrics(StrokePlan plan) {
        try {
            TargetImage target = new TargetImage(sourceImage, 192);
            VirtualCanvas virtualCanvas = new VirtualCanvas(target.width(), target.height());
            float sx = target.width() / (float) Math.max(1, previewView.currentImageRect().width());
            float sy = target.height() / (float) Math.max(1, previewView.currentImageRect().height());
            for (StrokeAction action : plan.actions) virtualCanvas.apply(scaleStroke(action, sx, sy));
            ReconstructionMetrics metrics = ReconstructionMetrics.compare(target, virtualCanvas);
            ErrorMap errorMap = ErrorMap.build(target, virtualCanvas, 16);
            return virtualCanvas.summary() + " | " + metrics.summary() + " | " + errorMap.summary(3);
        } catch (Exception e) { return "метрики упали: " + e.getMessage(); }
    }

    private StrokeAction scaleStroke(StrokeAction action, float sx, float sy) {
        java.util.ArrayList<android.graphics.PointF> scaled = new java.util.ArrayList<>();
        for (android.graphics.PointF p : action.path) scaled.add(new android.graphics.PointF(p.x * sx, p.y * sy));
        return new StrokeAction(action.stage, action.color, Math.max(1f, action.size * Math.max(sx, sy)), scaled);
    }

    private void exportPlan() {
        if (currentPlan == null) { status.setText("Сначала построй план."); return; }
        try {
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_stroke_plan_patch20.json");
            FileWriter writer = new FileWriter(out);
            writer.write(StrokePlanJson.toJson(currentPlan));
            writer.close();
            status.setText("План сохранён\n" + out.getAbsolutePath());
        } catch (Exception e) { status.setText("Экспорт не удался: " + e.getMessage()); }
    }

    private String analysisText(MultiEvidenceAnalyzer.Decision d, String zip) {
        StringBuilder b = new StringBuilder();
        b.append("Top1: ").append(ru(d.top1)).append("  ").append(Math.round(d.confidence * 100f)).append("%\n");
        b.append("Top3: ").append(ru(d.top1)).append(" / ").append(ru(d.top2)).append(" / ").append(ru(d.top3)).append("\n");
        b.append("План: ").append(ruPlan(d.strategy)).append("\n");
        b.append("Причины: ").append(shorten(d.warnings, 190));
        if (zip != null && zip.length() > 0) b.append("\nZIP: ").append(zip);
        return b.toString();
    }

    private String routeKind(String genre) {
        if (genre.contains("anime") || genre.contains("portrait") || genre.contains("body")) return "person";
        if (genre.contains("ui")) return "ui";
        if (genre.contains("logo")) return "logo";
        if (genre.contains("landscape") || genre.contains("architecture") || genre.contains("painting")) return "scene";
        return "general";
    }

    private static String ru(String g) {
        if (g == null) return "?";
        if (g.contains("game_engine")) return "game UI";
        if (g.contains("ui")) return "UI";
        if (g.contains("anime")) return "аниме";
        if (g.contains("portrait")) return "портрет";
        if (g.contains("body")) return "тело";
        if (g.contains("landscape")) return "пейзаж";
        if (g.contains("architecture")) return "архитектура";
        if (g.contains("logo")) return "логотип";
        if (g.contains("vector")) return "вектор";
        if (g.contains("painting")) return "арт";
        if (g.contains("photo")) return "фото";
        if (g.contains("texture")) return "текстура";
        if (g.contains("pattern")) return "паттерн";
        return g;
    }

    private static String ruPlan(String s) { return s == null ? "фон → формы → объект → детали" : s.replace(" -> ", " → "); }
    private static String pct(int value, int total) { return total <= 0 ? "n/a" : Math.round(100f * value / total) + "%"; }
    private static String shorten(String s, int max) { return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "..."); }
    private static String safe(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " "); }
}
