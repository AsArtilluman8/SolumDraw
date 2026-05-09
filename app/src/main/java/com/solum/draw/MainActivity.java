package com.solum.draw;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.solum.draw.analyze.AnalyzerBenchmark;
import com.solum.draw.analyze.ImageAnalysis;
import com.solum.draw.analyze.ImageAnalyzer;
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

    private TextView status;
    private StrokePreviewView previewView;
    private Bitmap sourceImage;
    private SafeBitmapLoader.Result lastImageInfo;
    private ImageAnalysis lastAnalysis;
    private StrokePlan currentPlan;
    private String lastReconstructionSummary = "no reconstruction metrics";

    @Override protected void onCreate(Bundle savedInstanceState) {
        CrashLogger.install(this);
        RuntimeLog.line("boot", "SolumDraw Patch 04E started");
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101218);

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(13f);
        status.setPadding(18, 14, 18, 10);
        status.setText("SolumDraw 04E: Analyzer + Benchmark. Import image or run benchmark.");

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(8, 8, 8, 4);

        LinearLayout drawBar = new LinearLayout(this);
        drawBar.setOrientation(LinearLayout.HORIZONTAL);
        drawBar.setPadding(8, 4, 8, 4);

        Button importButton = button("Import");
        Button analyzeButton = button("Analyze");
        Button infoButton = button("Info");
        Button canvasButton = button("Canvas");
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
        analyzeButton.setOnClickListener(v -> analyzeCurrentImage());
        infoButton.setOnClickListener(v -> showImageInfo());
        canvasButton.setOnClickListener(v -> togglePreviewMode());
        benchButton.setOnClickListener(v -> runAnalyzerBenchmark());
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
        status.setText("Preview mode: " + mode);
        RuntimeLog.line("preview_mode", mode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                if (uri == null) {
                    status.setText("Import failed: empty image uri");
                    RuntimeLog.line("image_import", "empty uri");
                    return;
                }
                lastImageInfo = SafeBitmapLoader.load(getContentResolver(), uri);
                sourceImage = lastImageInfo.bitmap;
                previewView.setSourceImage(sourceImage);
                currentPlan = null;
                lastAnalysis = null;
                lastReconstructionSummary = "no reconstruction metrics";
                previewView.setPlan(null);
                status.setText("Image: " + lastImageInfo.summary() + " | tap Analyze, Fast, or Natural");
            } catch (Exception e) {
                CrashLogger.logHandledError("image_import", e);
                RuntimeLog.error("image_import", e);
                status.setText("Import failed: " + e.getMessage());
            }
        }
    }

    private void analyzeCurrentImage() {
        if (sourceImage == null) {
            status.setText("Import image first.");
            return;
        }
        try {
            lastAnalysis = ImageAnalyzer.analyze(sourceImage, "current_import");
            String text = "Analysis: " + lastAnalysis.shortSummary() + " | strategy: " + lastAnalysis.strategy + " | warnings: " + lastAnalysis.warnings;
            status.setText(text);
            RuntimeLog.line("analyze", text);
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_analysis_current.json");
            FileWriter writer = new FileWriter(out);
            writer.write(lastAnalysis.toJson());
            writer.close();
            RuntimeLog.line("analyze_export", out.getAbsolutePath());
        } catch (Exception e) {
            CrashLogger.logHandledError("analyze_current", e);
            RuntimeLog.error("analyze_current", e);
            status.setText("Analyze failed: " + e.getMessage());
        }
    }

    private void runAnalyzerBenchmark() {
        try {
            status.setText("Benchmark running... input folder: /Download/" + AnalyzerBenchmark.INPUT_DIR);
            RuntimeLog.line("benchmark", "start");
            AnalyzerBenchmark.Result result = AnalyzerBenchmark.run(this);
            String text = "Benchmark done: images=" + result.images + " errors=" + result.errors + " zip=" + result.zipPath;
            status.setText(text);
            RuntimeLog.line("benchmark", text);
        } catch (Exception e) {
            CrashLogger.logHandledError("benchmark", e);
            RuntimeLog.error("benchmark", e);
            status.setText("Benchmark failed: " + e.getMessage());
        }
    }

    private void showImageInfo() {
        if (lastImageInfo == null) {
            status.setText("No image loaded. Test folder: /storage/emulated/0/Download/" + AnalyzerBenchmark.INPUT_DIR);
            RuntimeLog.line("image_info", "no image loaded");
            return;
        }
        String planInfo = currentPlan == null ? "no plan" : "plan actions=" + currentPlan.actions.size();
        String analysisInfo = lastAnalysis == null ? "no analysis" : lastAnalysis.shortSummary();
        Rect rect = previewView.currentImageRect();
        String text = lastImageInfo.summary() + " | preview=" + rect.width() + "x" + rect.height() + " | mode=" + previewView.previewModeName() + " | " + analysisInfo + " | " + planInfo + " | " + lastReconstructionSummary;
        status.setText(text);
        RuntimeLog.line("image_info", text);
    }

    private void buildPlan(DrawMode mode) {
        if (sourceImage == null) {
            status.setText("Import image first.");
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
            String summary = "Plan " + mode.name()
                    + " | genre=" + genre
                    + " | actions=" + currentPlan.actions.size()
                    + " | ms=" + ms
                    + " | S=" + currentPlan.countStagePrefix("SCULPTOR")
                    + " P=" + currentPlan.countStagePrefix("POTTER")
                    + " G=" + currentPlan.countStagePrefix("GRINDER")
                    + " Po=" + currentPlan.countStagePrefix("POLISHER")
                    + " | " + residual.summary();
            status.setText(summary);
            RuntimeLog.line("build_plan", summary + " | " + lastReconstructionSummary);
        } catch (Exception e) {
            CrashLogger.logHandledError("build_plan_" + mode.name(), e);
            RuntimeLog.error("build_plan_" + mode.name(), e);
            status.setText("Plan failed: " + e.getMessage());
        }
    }

    private String runVirtualCanvasMetrics(StrokePlan plan) {
        try {
            TargetImage target = new TargetImage(sourceImage, 192);
            VirtualCanvas virtualCanvas = new VirtualCanvas(target.width(), target.height());
            float sx = target.width() / (float) Math.max(1, previewView.currentImageRect().width());
            float sy = target.height() / (float) Math.max(1, previewView.currentImageRect().height());
            for (StrokeAction action : plan.actions) {
                virtualCanvas.apply(scaleStroke(action, sx, sy));
            }
            ReconstructionMetrics metrics = ReconstructionMetrics.compare(target, virtualCanvas);
            ErrorMap errorMap = ErrorMap.build(target, virtualCanvas, 16);
            String summary = virtualCanvas.summary() + " | " + metrics.summary() + " | " + errorMap.summary(3);
            RuntimeLog.line("reconstruct_metrics", summary);
            return summary;
        } catch (Exception e) {
            CrashLogger.logHandledError("reconstruct_metrics", e);
            RuntimeLog.error("reconstruct_metrics", e);
            return "reconstruction metrics failed: " + e.getMessage();
        }
    }

    private StrokeAction scaleStroke(StrokeAction action, float sx, float sy) {
        java.util.ArrayList<android.graphics.PointF> scaled = new java.util.ArrayList<>();
        for (android.graphics.PointF p : action.path) {
            scaled.add(new android.graphics.PointF(p.x * sx, p.y * sy));
        }
        return new StrokeAction(action.stage, action.color, Math.max(1f, action.size * Math.max(sx, sy)), scaled);
    }

    private void exportPlan() {
        if (currentPlan == null) {
            status.setText("Build plan first.");
            RuntimeLog.line("export_plan", "blocked: no plan");
            return;
        }

        try {
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_stroke_plan_patch04e.json");
            FileWriter writer = new FileWriter(out);
            writer.write(StrokePlanJson.toJson(currentPlan));
            writer.close();
            status.setText("Exported: " + out.getAbsolutePath());
            RuntimeLog.line("export_plan", "exported " + out.getAbsolutePath());
        } catch (Exception e) {
            CrashLogger.logHandledError("export_plan", e);
            RuntimeLog.error("export_plan", e);
            status.setText("Export failed: " + e.getMessage());
        }
    }
}
