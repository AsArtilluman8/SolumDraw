package com.solum.draw.analyze;

import java.util.ArrayList;
import java.util.List;

public final class DrawingIntentAnalysis {
    public final float uiEvidence;
    public final float characterEvidence;
    public final float portraitEvidence;
    public final float sceneEvidence;
    public final float logoEvidence;
    public final float sketchEvidence;
    public final float vectorEvidence;
    public final float textEvidence;
    public final String primaryIntent;
    public final String strategy;
    public final int estimatedUsefulActions;
    public final int noiseIgnoreLevel;
    public final List<String> semanticLayers;
    public final List<String> drawOrder;
    public final List<String> budgetHints;
    public final List<String> risks;

    private DrawingIntentAnalysis(float uiEvidence, float characterEvidence, float portraitEvidence,
            float sceneEvidence, float logoEvidence, float sketchEvidence, float vectorEvidence,
            float textEvidence, String primaryIntent, String strategy, int estimatedUsefulActions,
            int noiseIgnoreLevel, List<String> semanticLayers, List<String> drawOrder,
            List<String> budgetHints, List<String> risks) {
        this.uiEvidence = uiEvidence;
        this.characterEvidence = characterEvidence;
        this.portraitEvidence = portraitEvidence;
        this.sceneEvidence = sceneEvidence;
        this.logoEvidence = logoEvidence;
        this.sketchEvidence = sketchEvidence;
        this.vectorEvidence = vectorEvidence;
        this.textEvidence = textEvidence;
        this.primaryIntent = primaryIntent;
        this.strategy = strategy;
        this.estimatedUsefulActions = estimatedUsefulActions;
        this.noiseIgnoreLevel = noiseIgnoreLevel;
        this.semanticLayers = semanticLayers;
        this.drawOrder = drawOrder;
        this.budgetHints = budgetHints;
        this.risks = risks;
    }

    public static DrawingIntentAnalysis build(ImageAnalysis a, AnalysisLayers layers, ComponentRoleMap roles, UiLayoutAnalysis ui) {
        float centeredSubject = clamp01(a.centralObjectRatio * 2.10f + a.symmetryVertical * 0.22f + a.largestComponentRatio * 1.60f);
        float naturalTexture = clamp01(a.detailDensity * 0.75f + a.edgeDensity * 0.32f + Math.max(0f, 0.24f - a.paletteCompactness) * 0.35f);
        float flatGraphic = clamp01(a.paletteCompactness * 0.85f + Math.max(0f, 0.22f - a.detailDensity) + Math.max(0f, a.saturation - 0.16f) * 0.40f);
        float realUiControls = clamp01(ui.panelScore * 0.33f + ui.toolbarScore * 0.29f + ui.textScore * 0.16f + Math.min(0.26f, a.textLineCount * 0.018f));
        float falseUiPenalty = clamp01(centeredSubject * 0.34f + naturalTexture * 0.28f + a.skinRatio * 1.25f + Math.max(0f, a.glyphRatio - 0.45f) * 0.20f);
        float falseLogoPenalty = clamp01(naturalTexture * 0.48f + ui.uiScore * 0.16f + a.detailDensity * 0.34f + Math.max(0f, a.componentCount - 18) / 80f);

        float uiEvidence = clamp01(realUiControls * 0.75f + (a.genre.equals("game_engine_ui") || a.genre.equals("ui_screenshot") ? 0.18f : 0f) - falseUiPenalty * 0.58f);
        float textEvidence = clamp01((a.realTextRatio * 0.70f) + Math.min(0.28f, a.textLineCount * 0.032f) + ui.textScore * 0.11f);
        float logoEvidence = clamp01(a.logoScore * 0.58f + a.glyphRatio * 0.20f + flatGraphic * 0.20f + (a.genre.equals("logo_icon_flat") ? 0.18f : 0f) - falseLogoPenalty * 0.55f);
        float sketchEvidence = clamp01((a.genre.equals("sketch_lineart") ? 0.40f : 0f) + a.edgeDensity * 0.55f + Math.max(0f, 0.24f - a.saturation));
        float vectorEvidence = clamp01((a.genre.equals("vector_flat_art") ? 0.40f : 0f) + flatGraphic * 0.62f - naturalTexture * 0.24f);
        float portraitEvidence = clamp01((a.genre.equals("portrait_or_skin_photo") ? 0.35f : 0f) + a.skinRatio * 2.25f + centeredSubject * 0.22f + a.symmetryVertical * 0.14f - uiEvidence * 0.10f);
        float characterEvidence = clamp01((a.genre.equals("anime_cartoon_flat") ? 0.34f : 0f) + centeredSubject * 0.38f + a.saturation * 0.20f + Math.max(0f, a.glyphRatio - 0.25f) * 0.10f + a.skinRatio * 0.90f - uiEvidence * 0.14f);
        float sceneEvidence = clamp01((a.genre.equals("dark_cinematic") || a.genre.equals("digital_art_wallpaper") || a.genre.equals("general_photo_or_illustration") || a.genre.equals("detailed_noisy_photo") ? 0.22f : 0f) + naturalTexture * 0.52f + Math.max(0f, 0.36f - ui.panelScore) * 0.20f + ui.sceneScore * 0.14f - flatGraphic * 0.10f);

        String intent = pickIntent(uiEvidence, characterEvidence, portraitEvidence, sceneEvidence, logoEvidence, sketchEvidence, vectorEvidence, textEvidence);
        List<String> layersOut = semanticLayersFor(intent);
        List<String> order = drawOrderFor(intent);
        List<String> hints = hintsFor(intent, a);
        List<String> risks = risksFor(intent, a, ui, uiEvidence, characterEvidence, sceneEvidence, logoEvidence, textEvidence, falseUiPenalty, falseLogoPenalty);
        String strategy = strategyFor(intent);
        int actions = estimateActions(intent, a);
        int noiseIgnore = estimateNoiseIgnore(a);
        return new DrawingIntentAnalysis(uiEvidence, characterEvidence, portraitEvidence, sceneEvidence,
                logoEvidence, sketchEvidence, vectorEvidence, textEvidence, intent, strategy, actions,
                noiseIgnore, layersOut, order, hints, risks);
    }

