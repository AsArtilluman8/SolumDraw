package com.solum.draw.analyze;

import android.graphics.Bitmap;

public final class ImageAnalyzer {
    private ImageAnalyzer() {}

    public static ImageAnalysis analyze(Bitmap source, String name) {
        ImageFeatures f = ImageFeatures.build(source, name);
        MultiEvidenceAnalyzer.Decision decision = MultiEvidenceAnalyzer.analyze(f);
        return new ImageAnalysis(
                name,
                f.sourceWidth,
                f.sourceHeight,
                f.analysisWidth,
                f.analysisHeight,
                decision.top1,
                decision.confidence,
                f.edgeDensity,
                f.detailDensity,
                f.skinRatio,
                f.oldTextRatio,
                f.saturation,
                f.brightness,
                f.paletteCompactness,
                f.darkRatio,
                f.brightRatio,
                f.realTextRatio,
                f.glyphRatio,
                f.logoScore,
                f.saliencyDensity,
                f.centralObjectRatio,
                f.symmetryVertical,
                f.componentCount,
                f.textComponentCount,
                f.textLineCount,
                f.largeComponentCount,
                f.largestComponentRatio,
                f.palette,
                decision.strategy,
                decision.warnings
        );
    }
}
