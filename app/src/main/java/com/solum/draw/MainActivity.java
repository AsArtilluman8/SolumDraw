package com.solum.draw;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import com.solum.draw.analyze.AnalysisLayers;
import com.solum.draw.analyze.AnalysisVisualReport;
import com.solum.draw.analyze.AnalyzerBenchmark;
import com.solum.draw.analyze.ComponentRoleMap;
import com.solum.draw.analyze.DrawingIntentAnalysis;
import com.solum.draw.analyze.ImageAnalysis;
import com.solum.draw.analyze.ImageAnalyzer;
import com.solum.draw.analyze.SceneArtHeuristic;
import com.solum.draw.analyze.UiLayoutAnalysis;
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
    private static final int PANEL = 0xCC0D1824;
    private static final int CYAN = 0xFF22E6F2;
    private static final int VIOLET = 0xFF9B6BFF;
    private static final int TEXT = 0xFFE9F7FF;
    private static final int MUTED = 0xFF89A7B8;

    private TextView status;
    private StrokePreviewView previewView;
    private Bitmap sourceImage;
    private SafeBitmapLoader.Result lastImageInfo;
    private ImageAnalysis lastAnalysis;
    private DrawingIntentAnalysis lastIntent;
    private StrokePlan currentPlan;
    private String lastReconstructionSummary = "метрик реконструкции пока нет";
    private volatile boolean backgroundBusy = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        CrashLogger.install(this);
        RuntimeLog.line("boot", "SolumDraw Patch 19B started");
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView title = new TextView(this);
        title.setText("S O L U M   D R A W  ·  V I S I O N");
        title.setTextColor(CYAN);
        title.setTextSize(14f);
        title.setPadding(18, 14, 18, 4);

        status = new TextView(this);
        status.setTextColor(TEXT);
        status.setTextSize(13f);
        status.setPadding(18, 10, 18, 10);
        status.setBackground(cardBg(0xAA0D1824, CYAN, 1));
        status.setText("Готово. Import → Analyze → View: Видение / Маршрут / Контуры.");

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(10, 8, 10, 4);

        LinearLayout drawBar = new LinearLayout(this);
        drawBar.setOrientation(LinearLayout.HORIZONTAL);
        drawBar.setPadding(10, 4, 10, 8);

        Button importButton = button("Import", CYAN);
        Button analyzeButton = button("Analyze", CYAN);
        Button infoButton = button("Info", VIOLET);
        Button canvasButton = button("View", CYAN);
        Button benchButton = button("Bench", VIOLET);
        Button fastButton = button("Fast", CYAN);
        Button naturalButton = button("Natural", CYAN);
        Button exportButton = button("Export", VIOLET);

        topBar.addView(importButton);
        topBar.addView(analyzeButton);
        topBar.addView(infoButton);
        topBar.addView(canvasButton);
        topBar.addView(benchButton);
        drawBar.addView(fastButton);
        drawBar.addView(naturalButton);
        drawBar.addView(exportButton);

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
        canvasButton.setOnClickListener(v -> togglePreviewMode());
        benchButton.setOnClickListener(v -> runAnalyzerBenchmarkWithPermission());
        fastButton.setOnClickListener(v -> buildPlan(DrawMode.HUMAN_FAST));
        naturalButton.setOnClickListener(v -> buildPlan(DrawMode.HUMAN_NATURAL));
        exportButton.setOnClickListener(v -> exportPlan());
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
        d.setCornerRadius(18f);
        d.setStroke(strokeWidth, stroke);
        return d;
    }

    private void pickImage() {
        RuntimeLog.line("ui", "pick image requested");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void togglePreviewMode() {
        String mode = previewView.togglePreviewMode();
        status.setText("Вид: " + mode + "\nAnalyze строит видение, маршрут и контуры.");
        RuntimeLog.line("preview_mode", mode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                if (uri == null) { status.setText("Импорт не удался: пустой uri картинки"); return; }
                lastImageInfo = SafeBitmapLoader.load(getContentResolver(), uri);
                sourceImage = lastImageInfo.bitmap;
                previewView.setSourceImage(sourceImage);
                currentPlan = null;
                lastAnalysis = null;
                lastIntent = null;
                lastReconstructionSummary = "метрик реконструкции пока нет";
                previewView.setPlan(null);
                status.setText("Картинка загружена\n" + lastImageInfo.summary() + "\nДальше: Analyze или Fast/Natural.");
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
            status.setText("Bench нужен доступ к картинкам. Подтверди разрешение.");
            requestPermissions(new String[] { permission }, REQUEST_BENCH_PERMISSION);
            return;
        }
        runAnalyzerBenchmark();
    }

    private void analyzeCurrentImageVisual() {
        if (sourceImage == null) { status.setText("Сначала импортируй картинку."); return; }
        if (backgroundBusy) { status.setText("Занято. Дождись конца анализа или benchmark."); return; }
        backgroundBusy = true;
        status.setText("Analyze...\nСтрою видение, контуры и маршрут.");
        new Thread(() -> {
            try {
                ImageAnalysis analysis = ImageAnalyzer.analyze(sourceImage, "current_import");
                AnalysisLayers layers = AnalysisLayers.build(sourceImage);
                ComponentRoleMap roles = ComponentRoleMap.build(sourceImage, layers, analysis);
                UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(sourceImage);
                DrawingIntentAnalysis intent = DrawingIntentAnalysis.build(analysis, layers, roles, ui);
                Bitmap overlay = AnalysisVisualReport.overlay(sourceImage, analysis, layers);
                lastAnalysis = analysis;
                lastIntent = intent;

                File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_analysis_current.json");
                FileWriter writer = new FileWriter(out);
                writer.write(analysis.toJson());
                writer.close();

                AnalysisVisualReport.Result report = AnalysisVisualReport.writeSingle(sourceImage, analysis);
                String text = compactAnalysisSummary(analysis, intent, report.zipPath);
                RuntimeLog.line("analyze_visual", text);
                runOnUiThread(() -> { previewView.setAnalysisOverlay(overlay); status.setText(text); });
            } catch (Exception e) {
                CrashLogger.logHandledError("analyze_visual", e);
                runOnUiThread(() -> status.setText("Analyze не удался: " + e.getMessage()));
            } finally { backgroundBusy = false; }
        }).start();
    }

    private void runAnalyzerBenchmark() {
        if (backgroundBusy) { status.setText("Занято. Дождись конца анализа или benchmark."); return; }
        backgroundBusy = true;
        status.setText("Bench...\nИщу SolumDrawDataset_v1 и JSON-разметку.");
        new Thread(() -> {
            try {
                AnalyzerBenchmark.Result result = AnalyzerBenchmark.run(this, new AnalyzerBenchmark.Progress() {
                    @Override public void onStart(String datasetPath, int total, int labelsFound) { runOnUiThread(() -> status.setText("Bench\nКартинок: " + total + " | Меток: " + labelsFound + "\n" + datasetPath)); }
                    @Override public void onItem(int index, int total, String name, int top1, int top3, int missingLabels) { if (index == 1 || index == total || index % 5 == 0) runOnUiThread(() -> status.setText("Bench " + index + "/" + total + "\nTop1: " + top1 + " | Top3: " + top3 + " | Без меток: " + missingLabels + "\n" + name)); }
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
        if (lastImageInfo == null) { status.setText("Нет картинки\nBench path: /storage/emulated/0/Download/" + AnalyzerBenchmark.DATASET_DIR); return; }
        Rect rect = previewView.currentImageRect();
        String planInfo = currentPlan == null ? "План: ещё нет" : "План: действий " + currentPlan.actions.size();
        String analysisInfo = lastAnalysis == null ? "Анализ: ещё нет" : compactAnalysisSummary(lastAnalysis, lastIntent, "");
        status.setText(lastImageInfo.summary() + " | preview=" + rect.width() + "x" + rect.height() + "\n" + analysisInfo + "\n" + planInfo);
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
            String genre = lastAnalysis == null ? "no-analysis" : SceneArtHeuristic.correctedGenre(lastAnalysis);
            status.setText("План " + mode.name() + "\nЖанр: " + ruGenre(genre) + " | действий: " + currentPlan.actions.size() + " | " + ms + "ms\n" + residual.summary());
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
        } catch (Exception e) { return "метрики реконструкции упали: " + e.getMessage(); }
    }

    private StrokeAction scaleStroke(StrokeAction action, float sx, float sy) {
        java.util.ArrayList<android.graphics.PointF> scaled = new java.util.ArrayList<>();
        for (android.graphics.PointF p : action.path) scaled.add(new android.graphics.PointF(p.x * sx, p.y * sy));
        return new StrokeAction(action.stage, action.color, Math.max(1f, action.size * Math.max(sx, sy)), scaled);
    }

    private void exportPlan() {
        if (currentPlan == null) { status.setText("Сначала построй план Fast или Natural."); return; }
        try {
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_stroke_plan_patch19.json");
            FileWriter writer = new FileWriter(out);
            writer.write(StrokePlanJson.toJson(currentPlan));
            writer.close();
            status.setText("План сохранён\n" + out.getAbsolutePath());
        } catch (Exception e) { status.setText("Export не удался: " + e.getMessage()); }
    }

    private String compactAnalysisSummary(ImageAnalysis a, DrawingIntentAnalysis intent, String zip) {
        String predicted = SceneArtHeuristic.correctedGenre(a);
        StringBuilder b = new StringBuilder();
        b.append("Класс: ").append(ruGenre(predicted)).append("  ").append(Math.round(a.confidence * 100f)).append("%\n");
        b.append("План: ").append(routeShort(predicted, intent)).append("\n");
        b.append("Сигналы: контур ").append(Math.round(a.edgeDensity * 100f)).append("% · текст ").append(Math.round(a.realTextRatio * 100f)).append("% · кожа ").append(Math.round(a.skinRatio * 100f)).append("%\n");
        if (SceneArtHeuristic.note(a).length() > 0) b.append("Фикс: ").append(SceneArtHeuristic.note(a)).append('\n');
        if (zip != null && zip.length() > 0) b.append("ZIP: ").append(zip);
        return b.toString();
    }

    private static String routeShort(String genre, DrawingIntentAnalysis intent) {
        if (genre.contains("anime") || genre.contains("portrait")) return "фон → силуэт → тело/одежда → лицо → детали";
        if (genre.contains("ui")) return "фон → панели → карточки → иконки → текст";
        if (genre.contains("landscape") || genre.contains("painting") || genre.contains("concept")) return "фон → массы → свет/тень → объект → детали";
        return "фон → формы → контур → тени → детали";
    }

    private static String ruGenre(String g) { if (g == null) return "неизвестно"; if (g.contains("landscape")) return "пейзаж / окружение"; if (g.contains("painting") || g.contains("concept")) return "цифровой арт / концепт"; if (g.contains("ui")) return "интерфейс / UI"; if (g.contains("anime")) return "аниме / персонаж"; if (g.contains("portrait")) return "портрет / персонаж"; if (g.contains("logo")) return "логотип / символ"; if (g.contains("sketch")) return "скетч / линии"; if (g.contains("vector")) return "плоский вектор"; if (g.contains("photo")) return "фото"; return g; }
    private static String pct(int value, int total) { return total <= 0 ? "n/a" : Math.round(100f * value / total) + "%"; }
}
