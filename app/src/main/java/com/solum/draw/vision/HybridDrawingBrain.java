package com.solum.draw.vision;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HybridDrawingBrain {
    public static final class Result {
        public final String genreKey;
        public final String genreRu;
        public final int characterScore;
        public final int environmentScore;
        public final int architectureScore;
        public final int uiScore;
        public final int logoTextScore;
        public final int objectScore;
        public final int sketchScore;
        public final String drawOrderRu;
        public final String ignoreRu;
        public final String reasonRu;

        Result(
                String genreKey,
                String genreRu,
                int characterScore,
                int environmentScore,
                int architectureScore,
                int uiScore,
                int logoTextScore,
                int objectScore,
                int sketchScore,
                String drawOrderRu,
                String ignoreRu,
                String reasonRu
        ) {
            this.genreKey = genreKey;
            this.genreRu = genreRu;
            this.characterScore = clamp100(characterScore);
            this.environmentScore = clamp100(environmentScore);
            this.architectureScore = clamp100(architectureScore);
            this.uiScore = clamp100(uiScore);
            this.logoTextScore = clamp100(logoTextScore);
            this.objectScore = clamp100(objectScore);
            this.sketchScore = clamp100(sketchScore);
            this.drawOrderRu = drawOrderRu;
            this.ignoreRu = ignoreRu;
            this.reasonRu = reasonRu;
        }

        public String scoresLineRu() {
            return "scores: персонаж " + characterScore +
                    " | среда " + environmentScore +
                    " | архитектура " + architectureScore +
                    " | UI " + uiScore +
                    " | текст " + logoTextScore +
                    " | объект " + objectScore +
                    " | sketch " + sketchScore;
        }

        public String compactRu() {
            return "Тип: " + genreRu + " | " + scoresLineRu();
        }

        public boolean isCharacter() {
            return "character".equals(genreKey);
        }

        public boolean isEnvironmentLike() {
            return "environment".equals(genreKey) || "architecture".equals(genreKey);
        }
    }

    private HybridDrawingBrain() {}

    public static Result analyze(VisionResult input) {
        if (input == null) return unknown("нет VisionResult");

        int character = 0;
        int environment = 0;
        int architecture = 0;
        int ui = 0;
        int logoText = 0;
        int object = 0;
        int sketch = 0;

        StringBuilder reason = new StringBuilder();

        for (VisionLabel l : input.labels) {
            String t = norm(l.text);
            int w = weight(l.confidence);

            if (isCharacterLabel(t)) {
                character += w;
                reason.append("+character:").append(l.shortText()).append(" ");
            }

            if (isStylizedLabel(t)) {
                character += w / 2;
                sketch += w / 2;
                reason.append("+stylized:").append(l.shortText()).append(" ");
            }

            if (isEnvironmentLabel(t)) {
                environment += w;
                reason.append("+environment:").append(l.shortText()).append(" ");
            }

            if (isArchitectureLabel(t)) {
                architecture += w;
                environment += w / 2;
                reason.append("+architecture:").append(l.shortText()).append(" ");
            }

            if (isUiLabel(t)) {
                ui += w;
                logoText += w / 2;
                reason.append("+ui/text:").append(l.shortText()).append(" ");
            }

            if (isLogoTextLabel(t)) {
                logoText += w;
                reason.append("+logo/text:").append(l.shortText()).append(" ");
            }

            if (isObjectLabel(t)) {
                object += w;
                reason.append("+object:").append(l.shortText()).append(" ");
            }

            if (isSketchLabel(t)) {
                sketch += w;
                reason.append("+sketch:").append(l.shortText()).append(" ");
            }
        }

        for (VisionObject o : input.objects) {
            String t = norm(o.label);
            int w = weight(o.confidence);

            if (isCharacterLabel(t)) {
                character += w;
            } else if (isArchitectureLabel(t)) {
                architecture += Math.max(10, w);
                environment += Math.max(8, w / 2);
                object -= Math.max(0, w / 3);
            } else if (isEnvironmentLabel(t)) {
                environment += Math.max(8, w / 2);
                object -= Math.max(0, w / 4);
            } else if (isLogoTextLabel(t)) {
                logoText += w;
            } else if (isObjectLabel(t)) {
                object += Math.max(10, w / 2);
            } else {
                object += Math.max(8, w / 3);
            }
        }

        // Character/anime often gives good labels but no bbox.
        if (input.objects.isEmpty() && character >= 45) {
            character += 20;
            sketch += 10;
        }

        // Architecture must beat generic object if labels are Building/Roof/House/Home/Place.
        if (architecture >= 45 && object >= architecture - 10) {
            object = Math.max(0, object - 30);
            environment += 10;
        }

        // Environment with a small central bbox should stay environment, not become generic object.
        if (environment >= 60 && object >= 60 && hasSmallCentralHero(input.objects)) {
            object -= 22;
            character += 10;
            reason.append("+small-central-hero ");
        }

        // Forest/landscape with dog/object false positive should stay environment.
        if (environment >= 70 && character < 45 && architecture < 40) {
            object = Math.min(object, 58);
        }

        // UI/text dominates only when explicit.
        if (logoText >= 60) ui += 10;

        Map<String, Integer> scores = new HashMap<>();
        scores.put("character", character);
        scores.put("environment", environment);
        scores.put("architecture", architecture);
        scores.put("ui", ui);
        scores.put("logo_text", logoText);
        scores.put("object", object);
        scores.put("sketch", sketch);

        String best = "unknown";
        int bestScore = 0;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                best = e.getKey();
                bestScore = e.getValue();
            }
        }

        if (bestScore < 25) {
            return new Result(
                    "unknown", "неясно",
                    character, environment, architecture, ui, logoText, object, sketch,
                    "крупные массы -> силуэт -> тени -> детали",
                    "мелкий шум, слабые линии",
                    cut(reason.toString())
            );
        }

        String ru;
        String order;
        String ignore;

        switch (best) {
            case "character":
                ru = "персонаж / портрет";
                order = "силуэт -> голова/лицо -> волосы -> корпус/одежда -> тени -> детали";
                ignore = "случайный фон, watermark/text, мелкий шум кожи";
                break;
            case "environment":
                ru = "окружение / пейзаж";
                order = "горизонт/перспектива -> большие массы -> главный объект -> свет/тень -> детали";
                ignore = "мелкие листья/камни/шум, слабые дальние контуры";
                break;
            case "architecture":
                ru = "архитектура / здания";
                order = "крупные блоки -> крыши/стены -> окна/двери -> тени -> материалы/детали";
                ignore = "фон, мелкий шум текстур, повторяющиеся слабые линии";
                break;
            case "ui":
                ru = "интерфейс / скрин";
                order = "layout-сетка -> панели -> кнопки -> текстовые блоки -> иконки";
                ignore = "фото-шум и декоративные текстуры";
                break;
            case "logo_text":
                ru = "логотип / символ / текст";
                order = "внешний силуэт -> крупные формы -> внутренние линии -> текст/символы";
                ignore = "фон и случайные полутона";
                break;
            case "object":
                ru = "предмет / объект";
                order = "внешний силуэт -> основные объёмы -> тени -> материал -> детали";
                ignore = "фоновые области и неважные мелкие текстуры";
                break;
            case "sketch":
                ru = "скетч / line-art";
                order = "главные линии -> пропорции -> вторичные линии -> штриховка -> акценты";
                ignore = "цветовые полутона, слабые пятна";
                break;
            default:
                ru = "неясно";
                order = "крупные массы -> силуэт -> тени -> детали";
                ignore = "шум";
                break;
        }

        return new Result(best, ru, character, environment, architecture, ui, logoText, object, sketch, order, ignore, cut(reason.toString()));
    }

    public static float objectMainScore(VisionObject object) {
        if (object == null) return 0f;

        String label = norm(object.label);
        float area = object.area();
        float center = object.centerScore();
        float score = area * 0.32f + center * 0.43f + object.confidence * 0.25f;

        if (area > 0.62f) score *= 0.55f;
        if (area > 0.82f) score *= 0.35f;

        if (isBackgroundObjectLabel(label)) score *= 0.58f;
        if (isArchitectureLabel(label)) score *= 0.78f;
        if (isCharacterLabel(label) || isObjectLabel(label)) score *= 1.24f;

        // Tiny but central hero/person/rider/object in a landscape.
        if (area < 0.10f && center > 0.62f) score *= 1.42f;

        // Fallback character bbox should be visible but not pretend to be precise.
        if (label.contains("character fallback")) score *= 0.82f;

        return Math.max(0f, Math.min(1f, score));
    }

    public static int characterLabelScore(List<VisionLabel> labels) {
        int out = 0;
        if (labels == null) return 0;
        for (VisionLabel l : labels) {
            String t = norm(l.text);
            if (isCharacterLabel(t) || isStylizedLabel(t)) out += weight(l.confidence);
        }
        return clamp100(out);
    }

    public static boolean shouldCreateCharacterFallback(List<VisionLabel> labels, List<VisionObject> objects) {
        if (objects != null && !objects.isEmpty()) return false;
        return characterLabelScore(labels) >= 45;
    }

    private static boolean hasSmallCentralHero(List<VisionObject> objects) {
        if (objects == null) return false;
        for (VisionObject o : objects) {
            if (o.area() < 0.10f && o.centerScore() > 0.62f) return true;
        }
        return false;
    }

    private static boolean isCharacterLabel(String t) {
        return hasAny(t, "person", "people", "face", "portrait", "human", "girl", "boy", "woman", "man", "child",
                "fiction", "jacket", "shirt", "clothing", "hair", "muscle", "mouth", "flesh", "eyelash",
                "swimwear", "skin", "head", "eye", "nose", "lip");
    }

    private static boolean isStylizedLabel(String t) {
        return hasAny(t, "anime", "manga", "cartoon", "drawing", "illustration", "comic", "fiction");
    }

    private static boolean isEnvironmentLabel(String t) {
        return hasAny(t, "road", "street", "sky", "mountain", "rock", "tree", "forest", "landscape", "place",
                "room", "city", "flowerpot", "plant", "jungle", "branch", "trunk", "grass", "field", "hill",
                "valley", "water", "river", "lake", "cloud");
    }

    private static boolean isArchitectureLabel(String t) {
        return hasAny(t, "building", "roof", "house", "home", "hut", "cabin", "castle", "tower", "wall",
                "window", "door", "monument", "church", "temple", "architecture");
    }

    private static boolean isUiLabel(String t) {
        return hasAny(t, "text", "font", "screenshot", "software", "display", "web", "menu", "button", "document",
                "screen", "interface", "panel", "toolbar");
    }

    private static boolean isLogoTextLabel(String t) {
        return hasAny(t, "logo", "symbol", "brand", "icon", "trademark", "text", "font", "glyph");
    }

    private static boolean isObjectLabel(String t) {
        return hasAny(t, "object", "product", "toy", "weapon", "vehicle", "car", "dog", "cat", "animal", "food");
    }

    private static boolean isSketchLabel(String t) {
        return hasAny(t, "sketch", "line", "lineart", "pencil", "ink", "drawing", "illustration", "cartoon", "comic");
    }

    private static boolean isBackgroundObjectLabel(String t) {
        return hasAny(t, "place", "sky", "road", "building", "mountain", "rock", "tree", "forest", "room", "city",
                "roof", "house", "home", "plant", "jungle", "branch", "trunk");
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).trim();
    }

    private static boolean hasAny(String s, String... parts) {
        for (String p : parts) if (s.contains(p)) return true;
        return false;
    }

    private static int weight(float confidence) {
        return Math.max(5, Math.min(100, Math.round(confidence * 100f)));
    }

    private static int clamp100(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static String cut(String s) {
        if (s == null || s.length() == 0) return "нет сильных признаков";
        return s.length() > 180 ? s.substring(0, 180) + "..." : s;
    }

    private static Result unknown(String reason) {
        return new Result(
                "unknown", "неясно",
                0, 0, 0, 0, 0, 0, 0,
                "крупные массы -> силуэт -> тени -> детали",
                "шум",
                reason
        );
    }
}
