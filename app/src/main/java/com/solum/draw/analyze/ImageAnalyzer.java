package com.solum.draw.analyze;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ImageAnalyzer {
    private static final int SIDE = 160;

    private ImageAnalyzer() {}

    public static ImageAnalysis analyze(Bitmap source, String name) {
        Bitmap small = scale(source, SIDE);
        int w = small.getWidth();
        int h = small.getHeight();
        int n = Math.max(1, w * h);

        int[] hist = new int[64];
        Map<Integer, Integer> colorBins = new HashMap<>();
        long edgeSum = 0;
        long detailSum = 0;
        long satSum = 0;
        long brightSum = 0;
        int skinLike = 0;
        int textLike = 0;
        int dark = 0;
        int bright = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = small.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int l = luma(c);
                hist[Math.min(63, l / 4)]++;
                brightSum += l;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                satSum += max - min;
                if (l < 45) dark++;
                if (l > 210) bright++;
                if (isSkinLike(r, g, b)) skinLike++;
                int bin = Color.rgb((r / 32) * 32, (g / 32) * 32, (b / 32) * 32);
                colorBins.put(bin, colorBins.containsKey(bin) ? colorBins.get(bin) + 1 : 1);

                if (x > 0 && y > 0 && x < w - 1 && y < h - 1) {
                    int e = Math.abs(luma(small.getPixel(x + 1, y)) - luma(small.getPixel(x - 1, y)))
                          + Math.abs(luma(small.getPixel(x, y + 1)) - luma(small.getPixel(x, y - 1)));
                    edgeSum += e;
                    if (e > 90) textLike++;
                    detailSum += Math.min(255, e);
                }
            }
        }

        List<ColorCount> palette = topColors(colorBins, n, 8);
        float edgeDensity = clamp01(edgeSum / (float) (n * 255));
        float detailDensity = clamp01(detailSum / (float) (n * 170));
        float skinRatio = skinLike / (float) n;
        float textRatio = textLike / (float) n;
        float darkRatio = dark / (float) n;
        float brightRatio = bright / (float) n;
        float avgSat = satSum / (float) (n * 255);
        float avgBright = brightSum / (float) (n * 255);
        float paletteCompactness = palette.isEmpty() ? 0f : palette.get(0).ratio;

        String genre = detectGenre(edgeDensity, detailDensity, skinRatio, textRatio, avgSat, palette.size(), paletteCompactness, darkRatio, brightRatio);
        float confidence = confidenceFor(genre, edgeDensity, detailDensity, skinRatio, textRatio, avgSat, paletteCompactness);
        String strategy = strategyFor(genre, edgeDensity, detailDensity);
        String warnings = warningsFor(edgeDensity, detailDensity, skinRatio, textRatio, paletteCompactness);

        return new ImageAnalysis(name, source.getWidth(), source.getHeight(), w, h, genre, confidence,
                edgeDensity, detailDensity, skinRatio, textRatio, avgSat, avgBright,
                paletteCompactness, darkRatio, brightRatio, palette, strategy, warnings);
    }

    private static Bitmap scale(Bitmap source, int maxSide) {
        float scale = Math.min(1f, maxSide / (float) Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, Math.round(source.getWidth() * scale));
        int h = Math.max(1, Math.round(source.getHeight() * scale));
        return Bitmap.createScaledBitmap(source, w, h, true);
    }

    private static List<ColorCount> topColors(Map<Integer, Integer> bins, int total, int maxCount) {
        ArrayList<ColorCount> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : bins.entrySet()) {
            list.add(new ColorCount(e.getKey(), e.getValue(), e.getValue() / (float) total));
        }
        Collections.sort(list, new Comparator<ColorCount>() {
            @Override public int compare(ColorCount a, ColorCount b) { return b.count - a.count; }
        });
        if (list.size() > maxCount) return new ArrayList<>(list.subList(0, maxCount));
        return list;
    }

    private static String detectGenre(float edge, float detail, float skin, float text, float sat, int colors, float compact, float dark, float bright) {
        if (text > 0.16f && edge > 0.22f) return "ui_or_text_heavy";
        if (skin > 0.10f && edge < 0.28f) return "portrait_or_skin_photo";
        if (colors <= 5 && compact > 0.30f && edge > 0.12f) return "logo_icon_flat";
        if (sat > 0.30f && edge > 0.22f && detail < 0.40f) return "anime_cartoon_flat";
        if (dark > 0.45f && bright < 0.12f) return "dark_cinematic";
        if (edge < 0.12f && detail < 0.18f) return "soft_photo_or_gradient";
        if (detail > 0.42f) return "detailed_noisy_photo";
        return "general_photo_or_illustration";
    }

    private static float confidenceFor(String genre, float edge, float detail, float skin, float text, float sat, float compact) {
        float c = 0.45f;
        if (genre.equals("ui_or_text_heavy")) c = 0.55f + Math.min(0.35f, text);
        else if (genre.equals("portrait_or_skin_photo")) c = 0.50f + Math.min(0.35f, skin * 1.7f);
        else if (genre.equals("logo_icon_flat")) c = 0.50f + Math.min(0.35f, compact);
        else if (genre.equals("anime_cartoon_flat")) c = 0.50f + Math.min(0.25f, sat);
        else if (genre.equals("detailed_noisy_photo")) c = 0.50f + Math.min(0.25f, detail);
        else c = 0.48f + Math.min(0.18f, edge + detail);
        return clamp01(c);
    }

    private static String strategyFor(String genre, float edge, float detail) {
        if (genre.equals("ui_or_text_heavy")) return "background_blocks -> panels -> text_regions -> icons -> polish";
        if (genre.equals("portrait_or_skin_photo")) return "background -> silhouette -> skin_mass -> hair_clothes -> face_details -> polish";
        if (genre.equals("logo_icon_flat")) return "base_shape -> large_fills -> inner_cuts -> clean_edges -> polish";
        if (genre.equals("anime_cartoon_flat")) return "flat_fills -> silhouette -> major_regions -> contour -> eyes/details -> polish";
        if (genre.equals("dark_cinematic")) return "large_dark_masses -> midtones -> bright_accents -> edges -> polish";
        if (detail > 0.42f) return "large_masses -> medium_regions -> detail_clusters -> residual_correction -> polish";
        return "background -> large_masses -> regions -> edges -> residual -> polish";
    }

    private static String warningsFor(float edge, float detail, float skin, float text, float compact) {
        StringBuilder b = new StringBuilder();
        if (text > 0.16f) b.append("text-like zones; ");
        if (skin > 0.10f) b.append("skin/portrait-like zones; ");
        if (detail > 0.45f) b.append("high detail/noise; ");
        if (compact > 0.42f) b.append("dominant background or flat color; ");
        if (edge < 0.08f) b.append("weak edges; ");
        if (b.length() == 0) return "none";
        return b.toString();
    }

    private static boolean isSkinLike(int r, int g, int b) {
        return r > 75 && g > 40 && b > 25 && r > g && g > b && (r - b) > 35 && Math.abs(r - g) > 10;
    }

    private static int luma(int c) {
        return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100;
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
