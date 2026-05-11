package com.solum.draw.vision;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DatasetClassRouter {
    public static final class Candidate {
        public final String name;
        public final int score;
        public final String reason;

        Candidate(String name, int score, String reason) {
            this.name = name;
            this.score = score;
            this.reason = reason;
        }
    }

    public static final class Route {
        public final String predicted;
        public final int confidence;
        public final ArrayList<Candidate> top;
        public final String summary;
        public final String scoresLine;
        public final String reason;

        Route(String predicted, int confidence, ArrayList<Candidate> top, String summary, String scoresLine, String reason) {
            this.predicted = predicted;
            this.confidence = confidence;
            this.top = top;
            this.summary = summary;
            this.scoresLine = scoresLine;
            this.reason = reason;
        }
    }

    private DatasetClassRouter() {}

    public static Route route(Object labelsObj, Object objectsObj, String extraText) {
        String evidence = normalize(extractEvidence(labelsObj) + " " + extractEvidence(objectsObj) + " " + safe(extraText));

        LinkedHashMap<String, Integer> s = new LinkedHashMap<>();
        LinkedHashMap<String, StringBuilder> r = new LinkedHashMap<>();

        String[] classes = new String[] {
                "anime_manga",
                "portrait_character",
                "full_body_character",
                "sticker_chibi",
                "sprite_sheet",
                "animal_creature",
                "architecture_hardsurface",
                "interior_room",
                "landscape_environment",
                "space_scifi_bg",
                "watercolor_paint",
                "vector_flat",
                "lineart_sketch",
                "pixel_art",
                "retro_halftone",
                "low_poly",
                "isometric_art",
                "vfx_glow_magic",
                "pattern_seamless",
                "texture_pattern",
                "product_object",
                "still_life",
                "logo_icon",
                "text_document",
                "diagram_chart",
                "ui_screenshot",
                "map",
                "photo_general",
                "material_texture",
                "vehicle",
                "weapon",
                "food",
                "fashion_clothing",
                "plant_flower",
                "thumbnail_cover",
                "unknown"
        };

        for (String c : classes) {
            s.put(c, 0);
            r.put(c, new StringBuilder());
        }

        // ------------------------------------------------------------------
        // Character / anime
        // ------------------------------------------------------------------
        add(s, r, "anime_manga", evidence, 22, "anime", "manga", "cartoon", "comic", "fiction", "illustration");
        add(s, r, "portrait_character", evidence, 28, "face", "head", "mouth", "eye", "eyelash", "skin", "flesh", "person", "girl", "boy", "model");
        add(s, r, "full_body_character", evidence, 24, "person", "standing", "human", "body", "leg", "arm", "dress", "jacket", "shirt");
        add(s, r, "sticker_chibi", evidence, 28, "sticker", "chibi", "cute", "emoji", "mascot");
        add(s, r, "sprite_sheet", evidence, 40, "sprite", "spritesheet", "sprite sheet", "frame", "animation", "sequence");

        // ------------------------------------------------------------------
        // Scene / environment
        // ------------------------------------------------------------------
        add(s, r, "landscape_environment", evidence, 24, "mountain", "sky", "forest", "tree", "river", "lake", "road", "valley", "sea", "cloud", "nature", "jungle");
        add(s, r, "space_scifi_bg", evidence, 34, "space", "planet", "galaxy", "star", "spaceship", "nebula", "astronaut", "sci fi", "scifi");
        add(s, r, "architecture_hardsurface", evidence, 26, "building", "house", "castle", "tower", "roof", "wall", "window", "room", "city", "street", "monument");
        add(s, r, "interior_room", evidence, 28, "room", "chair", "table", "sofa", "bed", "indoor", "kitchen", "lamp");

        // ------------------------------------------------------------------
        // Art styles
        // ------------------------------------------------------------------
        add(s, r, "watercolor_paint", evidence, 32, "watercolor", "paint", "painting", "wash", "pastel", "paper");
        add(s, r, "vector_flat", evidence, 28, "vector", "flat", "icon", "simple", "graphic", "minimal");
        add(s, r, "lineart_sketch", evidence, 30, "lineart", "line art", "sketch", "drawing", "pencil", "outline", "ink");
        add(s, r, "pixel_art", evidence, 36, "pixel", "8bit", "8-bit", "16bit", "16-bit", "blocky", "retro game");
        add(s, r, "retro_halftone", evidence, 34, "halftone", "retro", "comic dots", "print", "grain");
        add(s, r, "low_poly", evidence, 34, "low poly", "lowpoly", "polygon", "faceted");
        add(s, r, "isometric_art", evidence, 34, "isometric", "iso", "top down", "top-down", "diorama");

        // ------------------------------------------------------------------
        // VFX / patterns / materials
        // ------------------------------------------------------------------
        add(s, r, "vfx_glow_magic", evidence, 38, "glow", "magic", "spell", "fire", "flame", "lightning", "energy", "portal", "aura", "particle", "vfx");
        add(s, r, "pattern_seamless", evidence, 34, "pattern", "seamless", "tile", "repeating", "ornament");
        add(s, r, "texture_pattern", evidence, 30, "texture", "surface", "fabric", "wood", "stone", "metal", "leather", "concrete");
        add(s, r, "material_texture", evidence, 34, "material", "pbr", "normal map", "roughness", "albedo", "texture");

        // ------------------------------------------------------------------
        // Objects / documents / UI
        // ------------------------------------------------------------------
        add(s, r, "product_object", evidence, 26, "object", "product", "toy", "bottle", "phone", "camera", "bag", "shoe", "tool", "furniture");
        add(s, r, "still_life", evidence, 30, "still life", "vase", "fruit", "flowerpot", "cup", "plate", "book", "table");
        add(s, r, "logo_icon", evidence, 30, "logo", "icon", "symbol", "emblem", "badge", "sign");
        add(s, r, "text_document", evidence, 34, "text", "document", "paper", "page", "letter", "font", "handwriting", "paragraph");
        add(s, r, "diagram_chart", evidence, 34, "diagram", "chart", "graph", "flowchart", "scheme", "blueprint", "infographic");
        add(s, r, "ui_screenshot", evidence, 38, "ui", "screenshot", "screen", "app", "button", "toolbar", "panel", "menu", "mobile phone", "interface");
        add(s, r, "map", evidence, 34, "map", "terrain map", "world map", "route", "gps");

        // ------------------------------------------------------------------
        // Concrete object families
        // ------------------------------------------------------------------
        add(s, r, "animal_creature", evidence, 34, "animal", "dog", "cat", "horse", "bird", "rhino", "rhinoceros", "elephant", "creature", "dragon");
        add(s, r, "vehicle", evidence, 32, "car", "vehicle", "truck", "bus", "bike", "motorcycle", "airplane", "ship", "train");
        add(s, r, "weapon", evidence, 32, "weapon", "sword", "gun", "rifle", "knife", "bow", "shield");
        add(s, r, "food", evidence, 30, "food", "meal", "fruit", "cake", "bread", "meat", "drink");
        add(s, r, "fashion_clothing", evidence, 30, "dress", "jacket", "shirt", "shoe", "clothing", "fashion", "tights", "swimsuit");
        add(s, r, "plant_flower", evidence, 30, "plant", "flower", "tree", "leaf", "grass", "flowerpot");
        add(s, r, "thumbnail_cover", evidence, 28, "poster", "cover", "thumbnail", "banner");

        // Generic fallback, but never allowed to dominate real classes.
        put(s, r, "photo_general", 8, "fallback photo/general");
        put(s, r, "unknown", 1, "fallback unknown");

        // ------------------------------------------------------------------
        // Anti-bias corrections.
        // These prevent early broad classes from swallowing precise classes.
        // ------------------------------------------------------------------
        if (has(evidence, "text", "font", "paragraph", "document", "page")) {
            boost(s, r, "text_document", 35, "anti-logo: text/document evidence");
            penalty(s, r, "logo_icon", 18, "text/document beats logo");
        }

        if (has(evidence, "pattern", "seamless", "tile", "texture", "surface")) {
            boost(s, r, "pattern_seamless", 22, "pattern/texture evidence");
            boost(s, r, "texture_pattern", 18, "pattern/texture evidence");
            penalty(s, r, "landscape_environment", 20, "texture/pattern beats landscape");
            penalty(s, r, "anime_manga", 20, "texture/pattern beats anime");
        }

        if (has(evidence, "product", "object", "bottle", "toy", "phone", "camera", "shoe", "bag")) {
            boost(s, r, "product_object", 24, "object/product evidence");
            penalty(s, r, "logo_icon", 12, "product beats logo");
        }

        if (has(evidence, "vase", "fruit", "cup", "plate", "flowerpot", "still life")) {
            boost(s, r, "still_life", 30, "still life evidence");
            penalty(s, r, "anime_manga", 18, "still life beats anime");
        }

        if (has(evidence, "button", "toolbar", "menu", "screen", "app", "interface")) {
            boost(s, r, "ui_screenshot", 35, "UI evidence");
            penalty(s, r, "product_object", 12, "UI beats product");
            penalty(s, r, "logo_icon", 12, "UI beats logo");
        }

        if (has(evidence, "building", "house", "castle", "tower", "roof", "window")) {
            boost(s, r, "architecture_hardsurface", 22, "architecture evidence");
            penalty(s, r, "anime_manga", 16, "architecture beats anime");
            penalty(s, r, "product_object", 8, "architecture beats product");
        }

        if (has(evidence, "face", "eye", "eyelash", "mouth", "skin", "flesh", "person")) {
            boost(s, r, "portrait_character", 24, "face/body evidence");
            penalty(s, r, "logo_icon", 16, "character beats logo");
        }

        if (has(evidence, "mountain", "sky", "river", "lake", "forest", "road", "valley")) {
            boost(s, r, "landscape_environment", 24, "landscape evidence");
            penalty(s, r, "product_object", 10, "landscape beats product");
        }

        ArrayList<Candidate> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : s.entrySet()) {
            int score = clamp(e.getValue(), 0, 100);
            String reason = r.get(e.getKey()).toString();
            list.add(new Candidate(e.getKey(), score, reason));
        }

        Collections.sort(list, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                return Integer.compare(b.score, a.score);
            }
        });

        ArrayList<Candidate> top = new ArrayList<>();
        for (int i = 0; i < Math.min(5, list.size()); i++) top.add(list.get(i));

        Candidate win = top.get(0);
        StringBuilder scores = new StringBuilder();
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            if (i > 0) scores.append(" | ");
            scores.append(list.get(i).name).append(" ").append(list.get(i).score);
        }

        String summary = "Тип: " + human(win.name) + "\n" +
                "dataset_class: " + win.name + " | score " + win.score + "\n" +
                "top: " + scores;

        return new Route(win.name, win.score, top, summary, scores.toString(), win.reason);
    }

    public static String compactRouteLine(Object labelsObj, Object objectsObj, String extraText) {
        Route r = route(labelsObj, objectsObj, extraText);
        return "dataset_class: " + r.predicted + " " + r.confidence + " | top: " + r.scoresLine;
    }

    private static void add(LinkedHashMap<String, Integer> s, LinkedHashMap<String, StringBuilder> r,
                            String cls, String evidence, int pts, String... keys) {
        for (String k : keys) {
            if (evidence.contains(normalize(k))) {
                put(s, r, cls, pts, "+" + pts + " " + k);
            }
        }
    }

    private static void boost(LinkedHashMap<String, Integer> s, LinkedHashMap<String, StringBuilder> r,
                              String cls, int pts, String reason) {
        put(s, r, cls, pts, "+" + pts + " " + reason);
    }

    private static void penalty(LinkedHashMap<String, Integer> s, LinkedHashMap<String, StringBuilder> r,
                                String cls, int pts, String reason) {
        put(s, r, cls, -pts, "-" + pts + " " + reason);
    }

    private static void put(LinkedHashMap<String, Integer> s, LinkedHashMap<String, StringBuilder> r,
                            String cls, int delta, String reason) {
        Integer old = s.get(cls);
        if (old == null) old = 0;
        s.put(cls, old + delta);
        StringBuilder b = r.get(cls);
        if (b != null) {
            if (b.length() > 0) b.append("; ");
            b.append(reason);
        }
    }

    private static boolean has(String evidence, String... keys) {
        for (String k : keys) {
            if (evidence.contains(normalize(k))) return true;
        }
        return false;
    }

    private static String extractEvidence(Object obj) {
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder();

        if (obj instanceof Iterable) {
            for (Object x : (Iterable<?>) obj) {
                sb.append(" ").append(extractOne(x));
            }
        } else if (obj.getClass().isArray()) {
            Object[] arr = (Object[]) obj;
            for (Object x : arr) sb.append(" ").append(extractOne(x));
        } else {
            sb.append(" ").append(extractOne(obj));
        }

        return sb.toString();
    }

    private static String extractOne(Object obj) {
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder();

        String[] fields = new String[] {
                "text", "label", "name", "className", "category", "type", "title", "displayName"
        };

        for (String f : fields) {
            try {
                Field field = obj.getClass().getDeclaredField(f);
                field.setAccessible(true);
                Object v = field.get(obj);
                if (v != null) sb.append(" ").append(v);
            } catch (Throwable ignored) {}
        }

        String[] methods = new String[] {
                "getText", "getLabel", "getName", "getClassName", "getCategory", "getType", "getTitle"
        };

        for (String m : methods) {
            try {
                Method method = obj.getClass().getMethod(m);
                Object v = method.invoke(obj);
                if (v != null) sb.append(" ").append(v);
            } catch (Throwable ignored) {}
        }

        if (sb.length() == 0) sb.append(" ").append(obj.toString());
        return sb.toString();
    }

    private static String normalize(String s) {
        return safe(s).toLowerCase(Locale.US)
                .replace("_", " ")
                .replace("-", " ")
                .replace("/", " ")
                .replace("\\", " ")
                .replace("\n", " ");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String human(String c) {
        if ("anime_manga".equals(c)) return "аниме / манга";
        if ("portrait_character".equals(c)) return "персонаж / портрет";
        if ("full_body_character".equals(c)) return "персонаж полный рост";
        if ("landscape_environment".equals(c)) return "окружение / пейзаж";
        if ("architecture_hardsurface".equals(c)) return "архитектура / hard surface";
        if ("ui_screenshot".equals(c)) return "UI / скриншот";
        if ("logo_icon".equals(c)) return "логотип / иконка";
        if ("text_document".equals(c)) return "текст / документ";
        if ("diagram_chart".equals(c)) return "диаграмма / схема";
        if ("product_object".equals(c)) return "предмет / объект";
        if ("animal_creature".equals(c)) return "животное / существо";
        if ("pattern_seamless".equals(c)) return "паттерн / seamless";
        if ("texture_pattern".equals(c)) return "текстура / паттерн";
        if ("vfx_glow_magic".equals(c)) return "VFX / магия / glow";
        if ("watercolor_paint".equals(c)) return "акварель / paint";
        if ("pixel_art".equals(c)) return "pixel art";
        if ("sprite_sheet".equals(c)) return "sprite sheet";
        return c.replace("_", " ");
    }
}
