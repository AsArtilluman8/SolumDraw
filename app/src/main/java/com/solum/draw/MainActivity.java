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
    private StrokePlan currentPlan;
    private String lastReconstructionSummary = "no reconstruction metrics";

    @Override protected void onCreate(Bundle savedInstanceState) {
        CrashLogger.install(this);
        RuntimeLog.line("boot", "SolumDraw Patch 04C started");
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101218);

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(14f);
        status.setPadding(18, 14, 18, 10);
        status.setText("SolumDraw Patch 04C: virtual canvas + error map");

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(8, 8, 8, 8);

        Button importButton = button("Import");
        Button infoButton = button("Info");
        Button canvasButton = button("Canvas");
        Button fastButton = button("Fast");
        Button naturalButton = button("Natural");
        Button exportButton = button("Export");

        bar.addView(importButton);
        bar.addView(infoButton);
        bar.addView(canvasButton);
        bar.addView(fastButton);
        bar.addView(naturalButton);
        bar.addView(exportButton);

        previewView = new StrokePreviewView(this);
        root.addView(status);
        root.addView(bar);
        root.addView(previewView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        importButton.setOnClickListener(v -> pickImage());
        infoButton.setOnClickListener(v -> showImageInfo());
        canvasButton.setOnClickListener(v -> togglePreviewMode());
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
                lastReconstructionSummary = "no reconstruction metrics";
                previewView.setPlan(null);
                status.setText("Image: " + lastImageInfo.summary() + " | " + RuntimeLog.memory());
            } catch (Exception e) {
                CrashLogger.logHandledError("image_import", e);
                RuntimeLog.error("image_import", e);
                status.setText("Import failed: " + e.getMessage());
            }
        }
    }

    private void showImageInfo() {
        if (lastImageInfo == null) {
            status.setText("No image loaded. " + RuntimeLog.memory());
            RuntimeLog.line("image_info", "no image loaded");
            return;
        }
        String planInfo = currentPlan == null ? "no plan" : "plan actions=" + currentPlan.actions.size();
        Rect rect = previewView.currentImageRect();
        String text = lastImageInfo.summary() + " | preview=" + rect.width() + "x" + rect.height() + " | mode=" + previewView.previewModeName() + " | " + planInfo + " | " + lastReconstructionSummary + " | " + RuntimeLog.memory();
        status.setText(text);
        RuntimeLog.line("image_info", text);
    }

    private void buildPlan(DrawMode mode) {
        if (sourceImage == null) {
            status.setText("Import image first.");
            RuntimeLog.line("build_plan", "blocked: no image");
            return;
        }

        try {
            Rect imageRect = previewView.currentImageRect();
            RuntimeLog.line("build_plan", "start mode=" + mode.name() + " bitmap=" + sourceImage.getWidth() + "x" + sourceImage.getHeight() + " preview=" + imageRect.width() + "x" + imageRect.height());
            int width = Math.max(1, imageRect.width());
            int height = Math.max(1, imageRect.height());
            long start = System.currentTimeMillis();
            currentPlan = HumanStrokePlanner.build(sourceImage, mode, width, height);
            long ms = System.currentTimeMillis() - start;
            previewView.setPlan(currentPlan);
            lastReconstructionSummary = runVirtualCanvasMetrics(currentPlan);

            String summary = "Plan " + mode.name()
                    + " | actions=" + currentPlan.actions.size()
                    + " | ms=" + ms
                    + " | Sculptor=" + currentPlan.countStagePrefix("SCULPTOR")
                    + " Potter=" + currentPlan.countStagePrefix("POTTER")
                    + " Grinder=" + currentPlan.countStagePrefix("GRINDER")
                    + " Polisher=" + currentPlan.countStagePrefix("POLISHER")
                    + " | " + lastReconstructionSummary;
            status.setText(summary);
            RuntimeLog.line("build_plan", summary);
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
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_stroke_plan_patch04c.json");
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
