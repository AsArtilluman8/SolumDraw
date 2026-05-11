package com.solum.draw.vision;

public final class VisionLabel {
    public final String text;
    public final float confidence;
    public final String source;

    public VisionLabel(String text, float confidence, String source) {
        this.text = text == null ? "" : text;
        this.confidence = confidence;
        this.source = source == null ? "unknown" : source;
    }

    public String shortText() {
        return text + " " + Math.round(confidence * 100f) + "%";
    }
}
