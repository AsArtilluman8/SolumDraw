package com.solum.draw.analyze;

import java.util.Locale;

public final class EvidenceProfile {
    public final int ui;
    public final int anime;
    public final int person;
    public final int scene;
    public final int logo;
    public final int text;
    public final int photo;
    public final int painting;
    public final int rawTop;
    public final int fixedTop;
    public final String rawGenre;
    public final String fixedGenre;
    public final String reason;

    private EvidenceProfile(int ui, int anime, int person, int scene, int logo, int text, int photo, int painting,
            String rawGenre, String fixedGenre, String reason) {
        this.ui = clamp(ui);
        this.anime = clamp(anime);
        this.person = clamp(person);
        this.scene = clamp(scene);
        this.logo = clamp(logo);
        this.text = clamp(text);
        this.photo = clamp(photo);
        this.painting = clamp(painting);
        this.rawGenre = rawGenre;
        this.fixedGenre = fixedGenre;
        this.reason = reason;
        this.rawTop = scoreFor(rawGenre);
        this.fixedTop = scoreFor(fixedGenre);
    }

    public static EvidenceProfile from(ImageAnalysis a) {
        if (a == null) return new EvidenceProfile(0,0,0,0,0,0,0,0,"unknown","unknown","нет анализа");

        boolean falseUi = SceneArtHeuristic.likelyFalseUi(a);
        String fixed = SceneArtHeuristic.correctedGenre(a);

        int ui = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.realTextRatio * 0.34f) + (a.paletteCompactness * 0.18f) + (a.textComponentCount / 80f) + (a.textLineCount / 12f))));
        int anime = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.skinRatio * 1.4f) + (a.edgeDensity * 0.55f) + (a.saturation * 0.55f) + (a.glyphRatio * 0.12f))));
        int person = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.skinRatio * 1.9f) + (a.centralObjectRatio * 0.75f) + (a.edgeDensity * 0.25f))));
        int scene = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.detailDensity * 0.9f) + (a.edgeDensity * 0.55f) + ((1f - a.paletteCompactness) * 0.55f) + (a.brightness * 0.18f))));
        int logo = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.logoScore * 0.75f) + (a.glyphRatio * 0.25f) + (a.symmetryVertical * 0.20f))));
        int text = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.realTextRatio * 1.25f) + (a.textLineCount / 8f) + (a.textComponentCount / 100f))));
        int photo = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.detailDensity * 0.55f) + ((1f - a.paletteCompactness) * 0.35f) + ((1f - a.glyphRatio) * 0.10f))));
        int painting = Math.round(100f * Math.max(0f, Math.min(1f,
                (a.saturation * 0.65f) + (a.edgeDensity * 0.35f) + ((1f - a.paletteCompactness) * 0.50f))));

        if (falseUi) {
            ui = Math.max(8, ui - 35);
            text = Math.max(4, text - 35);
            if (fixed.contains("anime")) anime = Math.max(anime, 82);
            if (fixed.contains("portrait")) person = Math.max(person, 78);
            if (fixed.contains("landscape") || fixed.contains("painting") || fixed.contains("concept")) scene = Math.max(scene, 70);
            if (fixed.contains("painting") || fixed.contains("concept")) painting = Math.max(painting, 70);
        }

        String reason = buildReason(a, fixed, falseUi, ui, anime, person, scene, logo, text, painting);
        return new EvidenceProfile(ui, anime, person, scene, logo, text, photo, painting, a.genre, fixed, reason);
    }

    public String compactRu() {
        return "Raw: " + ru(rawGenre) + " → Fix: " + ru(fixedGenre) + "\n"
                + "Баллы: UI " + ui + " · Anime " + anime + " · Person " + person + " · Scene " + scene
                + " · Logo " + logo + " · Text " + text + "\n"
                + "Почему: " + reason;
    }

    public String toJson() {
        return "{"
                + "\"raw\":\"" + esc(rawGenre) + "\","
                + "\"fixed\":\"" + esc(fixedGenre) + "\","
                + "\"ui\":" + ui + ","
                + "\"anime\":" + anime + ","
                + "\"person\":" + person + ","
                + "\"scene\":" + scene + ","
                + "\"logo\":" + logo + ","
                + "\"text\":" + text + ","
                + "\"photo\":" + photo + ","
                + "\"painting\":" + painting + ","
                + "\"reason\":\"" + esc(reason) + "\"} ";
    }

    public String routeKind() {
        if (fixedGenre.contains("anime") || fixedGenre.contains("portrait")) return "person";
        if (fixedGenre.contains("ui")) return "ui";
        if (fixedGenre.contains("logo")) return "logo";
        if (fixedGenre.contains("landscape") || fixedGenre.contains("painting") || fixedGenre.contains("concept")) return "scene";
        return "general";
    }

    private int scoreFor(String genre) {
        if (genre == null) return 0;
        if (genre.contains("ui")) return ui;
        if (genre.contains("anime")) return anime;
        if (genre.contains("portrait")) return person;
        if (genre.contains("landscape")) return scene;
        if (genre.contains("logo")) return logo;
        if (genre.contains("text")) return text;
        if (genre.contains("painting") || genre.contains("concept")) return painting;
        if (genre.contains("photo")) return photo;
        return Math.max(Math.max(scene, photo), painting);
    }

    private static String buildReason(ImageAnalysis a, String fixed, boolean falseUi, int ui, int anime, int person, int scene, int logo, int text, int painting) {
        if (falseUi) {
            if (fixed.contains("anime")) return "есть кожа, контуры и персонаж; UI/text похож на ложные линии одежды/надписей";
            if (fixed.contains("portrait")) return "есть кожа и центральный объект; UI/text похож на детали тела/фона";
            return "много сценических деталей; UI/text похож на траву, горы, мазки или архитектуру";
        }
        int best = Math.max(Math.max(Math.max(ui, anime), Math.max(person, scene)), Math.max(logo, Math.max(text, painting)));
        if (best == ui) return "много панелей/текста/ровных блоков";
        if (best == anime) return "кожа + линии + цветная стилизация";
        if (best == person) return "кожа и центральная фигура";
        if (best == scene) return "много деталей окружения и больших форм";
        if (best == logo) return "символ/логотип/симметрия";
        if (best == text) return "много текстовых строк";
        return "цветная иллюстрация/цифровая живопись";
    }

    private static String ru(String g) {
        if (g == null) return "?";
        if (g.contains("anime")) return "аниме";
        if (g.contains("portrait")) return "персонаж";
        if (g.contains("ui")) return "UI";
        if (g.contains("landscape")) return "пейзаж";
        if (g.contains("painting") || g.contains("concept")) return "арт";
        if (g.contains("logo")) return "логотип";
        if (g.contains("photo")) return "фото";
        return g;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
    private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
}
