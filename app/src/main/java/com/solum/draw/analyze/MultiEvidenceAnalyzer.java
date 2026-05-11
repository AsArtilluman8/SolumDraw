package com.solum.draw.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MultiEvidenceAnalyzer {
    private MultiEvidenceAnalyzer() {}

    public static Decision analyze(ImageFeatures f) {
        ArrayList<EvidenceResult> r = new ArrayList<>();
        r.add(ui(f));
        r.add(anime(f));
        r.add(portrait(f));
        r.add(landscape(f));
        r.add(architecture(f));
        r.add(logo(f));
        r.add(vector(f));
        r.add(sketch(f));
        r.add(painting(f));
        r.add(photo(f));
        r.add(texture(f));
        resolve(f, r);
        Collections.sort(r, new Comparator<EvidenceResult>() { @Override public int compare(EvidenceResult a, EvidenceResult b) { return Float.compare(b.score, a.score); } });
        return new Decision(r);
    }

    private static EvidenceResult ui(ImageFeatures f) { EvidenceResult r = new EvidenceResult("ui_screenshot");
        if (f.ui.panelScore > .42f && f.textLineCount >= 2) r.add(.28f, "панели + текст"); else r.sub(.18f, "нет UI-панелей/строк");
        if (f.ui.toolbarScore > .38f) r.add(.10f, "toolbar/nav");
        if (f.ui.textScore > .35f && f.textLineCount >= 2) r.add(.18f, "читаемые строки");
        if (f.hasNaturalSceneTexture()) r.sub(.24f, "натуральная сцена");
        if (f.hasCharacterSignal()) r.sub(.18f, "персонаж сильнее UI");
        return r.finish(.45f); }

    private static EvidenceResult anime(ImageFeatures f) { EvidenceResult r = new EvidenceResult("anime_manga");
        if (f.skinRatio > .08f) r.add(.28f, "кожа"); else r.sub(.18f, "мало кожи");
        if (f.edgeDensity > .12f || f.glyphRatio > .30f) r.add(.16f, "lineart/контуры");
        if (f.saturation > .10f) r.add(.12f, "цветная стилизация");
        if (f.centralObjectRatio > .10f || f.symmetryVertical > .35f) r.add(.14f, "центральная фигура");
        if (f.hasNaturalSceneTexture() && f.skinRatio < .08f) r.sub(.28f, "сцена без персонажа");
        return r.finish(.50f); }

    private static EvidenceResult portrait(ImageFeatures f) { EvidenceResult r = new EvidenceResult("portrait_character");
        if (f.skinRatio > .10f) r.add(.30f, "много кожи"); else r.sub(.20f, "мало кожи");
        if (f.centralObjectRatio > .10f) r.add(.20f, "центральный объект");
        if (f.symmetryVertical > .36f) r.add(.12f, "симметрия");
        if (f.hasNaturalSceneTexture() && f.skinRatio < .10f) r.sub(.18f, "скорее сцена");
        return r.finish(.48f); }

    private static EvidenceResult landscape(ImageFeatures f) { EvidenceResult r = new EvidenceResult("landscape_environment");
        if (f.hasNaturalSceneTexture()) r.add(.34f, "сценовая текстура");
        if (f.detailDensity > .18f) r.add(.16f, "много деталей окружения");
        if (f.paletteCompactness < .42f) r.add(.12f, "сложная палитра");
        if (f.skinRatio < .07f) r.add(.08f, "персонаж не доминирует");
        if (f.hasCharacterSignal()) r.sub(.16f, "персонажный кадр");
        if (f.ui.panelScore > .55f && f.textLineCount >= 3) r.sub(.14f, "реальный UI");
        return r.finish(.54f); }

    private static EvidenceResult architecture(ImageFeatures f) { EvidenceResult r = new EvidenceResult("architecture_hardsurface");
        if (f.edgeDensity > .11f && f.detailDensity > .14f) r.add(.22f, "жёсткие контуры/детали");
        if (f.largeComponentCount >= 2 || f.componentCount > 24) r.add(.18f, "крупные формы/строения");
        if (f.skinRatio < .07f && f.paletteCompactness < .50f) r.add(.10f, "сцена без персонажа");
        if (f.hasCharacterSignal()) r.sub(.14f, "персонаж важнее здания");
        return r.finish(.46f); }

    private static EvidenceResult logo(ImageFeatures f) { EvidenceResult r = new EvidenceResult("logo_icon");
        if (f.logoScore > .34f && f.detailDensity < .28f) r.add(.26f, "logo score + простая форма");
        if (f.glyphRatio > .36f && f.centralObjectRatio > .08f && f.textLineCount <= 1) r.add(.18f, "центральный символ");
        if (f.paletteCompactness > .30f && f.detailDensity < .28f) r.add(.12f, "плоская графика");
        if (f.hasNaturalSceneTexture()) r.sub(.34f, "натуральная сцена");
        if (f.skinRatio > .06f) r.sub(.18f, "персонаж/кожа");
        if (f.textLineCount >= 3) r.sub(.14f, "много текста");
        return r.finish(.44f); }

    private static EvidenceResult vector(ImageFeatures f) { EvidenceResult r = new EvidenceResult("vector_flat");
        if (f.paletteCompactness > .24f) r.add(.18f, "плоские цвета");
        if (f.detailDensity < .24f) r.add(.16f, "мало фото-деталей");
        if (f.saturation > .12f) r.add(.10f, "чистый цвет");
        if (f.hasNaturalSceneTexture()) r.sub(.24f, "натуральная текстура");
        return r.finish(.38f); }

    private static EvidenceResult sketch(ImageFeatures f) { EvidenceResult r = new EvidenceResult("lineart_sketch");
        if (f.whitePaperRatio > .34f) r.add(.20f, "светлая бумага");
        if (f.edgeDensity > .12f && f.saturation < .18f) r.add(.24f, "линии без цвета");
        if (f.blackInkRatio > .04f) r.add(.08f, "тёмные линии");
        if (f.saturation > .24f) r.sub(.14f, "слишком цветное");
        return r.finish(.40f); }

    private static EvidenceResult painting(ImageFeatures f) { EvidenceResult r = new EvidenceResult("digital_painting_concept");
        if (f.saturation > .08f) r.add(.12f, "цветная иллюстрация");
        if (f.detailDensity > .14f) r.add(.14f, "детали/мазки");
        if (f.paletteCompactness < .48f) r.add(.10f, "сложная палитра");
        if (f.hasCharacterSignal()) r.add(.08f, "персонажный арт");
        if (f.ui.panelScore > .58f && f.textLineCount >= 3) r.sub(.14f, "UI сильнее");
        return r.finish(.42f); }

    private static EvidenceResult photo(ImageFeatures f) { EvidenceResult r = new EvidenceResult("photo_general");
        if (f.detailDensity > .18f) r.add(.16f, "фото-детали");
        if (f.paletteCompactness < .36f) r.add(.12f, "много оттенков");
        if (f.saturation < .24f) r.add(.08f, "умеренный цвет");
        if (f.paletteCompactness > .50f) r.sub(.12f, "слишком плоско");
        return r.finish(.34f); }

    private static EvidenceResult texture(ImageFeatures f) { EvidenceResult r = new EvidenceResult("texture_pattern");
        if (f.detailDensity > .28f) r.add(.20f, "мелкая детализация");
        if (f.centralObjectRatio < .10f) r.add(.12f, "нет главного объекта");
        if (f.skinRatio > .04f || f.textLineCount >= 2) r.sub(.14f, "персонаж/текст");
        return r.finish(.30f); }

    private static void resolve(ImageFeatures f, List<EvidenceResult> r) {
        EvidenceResult ui = find(r, "ui_screenshot"), scene = find(r, "landscape_environment"), anime = find(r, "anime_manga"), portrait = find(r, "portrait_character"), logo = find(r, "logo_icon");
        if (f.hasNaturalSceneTexture()) { ui.sub(.10f, "resolver: scene"); logo.sub(.18f, "resolver: scene"); if (f.skinRatio < .08f) { anime.sub(.12f, "resolver: no skin"); portrait.sub(.12f, "resolver: no skin"); } scene.add(.08f, "resolver: natural scene"); }
        if (f.hasCharacterSignal()) { anime.add(.08f, "resolver: character"); portrait.add(.06f, "resolver: character"); scene.sub(.06f, "resolver: character"); ui.sub(.08f, "resolver: character"); }
        for (EvidenceResult e : r) e.finish(e.confidence);
    }

    private static EvidenceResult find(List<EvidenceResult> list, String name) { for (EvidenceResult r : list) if (r.className.equals(name)) return r; EvidenceResult x = new EvidenceResult(name); list.add(x); return x; }

    public static final class Decision {
        public final List<EvidenceResult> ranked;
        public final String top1, top2, top3, strategy, warnings;
        public final float confidence;
        Decision(List<EvidenceResult> ranked) {
            this.ranked = ranked;
            top1 = ranked.size() > 0 ? ranked.get(0).className : "photo_general";
            top2 = ranked.size() > 1 ? ranked.get(1).className : "photo_general";
            top3 = ranked.size() > 2 ? ranked.get(2).className : "photo_general";
            confidence = ranked.size() > 0 ? ranked.get(0).confidence : .35f;
            strategy = strategyFor(top1);
            warnings = buildWarnings(ranked);
        }
        private static String buildWarnings(List<EvidenceResult> ranked) { StringBuilder b = new StringBuilder("multiEvidence top3: "); for (int i = 0; i < ranked.size() && i < 3; i++) { if (i > 0) b.append(" | "); EvidenceResult e = ranked.get(i); b.append(e.compact()).append(" [").append(e.whyShort()).append(']'); } return b.toString(); }
        private static String strategyFor(String g) { if (g.contains("ui")) return "фон -> панели -> карточки -> иконки -> текст"; if (g.contains("anime") || g.contains("portrait")) return "фон -> силуэт -> кожа/волосы/одежда -> лицо -> детали"; if (g.contains("landscape")) return "небо/дальний фон -> земля/вода/лес -> главный объект -> тени -> детали"; if (g.contains("architecture")) return "крупные массы -> перспектива/грани -> окна/двери -> тени -> детали"; if (g.contains("logo")) return "фон -> знак -> вырезы -> glow/accent -> края"; if (g.contains("vector")) return "крупные плоские формы -> средние формы -> чистые края -> акценты"; return "фон -> крупные массы -> главный объект -> края -> детали"; }
        public String top3Pipe() { return top1 + "|" + top2 + "|" + top3; }
    }
}
