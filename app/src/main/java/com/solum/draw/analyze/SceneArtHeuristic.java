package com.solum.draw.analyze;

public final class SceneArtHeuristic {
    private SceneArtHeuristic() {}

    public static boolean likelyFalseUi(ImageAnalysis a) {
        if (a == null) return false;
        boolean predictedUi = isUiLike(a.genre);
        if (!predictedUi) return false;

        boolean realFlatDarkUi = a.paletteCompactness > 0.55f && a.darkRatio > 0.55f && a.saturation < 0.06f && a.skinRatio < 0.04f;
        if (realFlatDarkUi) return false;

        boolean characterLike = likelyCharacter(a);
        boolean animeLike = likelyAnime(a);
        boolean texturedScene = a.edgeDensity > 0.20f && a.detailDensity > 0.12f && a.paletteCompactness < 0.32f;
        boolean manyNaturalParts = a.componentCount >= 24 && a.textComponentCount >= 12 && a.largestComponentRatio > 0.025f;
        boolean fakeTextFromTexture = a.realTextRatio > 0.30f && a.saturation > 0.045f;
        boolean notFlatUi = a.paletteCompactness < 0.24f && a.brightness > 0.14f;

        return characterLike || animeLike || (texturedScene && manyNaturalParts && (fakeTextFromTexture || notFlatUi));
    }

    public static String correctedGenre(ImageAnalysis a) {
        if (a == null) return "photo_general";
        if (!likelyFalseUi(a)) return a.genre;
        if (likelyAnime(a)) return "anime_manga";
        if (likelyCharacter(a)) return "portrait_character";
        if (a.saturation > 0.09f || a.brightness > 0.28f) return "digital_painting_concept";
        return "landscape_environment";
    }

    public static String note(ImageAnalysis a) {
        if (!likelyFalseUi(a)) return "";
        if (likelyAnime(a)) return "Исправление: это похоже на аниме/персонажа, UI/text сработал на линиях, одежде или надписях.";
        if (likelyCharacter(a)) return "Исправление: это похоже на персонажа/портрет, UI/text сработал на деталях тела, одежды или фона.";
        return "Исправление: похоже, UI/text сработал на деталях сцены, траве, горах или цифровых мазках.";
    }

    public static boolean likelyCharacter(ImageAnalysis a) {
        return a.skinRatio > 0.16f && a.centralObjectRatio > 0.12f && a.edgeDensity > 0.12f;
    }

    public static boolean likelyAnime(ImageAnalysis a) {
        boolean strongSkin = a.skinRatio > 0.10f;
        boolean lineHeavy = a.edgeDensity > 0.18f || a.glyphRatio > 0.45f;
        boolean colorful = a.saturation > 0.10f || a.paletteCompactness < 0.20f;
        return strongSkin && lineHeavy && colorful;
    }

    private static boolean isUiLike(String genre) {
        return genre.equals("game_engine_ui") || genre.equals("ui_screenshot") || genre.equals("text_document") || genre.equals("ui_or_text_heavy");
    }
}
