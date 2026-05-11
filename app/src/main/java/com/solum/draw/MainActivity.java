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
import com.solum.draw.vision.MlKitVisionProbe;
import com.solum.draw.vision.VisionResult;
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

    private TextView title;
    private TextView status;
    private StrokePreviewView previewView;
    private Bitmap sourceImage;
    private SafeBitmapLoader.Result lastImageInfo;
    private ImageAnalysis lastAnalysis;
    private DrawingIntentAnalysis lastIntent;
    private VisionResult lastMlVisionResult;
    private StrokePlan currentPlan;
    private String lastReconstructionSummary = "метрик реконструкции пока нет";
    private volatile boolean backgroundBusy = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        CrashLogger.install(this);
        RuntimeLog.line("boot", "SolumDraw Patch 24 started");
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF040A11);

        title = new TextView(this);
        title.setText("S O L U M  D R A W · V I S I O N");
        title.setTextColor(0xFF22E6F2);
        title.setTextSize(18f);
        title.setLetterSpacing(0.18f);
        title.setPadding(12, 10, 12, 6);

        status = new TextView(this);
        status.setTextColor(0xFFEAF7FF);
        status.setTextSize(13f);
        status.setPadding(14, 10, 14, 10);
        status.setBackground(panelBg());
        status.setText("Готово. Импортируй картинку. Вид переключает: исходник → анализ → маршрут → контуры → холст.");

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(4, 8, 4, 4);

        LinearLayout drawBar = new LinearLayout(this);
        drawBar.setOrientation(LinearLayout.HORIZONTAL);
        drawBar.setPadding(4, 4, 4, 8);

        Button importButton = button("Импорт");
        Button analyzeButton = button("Анализ");
        Button mlButton = button("ML");
        Button infoButton = button("Инфо");
        Button canvasButton = button("Вид");
        Button benchButton = button("QuickBench");
        Button fastButton = button("Быстро");
        Button naturalButton = button("Натур.");
        Button exportButton = button("Экспорт");

        topBar.addView(importButton);
        topBar.addView(analyzeButton);
        topBar.addView(mlButton);
        topBar.addView(infoButton);
        topBar.addView(canvasButton);
        topBar.addView(benchButton);
        drawBar.addView(fastButton);
        drawBar.addView(naturalButton);
        drawBar.addView(exportButton);

        previewView = new StrokePreviewView(this);
        root.addView(title);
        root.addView(status);
        root.addView(topBar);
        root.addView(drawBar);
        root.addView(previewView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        importButton.setOnClickListener(v -> pickImage());
        analyzeButton.setOnClickListener(v -> analyzeCurrentImageVisual());
        mlButton.setOnClickListener(v -> runMlKitProbe());
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
        button.setTextSize(11f);
        button.setTextColor(0xFFEAF7FF);
        button.setAllCaps(false);
        button.setPadding(2, 10, 2, 10);
        button.setBackground(buttonBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(2, 2, 2, 2);
        button.setLayoutParams(lp);
        return button;
    }

    private GradientDrawable buttonBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF07131F);
        g.setStroke(1, 0xFF22E6F2);
        g.setCornerRadius(10f);
        return g;
    }

    private GradientDrawable panelBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF07131F);
        g.setStroke(1, 0xFF22E6F2);
        g.setCornerRadius(12f);
        return g;
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
        status.setText("Вид: " + mode + "\nРежимы: исходник → анализ → маршрут → контуры → холст.");
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
                lastMlVisionResult = null;
                previewView.setMlVisionResult(null);
                lastReconstructionSummary = "метрик реконструкции пока нет";
                previewView.setPlan(null);
                status.setText("Картинка загружена\n" + lastImageInfo.summary() + "\nНажми Анализ или Вид.");
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


    private void runMlKitProbe() {
        if (sourceImage == null) { status.setText("Сначала импортируй картинку."); return; }
        if (backgroundBusy) { status.setText("Занято. Дождись конца анализа."); return; }

        backgroundBusy = true;
        status.setText("ML Kit: ищу labels и объекты на картинке...");
        RuntimeLog.line("mlkit_probe", "start");

        MlKitVisionProbe.analyze(sourceImage, new MlKitVisionProbe.Callback() {
            @Override public void onResult(VisionResult result) {
                lastMlVisionResult = result;
                RuntimeLog.line("mlkit_probe", result.summaryRu());
                runOnUiThread(() -> {
                    previewView.setMlVisionResult(result);
                    status.setText(result.summaryRu() + "\nЕсли bbox пустые: модель могла ещё догружаться, попробуй ML ещё раз через минуту.");
                });
                backgroundBusy = false;
            }

            @Override public void onError(Exception error) {
                CrashLogger.logHandledError("mlkit_probe", error);
                RuntimeLog.error("mlkit_probe", error);
                runOnUiThread(() -> status.setText("ML Kit недоступен: " + error.getMessage() + "\nFallback: Java CV режимы Анализ/Контуры/Маршрут работают."));
                backgroundBusy = false;
            }
        });
    }

    private void analyzeCurrentImageVisual() {
        if (sourceImage == null) { status.setText("Сначала импортируй картинку."); return; }
        if (backgroundBusy) { status.setText("Занято. Дождись конца анализа или benchmark."); return; }
        backgroundBusy = true;
        status.setText("Анализ: строю признаки, overlay и отчёт...");
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
                runOnUiThread(() -> { previewView.setAnalysisOverlay(overlay); status.setText(text); });
            } catch (Exception e) {
                CrashLogger.logHandledError("analyze_visual", e);
                RuntimeLog.error("analyze_visual", e);
                runOnUiThread(() -> status.setText("Analyze не удался: " + e.getMessage()));
            } finally { backgroundBusy = false; }
        }).start();
    }

    private void runAnalyzerBenchmark() {
        if (backgroundBusy) { status.setText("Занято. Дождись конца анализа или benchmark."); return; }
        backgroundBusy = true;
        status.setText("Bench: ищу датасет SolumDrawDataset_v1 и sidecar JSON...");
        RuntimeLog.line("benchmark", "start async");
        new Thread(() -> {
            try {
                AnalyzerBenchmark.Result result = AnalyzerBenchmark.run(this, new AnalyzerBenchmark.Progress() {
                    @Override public void onStart(String datasetPath, int total, int labelsFound) { runOnUiThread(() -> status.setText("Bench dataset: " + datasetPath + "\nкартинок=" + total + " | меток=" + labelsFound)); }
                    @Override public void onItem(int index, int total, String name, int top1, int top3, int missingLabels) { if (index == 1 || index == total || index % 5 == 0) runOnUiThread(() -> status.setText("Bench " + index + "/" + total + " | top1=" + top1 + " | top3=" + top3 + " | без меток=" + missingLabels + "\n" + name)); }
                });
                String text = "Bench готов: " + result.images + " картинок, меток=" + result.labelsFound + ", top1=" + pct(result.top1, result.labelsFound) + ", top3=" + pct(result.top3, result.labelsFound) + ", ошибок=" + result.errors + "\nZIP: " + result.zipPath;
                RuntimeLog.line("benchmark", text);
                runOnUiThread(() -> status.setText(text));
            } catch (Exception e) {
                CrashLogger.logHandledError("benchmark", e);
                RuntimeLog.error("benchmark", e);
                runOnUiThread(() -> status.setText("Bench не удался: " + e.getMessage()));
            } finally { backgroundBusy = false; }
        }).start();
    }

    private void showImageInfo() {
        if (lastImageInfo == null) { status.setText("Нет картинки. Для Bench путь: /storage/emulated/0/Download/" + AnalyzerBenchmark.DATASET_DIR); return; }
        String planInfo = currentPlan == null ? "план ещё не построен" : "действий=" + currentPlan.actions.size();
        String analysisInfo = lastAnalysis == null ? "анализа ещё нет" : russianAnalysisSummary(lastAnalysis, lastIntent, "");
        String mlInfo = lastMlVisionResult == null ? "ML ещё не запускался" : lastMlVisionResult.summaryRu();
        Rect rect = previewView.currentImageRect();
        status.setText(lastImageInfo.summary() + "\npreview=" + rect.width() + "x" + rect.height() + " | вид=" + previewView.previewModeName() + "\n" + analysisInfo + "\n" + mlInfo + "\n" + planInfo + " | " + lastReconstructionSummary);
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
            String summary = "План " + mode.name() + " | жанр=" + genre + " | действий=" + currentPlan.actions.size() + " | " + ms + "ms | Sculptor=" + currentPlan.countStagePrefix("SCULPTOR") + " Potter=" + currentPlan.countStagePrefix("POTTER") + " Grinder=" + currentPlan.countStagePrefix("GRINDER") + " Polisher=" + currentPlan.countStagePrefix("POLISHER") + " | " + residual.summary();
            status.setText(summary);
            RuntimeLog.line("build_plan", summary + " | " + lastReconstructionSummary);
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
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_stroke_plan_patch24.json");
            FileWriter writer = new FileWriter(out);
            writer.write(StrokePlanJson.toJson(currentPlan));
            writer.close();
            status.setText("План сохранён: " + out.getAbsolutePath());
        } catch (Exception e) { status.setText("Export не удался: " + e.getMessage()); }
    }

    private String russianAnalysisSummary(ImageAnalysis a, DrawingIntentAnalysis intent, String zip) {
        String predicted = SceneArtHeuristic.correctedGenre(a);
        String note = SceneArtHeuristic.note(a);
        StringBuilder b = new StringBuilder();
        b.append("SolumDraw видит: ").append(ruGenre(predicted)).append(" | raw: ").append(ruGenre(a.genre)).append(" | уверенность ").append(Math.round(a.confidence * 100f)).append("%\n");
        if (note.length() > 0) b.append(note).append('\n');
        b.append("Контуры ").append(Math.round(a.edgeDensity * 100f)).append("%, детали ").append(Math.round(a.detailDensity * 100f)).append("%, текст ").append(Math.round(a.realTextRatio * 100f)).append("%, символы ").append(Math.round(a.glyphRatio * 100f)).append("%\n");
        if (intent != null) b.append("План: ").append(ruIntent(intent.primaryIntent)).append(" | действий примерно ").append(intent.estimatedUsefulActions).append(" | шум игнорировать ").append(intent.noiseIgnoreLevel).append("/5\n");
        b.append("Совет: ").append(ruStrategy(predicted, intent)).append('\n');
        if (a.warnings != null && a.warnings.length() > 0) b.append("Риски: ").append(shorten(a.warnings, 180)).append('\n');
        if (zip != null && zip.length() > 0) b.append("ZIP: ").append(zip);
        return b.toString();
    }

    private static String ruGenre(String g) { if (g == null) return "неизвестно"; if (g.contains("landscape")) return "пейзаж / окружение"; if (g.contains("architecture")) return "архитектура"; if (g.contains("painting") || g.contains("concept")) return "цифровой арт / концепт"; if (g.contains("ui")) return "интерфейс / UI"; if (g.contains("anime")) return "аниме / мульт"; if (g.contains("portrait")) return "портрет / персонаж"; if (g.contains("logo")) return "логотип / символ"; if (g.contains("sketch")) return "скетч / линии"; if (g.contains("vector")) return "плоский вектор"; if (g.contains("photo")) return "фото"; return g; }
    private static String ruIntent(String i) { if (i == null) return "обычный послойный рисунок"; if (i.contains("character")) return "сначала силуэт персонажа"; if (i.contains("portrait")) return "сначала масса лица и головы"; if (i.contains("scene")) return "сначала фон и большие формы сцены"; if (i.contains("ui")) return "сначала layout интерфейса"; if (i.contains("logo")) return "сначала главная форма символа"; if (i.contains("lineart")) return "сначала контуры"; if (i.contains("flat")) return "сначала большие плоские цвета"; return i; }
    private static String ruStrategy(String genre, DrawingIntentAnalysis intent) { if (genre.contains("landscape") || genre.contains("painting") || genre.contains("concept")) return "фон -> большие массы -> свет/тень -> главный объект -> детали"; if (intent != null) { String i = intent.primaryIntent; if (i.contains("character")) return "фон -> силуэт -> волосы/одежда -> лицо -> детали"; if (i.contains("portrait")) return "фон -> голова/кожа -> волосы -> глаза/нос/рот -> тени"; if (i.contains("scene")) return "фон -> большие массы -> свет/тень -> главные объекты -> мелкие детали"; if (i.contains("ui")) return "фон -> панели -> кнопки -> иконки -> текст"; if (i.contains("logo")) return "фон -> главный символ -> вырезы -> чёткие края -> блики"; } return "фон -> крупные формы -> контуры -> тени -> блики -> детали"; }
    private static String shorten(String s, int max) { return s.length() <= max ? s : s.substring(0, max) + "..."; }
    private static String pct(int value, int total) { return total <= 0 ? "n/a" : Math.round(100f * value / total) + "%"; }
}
