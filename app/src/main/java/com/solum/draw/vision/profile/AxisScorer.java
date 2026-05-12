package com.solum.draw.vision.profile;

import java.lang.reflect.Field;
import java.util.Locale;

public final class AxisScorer {
    private AxisScorer() {}

    public static void score(ImageProfile profile, Object labels, Object objects, String hint) {
        if (profile == null) return;

        String bag = (
                safe(hint) + " " +
                flatten(labels) + " " +
                flatten(objects) + " " +
                safe(profile.rawPredicted)
        ).toLowerCase(Locale.US);

        VisualFeatureVector f = profile.features == null ? new VisualFeatureVector() : profile.features;

        scoreQuality(profile, f);
        scoreStyle(profile, f, bag);
        scoreContent(profile, f, bag);
        scorePurpose(profile, f, bag);

        profile.axesDone = true;
    }

    private static void scoreQuality(ImageProfile p, VisualFeatureVector f) {
        if (f.sharpness < 0.35f && f.edgeDensity > 0.25f) {
            p.qualityAxis = ImageAxes.QualityAxis.NOISY_COMPRESSED;
            p.qualityAxisConf = clamp01((0.35f - f.sharpness) / 0.35f);
            return;
        }
        if (f.sharpness < 0.28f) {
            p.qualityAxis = ImageAxes.QualityAxis.SKETCH_ROUGH;
            p.qualityAxisConf = clamp01((0.28f - f.sharpness) / 0.28f);
            return;
        }
        p.qualityAxis = ImageAxes.QualityAxis.CLEAN;
        p.qualityAxisConf = clamp01(0.45f + f.sharpness * 0.45f);
    }

    private static void scoreStyle(ImageProfile p, VisualFeatureVector f, String bag) {
        float lineart = 0f, painterly = 0f, flat = 0f, pixel = 0f, photo = 0f, abs = 0f, textured = 0f;

        if (f.edgeDensity > 0.35f) lineart += 0.35f;
        if (f.hardLineScore > 0.35f) lineart += 0.25f;
        if (f.saturation < 0.12f && f.edgeDensity > 0.18f) lineart += 0.20f;

        if (f.edgeDensity < 0.28f) painterly += 0.25f;
        if (f.colorEntropy > 0.45f) painterly += 0.20f;
        if (f.glowScore > 0.18f) painterly += 0.20f;
        if (hasAny(bag, "painting", "paint", "art", "illustration")) painterly += 0.18f;

        if (f.colorEntropy < 0.35f && f.saturation > 0.20f) flat += 0.25f;
        if (hasAny(bag, "cartoon", "comic", "clip art", "vector", "graphic")) flat += 0.25f;

        if (hasAny(bag, "pixel", "sprite", "8-bit", "8 bit")) pixel += 0.50f;
        if (f.edgeDensity > 0.45f && f.colorEntropy < 0.40f) pixel += 0.15f;

        if (hasAny(bag, "photo", "photograph", "photography", "camera")) photo += 0.45f;
        if (f.sharpness > 0.55f && f.edgeDensity < 0.24f) photo += 0.15f;

        if (f.colorEntropy > 0.68f && f.hardLineScore < 0.25f) abs += 0.35f;
        if (hasAny(bag, "abstract")) abs += 0.40f;

        if (f.edgeDensity > 0.30f && f.colorEntropy > 0.55f) textured += 0.25f;
        if (hasAny(bag, "texture", "pattern", "fabric", "wallpaper")) textured += 0.30f;

        float[] vals = new float[]{lineart, painterly, flat, pixel, photo, abs, textured};
        int best = bestIndex(vals);
        if (vals[best] < 0.15f) {
            p.styleAxis = ImageAxes.StyleAxis.UNKNOWN;
            p.styleAxisConf = 0f;
            return;
        }

        switch (best) {
            case 0: p.styleAxis = ImageAxes.StyleAxis.LINEART; break;
            case 1: p.styleAxis = ImageAxes.StyleAxis.PAINTERLY; break;
            case 2: p.styleAxis = ImageAxes.StyleAxis.FLAT_VECTOR; break;
            case 3: p.styleAxis = ImageAxes.StyleAxis.PIXEL; break;
            case 4: p.styleAxis = ImageAxes.StyleAxis.PHOTO_REALISTIC; break;
            case 5: p.styleAxis = ImageAxes.StyleAxis.ABSTRACT; break;
            case 6: p.styleAxis = ImageAxes.StyleAxis.TEXTURED; break;
            default: p.styleAxis = ImageAxes.StyleAxis.UNKNOWN; break;
        }
        p.styleAxisConf = confidence(vals);
    }