    private static String pickIntent(float ui, float character, float portrait, float scene, float logo, float sketch, float vector, float text) {
        if (character > 0.34f && character >= ui * 0.72f && character >= logo * 0.86f) return "character_silhouette_first";
        if (portrait > 0.36f && portrait >= ui * 0.70f && portrait >= logo * 0.82f) return "portrait_mass_first";
        if (scene > 0.34f && scene >= logo * 0.76f && scene >= ui * 0.64f) return "scene_mass_first";
        String best = "general_layered_drawing";
        float score = 0f;
        if (ui > score) { score = ui; best = ui > 0.66f ? "ui_layout_first" : "ui_candidate_review"; }
        if (character > score) { score = character; best = "character_silhouette_first"; }
        if (portrait > score) { score = portrait; best = "portrait_mass_first"; }
        if (scene > score) { score = scene; best = "scene_mass_first"; }
        if (logo > score) { score = logo; best = "logo_shape_first"; }
        if (sketch > score) { score = sketch; best = "contour_lineart_first"; }
        if (vector > score) { score = vector; best = "flat_shape_first"; }
        if (text > score && text > 0.62f) best = "text_layout_first";
        return best;
    }

    private static List<String> semanticLayersFor(String intent) {
        ArrayList<String> s = new ArrayList<>();
        s.add("BACKGROUND");
        if (intent.contains("ui") || intent.equals("text_layout_first")) {
            add(s, "SCENE_OR_APP_BACKGROUND", "LARGE_UI_PANELS", "CARDS_OR_BUTTONS", "ICONS", "TEXT_LABELS", "FOCUS_CURSOR_OR_GIZMO", "IGNORE_DECORATIVE_NOISE");
        } else if (intent.equals("character_silhouette_first")) {
            add(s, "MAIN_CHARACTER_SILHOUETTE", "SKIN_OR_FACE_REGION", "HAIR_OR_HEAD_MASS", "CLOTHES_COLOR_MASS", "OUTER_CONTOUR", "FACE_DETAILS", "HIGHLIGHTS", "IGNORE_TEXTURE_NOISE");
        } else if (intent.equals("portrait_mass_first")) {
            add(s, "HEAD_SHAPE", "SKIN_LIGHT_SHADOW_MASS", "HAIR_MASS", "EYES_NOSE_MOUTH", "SOFT_SHADOWS", "EDGE_ACCENTS", "IGNORE_SENSOR_NOISE");
        } else if (intent.equals("logo_shape_first")) {
            add(s, "BACKGROUND_SHAPE", "GLOW_OR_ACCENT_MASS", "MAIN_SYMBOL", "INNER_CUTS", "CRISP_EDGES", "HIGHLIGHTS", "IGNORE_COMPRESSION_NOISE");
        } else if (intent.equals("contour_lineart_first")) {
            add(s, "PRIMARY_CONTOURS", "STRUCTURE_LINES", "SECONDARY_DETAILS", "CROSS_LINES", "CLEANUP", "IGNORE_PAPER_NOISE");
        } else if (intent.equals("flat_shape_first")) {
            add(s, "LARGE_FLAT_COLOR_REGIONS", "MEDIUM_SHAPES", "CLEAN_BOUNDARIES", "ACCENT_SHAPES", "SMALL_DETAILS");
        } else if (intent.equals("scene_mass_first")) {
            add(s, "SKY_OR_FAR_BACKGROUND", "GROUND_OR_ARCHITECTURE_MASS", "PERSPECTIVE_LINES", "MAIN_OBJECTS", "LIGHT_AND_SHADOW", "DETAIL_CLUSTERS", "IGNORE_TINY_TEXTURE_NOISE");
        } else {
            add(s, "LARGE_COLOR_MASS", "MAIN_SUBJECT", "OUTER_CONTOUR", "INNER_DETAIL", "HIGHLIGHT", "IGNORE_NOISE");
        }
        return s;
    }

