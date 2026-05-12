package com.solum.draw.vision.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShadowClassRouter {
    private ShadowClassRouter() {}

    public static void route(ImageProfile p, String oldPredicted, List<String> oldTop3) {
        if (p == null) return;

        p.shadowFinalClass = "";
        p.shadowTop3.clear();
        p.shadowConfidence = 0f;
        p.classScores.clear();

        // Old prediction remains a strong prior. Shadow router must not destroy a working baseline.
        add(p, oldPredicted, 0.42f);

        if (oldTop3 != null) {
            float[] boosts = new float[]{0.20f, 0.12f, 0.06f};
            for (int i = 0; i < oldTop3.size() && i < boosts.length; i++) {
                add(p, oldTop3.get(i), boosts[i]);
            }
        }

        addByQuality(p);
        addByStyle(p);
        addByContent(p);
        addByPurpose(p);
        addByFeatures(p);

        List<Map.Entry<String, Float>> sorted = new ArrayList<>(p.classScores.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Float>>() {
            @Override public int compare(Map.Entry<String, Float> a, Map.Entry<String, Float> b) {
                return Float.compare(b.getValue(), a.getValue());
            }
        });

        for (Map.Entry<String, Float> e : sorted) {
            String cls = e.getKey();
            if (!DatasetClasses.isValid(cls) || DatasetClasses.isForbidden(cls)) continue;
            if (!p.shadowTop3.contains(cls)) p.shadowTop3.add(cls);
            if (p.shadowTop3.size() >= 3) break;
        }

        // Always return exactly 3 real dataset classes.
        for (String cls : DatasetClasses.ALL) {
            if (p.shadowTop3.size() >= 3) break;
            if (!p.shadowTop3.contains(cls)) p.shadowTop3.add(cls);
        }

        if (!p.shadowTop3.isEmpty()) {
            p.shadowFinalClass = p.shadowTop3.get(0);
        }

        float first = scoreOf(p, p.shadowTop3.size() > 0 ? p.shadowTop3.get(0) : "");
        float second = scoreOf(p, p.shadowTop3.size() > 1 ? p.shadowTop3.get(1) : "");
        p.shadowConfidence = clamp01(first <= 0f ? 0f : (first - second) / Math.max(0.0001f, first + second));
        p.scoringDone = true;
    }

    private static void addByQuality(ImageProfile p) {
        if (p.qualityAxis == ImageAxes.QualityAxis.NOISY_COMPRESSED) add(p, "noisy_compressed", 0.55f);
        if (p.qualityAxis == ImageAxes.QualityAxis.SKETCH_ROUGH) {
            add(p, "pencil_drawing", 0.22f);
            add(p, "lineart_sketch", 0.16f);
        }
    }

    private static void addByStyle(ImageProfile p) {
        switch (p.styleAxis) {
            case LINEART:
                add(p, "lineart_sketch", 0.28f);
                add(p, "pencil_drawing", 0.18f);
                add(p, "diagram_chart", 0.12f);
                add(p, "logo_icon", 0.10f);
                break;
            case PAINTERLY:
                add(p, "watercolor_paint", 0.24f);
                add(p, "oil_painting", 0.18f);
                add(p, "digital_painting_concept", 0.18f);
                add(p, "ink_wash", 0.10f);
                break;
            case FLAT_VECTOR:
                add(p, "vector_flat", 0.24f);
                add(p, "cartoon_comic", 0.20f);
                add(p, "logo_icon", 0.14f);
                add(p, "game_ui_hud", 0.10f);
                break;
            case PIXEL:
                add(p, "pixel_art", 0.45f);
                add(p, "sprite_sheet", 0.22f);
                break;
            case PHOTO_REALISTIC:
                add(p, "photo_general", 0.30f);
                add(p, "portrait_character", 0.12f);
                add(p, "landscape_environment", 0.12f);
                break;
            case ABSTRACT:
                add(p, "abstract_art", 0.36f);
                add(p, "digital_painting_concept", 0.14f);
                add(p, "vfx_glow_magic", 0.10f);
                break;
            case TEXTURED:
                add(p, "texture_pattern", 0.26f);
                add(p, "pattern_seamless", 0.24f);
                add(p, "transparent_layered", 0.10f);
                break;
            default:
                break;
        }
    }

    private static void addByContent(ImageProfile p) {
        switch (p.contentAxis) {
            case CHARACTER:
                add(p, "portrait_character", 0.28f);
                add(p, "anime_manga", 0.22f);
                add(p, "human_body_fullbody", 0.18f);
                add(p, "sticker_chibi", 0.08f);
                break;
            case CREATURE:
                add(p, "animal_creature", 0.46f);
                break;
            case LANDSCAPE:
                add(p, "landscape_environment", 0.38f);
                add(p, "space_scifi_bg", 0.14f);
                break;
            case ARCHITECTURE:
                add(p, "architecture_hardsurface", 0.42f);
                add(p, "isometric_art", 0.16f);
                add(p, "product_object", 0.08f);
                break;
            case UI_ELEMENT:
                add(p, "ui_screenshot", 0.36f);
                add(p, "game_ui_hud", 0.22f);
                add(p, "text_document", 0.10f);
                break;
            case DIAGRAM:
                add(p, "diagram_chart", 0.48f);
                add(p, "ui_screenshot", 0.12f);
                add(p, "lineart_sketch", 0.10f);
                break;
            case LOGO:
                add(p, "logo_icon", 0.48f);
                add(p, "vector_flat", 0.16f);
                break;
            case PATTERN:
                add(p, "pattern_seamless", 0.36f);
                add(p, "texture_pattern", 0.30f);
                break;
            case TEXT_BLOCK:
                add(p, "text_document", 0.48f);
                add(p, "diagram_chart", 0.10f);
                break;
            case PRODUCT_OBJECT:
                add(p, "product_object", 0.34f);
                add(p, "transparent_layered", 0.10f);
                break;
            case VFX_EFFECT:
                add(p, "vfx_glow_magic", 0.50f);
                add(p, "space_scifi_bg", 0.12f);
                break;
            default:
                break;
        }
    }

    private static void addByPurpose(ImageProfile p) {
        switch (p.purposeAxis) {
            case UI_SCREENSHOT:
                add(p, "ui_screenshot", 0.18f);
                add(p, "game_ui_hud", 0.14f);
                break;
            case DOCUMENT:
                add(p, "text_document", 0.20f);
                add(p, "diagram_chart", 0.12f);
                break;
            case GAME_ASSET:
                add(p, "sprite_sheet", 0.14f);
                add(p, "game_ui_hud", 0.14f);
                add(p, "pixel_art", 0.10f);
                break;
            case DECORATIVE_PATTERN:
                add(p, "pattern_seamless", 0.18f);
                add(p, "texture_pattern", 0.16f);
                break;
            case REFERENCE_PHOTO:
                add(p, "photo_general", 0.18f);
                break;
            case ILLUSTRATION:
                add(p, "digital_painting_concept", 0.10f);
                add(p, "watercolor_paint", 0.08f);
                break;
            default:
                break;
        }
    }

    private static void addByFeatures(ImageProfile p) {
        VisualFeatureVector f = p.features == null ? new VisualFeatureVector() : p.features;

        if (f.glowScore > 0.22f) add(p, "vfx_glow_magic", 0.26f);
        if (f.pixelGridScore > 0.62f) add(p, "pixel_art", 0.28f);
        if (f.symmetryScore > 0.75f && f.colorEntropy < 0.46f) add(p, "logo_icon", 0.18f);
        if (f.tileRepetition > 0.88f && f.hardLineScore < 0.34f) {
            add(p, "pattern_seamless", 0.18f);
            add(p, "texture_pattern", 0.12f);
        }
        if (f.textDensity > 0.90f && f.saturation < 0.25f) add(p, "text_document", 0.16f);
        if (f.hardLineScore > 0.42f) add(p, "architecture_hardsurface", 0.12f);
        if (f.softEdgeRatio > 0.55f && f.edgeDensity < 0.30f) add(p, "watercolor_paint", 0.12f);
    }

    private static void add(ImageProfile p, String cls, float v) {
        if (!DatasetClasses.isValid(cls) || DatasetClasses.isForbidden(cls)) return;
        Float old = p.classScores.get(cls);
        p.classScores.put(cls, old == null ? v : old + v);
    }

    private static float scoreOf(ImageProfile p, String cls) {
        Float v = p.classScores.get(cls);
        return v == null ? 0f : v;
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
