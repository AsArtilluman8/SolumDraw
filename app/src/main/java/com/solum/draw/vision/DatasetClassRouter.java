package com.solum.draw.vision;

import com.solum.draw.vision.profile.DatasetClasses;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        LinkedHashMap<String, StringBuilder> reasons = new LinkedHashMap<>();

        String[] classes = new String[] {
                "abstract_art",
                "animal_creature",
                "anime_manga",
                "architecture_hardsurface",
                "cartoon_comic",
                "digital_painting_concept",
                "game_ui_hud",
                "human_body_fullbody",
                "isometric_art",
                "landscape_environment",
                "lineart_sketch",
                "logo_icon",
                "low_poly",
                "pattern_seamless",
                "pencil_drawing",
                "photo_general",
                "pixel_art",
                "portrait_character",
                "product_object",
                "sprite_sheet",
                "sticker_chibi",
                "texture_pattern",
                "transparent_layered",
                "ui_screenshot",
                "vector_flat",
                "watercolor_paint",
                "vfx_glow_magic",
                "space_scifi_bg",
                "interior_room",
                "material_texture",
                "vehicle",
                "weapon",
                "food",
                "fashion_clothing",
                "plant_flower",
                "thumbnail_cover",
                "text_document",
                "diagram_chart",
                "map",
                "unknown"
        };

        for (String c : classes) {
            scores.put(c, 0);
            reasons.put(c, new StringBuilder());
        }

        add(scores, reasons, "anime_manga", evidence, 25, "anime", "manga", "fiction", "illustration");
        add(scores, reasons, "cartoon_comic", evidence, 25, "cartoon", "comic", "toon", "panel", "character art");
        add(scores, reasons, "digital_painting_concept", evidence, 25, "digital", "painting", "concept", "fantasy", "cinematic", "artwork");
        add(scores, reasons, "abstract_art", evidence, 30, "abstract", "surreal", "shape", "color field", "composition", "non representational");
        add(scores, reasons, "portrait_character", evidence, 35, "face", "eye", "eyelash", "mouth", "skin", "flesh", "person", "girl", "boy", "model", "portrait");
        add(scores, reasons, "human_body_fullbody", evidence, 30, "person", "standing", "human", "body", "leg", "arm", "full body", "dress", "jacket", "shirt");
        add(scores, reasons, "sticker_chibi", evidence, 40, "sticker", "chibi", "cute", "emoji", "mascot");
        add(scores, reasons, "sprite_sheet", evidence, 45, "sprite", "sprite sheet", "spritesheet", "frame", "animation", "sequence");

        add(scores, reasons, "animal_creature", evidence, 40, "animal", "dog", "cat", "horse", "bird", "rhino", "rhinoceros", "elephant", "dragon", "creature");
        add(scores, reasons, "architecture_hardsurface", evidence, 40, "building", "house", "castle", "tower", "roof", "wall", "window", "monument");
        add(scores, reasons, "interior_room", evidence, 35, "room", "chair", "table", "sofa", "bed", "indoor", "kitchen", "lamp");
        add(scores, reasons, "landscape_environment", evidence, 35, "mountain", "sky", "forest", "tree", "river", "lake", "road", "valley", "sea", "cloud", "nature", "jungle");
        add(scores, reasons, "space_scifi_bg", evidence, 45, "space", "planet", "galaxy", "star", "spaceship", "nebula", "astronaut", "scifi", "sci fi");

        add(scores, reasons, "watercolor_paint", evidence, 45, "watercolor", "paint", "painting", "wash", "pastel", "paper");
        add(scores, reasons, "vector_flat", evidence, 35, "vector", "flat", "simple", "graphic", "minimal");
        add(scores, reasons, "lineart_sketch", evidence, 40, "lineart", "line art", "sketch", "drawing", "pencil", "outline", "ink");
        add(scores, reasons, "pixel_art", evidence, 45, "pixel", "8bit", "8 bit", "16bit", "16 bit", "blocky", "retro game");
        add(scores, reasons, "retro_halftone", evidence, 45, "halftone", "retro", "comic dots", "print", "grain");
        add(scores, reasons, "low_poly", evidence, 45, "low poly", "lowpoly", "polygon", "faceted");
        add(scores, reasons, "isometric_art", evidence, 45, "isometric", "top down", "top-down", "diorama");

        add(scores, reasons, "vfx_glow_magic", evidence, 45, "glow", "magic", "spell", "fire", "flame", "lightning", "energy", "portal", "aura", "particle", "vfx");
        add(scores, reasons, "pattern_seamless", evidence, 45, "pattern", "seamless", "tile", "repeating", "ornament");
        add(scores, reasons, "texture_pattern", evidence, 40, "texture", "surface", "fabric", "wood", "stone", "metal", "leather", "concrete");
        add(scores, reasons, "material_texture", evidence, 45, "material", "pbr", "normal map", "roughness", "albedo");

        add(scores, reasons, "product_object", evidence, 35, "object", "product", "toy", "bottle", "phone", "camera", "bag", "shoe", "tool", "furniture");
        add(scores, reasons, "still_life", evidence, 40, "still life", "vase", "fruit", "flowerpot", "cup", "plate", "book", "table");
        add(scores, reasons, "logo_icon", evidence, 40, "logo", "icon", "symbol", "emblem", "badge", "sign");
        add(scores, reasons, "text_document", evidence, 45, "text", "document", "paper", "page", "letter", "font", "handwriting", "paragraph");
        add(scores, reasons, "diagram_chart", evidence, 45, "diagram", "chart", "graph", "flowchart", "scheme", "blueprint", "infographic");
        add(scores, reasons, "ui_screenshot", evidence, 45, "ui", "screenshot", "screen", "app", "button", "toolbar", "panel", "menu", "mobile phone", "interface");
        add(scores, reasons, "map", evidence, 40, "map", "terrain map", "world map", "route", "gps");
        add(scores, reasons, "game_ui_hud", evidence, 45, "hud", "game ui", "health bar", "mana", "inventory", "crosshair");
        add(scores, reasons, "pencil_drawing", evidence, 45, "pencil", "graphite", "sketchbook", "paper drawing");
        add(scores, reasons, "transparent_layered", evidence, 40, "transparent", "alpha", "layer", "cutout", "png");


        add(scores, reasons, "vehicle", evidence, 40, "car", "vehicle", "truck", "bus", "bike", "motorcycle", "airplane", "ship", "train");
        add(scores, reasons, "weapon", evidence, 40, "weapon", "sword", "gun", "rifle", "knife", "bow", "shield");
        add(scores, reasons, "food", evidence, 35, "food", "meal", "fruit", "cake", "bread", "meat", "drink");
        add(scores, reasons, "fashion_clothing", evidence, 35, "dress", "jacket", "shirt", "shoe", "clothing", "fashion", "tights", "swimsuit");
        add(scores, reasons, "plant_flower", evidence, 35, "plant", "flower", "tree", "leaf", "grass", "flowerpot");
        add(scores, reasons, "thumbnail_cover", evidence, 35, "poster", "cover", "thumbnail", "banner");

        put(scores, reasons, "photo_general", 5, "fallback photo/general");
        put(scores, reasons, "unknown", 1, "fallback unknown");

        antiBias(scores, reasons, evidence);

        ArrayList<Candidate> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            String cls = remapToDatasetClass(e.getKey());
            if (DatasetClasses.isForbidden(cls) || !DatasetClasses.isValid(cls)) continue;

            int v = clamp(e.getValue(), 0, 100);
            StringBuilder rb = reasons.get(e.getKey());
            String reason = rb == null ? "" : rb.toString();
            list.add(new Candidate(cls, v, reason));
        }

        dedupeCandidatesByClass(list);

        Collections.sort(list, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                return Integer.compare(b.score, a.score);
            }
        });

        ArrayList<Candidate> top = new ArrayList<>();
        for (int i = 0; i < Math.min(5, list.size()); i++) top.add(list.get(i));

        Candidate win = top.get(0);

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) line.append(" | ");
            line.append(top.get(i).name).append(" ").append(top.get(i).score);
        }

        String summary =
                "dataset_class: " + win.name + " | score " + win.score + "\n" +
                "top5: " + line;

        return new Route(win.name, win.score, top, summary, line.toString(), win.reason);
    }


    private static void dedupeCandidatesByClass(ArrayList<Candidate> list) {
        LinkedHashMap<String, Candidate> best = new LinkedHashMap<>();

        for (Candidate c : list) {
            if (c == null) continue;
            if (DatasetClasses.isForbidden(c.name) || !DatasetClasses.isValid(c.name)) continue;

            Candidate old = best.get(c.name);
            if (old == null || c.score > old.score) {
                best.put(c.name, c);
            }
        }

        list.clear();
        list.addAll(best.values());
    }

    private static void antiBias(LinkedHashMap<String, Integer> s, LinkedHashMap<String, StringBuilder> r, String e) {
        if (has(e, "text", "font", "paragraph", "document", "page")) {
            put(s, r, "text_document", 35, "anti-bias text/document");
            put(s, r, "logo_icon", -20, "text beats logo");
        }

        if (has(e, "pattern", "seamless", "tile", "texture", "surface")) {
            put(s, r, "pattern_seamless", 25, "pattern evidence");
            put(s, r, "texture_pattern", 25, "texture evidence");
            put(s, r, "landscape_environment", -20, "pattern beats landscape");
            put(s, r, "anime_manga", -20, "pattern beats anime");
        }

        if (has(e, "product", "object", "bottle", "toy", "phone", "camera", "shoe", "bag")) {
            put(s, r, "product_object", 25, "product evidence");
            put(s, r, "logo_icon", -15, "product beats logo");
        }

        if (has(e, "vase", "fruit", "cup", "plate", "flowerpot", "still life")) {
            put(s, r, "still_life", 30, "still life evidence");
            put(s, r, "anime_manga", -20, "still life beats anime");
        }

        if (has(e, "button", "toolbar", "menu", "screen", "app", "interface")) {
            put(s, r, "ui_screenshot", 35, "UI evidence");
            put(s, r, "product_object", -15, "UI beats product");
            put(s, r, "logo_icon", -15, "UI beats logo");
        }

        if (has(e, "building", "house", "castle", "tower", "roof", "window")) {
            put(s, r, "architecture_hardsurface", 30, "architecture evidence");
            put(s, r, "anime_manga", -20, "architecture beats anime");
        }

        if (has(e, "face", "eye", "eyelash", "mouth", "skin", "flesh", "person")) {
            put(s, r, "portrait_character", 30, "portrait evidence");
            put(s, r, "logo_icon", -20, "character beats logo");
        }

        if (has(e, "mountain", "sky", "river", "lake", "forest", "road", "valley")) {
            put(s, r, "landscape_environment", 30, "landscape evidence");
            put(s, r, "product_object", -15, "landscape beats product");
        }
    }

    private static void add(LinkedHashMap<String, Integer> s, LinkedHashMap<String, StringBuilder> r, String cls, String e, int pts, String... keys) {
        for (String k : keys) {
            if (e.contains(normalize(k))) put(s, r, cls, pts, "+" + pts + " " + k);
        }
    }

    private static boolean has(String e, String... keys) {
        for (String k : keys) if (e.contains(normalize(k))) return true;
        return false;
    }

    private static void put(LinkedHashMap<String, Integer> s, LinkedHashMap<String, StringBuilder> r, String cls, int delta, String why) {
        cls = remapToDatasetClass(cls);

        // Patch 29A: router candidates must be real dataset classes only.
        // Internal/non-dataset labels may add evidence, but cannot become final classes.
        if (DatasetClasses.isForbidden(cls) || !DatasetClasses.isValid(cls)) return;

        Integer old = s.get(cls);
        if (old == null) old = 0;
        s.put(cls, old + delta);

        StringBuilder b = r.get(cls);
        if (b != null) {
            if (b.length() > 0) b.append("; ");
            b.append(why);
        }
    }

    private static String remapToDatasetClass(String cls) {
        if (cls == null) return "";
        String c = cls.trim();

        // Non-dataset object aliases.
        if ("vehicle".equals(c)) return "product_object";
        if ("weapon".equals(c)) return "product_object";
        if ("food".equals(c)) return DatasetClasses.isValid("still_life") ? "still_life" : "product_object";
        if ("fashion_clothing".equals(c)) return "human_body_fullbody";
        if ("plant_flower".equals(c)) return "landscape_environment";
        if ("thumbnail_cover".equals(c)) return "ui_screenshot";
        if ("map".equals(c)) return "diagram_chart";

        // Non-dataset scene/material aliases.
        if ("interior_room".equals(c)) return "architecture_hardsurface";
        if ("material_texture".equals(c)) return "texture_pattern";

        // Internal fallback aliases.
        if ("unknown".equals(c)) return "photo_general";

        return c;
    }

    private static String extractEvidence(Object obj) {
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder();

        if (obj instanceof Iterable) {
            for (Object x : (Iterable<?>) obj) sb.append(" ").append(extractOne(x));
            return sb.toString();
        }

        sb.append(" ").append(extractOne(obj));
        return sb.toString();
    }

    private static String extractOne(Object obj) {
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder();

        String[] fields = {"text", "label", "name", "className", "category", "type", "title", "displayName"};
        for (String f : fields) {
            try {
                Field field = obj.getClass().getDeclaredField(f);
                field.setAccessible(true);
                Object v = field.get(obj);
                if (v != null) sb.append(" ").append(v);
            } catch (Throwable ignored) {}
        }

        String[] methods = {"getText", "getLabel", "getName", "getClassName", "getCategory", "getType", "getTitle"};
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
}
