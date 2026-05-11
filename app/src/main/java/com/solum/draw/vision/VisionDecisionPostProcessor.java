package com.solum.draw.vision;

import java.lang.reflect.Field;
import java.util.Locale;

public final class VisionDecisionPostProcessor {
    private VisionDecisionPostProcessor() {}

    public static VisionDecisionEngine.Decision refine(
            VisionDecisionEngine.Decision decision,
            Object labels,
            Object objects,
            String hint
    ) {
        // Patch 27P: disabled aggressive 27O calibration.
        // It collapsed too many classes into anime_manga and reduced full Bench from 13%/25% to 4%/6%.
        if (decision == null) return decision;
        if (true) return decision;

        String oldClass = safe(getStringField(decision, "datasetClass"));
        String oldTop = safe(getStringField(decision, "topLine"));
        String oldReason = safe(getStringField(decision, "reason"));

        String bag = (
                safe(hint) + " " +
                flatten(labels) + " " +
                flatten(objects) + " " +
                oldClass + " " +
                oldTop + " " +
                oldReason
        ).toLowerCase(Locale.US);

        String next = chooseBetterClass(oldClass, bag);

        if (next.equals(oldClass)) return decision;

        setStringField(decision, "datasetClass", next);
        setStringField(decision, "topLine", next + " | calibrated from " + oldClass);
        setStringField(decision, "reason", oldReason + " | postprocessor: " + oldClass + " -> " + next);

        return decision;
    }

    private static String chooseBetterClass(String current, String bag) {
        String c = safe(current);

        boolean hasSkin = hasAny(bag, "skin", "flesh", "mouth", "eyelash", "eye", "face", "hair", "hand", "nail", "dress", "model", "person", "woman", "man");
        boolean animeLike = hasAny(bag, "anime", "manga", "cartoon", "comic", "fiction", "illustration", "lineart", "character", "chibi");
        boolean uiLike = hasAny(bag, "mobile phone", "screen", "screenshot", "app", "button", "menu", "toolbar", "panel", "interface", "hud", "poster", "web site");
        boolean diagramLike = hasAny(bag, "diagram", "chart", "graph", "arrow", "flow", "map", "plan", "infographic");
        boolean logoLike = hasAny(bag, "logo", "icon", "symbol", "emblem", "sign");
        boolean vfxLike = hasAny(bag, "glow", "magic", "neon", "spark", "lightning", "fire", "flame", "energy", "laser", "electric", "aura");
        boolean pixelLike = hasAny(bag, "pixel", "8-bit", "8 bit", "blocky", "sprite");
        boolean animalLike = hasAny(bag, "animal", "dog", "cat", "horse", "bird", "fish", "wolf", "rhino", "elephant", "creature");
        boolean archLike = hasAny(bag, "building", "architecture", "castle", "tower", "roof", "house", "window", "door", "room", "monument");
        boolean landscapeLike = hasAny(bag, "forest", "tree", "mountain", "sky", "lake", "river", "sea", "ocean", "rock", "plant", "jungle", "landscape");
        boolean patternLike = hasAny(bag, "pattern", "texture", "seamless", "fabric", "wallpaper", "ornament");
        boolean textLike = hasAny(bag, "document", "text", "book", "page", "letter", "font", "writing");

        // Высший приоритет: специфичные классы, которые ML Kit часто сливает в общие.
        if (vfxLike) return "vfx_glow_magic";
        if (pixelLike) return "pixel_art";

        // UI/document: не давать lineart/vector/product перехватывать реальные интерфейсы.
        if (diagramLike) return "diagram_chart";
        if (logoLike && !hasSkin && !landscapeLike && !archLike) return "logo_icon";
        if (uiLike && !hasSkin && !animeLike) {
            if (hasAny(bag, "hud", "game", "health", "score", "minimap")) return "game_ui_hud";
            return "ui_screenshot";
        }
        if (textLike && !hasSkin && !landscapeLike) return "text_document";

        // Персонажи: ML Kit часто даёт Fiction/Jacket/Flesh, а старые правила уводят в ui/product.
        if (hasSkin && animeLike) return "anime_manga";
        if (hasSkin && hasAny(bag, "fullbody", "full body", "body", "leg", "arm", "pose")) return "human_body_fullbody";
        if (hasSkin) return "portrait_character";

        // Животные до landscape, иначе лошадь/носорог улетает в environment.
        if (animalLike) return "animal_creature";

        // Архитектура до landscape, иначе castle/tower/house уходит в environment/style.
        if (archLike) return "architecture_hardsurface";

        // Природа/сцена.
        if (landscapeLike) return "landscape_environment";

        // Паттерны/текстуры.
        if (patternLike) {
            if (hasAny(bag, "seamless", "repeat", "tile")) return "pattern_seamless";
            return "texture_pattern";
        }

        // Пост-коррекции частых завалов из bench.
        if (c.equals("product_object")) {
            if (animeLike) return "cartoon_comic";
            if (uiLike) return "ui_screenshot";
            if (patternLike) return "pattern_seamless";
        }

        if (c.equals("watercolor_paint")) {
            if (hasAny(bag, "abstract", "non-object", "shape", "color field")) return "abstract_art";
            if (animeLike && !hasSkin) return "digital_painting_concept";
        }

        return c;
    }

    private static boolean hasAny(String s, String... keys) {
        if (s == null) return false;
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private static String flatten(Object obj) {
        StringBuilder b = new StringBuilder();
        flattenInto(obj, b, 0);
        return b.toString();
    }

    private static void flattenInto(Object obj, StringBuilder b, int depth) {
        if (obj == null || depth > 3) return;

        if (obj instanceof Iterable) {
            for (Object x : (Iterable<?>) obj) flattenInto(x, b, depth + 1);
            return;
        }

        Class<?> cls = obj.getClass();
        if (cls == String.class || Number.class.isAssignableFrom(cls) || cls == Boolean.class) {
            b.append(' ').append(obj);
            return;
        }

        b.append(' ').append(String.valueOf(obj));

        Field[] fields = cls.getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != obj) b.append(' ').append(String.valueOf(v));
            } catch (Throwable ignored) {
            }
        }
    }

    private static String getStringField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v == null ? "" : String.valueOf(v);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void setStringField(Object obj, String name, String value) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value == null ? "" : value);
        } catch (Throwable ignored) {
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
