package com.solum.draw;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.solum.draw.debug.CrashLogger;
import com.solum.draw.planner.DrawMode;
import com.solum.draw.planner.HumanStrokePlanner;
import com.solum.draw.planner.StrokePlan;
import com.solum.draw.planner.StrokePlanJson;
import com.solum.draw.preview.StrokePreviewView;
import java.io.File;
import java.io.FileWriter;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 1001;

    private TextView status;
    private StrokePreviewView previewView;
    private Bitmap sourceImage;
    private StrokePlan currentPlan;

    @Override protected void onCreate(Bundle savedInstanceState) {
        CrashLogger.install(this);
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101218);

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(14f);
        status.setPadding(18, 14, 18, 10);
        status.setText("SolumDraw Patch 02A: crash log + human stroke foundation");

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(8, 8, 8, 8);

        Button importButton = button("Import");
        Button printerButton = button("Printer");
        Button fastButton = button("Human fast");
        Button naturalButton = button("Natural");
        Button exportButton = button("Export");

        bar.addView(importButton);
        bar.addView(printerButton);
        bar.addView(fastButton);
        bar.addView(naturalButton);
        bar.addView(exportButton);

        previewView = new StrokePreviewView(this);
        root.addView(status);
        root.addView(bar);
        root.addView(previewView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        importButton.setOnClickListener(v -> pickImage());
        printerButton.setOnClickListener(v -> buildPlan(DrawMode.PRINTER_DEBUG));
        fastButton.setOnClickListener(v -> buildPlan(DrawMode.HUMAN_FAST));
        naturalButton.setOnClickListener(v -> buildPlan(DrawMode.HUMAN_NATURAL));
        exportButton.setOnClickListener(v -> exportPlan());
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11f);
        button.setAllCaps(false);
        button.setPadding(4, 2, 4, 2);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                if (uri == null) {
                    status.setText("Import failed: empty image uri");
                    return;
                }
                sourceImage = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                previewView.setSourceImage(sourceImage);
                currentPlan = null;
                previewView.setPlan(null);
                status.setText("Image loaded: " + sourceImage.getWidth() + "x" + sourceImage.getHeight());
            } catch (Exception e) {
                CrashLogger.logHandledError("image_import", e);
                status.setText("Import failed: " + e.getMessage());
            }
        }
    }

    private void buildPlan(DrawMode mode) {
        if (sourceImage == null) {
            status.setText("Import image first.");
            return;
        }

        try {
            int width = Math.max(1, previewView.getWidth());
            int height = Math.max(1, previewView.getHeight());
            currentPlan = HumanStrokePlanner.build(sourceImage, mode, width, height);
            previewView.setPlan(currentPlan);

            status.setText("Plan " + mode.name()
                    + " | actions=" + currentPlan.actions.size()
                    + " | Sculptor=" + currentPlan.countStagePrefix("SCULPTOR")
                    + " Potter=" + currentPlan.countStagePrefix("POTTER")
                    + " Grinder=" + currentPlan.countStagePrefix("GRINDER")
                    + " Polisher=" + currentPlan.countStagePrefix("POLISHER"));
        } catch (Exception e) {
            CrashLogger.logHandledError("build_plan_" + mode.name(), e);
            status.setText("Plan failed: " + e.getMessage());
        }
    }

    private void exportPlan() {
        if (currentPlan == null) {
            status.setText("Build plan first.");
            return;
        }

        try {
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_stroke_plan_patch01.json");
            FileWriter writer = new FileWriter(out);
            writer.write(StrokePlanJson.toJson(currentPlan));
            writer.close();
            status.setText("Exported: " + out.getAbsolutePath());
        } catch (Exception e) {
            CrashLogger.logHandledError("export_plan", e);
            status.setText("Export failed: " + e.getMessage());
        }
    }
}
