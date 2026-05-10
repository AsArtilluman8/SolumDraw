package com.solum.draw.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MultiEvidenceAnalyzer {
    private MultiEvidenceAnalyzer() {}

    public static Decision analyze(ImageFeatures f) {
        ArrayList<EvidenceResult> r = new ArrayList<>();
        r.add(uiScreenshot(f)); r.add(gameUi(f)); r.add(gameHud(f)); r.add(textDocument(f)); r.add(diagramChart(f));
        r.add(animeManga(f)); r.add(portraitCharacter(f)); r.add(humanBody(f)); r.add(stickerChibi(f));
        r.add(landscape(f)); r.add(architecture(f)); r.add(isometricArt(f)); r.add(lowPoly(f));
        r.add(logoIcon(f)); r.add(vectorFlat(f)); r.add(lineartSketch(f)); r.add(digitalPainting(f)); r.add(photoGeneral(f));
        r.add(texturePattern(f)); r.add(patternSeamless(f)); r.add(pixelArt(f)); r.add(spriteSheet(f));
        r.add(vfxGlow(f)); r.add(spaceScifiBg(f)); r.add(watercolor(f)); r.add(oilPainting(f)); r.add(pencilDrawing(f));
        r.add(inkWash(f)); r.add(grayscaleInk(f)); r.add(abstractArt(f)); r.add(noisyCompressed(f)); r.add(transparentLayered(f));
        resolveConflicts(f, r);
        Collections.sort(r, new Comparator<EvidenceResult>() { @Override public int compare(EvidenceResult a, EvidenceResult b) { return Float.compare(b.score, a.score); } });
        return new Decision(r);
    }

    private static EvidenceResult uiScreenshot(ImageFeatures f) { EvidenceResult r = new EvidenceResult("ui_screenshot");
        if (f.ui.panelScore > .42f) r.add(.22f,"ровные панели"); else r.sub(.14f,"панели слабые");
        if (f.ui.textScore > .38f && f.textLineCount >= 2) r.add(.22f,"есть строки текста"); else r.sub(.22f,"нет настоящих строк текста");
        if (f.ui.toolbarScore > .38f && f.textLineCount >= 2) r.add(.12f,"toolbar/nav зоны");
        if (f.paletteCompactness > .34f && f.detailDensity < .32f) r.add(.08f,"плоские области");
        if (f.hasNaturalSceneTexture()) r.sub(.38f,"натуральная/сценовая текстура");
        if (f.hasCharacterSignal()) r.sub(.24f,"персонаж/кожа");
        if (f.ui.sceneScore > .42f && f.textLineCount < 3) r.sub(.24f,"viewport без настоящего UI");
        return r.finish(.50f); }

    private static EvidenceResult gameUi(ImageFeatures f) { EvidenceResult r = new EvidenceResult("game_engine_ui");
        if (f.ui.panelScore > .50f && f.textLineCount >= 2) r.add(.16f,"панели редактора + подписи");
        if (f.ui.toolbarScore > .48f && f.textLineCount >= 2) r.add(.14f,"toolbar");
        if (f.ui.sceneScore > .40f && f.ui.panelScore > .48f && f.textLineCount >= 2) r.add(.10f,"viewport внутри UI");
        if (f.ui.textScore > .42f && f.textLineCount >= 3) r.add(.16f,"UI подписи");
        if (f.ui.panelScore < .40f || f.textLineCount < 2) r.sub(.30f,"нет настоящих панелей/текста");
        if (f.hasNaturalSceneTexture()) r.sub(.44f,"натуральная сцена не game UI");
        if (f.hasCharacterSignal()) r.sub(.30f,"персонаж важнее UI");
        return r.finish(.44f); }

    private static EvidenceResult gameHud(ImageFeatures f) { EvidenceResult r = new EvidenceResult("game_ui_hud");
        if (f.ui.panelScore > .42f && f.textLineCount >= 2) r.add(.20f,"HUD панели/текст");
        if (f.ui.toolbarScore > .30f) r.add(.08f,"краевые UI зоны");
        if (f.hasNaturalSceneTexture() && f.textLineCount >= 2) r.add(.08f,"сцена под HUD");
        if (f.textLineCount < 2) r.sub(.20f,"мало HUD текста");
        return r.finish(.36f); }

    private static EvidenceResult textDocument(ImageFeatures f) { EvidenceResult r = new EvidenceResult("text_document");
        if (f.textLineCount >= 5) r.add(.40f,"много строк");
        if (f.realTextRatio > .26f) r.add(.22f,"сильный text score");
        if (f.whitePaperRatio > .35f) r.add(.16f,"светлый фон");
        if (f.skinRatio > .05f) r.sub(.20f,"есть кожа/персонаж");
        if (f.hasNaturalSceneTexture()) r.sub(.24f,"сцена/текстура");
        return r.finish(.48f); }

    private static EvidenceResult diagramChart(ImageFeatures f) { EvidenceResult r = new EvidenceResult("diagram_chart");
        if (f.textLineCount >= 3) r.add(.22f,"подписи");
        if (f.ui.panelScore > .32f && f.edgeDensity > .10f) r.add(.18f,"линии/блоки");
        if (f.whitePaperRatio > .25f) r.add(.10f,"светлый лист");
        if (f.hasNaturalSceneTexture()) r.sub(.26f,"натуральная сцена");
        return r.finish(.36f); }

    private static EvidenceResult animeManga(ImageFeatures f) { EvidenceResult r = new EvidenceResult("anime_manga");
        if (f.skinRatio > .08f) r.add(.30f,"кожа"); else r.sub(.24f,"кожи мало");
        if (f.edgeDensity > .14f || f.glyphRatio > .34f) r.add(.20f,"lineart/контуры");
        if (f.saturation > .08f) r.add(.14f,"цветная стилизация");
        if (f.centralObjectRatio > .10f || f.symmetryVertical > .36f) r.add(.16f,"центральный персонаж/симметрия");
        if (f.hasNaturalSceneTexture() && f.skinRatio < .08f) r.sub(.28f,"сцена без персонажа");
        if (f.ui.panelScore > .60f && f.skinRatio < .07f) r.sub(.20f,"UI сильнее персонажа");
        return r.finish(.56f); }

    private static EvidenceResult portraitCharacter(ImageFeatures f) { EvidenceResult r = new EvidenceResult("portrait_character");
        if (f.skinRatio > .11f) r.add(.30f,"много кожи"); else r.sub(.26f,"кожи мало");
        if (f.centralObjectRatio > .12f) r.add(.22f,"центральный объект");
        if (f.symmetryVertical > .38f) r.add(.12f,"симметрия лица/тела");
        if (f.edgeDensity > .10f) r.add(.10f,"читаемые контуры");
        if (f.hasNaturalSceneTexture() && f.skinRatio < .09f) r.sub(.20f,"сцена без портрета");
        return r.finish(.52f); }

    private static EvidenceResult humanBody(ImageFeatures f) { EvidenceResult r = new EvidenceResult("human_body_fullbody");
        if (f.skinRatio > .08f) r.add(.20f,"кожа"); else r.sub(.24f,"нет тела/кожи");
        if (f.centralObjectRatio > .10f && f.largestComponentRatio > .010f) r.add(.18f,"крупная фигура");
        if (f.sourceHeight > f.sourceWidth) r.add(.08f,"вертикальный кадр");
        if (f.skinRatio > .16f && f.edgeDensity > .12f) r.add(.14f,"тело/силуэт");
        return r.finish(.42f); }

    private static EvidenceResult stickerChibi(ImageFeatures f) { EvidenceResult r = new EvidenceResult("sticker_chibi");
        if (f.hasCharacterSignal()) r.add(.20f,"персонаж");
        if (f.paletteCompactness > .18f) r.add(.10f,"плоская стилизация");
        if (f.whitePaperRatio > .20f || f.brightRatio > .20f) r.add(.08f,"чистый фон");
        if (f.skinRatio < .04f) r.sub(.18f,"нет персонажа");
        return r.finish(.34f); }

    private static EvidenceResult landscape(ImageFeatures f) { EvidenceResult r = new EvidenceResult("landscape_environment");
        if (f.hasNaturalSceneTexture()) r.add(.36f,"природная/сценовая текстура");
        if (f.detailDensity > .18f) r.add(.18f,"детали окружения");
        if (f.paletteCompactness < .42f) r.add(.14f,"много оттенков");
        if (f.skinRatio < .07f) r.add(.10f,"персонаж не доминирует");
        if (f.ui.panelScore > .58f && f.textLineCount >= 3) r.sub(.18f,"реальный UI сильный");
        if (f.skinRatio > .14f && f.centralObjectRatio > .12f) r.sub(.20f,"персонаж доминирует");
        return r.finish(.58f); }

    private static EvidenceResult architecture(ImageFeatures f) { EvidenceResult r = new EvidenceResult("architecture_hardsurface");
        if (f.edgeDensity > .11f && f.detailDensity > .14f) r.add(.24f,"жёсткие контуры/детали");
        if (f.largeComponentCount >= 2 || f.componentCount > 24) r.add(.20f,"крупные формы/строения");
        if (f.paletteCompactness < .48f && f.skinRatio < .06f) r.add(.14f,"сцена без персонажа");
        if (f.sourceWidth >= f.sourceHeight && f.skinRatio < .06f) r.add(.08f,"горизонтальный объект/здание");
        if (f.ui.panelScore > .54f && f.textLineCount >= 3) r.sub(.14f,"может быть UI");
        return r.finish(.50f); }

    private static EvidenceResult isometricArt(ImageFeatures f) { EvidenceResult r = new EvidenceResult("isometric_art");
        if (f.edgeDensity > .12f && f.sourceWidth >= f.sourceHeight) r.add(.12f,"жёсткие линии");
        if (f.paletteCompactness > .16f && f.detailDensity < .34f) r.add(.12f,"стилизованная палитра");
        if (f.hasNaturalSceneTexture()) r.sub(.10f,"слишком натурально");
        return r.finish(.30f); }

    private static EvidenceResult lowPoly(ImageFeatures f) { EvidenceResult r = new EvidenceResult("low_poly");
        if (f.paletteCompactness > .12f && f.edgeDensity > .10f) r.add(.12f,"крупные плоскости");
        if (f.detailDensity < .26f) r.add(.10f,"мало деталей");
        if (f.hasNaturalSceneTexture()) r.sub(.12f,"текстура не low-poly");
        return r.finish(.28f); }

    private static EvidenceResult logoIcon(ImageFeatures f) { EvidenceResult r = new EvidenceResult("logo_icon");
        if (f.logoScore > .36f && f.detailDensity < .24f) r.add(.30f,"logo score + простая форма");
        if (f.glyphRatio > .38f && f.centralObjectRatio > .08f && f.detailDensity < .30f) r.add(.18f,"центральный символ/glyph");
        if (f.symmetryVertical > .44f && f.detailDensity < .28f) r.add(.10f,"симметрия");
        if (f.paletteCompactness > .30f && f.detailDensity < .26f) r.add(.14f,"плоская графика");
        if (f.hasNaturalSceneTexture()) r.sub(.50f,"натуральная сцена, не логотип");
        if (f.edgeDensity > .26f && f.componentCount > 26) r.sub(.22f,"слишком много деталей");
        if (f.skinRatio > .06f) r.sub(.20f,"есть кожа/персонаж");
        if (f.textLineCount >= 3) r.sub(.20f,"много текста");
        return r.finish(.46f); }

    private static EvidenceResult vectorFlat(ImageFeatures f) { EvidenceResult r = new EvidenceResult("vector_flat");
        if (f.paletteCompactness > .24f) r.add(.18f,"плоские цвета");
        if (f.saturation > .12f) r.add(.12f,"чистый цвет");
        if (f.detailDensity < .24f) r.add(.18f,"мало фото-деталей");
        if (f.edgeDensity > .09f && f.edgeDensity < .26f) r.add(.08f,"чистые границы");
        if (f.hasNaturalSceneTexture()) r.sub(.36f,"натуральная текстура");
        if (f.detailDensity > .34f) r.sub(.18f,"слишком много деталей");
        return r.finish(.40f); }

    private static EvidenceResult lineartSketch(ImageFeatures f) { EvidenceResult r = new EvidenceResult("lineart_sketch"); if (f.whitePaperRatio > .36f) r.add(.22f,"светлая бумага"); if (f.edgeDensity > .12f && f.saturation < .18f) r.add(.26f,"линии без цвета"); if (f.blackInkRatio > .04f) r.add(.10f,"тёмные линии"); if (f.saturation > .26f) r.sub(.18f,"слишком цветное"); if (f.hasNaturalSceneTexture()) r.sub(.14f,"фото/сцена"); return r.finish(.45f); }
    private static EvidenceResult digitalPainting(ImageFeatures f) { EvidenceResult r = new EvidenceResult("digital_painting_concept"); if (f.saturation > .08f) r.add(.14f,"цветная иллюстрация"); if (f.edgeDensity > .10f) r.add(.12f,"контуры/мазки"); if (f.detailDensity > .14f) r.add(.16f,"детали/мазки"); if (f.paletteCompactness < .48f) r.add(.12f,"сложная палитра"); if (f.hasCharacterSignal()) r.add(.08f,"персонажный арт"); if (f.ui.panelScore > .60f && f.textLineCount >= 3) r.sub(.18f,"UI сильнее"); return r.finish(.46f); }
    private static EvidenceResult photoGeneral(ImageFeatures f) { EvidenceResult r = new EvidenceResult("photo_general"); if (f.detailDensity > .18f) r.add(.18f,"фото-детали"); if (f.paletteCompactness < .36f) r.add(.14f,"много оттенков"); if (f.glyphRatio < .42f) r.add(.08f,"не символ"); if (f.saturation < .24f) r.add(.08f,"умеренный цвет"); if (f.paletteCompactness > .50f) r.sub(.14f,"слишком плоско"); return r.finish(.40f); }
    private static EvidenceResult texturePattern(ImageFeatures f) { EvidenceResult r = new EvidenceResult("texture_pattern"); if (f.detailDensity > .28f) r.add(.22f,"мелкая детализация"); if (f.centralObjectRatio < .10f) r.add(.14f,"нет главного объекта"); if (f.skinRatio < .04f && f.textLineCount < 2) r.add(.10f,"нет текста/персонажа"); if (f.largestComponentRatio > .05f) r.sub(.14f,"есть объект"); return r.finish(.36f); }
    private static EvidenceResult patternSeamless(ImageFeatures f) { EvidenceResult r = new EvidenceResult("pattern_seamless"); if (f.detailDensity > .22f && f.centralObjectRatio < .08f) r.add(.18f,"равномерные детали"); if (f.paletteCompactness > .18f && f.paletteCompactness < .48f) r.add(.08f,"повторяемая палитра"); if (f.largestComponentRatio > .035f) r.sub(.18f,"есть объект"); if (f.skinRatio > .04f || f.textLineCount >= 2) r.sub(.16f,"персонаж/текст"); return r.finish(.32f); }
    private static EvidenceResult pixelArt(ImageFeatures f) { EvidenceResult r = new EvidenceResult("pixel_art"); if (f.paletteCompactness > .18f && f.detailDensity < .34f) r.add(.18f,"ограниченная палитра"); if (f.edgeDensity > .10f && f.edgeDensity < .30f) r.add(.10f,"ступенчатые границы"); if (f.sourceWidth < 512 || f.sourceHeight < 512) r.add(.10f,"малый размер"); if (f.hasNaturalSceneTexture()) r.sub(.20f,"слишком натурально"); return r.finish(.32f); }
    private static EvidenceResult spriteSheet(ImageFeatures f) { EvidenceResult r = new EvidenceResult("sprite_sheet"); if (f.ui.panelScore > .35f && f.componentCount > 20) r.add(.18f,"сеточные элементы"); if (f.sourceWidth > f.sourceHeight) r.add(.06f,"широкий лист"); if (f.textLineCount > 2) r.sub(.12f,"текст вместо спрайтов"); return r.finish(.28f); }
    private static EvidenceResult vfxGlow(ImageFeatures f) { EvidenceResult r = new EvidenceResult("vfx_glow_magic"); if (f.brightRatio > .12f && f.darkRatio > .22f) r.add(.20f,"яркое на тёмном"); if (f.saturation > .18f && f.edgeDensity < .22f) r.add(.12f,"цветное свечение"); if (f.skinRatio > .08f) r.sub(.12f,"скорее персонаж"); if (f.ui.panelScore > .42f) r.sub(.10f,"может быть UI"); return r.finish(.32f); }
    private static EvidenceResult spaceScifiBg(ImageFeatures f) { EvidenceResult r = new EvidenceResult("space_scifi_bg"); if (f.darkRatio > .35f && f.brightRatio > .04f) r.add(.16f,"тёмный фон + свет"); if (f.saturation > .08f) r.add(.08f,"sci-fi цвет"); if (f.skinRatio > .05f) r.sub(.12f,"кожа"); return r.finish(.30f); }
    private static EvidenceResult watercolor(ImageFeatures f) { EvidenceResult r = new EvidenceResult("watercolor_paint"); if (f.saturation < .22f && f.brightRatio > .10f) r.add(.14f,"мягкие тона"); if (f.edgeDensity < .20f && f.detailDensity > .10f) r.add(.12f,"мягкие края"); if (f.blackInkRatio > .08f) r.sub(.12f,"слишком графично"); return r.finish(.32f); }
    private static EvidenceResult oilPainting(ImageFeatures f) { EvidenceResult r = new EvidenceResult("oil_painting"); if (f.detailDensity > .18f && f.edgeDensity < .24f) r.add(.14f,"мазки/мягкие формы"); if (f.saturation > .06f && f.paletteCompactness < .48f) r.add(.12f,"живописная палитра"); if (f.ui.panelScore > .50f) r.sub(.12f,"похоже на UI"); return r.finish(.30f); }
    private static EvidenceResult pencilDrawing(ImageFeatures f) { EvidenceResult r = new EvidenceResult("pencil_drawing"); if (f.saturation < .10f && f.edgeDensity > .10f) r.add(.20f,"монохромные линии"); if (f.whitePaperRatio > .20f) r.add(.10f,"бумага"); if (f.saturation > .20f) r.sub(.18f,"цветное"); return r.finish(.34f); }
    private static EvidenceResult inkWash(ImageFeatures f) { EvidenceResult r = new EvidenceResult("ink_wash"); if (f.saturation < .12f && f.darkRatio > .12f && f.brightRatio > .12f) r.add(.18f,"тушь/размыв"); if (f.edgeDensity < .24f) r.add(.08f,"мягкая графика"); if (f.saturation > .20f) r.sub(.16f,"цветное"); return r.finish(.32f); }
    private static EvidenceResult grayscaleInk(ImageFeatures f) { EvidenceResult r = new EvidenceResult("grayscale_ink"); if (f.saturation < .08f && f.edgeDensity > .10f) r.add(.18f,"ч/б графика"); if (f.blackInkRatio > .04f) r.add(.08f,"чёрные линии"); if (f.saturation > .18f) r.sub(.16f,"цветное"); return r.finish(.30f); }
    private static EvidenceResult abstractArt(ImageFeatures f) { EvidenceResult r = new EvidenceResult("abstract_art"); if (f.centralObjectRatio < .10f && f.saturation > .10f) r.add(.16f,"нет главного объекта + цвет"); if (f.detailDensity > .16f) r.add(.10f,"абстрактные детали"); if (f.skinRatio > .08f || f.textLineCount > 2) r.sub(.14f,"персонаж/текст"); return r.finish(.30f); }
    private static EvidenceResult noisyCompressed(ImageFeatures f) { EvidenceResult r = new EvidenceResult("noisy_compressed"); if (f.detailDensity > .32f && f.edgeDensity > .22f) r.add(.16f,"шум/детали"); if (f.paletteCompactness < .20f) r.add(.08f,"много цветов"); if (f.centralObjectRatio > .14f) r.sub(.10f,"есть объект"); return r.finish(.24f); }
    private static EvidenceResult transparentLayered(ImageFeatures f) { EvidenceResult r = new EvidenceResult("transparent_layered"); if (f.brightRatio > .20f && f.centralObjectRatio > .08f) r.add(.10f,"объект на чистом фоне"); if (f.paletteCompactness > .22f) r.add(.08f,"плоский фон"); if (f.hasNaturalSceneTexture()) r.sub(.18f,"натуральная сцена"); return r.finish(.24f); }

    private static void resolveConflicts(ImageFeatures f, List<EvidenceResult> r) {
        EvidenceResult ui=find(r,"ui_screenshot"), game=find(r,"game_engine_ui"), hud=find(r,"game_ui_hud"), scene=find(r,"landscape_environment"), arch=find(r,"architecture_hardsurface"), anime=find(r,"anime_manga"), portrait=find(r,"portrait_character"), logo=find(r,"logo_icon"), vector=find(r,"vector_flat"), painting=find(r,"digital_painting_concept");
        if (f.hasNaturalSceneTexture()) { ui.sub(.18f,"resolver: scene beats weak UI"); game.sub(.24f,"resolver: no real editor labels"); hud.sub(.12f,"resolver: scene not HUD"); logo.sub(.30f,"resolver: scene rejects logo"); vector.sub(.18f,"resolver: scene rejects vector"); scene.add(.12f,"resolver: natural scene wins"); arch.add(.08f,"resolver: hard-surface scene possible"); painting.add(.06f,"resolver: painted scene possible"); if (f.skinRatio < .07f) { anime.sub(.18f,"resolver: no skin"); portrait.sub(.18f,"resolver: no skin"); } }
        if (f.hasCharacterSignal()) { ui.sub(.18f,"resolver: character signal"); game.sub(.18f,"resolver: character signal"); anime.add(.10f,"resolver: anime/person candidate"); portrait.add(.08f,"resolver: person candidate"); scene.sub(.06f,"resolver: character dominates"); }
        if (f.textLineCount >= 4 && f.ui.panelScore > .44f && !f.hasNaturalSceneTexture()) { ui.add(.10f,"resolver: real UI layout"); game.add(.05f,"resolver: real UI layout"); }
        for (EvidenceResult e : r) e.finish(e.confidence);
    }
    private static EvidenceResult find(List<EvidenceResult> list, String name) { for (EvidenceResult r : list) if (r.className.equals(name)) return r; EvidenceResult x = new EvidenceResult(name); list.add(x); return x; }

    public static final class Decision { public final List<EvidenceResult> ranked; public final String top1, top2, top3; public final float confidence; public final String strategy, warnings; Decision(List<EvidenceResult> ranked) { this.ranked=ranked; top1=ranked.size()>0?ranked.get(0).className:"photo_general"; top2=ranked.size()>1?ranked.get(1).className:"photo_general"; top3=ranked.size()>2?ranked.get(2).className:"photo_general"; confidence=ranked.size()>0?ranked.get(0).confidence:.35f; strategy=strategyFor(top1); warnings=buildWarnings(ranked); }
        private static String buildWarnings(List<EvidenceResult> ranked) { StringBuilder b=new StringBuilder("multiEvidence top3: "); for(int i=0;i<ranked.size()&&i<3;i++){ if(i>0)b.append(" | "); EvidenceResult e=ranked.get(i); b.append(e.compact()).append(" [").append(e.whyShort()).append(']'); } return b.toString(); }
        private static String strategyFor(String g) { if (g.equals("game_engine_ui")||g.equals("ui_screenshot")||g.equals("game_ui_hud")) return "фон -> панели -> карточки -> иконки -> текст"; if (g.equals("text_document")||g.equals("diagram_chart")) return "фон -> строки/блоки -> заголовки -> мелкие знаки"; if (g.equals("anime_manga")||g.equals("portrait_character")||g.equals("human_body_fullbody")||g.equals("sticker_chibi")) return "фон -> силуэт -> кожа/волосы/одежда -> лицо -> детали"; if (g.equals("landscape_environment")) return "небо/дальний фон -> земля/вода/лес -> главный объект -> тени -> детали"; if (g.equals("architecture_hardsurface")||g.equals("isometric_art")||g.equals("low_poly")) return "крупные массы -> перспектива/грани -> окна/двери -> тени -> детали"; if (g.equals("logo_icon")) return "фон -> знак -> вырезы -> glow/accent -> края"; if (g.equals("vector_flat")) return "крупные плоские формы -> средние формы -> чистые края -> акценты"; if (g.equals("lineart_sketch")||g.equals("pencil_drawing")||g.equals("ink_wash")||g.equals("grayscale_ink")) return "главные контуры -> структура -> вторичные детали -> очистка"; if (g.equals("texture_pattern")||g.equals("pattern_seamless")||g.equals("noisy_compressed")) return "base color -> repeated masses -> texture clusters -> ignore micro-noise"; if (g.equals("vfx_glow_magic")||g.equals("space_scifi_bg")) return "dark/base mass -> glow cores -> bloom strokes -> sparks -> polish"; return "фон -> крупные массы -> главный объект -> края -> детали"; }
        public String top3Pipe(){return top1+"|"+top2+"|"+top3;}
    }
}
