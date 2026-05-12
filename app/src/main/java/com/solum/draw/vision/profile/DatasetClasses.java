package com.solum.draw.vision.profile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DatasetClasses {
    private DatasetClasses() {}

    public static final List<String> ALL = Arrays.asList(
            "abstract_art",
            "anime_manga",
            "architecture_hardsurface",
            "animal_creature",
            "cartoon_comic",
            "diagram_chart",
            "digital_painting_concept",
            "game_ui_hud",
            "grayscale_ink",
            "human_body_fullbody",
            "ink_wash",
            "isometric_art",
            "landscape_environment",
            "lineart_sketch",
            "logo_icon",
            "low_poly",
            "noisy_compressed",
            "oil_painting",
            "pattern_seamless",
            "pencil_drawing",
            "photo_general",
            "pixel_art",
            "portrait_character",
            "product_object",
            "retro_halftone",
            "space_scifi_bg",
            "sprite_sheet",
            "sticker_chibi",
            "text_document",
            "texture_pattern",
            "transparent_layered",
            "ui_screenshot",
            "vector_flat",
            "vfx_glow_magic",
            "watercolor_paint"
    );

    public static final Set<String> FORBIDDEN_TOKENS = new HashSet<>(Arrays.asList(
            "calibrated",
            "unknown",
            "fallback",
            "generic",
            "other",
            "misc",
            "null",
            "none",
            ""
    ));

    public static final Map<String, String> CLASS_TO_AXIS = new HashMap<>();

    static {
        for (String c : Arrays.asList(
                "abstract_art", "anime_manga", "cartoon_comic",
                "digital_painting_concept", "grayscale_ink", "ink_wash",
                "lineart_sketch", "low_poly", "oil_painting", "pencil_drawing",
                "pixel_art", "retro_halftone", "vector_flat", "watercolor_paint")) {
            CLASS_TO_AXIS.put(c, "style_art");
        }

        for (String c : Arrays.asList(
                "diagram_chart", "game_ui_hud", "logo_icon",
                "text_document", "ui_screenshot")) {
            CLASS_TO_AXIS.put(c, "ui_document");
        }

        for (String c : Arrays.asList(
                "pattern_seamless", "texture_pattern", "transparent_layered")) {
            CLASS_TO_AXIS.put(c, "texture_pattern");
        }

        for (String c : Arrays.asList(
                "landscape_environment", "space_scifi_bg")) {
            CLASS_TO_AXIS.put(c, "scene_environment");
        }

        for (String c : Arrays.asList(
                "architecture_hardsurface", "isometric_art", "product_object")) {
            CLASS_TO_AXIS.put(c, "architecture_object");
        }

        for (String c : Arrays.asList(
                "human_body_fullbody", "portrait_character",
                "sprite_sheet", "sticker_chibi")) {
            CLASS_TO_AXIS.put(c, "character_body");
        }

        CLASS_TO_AXIS.put("vfx_glow_magic", "vfx_fx");
        CLASS_TO_AXIS.put("noisy_compressed", "quality_noise");
        CLASS_TO_AXIS.put("animal_creature", "animal_creature");
        CLASS_TO_AXIS.put("photo_general", "unknown");
    }

    public static boolean isValid(String cls) {
        return cls != null && ALL.contains(cls);
    }

    public static boolean isForbidden(String token) {
        if (token == null) return true;
        return FORBIDDEN_TOKENS.contains(token.trim().toLowerCase(Locale.US));
    }

    public static String axisOf(String cls) {
        String axis = CLASS_TO_AXIS.get(cls);
        return axis == null ? "unknown" : axis;
    }
}
