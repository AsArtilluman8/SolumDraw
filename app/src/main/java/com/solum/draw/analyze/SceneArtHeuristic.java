package com.solum.draw.analyze;

public final class SceneArtHeuristic {
    private SceneArtHeuristic() {}

    public static boolean likelyFalseUi(ImageAnalysis a) {
        if (a == null) return false;
        boolean predictedUi = a.genre.equals("game_engine_ui") || a.genre.equals("ui_screenshot") || a.genre.equals("text_document") || a.genre.equals("ui_or_text_heavy");
        if (!predictedUi) return false;
        boolean texturedScene = a.edgeDensity > 0.26f && a.detailDensity > 0.16f && a.paletteCompactness < 0.24f;
        boolean manyNaturalParts = a.componentCount >= 35 && a.textComponentCount >= 18 && a.largestComponentRatio > 0.04f;
        boolean fakeTextFromTexture = a.realTextRatio > 0.55f && a.textLineCount >= 3 && a.saturation > 0.05f;
        boolean notFlatUi = a.paletteCompactness < 0.18f && a.brightness > 0.16f;
        return texturedScene && manyNaturalParts && (fakeTextFromTexture || notFlatUi);
    }

    public static String correctedGenre(ImageAnalysis a) {
        if (!likelyFalseUi(a)) return a == null ? "photo_general" : a.genre;
        if (a.saturation > 0.09f || a.brightness > 0.28f) return "digital_painting_concept";
        return "landscape_environment";
    }

    public static String note(ImageAnalysis a) {
        if (!likelyFalseUi(a)) return "";
        return "Исправление: похоже, UI/text сработал на деталях сцены, траве, горах или цифровых мазках.";
    }
}
