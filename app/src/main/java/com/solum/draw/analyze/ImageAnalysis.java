package com.solum.draw.analyze;

import java.util.List;

public final class ImageAnalysis {
    public final String name;
    public final int sourceWidth;
    public final int sourceHeight;
    public final int analysisWidth;
    public final int analysisHeight;
    public final String genre;
    public final float confidence;
    public final float edgeDensity;
    public final float detailDensity;
    public final float skinRatio;
    public final float textRatio;
    public final float saturation;
    public final float brightness;
    public final float paletteCompactness;
    public final float darkRatio;
    public final float brightRatio;
    public final float realTextRatio;
    public final float glyphRatio;
    public final float logoScore;
    public final float saliencyDensity;
    public final float centralObjectRatio;
    public final float symmetryVertical;
    public final List<ColorCount> palette;
    public final String strategy;
    public final String warnings;

    public ImageAnalysis(String name, int sourceWidth, int sourceHeight, int analysisWidth, int analysisHeight,
            String genre, float confidence, float edgeDensity, float detailDensity, float skinRatio, float textRatio,
            float saturation, float brightness, float paletteCompactness, float darkRatio, float brightRatio,
            List<ColorCount> palette, String strategy, String warnings) {
        this(name, sourceWidth, sourceHeight, analysisWidth, analysisHeight, genre, confidence, edgeDensity,
                detailDensity, skinRatio, textRatio, saturation, brightness, paletteCompactness, darkRatio, brightRatio,
                0f, 0f, 0f, 0f, 0f, 0f, palette, strategy, warnings);
    }

    public ImageAnalysis(String name, int sourceWidth, int sourceHeight, int analysisWidth, int analysisHeight,
            String genre, float confidence, float edgeDensity, float detailDensity, float skinRatio, float textRatio,
            float saturation, float brightness, float paletteCompactness, float darkRatio, float brightRatio,
            float realTextRatio, float glyphRatio, float logoScore, float saliencyDensity,
            float centralObjectRatio, float symmetryVertical, List<ColorCount> palette, String strategy, String warnings) {
        this.name = name;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.analysisWidth = analysisWidth;
        this.analysisHeight = analysisHeight;
        this.genre = genre;
        this.confidence = confidence;
        this.edgeDensity = edgeDensity;
        this.detailDensity = detailDensity;
        this.skinRatio = skinRatio;
        this.textRatio = textRatio;
        this.saturation = saturation;
        this.brightness = brightness;
        this.paletteCompactness = paletteCompactness;
        this.darkRatio = darkRatio;
        this.brightRatio = brightRatio;
        this.realTextRatio = realTextRatio;
        this.glyphRatio = glyphRatio;
        this.logoScore = logoScore;
        this.saliencyDensity = saliencyDensity;
        this.centralObjectRatio = centralObjectRatio;
        this.symmetryVertical = symmetryVertical;
        this.palette = palette;
        this.strategy = strategy;
        this.warnings = warnings;
    }

    public String shortSummary() {
        return genre + " conf=" + pct(confidence) + " edge=" + pct(edgeDensity) + " detail=" + pct(detailDensity)
                + " skin=" + pct(skinRatio) + " text=" + pct(realTextRatio) + " glyph=" + pct(glyphRatio)
                + " logo=" + pct(logoScore);
    }

    public String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        kv(b, "name", name, true);
        b.append("  \"sourceWidth\": ").append(sourceWidth).append(",\n");
        b.append("  \"sourceHeight\": ").append(sourceHeight).append(",\n");
        b.append("  \"analysisWidth\": ").append(analysisWidth).append(",\n");
        b.append("  \"analysisHeight\": ").append(analysisHeight).append(",\n");
        kv(b, "genre", genre, true);
        b.append("  \"confidence\": ").append(num(confidence)).append(",\n");
        b.append("  \"edgeDensity\": ").append(num(edgeDensity)).append(",\n");
        b.append("  \"detailDensity\": ").append(num(detailDensity)).append(",\n");
        b.append("  \"skinRatio\": ").append(num(skinRatio)).append(",\n");
        b.append("  \"oldTextLikeRatio\": ").append(num(textRatio)).append(",\n");
        b.append("  \"realTextRatio\": ").append(num(realTextRatio)).append(",\n");
        b.append("  \"glyphRatio\": ").append(num(glyphRatio)).append(",\n");
        b.append("  \"logoScore\": ").append(num(logoScore)).append(",\n");
        b.append("  \"saliencyDensity\": ").append(num(saliencyDensity)).append(",\n");
        b.append("  \"centralObjectRatio\": ").append(num(centralObjectRatio)).append(",\n");
        b.append("  \"symmetryVertical\": ").append(num(symmetryVertical)).append(",\n");
        b.append("  \"saturation\": ").append(num(saturation)).append(",\n");
        b.append("  \"brightness\": ").append(num(brightness)).append(",\n");
        b.append("  \"paletteCompactness\": ").append(num(paletteCompactness)).append(",\n");
        b.append("  \"darkRatio\": ").append(num(darkRatio)).append(",\n");
        b.append("  \"brightRatio\": ").append(num(brightRatio)).append(",\n");
        kv(b, "strategy", strategy, true);
        kv(b, "warnings", warnings, true);
        b.append("  \"palette\": [");
        for (int i = 0; i < palette.size(); i++) {
            ColorCount c = palette.get(i);
            if (i > 0) b.append(",");
            b.append("{\"hex\":\"").append(c.hex()).append("\",\"ratio\":").append(num(c.ratio)).append("}");
        }
        b.append("]\n}");
        return b.toString();
    }

    private static void kv(StringBuilder b, String k, String v, boolean comma) {
        b.append("  \"").append(k).append("\": \"").append(escape(v)).append("\"");
        if (comma) b.append(",");
        b.append("\n");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static String num(float v) { return String.format(java.util.Locale.US, "%.4f", v); }
    private static String pct(float v) { return Math.round(v * 100f) + "%"; }
}
