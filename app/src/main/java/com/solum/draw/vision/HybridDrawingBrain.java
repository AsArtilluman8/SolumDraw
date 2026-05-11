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
                    " | UI " + uiScore +
                    " | текст " + logoTextScore +
                    " | объект " + objectScore +
                    " | sketch " + sketchScore;
        }

        public String compactRu() {
            return "Тип: " + genreRu + " | " + scoresLineRu();
        }
    }

    private HybridDrawingBrain() {}

    public static Result analyze(VisionResult input) {
        if (input == null) {
            return unknown("нет VisionResult");
        }

        int character = 0;
        int environment = 0;
        int ui = 0;
        int logoText = 0;
        int object = 0;
        int sketch = 0;

        StringBuilder reason = new StringBuilder();

        for (VisionLabel l : input.labels) {
            String t = norm(l.text);
            int w = weight(l.confidence);

            if (hasAny(t, "person", "people", "face", "portrait", "human", "girl", "boy", "woman", "man", "child", "fiction", "jacket", "shirt", "clothing", "hair")) {
                character += w;
                reason.append("+character:").append(l.shortText()).append(" ");
            }
            if (hasAny(t, "anime", "manga", "cartoon", "drawing", "illustration", "comic", "fiction")) {
                character += w / 2;
                sketch += w / 2;
                reason.append("+stylized:").append(l.shortText()).append(" ");
            }
            if (hasAny(t, "road", "street", "building", "sky", "mountain", "rock", "tree", "forest", "landscape", "place", "house", "room", "city", "flowerpot", "chair", "tire", "monument")) {
                environment += w;
                reason.append("+environment:").append(l.shortText()).append(" ");
            }
            if (hasAny(t, "text", "font", "screenshot", "software", "display", "web", "menu", "button", "document")) {
                ui += w;
                logoText += w / 2;
                reason.append("+ui/text:").append(l.shortText()).append(" ");
            }
            if (hasAny(t, "logo", "symbol", "brand", "icon", "trademark")) {
                logoText += w;
                reason.append("+logo:").append(l.shortText()).append(" ");
            }
            if (hasAny(t, "object", "product", "toy", "weapon", "vehicle", "car", "dog", "cat", "animal", "food", "plant")) {
                object += w;
                reason.append("+object:").append(l.shortText()).append(" ");
            }
            if (hasAny(t, "sketch", "line", "lineart", "pencil", "ink", "drawing", "illustration", "cartoon", "comic")) {
                sketch += w;
                reason.append("+sketch:").append(l.shortText()).append(" ");
            }
        }

        for (VisionObject o : input.objects) {
            String t = norm(o.label);
            int w = weight(o.confidence);

            if (hasAny(t, "person", "face", "human", "clothing", "fiction", "jacket")) {
                character += w;
            } else if (hasAny(t, "place", "building", "sky", "road", "mountain", "rock", "tree")) {
                environment += Math.max(8, w / 2);
            } else if (hasAny(t, "text", "logo", "symbol")) {
                logoText += w;
            } else {
                object += Math.max(10, w / 2);
            }
        }

        // Heuristics when ML Kit is vague.
        if (input.objects.isEmpty() && character >= 45) {
            character += 20; // anime/person often has labels but no bbox
        }
        if (environment >= 55 && character < 40 && input.objects.size() >= 2) {
            environment += 10;
        }
        if (logoText >= 60) {
            ui += 10;
        }

        Map<String, Integer> scores = new HashMap<>();
        scores.put("character", character);
        scores.put("environment", environment);
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
                    "unknown",
                    "неясно",
                    character, environment, ui, logoText, object, sketch,
                    "крупные массы -> силуэт -> тени -> детали",
                    "мелкий шум, слабые линии",
                    cut(reason.toString())
            );
        }

        String order;
        String ignore;
        String ru;

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

        return new Result(best, ru, character, environment, ui, logoText, object, sketch, order, ignore, cut(reason.toString()));
    }

    public static float objectMainScore(VisionObject object) {
        if (object == null) return 0f;

        String label = norm(object.label);
        float area = object.area();
        float center = object.centerScore();
        float score = area * 0.35f + center * 0.40f + object.confidence * 0.25f;

        // Too-large boxes are usually background/scene/place.
        if (area > 0.62f) score *= 0.55f;
        if (area > 0.82f) score *= 0.35f;

        // Background-like labels should not dominate the drawing plan.
        if (hasAny(label, "place", "sky", "road", "building", "mountain", "rock", "tree", "forest", "room", "city")) {
            score *= 0.62f;
        }

        // Object/person-ish labels get priority.
        if (hasAny(label, "person", "face", "human", "animal", "dog", "cat", "object", "vehicle", "clothing", "fiction", "jacket")) {
            score *= 1.22f;
        }

        // Small central object can be important, e.g. rider/person.
        if (area < 0.08f && center > 0.68f) {
            score *= 1.35f;
        }

        return Math.max(0f, Math.min(1f, score));
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).trim();
    }

    private static boolean hasAny(String s, String... parts) {
        for (String p : parts) {
            if (s.contains(p)) return true;
        }
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
                "unknown",
                "неясно",
                0, 0, 0, 0, 0, 0,
                "крупные массы -> силуэт -> тени -> детали",
                "шум",
                reason
        );
    }
}
