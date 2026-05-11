package com.solum.draw.vision;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

public final class VisionObject {
    public final RectF boxNorm;
    public final float confidence;
    public final String label;
    public final String source;
    public final List<VisionLabel> labels;

    public VisionObject(RectF boxNorm, float confidence, String label, String source, List<VisionLabel> labels) {
        this.boxNorm = boxNorm == null ? new RectF(0.25f, 0.25f, 0.75f, 0.75f) : boxNorm;
        this.confidence = confidence;
        this.label = label == null ? "object" : label;
        this.source = source == null ? "unknown" : source;
        this.labels = labels == null ? new ArrayList<>() : labels;
    }

    public float area() {
        return Math.max(0f, boxNorm.width()) * Math.max(0f, boxNorm.height());
    }

    public float centerScore() {
        float cx = (boxNorm.left + boxNorm.right) * 0.5f;
        float cy = (boxNorm.top + boxNorm.bottom) * 0.5f;
        return 1f - Math.min(1f, Math.abs(cx - 0.5f) + Math.abs(cy - 0.52f));
    }

    public float mainScore() {
        return area() * 0.55f + centerScore() * 0.30f + confidence * 0.15f;
    }
}
