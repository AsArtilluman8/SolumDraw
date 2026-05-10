package com.solum.draw;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
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
        RuntimeLog.line("boot", "SolumDraw Patch 17 started");
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101218);

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(13f);
        status.setPadding(18, 14, 18, 10);
        status.setText("SolumDraw 17: импорт, визуальный анализ, benchmark по датасету, план рисования.");

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(8, 8, 8, 4);

        LinearLayout drawBar = new LinearLayout(this);
        drawBar.setOrientation(LinearLayout.HORIZONTAL);
        drawBar.setPadding(8, 4, 8, 4);

        Button importButton = button("Import");
        Button analyzeButton = button("Analyze");
        Button infoButton = button("Info");
        Button canvasButton = button("View");
        Button benchButton = button("Bench");
        Button fastButton = button("Fast");
        Button naturalButton = button("Natural");
        Button exportButton = button("Export");

        topBar.addView(importButton);
        topBar.addView(analyzeButton);
        topBar.addView(infoButton);
        topBar.addView(canvasButton);
        topBar.addView(benchButton);
        drawBar.addView(fastButton);
        drawBar.addView(naturalButton);
        drawBar.addView(exportButton);

        previewView = new StrokePreviewView(this);
        root.addView(status);
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

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(10f);
        button.setAllCaps(false);
        button.setPadding(2, 2, 2, 2);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
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
        status.setText("Вид: " + mode + " | Analyze покажет overlay, Canvas/View переключает режимы.");
        RuntimeLog.line("preview_mode", mode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                if (uri == null) {
                    status.setText("Импорт не удался: пустой uri картинки");
                    RuntimeLog.line("image_import", "empty uri");
                    return;
                }
                lastImageInfo = SafeBitmapLoader.load(getContentResolver(), uri);
                sourceImage = lastImageInfo.bitmap;
                previewView.setSourceImage(sourceImage);
                currentPlan = null;
                lastAnalysis = null;
                lastIntent = null;
                lastReconstructionSummary = "метрик реконструкции пока нет";
                previewView.setPlan(null);
                status.setText("Картинка загружена: " + lastImageInfo.summary() + " | нажми Analyze или Fast/Natural");
            } catch (Exception e) {
                CrashLogger.logHandledError("image_import", e);
                RuntimeLog.error("image_import", e);
                status.setText("Импорт не удался: " + e.getMessage());
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BENCH_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            RuntimeLog.line("benchmark_permission", granted ? "granted" : "denied");
            if (granted) runAnalyzerBenchmark();
            else status.setText("Нет разрешения на чтение картинок. Дай Photos/Images permission и нажми Bench ещё раз.");
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
            RuntimeLog.line("benchmark_permission", "request " + permission);
            requestPermissions(new String[] { permission }, REQUEST_BENCH_PERMISSION);
            return;
        }
        runAnalyzerBenchmark();
    }

    private void analyzeCurrentImageVisual() {
        if (sourceImage == null) {
            status.setText("Сначала импортируй картинку.");
            return;
        }
        if (backgroundBusy) {
            status.setText("Занято. Дождись конца анализа или benchmark.");
            return;
        }
        backgroundBusy = true;
        status.setText("Analyze: строю карту внимания, контуры, палитру, overlay и русский вывод...");
        RuntimeLog.line("analyze_visual", "start");
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
                String text = russianAnalysisSummary(analysis, intent, report.zipPath);
                RuntimeLog.line("analyze_visual", text);
                runOnUiThread(() -> {
                    previewView.setAnalysisOverlay(overlay);
                    status.setText(text);
                });
            } catch (Exception e) {
                CrashLogger.logHandledError("analyze_visual", e);
                RuntimeLog.error("analyze_visual", e);
                runOnUiThread(() -> status.setText("Analyze не удался: " + e.getMessage()));
            } finally {
                backgroundBusy = false;
            }
        }).start();
    }

    private void runAnalyzerBenchmark() {
        if (backgroundBusy) {
            status.setText("Занято. Дождись конца анализа или benchmark.");
            return;
        }
        backgroundBusy = true;
        status.setText("Bench: ищу датасет SolumDrawDataset_v1 и sidecar JSON...");
        RuntimeLog.line("benchmark", "start async");
        new Thread(() -> {
            try {
                AnalyzerBenchmark.Result result = AnalyzerBenchmark.run(this, new AnalyzerBenchmark.Progress() {
                    @Override public void onStart(String datasetPath, int total, int labelsFound) {
                        runOnUiThread(() -> status.setText("Bench dataset: " + datasetPath + " | картинок=" + total + " | меток=" + labelsFound));
                    }
                    @Override public void onItem(int index, int total, String name, int top1, int top3, int missingLabels) {
                        if (index == 1 || index == total || index % 5 == 0) {
                            runOnUiThread(() -> status.setText("Bench " + index + "/" + total + " | top1=" + top1 + " | top3=" + top3 + " | без меток=" + missingLabels + "\n" + name));
                        }
                    }
                });
                String text = "Bench готов: " + result.images + " картинок, меток=" + result.labelsFound
                        + ", top1=" + pct(result.top1, result.labelsFound) + ", top3=" + pct(result.top3, result.labelsFound)
                        + ", ошибок=" + result.errors + "\nZIP: " + result.zipPath;
                RuntimeLog.line("benchmark", text);
                runOnUiThread(() -> status.setText(text));
            } catch (Exception e) {
                CrashLogger.logHandledError("benchmark", e);
                RuntimeLog.error("benchmark", e);
                runOnUiThread(() -> status.setText("Bench не удался: " + e.getMessage()));
            } finally {
                backgroundBusy = false;
            }
        }).start();
    }

    private void showImageInfo() {
        if (lastImageInfo == null) {
            status.setText("Нет картинки. Для Bench основной путь: /storage/emulated/0/Download/" + AnalyzerBenchmark.DATASET_DIR);
            RuntimeLog.line("image_info", "no image loaded");
            return;
        }
        String planInfo = currentPlan == null ? "план ещё не построен" : "действий=" + currentPlan.actions.size();
        String analysisInfo = lastAnalysis == null ? "анализа ещё нет" : russianAnalysisSummary(lastAnalysis, lastIntent, "");
        Rect rect = previewView.currentImageRect();
        String text = lastImageInfo.summary() + " | preview=" + rect.width() + "x" + rect.height() + " | вид=" + previewView.previewModeName() + "\n" + analysisInfo + "\n" + planInfo + " | " + lastReconstructionSummary;
        status.setText(text);
        RuntimeLog.line("image_info", text);
    }

    private void buildPlan(DrawMode mode) {
        if (sourceImage == null) {
            status.setText("Сначала импортируй картинку.");
            RuntimeLog.line("build_plan", "blocked: no image");
            return;
        }
        if (lastAnalysis == null) {
            try { lastAnalysis = ImageAnalyzer.analyze(sourceImage, "current_import"); } catch (Exception ignored) {}
        }
        try {
            Rect imageRect = previewView.currentImageRect();
            RuntimeLog.line("build_plan", "start mode=" + mode.name() + " bitmap=" + sourceImage.getWidth() + "x" + sourceImage.getHeight() + " preview=" + imageRect.width() + "x" + imageRect.height());
            int width = Math.max(1, imageRect.width());
            int height = Math.max(1, imageRect.height());
            long start = System.currentTimeMillis();
            StrokePlan basePlan = HumanStrokePlanner.build(sourceImage, mode, width, height);
            ResidualPlanner.Result residual = ResidualPlanner.addResidualStrokes(sourceImage, basePlan, mode, width, height);
            currentPlan = residual.plan;
            long ms = System.currentTimeMillis() - start;
            previewView.setPlan(currentPlan);
            lastReconstructionSummary = residual.summary() + " | " + runVirtualCanvasMetrics(currentPlan);
            String genre = lastAnalysis == null ? "no-analysis" : lastAnalysis.genre;
            String summary = "План " + mode.name() + " | жанр=" + genre + " | действий=" + currentPlan.actions.size() + " | " + ms + "ms | Sculptor=" + currentPlan.countStagePrefix("SCULPTOR") + " Potter=" + currentPlan.countStagePrefix("POTTER") + " Grinder=" + currentPlan.countStagePrefix("GRINDER") + " Polisher=" + currentPlan.countStagePrefix("POLISHER") + " | " + residual.summary();
            status.setText(summary);
            RuntimeLog.line("build_plan", summary + " | " + lastReconstructionSummary);
        } catch (Exception e) {
            CrashLogger.logHandledError("build_plan_" + mode.name(), e);
            RuntimeLog.error("build_plan_" + mode.name(), e);
            status.setText("План не построился: " + e.getMessage());
        }
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
            String summary = virtualCanvas.summary() + " | " + metrics.summary() + " | " + errorMap.summary(3);
            RuntimeLog.line("reconstruct_metrics", summary);
            return summary;
        } catch (Exception e) {
            CrashLogger.logHandledError("reconstruct_metrics", e);
            RuntimeLog.error("reconstruct_metrics", e);
            return "метрики реконструкции упали: " + e.getMessage();
        }
    }

    private StrokeAction scaleStroke(StrokeAction action, float sx, float sy) {
        java.util.ArrayList<android.graphics.PointF> scaled = new java.util.ArrayList<>();
        for (android.graphics.PointF p : action.path) scaled.add(new android.graphics.PointF(p.x * sx, p.y * sy));
        return new StrokeAction(action.stage, action.color, Math.max(1f, action.size * Math.max(sx, sy)), scaled);
    }

    private void exportPlan() {
        if (currentPlan == null) {
            status.setText("Сначала построй план Fast или Natural.");
            RuntimeLog.line("export_plan", "blocked: no plan");
            return;
        }
        try {
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_stroke_plan_patch17.json");
            FileWriter writer = new FileWriter(out);
            writer.write(StrokePlanJson.toJson(currentPlan));
            writer.close();
            status.setText("План сохранён: " + out.getAbsolutePath());
            RuntimeLog.line("export_plan", "exported " + out.getAbsolutePath());
        } catch (Exception e) {
            CrashLogger.logHandledError("export_plan", e);
            RuntimeLog.error("export_plan", e);
            status.setText("Export не удался: " + e.getMessage());
        }
    }

    private String russianAnalysisSummary(ImageAnalysis a, DrawingIntentAnalysis intent, String zip) {
        StringBuilder b = new StringBuilder();
        b.append("SolumDraw видит: ").append(ruGenre(a.genre)).append(" | уверенность ").append(Math.round(a.confidence * 100f)).append("%\n");
        b.append("Контуры ").append(Math.round(a.edgeDensity * 100f)).append("%, детали ").append(Math.round(a.detailDensity * 100f)).append("%, текст ").append(Math.round(a.realTextRatio * 100f)).append("%, символы ").append(Math.round(a.glyphRatio * 100f)).append("%\n");
        if (intent != null) {
            b.append("План: ").append(ruIntent(intent.primaryIntent)).append(" | действий примерно ").append(intent.estimatedUsefulActions).append(" | шум игнорировать ").append(intent.noiseIgnoreLevel).append("/5\n");
        }
        b.append("Совет: ").append(ruStrategy(a.genre, intent)).append('\n');
        if (a.warnings != null && a.warnings.length() > 0) b.append("Риски: ").append(shorten(a.warnings, 180)).append('\n');
        if (zip != null && zip.length() > 0) b.append("ZIP: ").append(zip);
        return b.toString();
    }

    private static String ruGenre(String g) {
        if (g == null) return "неизвестно";
        if (g.contains("ui")) return "интерфейс / UI";
        if (g.contains("anime")) return "аниме / мульт";
        if (g.contains("portrait")) return "портрет / персонаж";
        if (g.contains("logo")) return "логотип / символ";
        if (g.contains("sketch")) return "скетч / линии";
        if (g.contains("vector")) return "плоский вектор";
        if (g.contains("cinematic")) return "тёмная сцена / кино";
        if (g.contains("wallpaper") || g.contains("illustration")) return "арт / иллюстрация";
        if (g.contains("photo")) return "фото";
        return g;
    }

    private static String ruIntent(String i) {
        if (i == null) return "обычный послойный рисунок";
        if (i.contains("character")) return "сначала силуэт персонажа";
        if (i.contains("portrait")) return "сначала масса лица и головы";
        if (i.contains("scene")) return "сначала фон и большие формы сцены";
        if (i.contains("ui")) return "сначала layout интерфейса";
        if (i.contains("logo")) return "сначала главная форма символа";
        if (i.contains("lineart")) return "сначала контуры";
        if (i.contains("flat")) return "сначала большие плоские цвета";
        return i;
    }

    private static String ruStrategy(String genre, DrawingIntentAnalysis intent) {
        if (intent != null) {
            String i = intent.primaryIntent;
            if (i.contains("character")) return "фон -> силуэт -> волосы/одежда -> лицо -> детали";
            if (i.contains("portrait")) return "фон -> голова/кожа -> волосы -> глаза/нос/рот -> тени";
            if (i.contains("scene")) return "фон -> большие массы -> свет/тень -> главные объекты -> мелкие детали";
            if (i.contains("ui")) return "фон -> панели -> кнопки -> иконки -> текст";
            if (i.contains("logo")) return "фон -> главный символ -> вырезы -> чёткие края -> блики";
        }
        return "фон -> крупные формы -> контуры -> тени -> блики -> детали";
    }

    private static String shorten(String s, int max) { return s.length() <= max ? s : s.substring(0, max) + "..."; }
    private static String pct(int value, int total) { return total <= 0 ? "n/a" : Math.round(100f * value / total) + "%"; }
}