    private static List<String> drawOrderFor(String intent) {
        ArrayList<String> o = new ArrayList<>();
        if (intent.contains("ui") || intent.equals("text_layout_first")) add(o, "background/app backdrop", "main viewport or app content", "large panels", "cards/buttons", "icons", "text labels", "cursor/gizmo", "polish");
        else if (intent.equals("character_silhouette_first")) add(o, "background", "body/head silhouette", "hair and clothing masses", "outer contour", "face/eyes priority details", "accent marks", "highlights", "polish");
        else if (intent.equals("portrait_mass_first")) add(o, "background", "head/skin masses", "hair mass", "facial structure", "eyes/nose/mouth", "soft shadows", "edge accents", "polish");
        else if (intent.equals("logo_shape_first")) add(o, "background shape", "glow/accent mass", "main symbol", "inner cuts", "crisp edges", "highlights", "texture/noise only if useful");
        else if (intent.equals("contour_lineart_first")) add(o, "main contours", "big structure lines", "secondary contours", "small details", "cleanup", "polish");
        else if (intent.equals("flat_shape_first")) add(o, "largest flat shapes", "medium color regions", "clean boundaries", "accent shapes", "small details", "polish");
        else if (intent.equals("scene_mass_first")) add(o, "far background/atmosphere", "large tree/building/terrain masses", "depth and perspective structure", "main subject silhouette", "light/shadow blocks", "detail clusters", "polish");
        else add(o, "background", "large masses", "main subject", "outer contour", "inner detail", "highlight", "polish");
        return o;
    }

    private static List<String> hintsFor(String intent, ImageAnalysis a) {
        ArrayList<String> h = new ArrayList<>();
        h.add("Avoid printer/zigzag scanline path unless intentionally filling a flat background.");
        h.add("Prefer semantic strokes: contour, mass fill stroke, detail stroke, highlight stroke.");
        if (a.detailDensity > 0.40f) h.add("High detail: spend budget only on clustered details, not random texture noise.");
        if (a.edgeDensity > 0.18f) h.add("Preserve strong contours; they carry identity and object readability.");
        if (intent.contains("ui")) h.add("Use layout-first strokes: panels and boxes before labels/icons.");
        if (intent.contains("character") || intent.contains("portrait")) h.add("Preserve face/head/outer silhouette before clothing or background details.");
        if (intent.contains("logo")) h.add("Keep edges crisp; do not bridge separate cuts if bridge would remain visible.");
        if (intent.contains("scene")) h.add("Use atmospheric layers: far background first, close detail last.");
        return h;
    }

    private static List<String> risksFor(String intent, ImageAnalysis a, UiLayoutAnalysis ui, float uiEv, float charEv, float sceneEv, float logoEv, float textEv, float falseUiPenalty, float falseLogoPenalty) {
        ArrayList<String> r = new ArrayList<>();
        if (falseUiPenalty > 0.32f) r.add("Anti-UI fired: centered subject/natural texture/skin makes UI classification risky.");
        if (falseLogoPenalty > 0.34f) r.add("Anti-logo fired: natural texture/detail makes logo/symbol classification risky.");
        if (uiEv > 0.48f && (charEv > 0.30f || sceneEv > 0.30f)) r.add("UI false-positive risk: image may be character/scene with text or panel-like shapes.");
        if (textEv > 0.46f && a.textLineCount <= 1) r.add("Text evidence is weak: likely glyph/logo or edge rows, not real text.");
        if (logoEv > 0.38f && (sceneEv > 0.28f || uiEv > 0.38f)) r.add("Logo ambiguity: check if large contrast is natural object, character feature, or app icon.");
        if (a.componentCount > 45) r.add("Many components: risk of wasting action budget on tiny islands.");
        if (a.largestComponentRatio < 0.012f && !intent.contains("ui")) r.add("No strong main component: planner should use large color masses before details.");
        if (r.isEmpty()) r.add("No major routing risk detected; still validate with benchmark.");
        return r;
    }

