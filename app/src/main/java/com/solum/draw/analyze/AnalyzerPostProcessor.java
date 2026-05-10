package com.solum.draw.analyze;

public final class AnalyzerPostProcessor {
    private AnalyzerPostProcessor() {}

    public static ImageAnalysis apply(ImageAnalysis a) {
        if (a == null) return a;
        String corrected = SceneArtHeuristic.correctedGenre(a);
        if (corrected.equals(a.genre)) return a;

        float text = a.realTextRatio;
        float glyph = a.glyphRatio;
        if (SceneArtHeuristic.likelyFalseUi(a)) {
            text = Math.min(text, 0.18f);
            glyph = Math.min(glyph, 0.20f);
        }

        String strategy = strategyFor(corrected);
        String warnings = "postprocess: raw=" + a.genre + " -> " + corrected + "; " + SceneArtHeuristic.note(a) + " " + a.warnings;
        float confidence = Math.min(0.82f, Math.max(0.52f, a.confidence - 0.10f));

        return new ImageAnalysis(
                a.name,
                a.sourceWidth,
                a.sourceHeight,
                a.analysisWidth,
                a.analysisHeight,
                corrected,
                confidence,
                a.edgeDensity,
                a.detailDensity,
                a.skinRatio,
                a.textRatio,
                a.saturation,
                a.brightness,
                a.paletteCompactness,
                a.darkRatio,
                a.brightRatio,
                text,
                glyph,
                a.logoScore,
                a.saliencyDensity,
                a.centralObjectRatio,
                a.symmetryVertical,
                a.componentCount,
                a.textComponentCount,
                a.textLineCount,
                a.largeComponentCount,
                a.largestComponentRatio,
                a.palette,
                strategy,
                warnings
        );
    }

    private static String strategyFor(String genre) {
        if (genre.equals("digital_painting_concept") || genre.equals("landscape_environment")) {
            return "background_light -> large_scene_masses -> main_subject -> shadows -> highlights -> details";
        }
        if (genre.equals("portrait_character")) return "background -> silhouette -> skin_mass -> hair_clothes -> face_details -> polish";
        if (genre.equals("photo_general")) return "large_masses -> medium_regions -> edges -> residual -> polish";
        return "background -> large_masses -> regions -> edges -> residual -> polish";
    }
}
