package com.solum.draw.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MultiEvidenceAnalyzer {
    private MultiEvidenceAnalyzer() {}

    public static Decision analyze(ImageFeatures f) {
        ArrayList<EvidenceResult> results = new ArrayList<>();
        results.add(uiScreenshot(f));
        results.add(gameUi(f));
        results.add(textDocument(f));
        results.add(animeManga(f));
        results.add(portraitCharacter(f));
        results.add(humanBody(f));
        results.add(landscape(f));
        results.add(architecture(f));
        results.add(logoIcon(f));
        results.add(vectorFlat(f));
        results.add(lineartSketch(f));
        results.add(digitalPainting(f));
        results.add(photoGeneral(f));
        results.add(texturePattern(f));
        results.add(patternSeamless(f));
        results.add(vfxGlow(f));
        resolveConflicts(f, results);
        Collections.sort(results, new Comparator<EvidenceResult>() { @Override public int compare(EvidenceResult a, EvidenceResult b) { return Float.compare(b.score, a.score); } });
        return new Decision(results);
    }

    private static EvidenceResult uiScreenshot(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("ui_screenshot");
        if (f.ui.panelScore > 0.42f) r.add(0.26f, "ровные панели"); else r.sub(0.10f, "панели слабые");
        if (f.ui.textScore > 0.38f && f.textLineCount >= 2) r.add(0.24f, "есть строки текста"); else r.sub(0.18f, "нет подтверждённых строк текста");
        if (f.ui.toolbarScore > 0.34f) r.add(0.18f, "toolbar/nav похожие зоны");
        if (f.paletteCompactness > 0.34f && f.detailDensity < 0.35f) r.add(0.10f, "плоские области");
        if (f.hasNaturalSceneTexture()) r.sub(0.28f, "много природной/сценовой текстуры");
        if (f.hasCharacterSignal()) r.sub(0.22f, "есть персонаж/кожа");
        if (f.ui.sceneScore > 0.45f && f.ui.textScore < 0.42f) r.sub(0.12f, "viewport похож на сцену, не UI");
        return r.finish(0.54f);
    }

    private static EvidenceResult gameUi(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("game_engine_ui");
        if (f.ui.panelScore > 0.46f) r.add(0.18f, "панели редактора");
        if (f.ui.toolbarScore > 0.42f) r.add(0.18f, "верх/низ как toolbar");
        if (f.ui.sceneScore > 0.34f) r.add(0.12f, "есть viewport-сцена");
        if (f.ui.textScore > 0.36f && f.textLineCount >= 2) r.add(0.16f, "UI подписи");
        if (f.ui.panelScore < 0.34f || f.textLineCount < 2) r.sub(0.22f, "не хватает настоящих панелей/текста");
        if (f.hasNaturalSceneTexture() && f.textLineCount < 3) r.sub(0.34f, "сцена маскируется под viewport");
        if (f.hasCharacterSignal()) r.sub(0.28f, "персонаж важнее UI");
        return r.finish(0.50f);
    }

    private static EvidenceResult textDocument(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("text_document");
        if (f.textLineCount >= 5) r.add(0.40f, "много строк");
        if (f.realTextRatio > 0.26f) r.add(0.24f, "сильный text score");
        if (f.whitePaperRatio > 0.35f) r.add(0.18f, "светлая бумага/фон");
        if (f.skinRatio > 0.05f) r.sub(0.20f, "есть кожа/персонаж");
        if (f.hasNaturalSceneTexture()) r.sub(0.22f, "фон похож на сцену/текстуру");
        return r.finish(0.48f);
    }

    private static EvidenceResult animeManga(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("anime_manga");
        if (f.skinRatio > 0.08f) r.add(0.28f, "кожа");
        if (f.edgeDensity > 0.14f || f.glyphRatio > 0.34f) r.add(0.22f, "lineart/контуры");
        if (f.saturation > 0.08f) r.add(0.16f, "цветная стилизация");
        if (f.centralObjectRatio > 0.10f || f.symmetryVertical > 0.36f) r.add(0.16f, "центральный персонаж/симметрия");
        if (f.realTextRatio > 0.35f && f.textLineCount >= 3) r.sub(0.22f, "слишком много настоящего текста");
        if (f.ui.panelScore > 0.60f && f.skinRatio < 0.07f) r.sub(0.20f, "UI панели сильнее персонажа");
        return r.finish(0.58f);
    }

    private static EvidenceResult portraitCharacter(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("portrait_character");
        if (f.skinRatio > 0.11f) r.add(0.30f, "много кожи");
        if (f.centralObjectRatio > 0.12f) r.add(0.22f, "центральный объект");
        if (f.symmetryVertical > 0.38f) r.add(0.12f, "симметрия лица/тела");
        if (f.edgeDensity > 0.10f) r.add(0.10f, "читаемые контуры");
        if (f.skinRatio < 0.06f) r.sub(0.24f, "кожи мало");
        if (f.ui.textScore > 0.50f && f.textLineCount >= 3) r.sub(0.18f, "много настоящего текста");
        return r.finish(0.54f);
    }

    private static EvidenceResult humanBody(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("human_body_fullbody");
        if (f.skinRatio > 0.08f) r.add(0.22f, "кожа");
        if (f.centralObjectRatio > 0.10f && f.largestComponentRatio > 0.010f) r.add(0.20f, "крупная фигура");
        if (f.sourceHeight > f.sourceWidth) r.add(0.08f, "вертикальный кадр");
        if (f.skinRatio > 0.16f && f.edgeDensity > 0.12f) r.add(0.14f, "тело/силуэт");
        if (f.skinRatio < 0.05f) r.sub(0.22f, "нет тела/кожи");
        return r.finish(0.45f);
    }

    private static EvidenceResult landscape(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("landscape_environment");
        if (f.hasNaturalSceneTexture()) r.add(0.30f, "природная/сценовая текстура");
        if (f.detailDensity > 0.18f) r.add(0.16f, "детали окружения");
        if (f.paletteCompactness < 0.38f) r.add(0.14f, "нет одного плоского фона");
        if (f.skinRatio < 0.06f) r.add(0.08f, "персонаж не доминирует");
        if (f.ui.panelScore > 0.58f && f.textLineCount >= 3) r.sub(0.18f, "реальный UI сильный");
        if (f.skinRatio > 0.12f && f.centralObjectRatio > 0.12f) r.sub(0.18f, "персонаж доминирует");
        return r.finish(0.55f);
    }

    private static EvidenceResult architecture(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("architecture_hardsurface");
        if (f.edgeDensity > 0.12f && f.detailDensity > 0.16f) r.add(0.22f, "много жёстких контуров");
        if (f.largeComponentCount >= 2 || f.componentCount > 28) r.add(0.18f, "крупные формы/строения");
        if (f.paletteCompactness < 0.42f && f.skinRatio < 0.06f) r.add(0.14f, "сцена без персонажа");
        if (f.ui.panelScore > 0.54f && f.textLineCount >= 3) r.sub(0.16f, "может быть UI");
        return r.finish(0.44f);
    }

    private static EvidenceResult logoIcon(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("logo_icon");
        if (f.logoScore > 0.32f) r.add(0.30f, "logo score");
        if (f.glyphRatio > 0.34f) r.add(0.20f, "центральный символ/glyph");
        if (f.symmetryVertical > 0.42f) r.add(0.10f, "симметрия");
        if (f.paletteCompactness > 0.28f && f.detailDensity < 0.32f) r.add(0.14f, "плоская графика");
        if (f.hasNaturalSceneTexture()) r.sub(0.30f, "натуральная сцена, не логотип");
        if (f.skinRatio > 0.06f) r.sub(0.18f, "есть кожа/персонаж");
        if (f.textLineCount >= 3) r.sub(0.18f, "много текста");
        return r.finish(0.52f);
    }

    private static EvidenceResult vectorFlat(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("vector_flat");
        if (f.paletteCompactness > 0.24f) r.add(0.18f, "плоские цвета");
        if (f.saturation > 0.12f) r.add(0.14f, "чистый цвет");
        if (f.detailDensity < 0.28f) r.add(0.18f, "мало фото-деталей");
        if (f.edgeDensity > 0.09f) r.add(0.08f, "чистые границы");
        if (f.hasNaturalSceneTexture()) r.sub(0.22f, "слишком много натуральной текстуры");
        return r.finish(0.42f);
    }

    private static EvidenceResult lineartSketch(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("lineart_sketch");
        if (f.whitePaperRatio > 0.36f) r.add(0.22f, "светлая бумага");
        if (f.edgeDensity > 0.12f && f.saturation < 0.18f) r.add(0.26f, "линии без сильного цвета");
        if (f.blackInkRatio > 0.04f) r.add(0.10f, "тёмные линии");
        if (f.saturation > 0.26f) r.sub(0.18f, "слишком цветное");
        if (f.hasNaturalSceneTexture()) r.sub(0.14f, "похоже на фото/сцену");
        return r.finish(0.45f);
    }

    private static EvidenceResult digitalPainting(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("digital_painting_concept");
        if (f.saturation > 0.08f) r.add(0.14f, "цветная иллюстрация");
        if (f.edgeDensity > 0.10f) r.add(0.12f, "контуры/мазки");
        if (f.detailDensity > 0.14f) r.add(0.16f, "детали/мазки");
        if (f.paletteCompactness < 0.45f) r.add(0.12f, "сложная палитра");
        if (f.hasCharacterSignal()) r.add(0.08f, "может быть персонажный арт");
        if (f.ui.panelScore > 0.60f && f.textLineCount >= 3) r.sub(0.18f, "UI сильнее арта");
        return r.finish(0.44f);
    }

    private static EvidenceResult photoGeneral(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("photo_general");
        if (f.detailDensity > 0.18f) r.add(0.18f, "фото-детали");
        if (f.paletteCompactness < 0.36f) r.add(0.14f, "много оттенков");
        if (f.glyphRatio < 0.42f) r.add(0.08f, "не символ");
        if (f.saturation < 0.24f) r.add(0.08f, "умеренный цвет");
        if (f.paletteCompactness > 0.50f) r.sub(0.14f, "слишком плоско");
        return r.finish(0.40f);
    }

    private static EvidenceResult texturePattern(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("texture_pattern");
        if (f.detailDensity > 0.28f) r.add(0.22f, "мелкая детализация");
        if (f.centralObjectRatio < 0.10f) r.add(0.14f, "нет главного объекта");
        if (f.skinRatio < 0.04f && f.textLineCount < 2) r.add(0.10f, "нет текста/персонажа");
        if (f.largestComponentRatio > 0.05f) r.sub(0.14f, "есть крупный объект");
        return r.finish(0.36f);
    }

    private static EvidenceResult patternSeamless(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("pattern_seamless");
        if (f.detailDensity > 0.22f && f.centralObjectRatio < 0.08f) r.add(0.18f, "равномерные детали");
        if (f.paletteCompactness > 0.18f && f.paletteCompactness < 0.48f) r.add(0.08f, "повторяемая палитра");
        if (f.largestComponentRatio > 0.035f) r.sub(0.18f, "есть главный объект");
        if (f.skinRatio > 0.04f || f.textLineCount >= 2) r.sub(0.16f, "персонаж/текст мешает паттерну");
        return r.finish(0.32f);
    }

    private static EvidenceResult vfxGlow(ImageFeatures f) {
        EvidenceResult r = new EvidenceResult("vfx_glow_magic");
        if (f.brightRatio > 0.12f && f.darkRatio > 0.22f) r.add(0.20f, "яркие эффекты на тёмном");
        if (f.saturation > 0.18f && f.edgeDensity < 0.22f) r.add(0.12f, "цветное свечение");
        if (f.skinRatio > 0.08f) r.sub(0.12f, "скорее персонаж");
        if (f.ui.panelScore > 0.42f) r.sub(0.10f, "может быть UI");
        return r.finish(0.32f);
    }

    private static void resolveConflicts(ImageFeatures f, List<EvidenceResult> r) {
        EvidenceResult ui = find(r, "ui_screenshot");
        EvidenceResult game = find(r, "game_engine_ui");
        EvidenceResult scene = find(r, "landscape_environment");
        EvidenceResult arch = find(r, "architecture_hardsurface");
        EvidenceResult anime = find(r, "anime_manga");
        EvidenceResult portrait = find(r, "portrait_character");
        EvidenceResult logo = find(r, "logo_icon");
        if (f.hasNaturalSceneTexture() && f.textLineCount < 3) {
            ui.sub(0.16f, "resolver: scene texture beats weak UI text");
            game.sub(0.20f, "resolver: no real editor labels");
            scene.add(0.10f, "resolver: natural scene wins");
            arch.add(0.08f, "resolver: hard-surface scene possible");
        }
        if (f.hasCharacterSignal()) {
            ui.sub(0.18f, "resolver: character signal");
            game.sub(0.18f, "resolver: character signal");
            anime.add(0.08f, "resolver: character/anime candidate");
            portrait.add(0.06f, "resolver: character candidate");
        }
        if (f.textLineCount >= 4 && f.ui.panelScore > 0.44f && !f.hasNaturalSceneTexture()) {
            ui.add(0.10f, "resolver: real UI layout");
            game.add(0.06f, "resolver: real UI layout");
        }
        if (f.hasNaturalSceneTexture()) logo.sub(0.20f, "resolver: scene texture rejects logo");
        for (EvidenceResult e : r) e.finish(e.confidence);
    }

    private static EvidenceResult find(List<EvidenceResult> list, String name) {
        for (EvidenceResult r : list) if (r.className.equals(name)) return r;
        EvidenceResult fallback = new EvidenceResult(name);
        list.add(fallback);
        return fallback;
    }

    public static final class Decision {
        public final List<EvidenceResult> ranked;
        public final String top1;
        public final String top2;
        public final String top3;
        public final float confidence;
        public final String strategy;
        public final String warnings;

        Decision(List<EvidenceResult> ranked) {
            this.ranked = ranked;
            this.top1 = ranked.size() > 0 ? ranked.get(0).className : "photo_general";
            this.top2 = ranked.size() > 1 ? ranked.get(1).className : "photo_general";
            this.top3 = ranked.size() > 2 ? ranked.get(2).className : "photo_general";
            this.confidence = ranked.size() > 0 ? ranked.get(0).confidence : 0.35f;
            this.strategy = strategyFor(top1);
            this.warnings = buildWarnings(ranked);
        }

        private static String buildWarnings(List<EvidenceResult> ranked) {
            StringBuilder b = new StringBuilder("multiEvidence top3: ");
            for (int i = 0; i < ranked.size() && i < 3; i++) {
                if (i > 0) b.append(" | ");
                EvidenceResult e = ranked.get(i);
                b.append(e.compact()).append(" [").append(e.whyShort()).append(']');
            }
            return b.toString();
        }

        private static String strategyFor(String genre) {
            if (genre.equals("game_engine_ui")) return "viewport -> panels -> toolbar -> icons -> labels -> polish";
            if (genre.equals("ui_screenshot")) return "background -> panels -> cards/buttons -> icons -> text -> polish";
            if (genre.equals("text_document")) return "paper/background -> text rows -> headings -> small marks -> polish";
            if (genre.equals("anime_manga")) return "background -> silhouette -> skin/hair/clothes -> lineart -> face -> details";
            if (genre.equals("portrait_character")) return "background -> head/body mass -> skin -> hair/clothes -> face -> polish";
            if (genre.equals("human_body_fullbody")) return "background -> full silhouette -> body/clothes -> face/hands -> details";
            if (genre.equals("landscape_environment")) return "sky/far background -> terrain/trees/water -> main objects -> shadows -> details";
            if (genre.equals("architecture_hardsurface")) return "large building masses -> perspective edges -> windows/doors -> shadows -> details";
            if (genre.equals("logo_icon")) return "background -> main symbol -> inner cuts -> glow/accent -> crisp edges";
            if (genre.equals("vector_flat")) return "large flat shapes -> medium shapes -> clean edges -> accents";
            if (genre.equals("lineart_sketch")) return "main contours -> structure lines -> secondary details -> cleanup";
            if (genre.equals("texture_pattern") || genre.equals("pattern_seamless")) return "base color -> repeated masses -> texture clusters -> avoid micro-noise";
            if (genre.equals("vfx_glow_magic")) return "dark/base mass -> glow cores -> bloom strokes -> sparks -> polish";
            return "background -> large masses -> main subject -> edges -> details";
        }

        public String top3Pipe() { return top1 + "|" + top2 + "|" + top3; }
    }
}