    private static void scoreContent(ImageProfile p, VisualFeatureVector f, String bag) {
        float character = 0f, creature = 0f, landscape = 0f, architecture = 0f, ui = 0f, diagram = 0f;
        float logo = 0f, pattern = 0f, text = 0f, product = 0f, vfx = 0f;

        if (hasAny(bag, "person", "human", "face", "hair", "skin", "woman", "man", "portrait", "anime", "manga")) character += 0.45f;
        if (hasAny(bag, "animal", "creature", "dog", "cat", "bird", "horse", "fish", "wolf")) creature += 0.55f;

        if (hasAny(bag, "landscape", "mountain", "forest", "sky", "tree", "river", "lake", "sea", "nature")) landscape += 0.45f;
        if (f.colorEntropy > 0.45f && f.hardLineScore < 0.25f && f.edgeDensity < 0.35f) landscape += 0.15f;

        if (hasAny(bag, "architecture", "building", "house", "tower", "castle", "room", "window", "door")) architecture += 0.45f;
        if (f.hardLineScore > 0.35f) architecture += 0.35f;

        if (hasAny(bag, "screenshot", "screen", "app", "mobile", "interface", "button", "menu", "toolbar")) ui += 0.45f;
        if (f.hardLineScore > 0.30f && f.edgeDensity > 0.20f) ui += 0.18f;

        if (hasAny(bag, "diagram", "chart", "graph", "arrow", "flow", "infographic")) diagram += 0.55f;
        if (f.hardLineScore > 0.45f && f.saturation < 0.25f) diagram += 0.18f;

        if (hasAny(bag, "logo", "icon", "symbol", "emblem")) logo += 0.55f;
        if (f.colorEntropy < 0.30f && f.edgeDensity > 0.18f) logo += 0.12f;

        if (hasAny(bag, "pattern", "texture", "seamless", "wallpaper", "ornament")) pattern += 0.55f;
        if (f.edgeDensity > 0.30f && f.colorEntropy > 0.50f && f.hardLineScore < 0.25f) pattern += 0.12f;

        if (hasAny(bag, "text", "document", "paper", "font", "letter", "writing")) text += 0.55f;

        if (hasAny(bag, "product", "object", "tool", "item")) product += 0.35f;
        if (f.edgeDensity > 0.18f && f.hardLineScore < 0.20f && f.colorEntropy < 0.55f) product += 0.12f;

        if (hasAny(bag, "glow", "magic", "fire", "flame", "energy", "lightning", "neon", "laser")) vfx += 0.55f;
        if (f.glowScore > 0.28f) vfx += 0.45f;

        float[] vals = new float[]{character, creature, landscape, architecture, ui, diagram, logo, pattern, text, product, vfx};
        int best = bestIndex(vals);
        if (vals[best] < 0.15f) {
            p.contentAxis = ImageAxes.ContentAxis.UNKNOWN;
            p.contentAxisConf = 0f;
            return;
        }

        switch (best) {
            case 0: p.contentAxis = ImageAxes.ContentAxis.CHARACTER; break;
            case 1: p.contentAxis = ImageAxes.ContentAxis.CREATURE; break;
            case 2: p.contentAxis = ImageAxes.ContentAxis.LANDSCAPE; break;
            case 3: p.contentAxis = ImageAxes.ContentAxis.ARCHITECTURE; break;
            case 4: p.contentAxis = ImageAxes.ContentAxis.UI_ELEMENT; break;
            case 5: p.contentAxis = ImageAxes.ContentAxis.DIAGRAM; break;
            case 6: p.contentAxis = ImageAxes.ContentAxis.LOGO; break;
            case 7: p.contentAxis = ImageAxes.ContentAxis.PATTERN; break;
            case 8: p.contentAxis = ImageAxes.ContentAxis.TEXT_BLOCK; break;
            case 9: p.contentAxis = ImageAxes.ContentAxis.PRODUCT_OBJECT; break;
            case 10: p.contentAxis = ImageAxes.ContentAxis.VFX_EFFECT; break;
            default: p.contentAxis = ImageAxes.ContentAxis.UNKNOWN; break;
        }
        p.contentAxisConf = confidence(vals);
    }

    private static void scorePurpose(ImageProfile p, VisualFeatureVector f, String bag) {
        float illustration = 0.22f, reference = 0f, ui = 0f, document = 0f, game = 0f, decorative = 0f;

        if (hasAny(bag, "photo", "camera", "photograph")) reference += 0.45f;
        if (hasAny(bag, "screenshot", "app", "interface", "screen", "mobile")) ui += 0.50f;
        if (hasAny(bag, "document", "text", "paper", "letter", "font")) document += 0.50f;
        if (hasAny(bag, "game", "hud", "sprite", "asset", "icon")) game += 0.40f;
        if (hasAny(bag, "pattern", "texture", "wallpaper", "decorative", "ornament")) decorative += 0.45f;

        if (f.hardLineScore > 0.35f && f.edgeDensity > 0.20f) ui += 0.12f;
        if (f.colorEntropy > 0.55f && f.hardLineScore < 0.25f) illustration += 0.10f;

        float[] vals = new float[]{illustration, reference, ui, document, game, decorative};
        int best = bestIndex(vals);
        if (vals[best] < 0.15f) {
            p.purposeAxis = ImageAxes.PurposeAxis.UNKNOWN;
            p.purposeAxisConf = 0f;
            return;
        }

        switch (best) {
            case 0: p.purposeAxis = ImageAxes.PurposeAxis.ILLUSTRATION; break;
            case 1: p.purposeAxis = ImageAxes.PurposeAxis.REFERENCE_PHOTO; break;
            case 2: p.purposeAxis = ImageAxes.PurposeAxis.UI_SCREENSHOT; break;
            case 3: p.purposeAxis = ImageAxes.PurposeAxis.DOCUMENT; break;
            case 4: p.purposeAxis = ImageAxes.PurposeAxis.GAME_ASSET; break;
            case 5: p.purposeAxis = ImageAxes.PurposeAxis.DECORATIVE_PATTERN; break;
            default: p.purposeAxis = ImageAxes.PurposeAxis.UNKNOWN; break;
        }
        p.purposeAxisConf = confidence(vals);
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
            } catch (Throwable ignored) {}
        }
    }

    private static boolean hasAny(String s, String... keys) {
        if (s == null) return false;
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private static int bestIndex(float[] vals) {
        int best = 0;
        for (int i = 1; i < vals.length; i++) if (vals[i] > vals[best]) best = i;
        return best;
    }

    private static float confidence(float[] vals) {
        float best = 0f, second = 0f;
        for (float v : vals) {
            if (v > best) { second = best; best = v; }
            else if (v > second) second = v;
        }
        if (best < 0.15f) return 0f;
        return clamp01((best - second) / Math.max(0.0001f, best + second));
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
