package com.solum.draw.vision.profile;

import java.util.Locale;

public final class VisualFeatureVector {
    public float edgeDensity = 0f;
    public float sharpness = 0f;
    public float saturation = 0f;
    public float colorEntropy = 0f;
    public float glowScore = 0f;
    public float hardLineScore = 0f;

    // Patch 27V features.
    public float tileRepetition = 0f;
    public float pixelGridScore = 0f;
    public float textDensity = 0f;
    public float symmetryScore = 0f;
    public float softEdgeRatio = 0f;

    public int analysisWidth = 0;
    public int analysisHeight = 0;
    public long computeTimeMs = 0L;

    public static String csvHeader() {
        return "edgeDensity,sharpness,saturation,colorEntropy,glowScore,hardLineScore,"
                + "tileRepetition,pixelGridScore,textDensity,symmetryScore,softEdgeRatio,"
                + "analysisW,analysisH,computeMs";
    }

    public String toCsvRow() {
        return String.format(Locale.US,
                "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d",
                edgeDensity,
                sharpness,
                saturation,
                colorEntropy,
                glowScore,
                hardLineScore,
                tileRepetition,
                pixelGridScore,
                textDensity,
                symmetryScore,
                softEdgeRatio,
                analysisWidth,
                analysisHeight,
                computeTimeMs);
    }

    public String toDebugString() {
        return "edge=" + f(edgeDensity)
                + ", sharp=" + f(sharpness)
                + ", sat=" + f(saturation)
                + ", entropy=" + f(colorEntropy)
                + ", glow=" + f(glowScore)
                + ", hardLine=" + f(hardLineScore)
                + ", tile=" + f(tileRepetition)
                + ", pixel=" + f(pixelGridScore)
                + ", text=" + f(textDensity)
                + ", sym=" + f(symmetryScore)
                + ", soft=" + f(softEdgeRatio)
                + ", size=" + analysisWidth + "x" + analysisHeight
                + ", ms=" + computeTimeMs;
    }

    private static String f(float v) {
        return String.format(Locale.US, "%.3f", v);
    }
}