    private static String strategyFor(String intent) {
        if (intent.equals("ui_layout_first")) return "layout_first";
        if (intent.equals("ui_candidate_review")) return "layout_first_but_verify_false_ui";
        if (intent.equals("text_layout_first")) return "text_and_layout_first";
        if (intent.equals("character_silhouette_first")) return "silhouette_first";
        if (intent.equals("portrait_mass_first")) return "mass_first_face_priority";
        if (intent.equals("logo_shape_first")) return "shape_first_crisp_edges";
        if (intent.equals("contour_lineart_first")) return "contour_first";
        if (intent.equals("flat_shape_first")) return "flat_color_block_first";
        if (intent.equals("scene_mass_first")) return "scene_mass_first";
        return "general_layered";
    }

    private static int estimateActions(String intent, ImageAnalysis a) {
        int base = 70;
        if (intent.contains("ui")) base = 90;
        else if (intent.contains("logo")) base = 55;
        else if (intent.contains("character")) base = 95;
        else if (intent.contains("portrait")) base = 110;
        else if (intent.contains("scene")) base = 125;
        else if (intent.contains("lineart")) base = 75;
        base += Math.min(80, Math.round(a.detailDensity * 90f));
        base += Math.min(40, a.componentCount / 2);
        return Math.max(35, Math.min(240, base));
    }

    private static int estimateNoiseIgnore(ImageAnalysis a) {
        int v = 1;
        if (a.detailDensity > 0.34f) v++;
        if (a.componentCount > 35) v++;
        if (a.largestComponentRatio < 0.02f) v++;
        return Math.max(1, Math.min(5, v));
    }

    public String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        kv(b, "primaryIntent", primaryIntent, true);
        kv(b, "strategy", strategy, true);
        b.append("  \"estimatedUsefulActions\": ").append(estimatedUsefulActions).append(",\n");
        b.append("  \"noiseIgnoreLevel\": ").append(noiseIgnoreLevel).append(",\n");
        b.append("  \"evidence\": {\n");
        ev(b, "ui", uiEvidence, true); ev(b, "character", characterEvidence, true); ev(b, "portrait", portraitEvidence, true); ev(b, "scene", sceneEvidence, true); ev(b, "logo", logoEvidence, true); ev(b, "sketch", sketchEvidence, true); ev(b, "vector", vectorEvidence, true); ev(b, "text", textEvidence, false);
        b.append("  },\n");
        array(b, "semanticLayers", semanticLayers, true);
        array(b, "drawOrder", drawOrder, true);
        array(b, "budgetHints", budgetHints, true);
        array(b, "risks", risks, false);
        b.append("\n}");
        return b.toString();
    }

    public String html() {
        StringBuilder b = new StringBuilder();
        b.append("<table><tr><th>Evidence</th><th>Score</th></tr>");
        row(b, "ui", uiEvidence); row(b, "character", characterEvidence); row(b, "portrait", portraitEvidence); row(b, "scene", sceneEvidence); row(b, "logo", logoEvidence); row(b, "sketch", sketchEvidence); row(b, "vector", vectorEvidence); row(b, "text", textEvidence);
        b.append("</table><p><b>Intent:</b> ").append(esc(primaryIntent)).append(" / ").append(esc(strategy)).append("</p>");
        b.append("<p><b>Budget:</b> ~").append(estimatedUsefulActions).append(" useful actions, noiseIgnore=").append(noiseIgnoreLevel).append("/5</p>");
        b.append("<b>Draw order</b><ol>"); for (String s : drawOrder) b.append("<li>").append(esc(s)).append("</li>"); b.append("</ol>");
        b.append("<b>Risks</b><ul>"); for (String s : risks) b.append("<li>").append(esc(s)).append("</li>"); b.append("</ul>");
        return b.toString();
    }

    private static void row(StringBuilder b, String k, float v) { b.append("<tr><td>").append(k).append("</td><td>").append(Math.round(v * 100f)).append("%</td></tr>"); }
    private static void kv(StringBuilder b, String k, String v, boolean comma) { b.append("  \"").append(k).append("\": \"").append(escJson(v)).append("\""); if (comma) b.append(','); b.append('\n'); }
    private static void ev(StringBuilder b, String k, float v, boolean comma) { b.append("    \"").append(k).append("\": ").append(num(v)); if (comma) b.append(','); b.append('\n'); }
    private static void array(StringBuilder b, String k, List<String> list, boolean comma) { b.append("  \"").append(k).append("\": ["); for (int i = 0; i < list.size(); i++) { if (i > 0) b.append(','); b.append("\"").append(escJson(list.get(i))).append("\""); } b.append(']'); if (comma) b.append(','); b.append('\n'); }
    private static void add(List<String> dst, String... values) { for (String v : values) dst.add(v); }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static String num(float v) { return String.format(java.util.Locale.US, "%.4f", v); }
    private static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static String escJson(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
