package com.solum.draw.analyze;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ImageFeatures {
    private static final int SIDE = 160;

    public final String name;
    public final int sourceWidth;
    public final int sourceHeight;
    public final int analysisWidth;
    public final int analysisHeight;
    public final List<ColorCount> palette;
    public final float edgeDensity;
    public final float detailDensity;
    public final float skinRatio;
    public final float oldTextRatio;
    public final float saturation;
    public final float brightness;
    public final float paletteCompactness;
    public final float darkRatio;
    public final float brightRatio;
    public final float whitePaperRatio;
    public final float blackInkRatio;
    public final float realTextRatio;
    public final float glyphRatio;
    public final float logoScore;
    public final float saliencyDensity;
    public final float centralObjectRatio;
    public final float symmetryVertical;
    public final int componentCount;
    public final int textComponentCount;
    public final int textLineCount;
    public final int largeComponentCount;
    public final float largestComponentRatio;
    public final AnalysisLayers layers;
    public final UiLayoutAnalysis ui;

    private ImageFeatures(String name, int sourceWidth, int sourceHeight, int analysisWidth, int analysisHeight,
            List<ColorCount> palette, float edgeDensity, float detailDensity, float skinRatio, float oldTextRatio,
            float saturation, float brightness, float paletteCompactness, float darkRatio, float brightRatio,
            float whitePaperRatio, float blackInkRatio, float realTextRatio, float glyphRatio, float logoScore,
            float saliencyDensity, float centralObjectRatio, float symmetryVertical, int componentCount,
            int textComponentCount, int textLineCount, int largeComponentCount, float largestComponentRatio,
            AnalysisLayers layers, UiLayoutAnalysis ui) {
        this.name = name;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.analysisWidth = analysisWidth;
        this.analysisHeight = analysisHeight;
        this.palette = palette;
        this.edgeDensity = edgeDensity;
        this.detailDensity = detailDensity;
        this.skinRatio = skinRatio;
        this.oldTextRatio = oldTextRatio;
        this.saturation = saturation;
        this.brightness = brightness;
        this.paletteCompactness = paletteCompactness;
        this.darkRatio = darkRatio;
        this.brightRatio = brightRatio;
        this.whitePaperRatio = whitePaperRatio;
        this.blackInkRatio = blackInkRatio;
        this.realTextRatio = realTextRatio;
        this.glyphRatio = glyphRatio;
        this.logoScore = logoScore;
        this.saliencyDensity = saliencyDensity;
        this.centralObjectRatio = centralObjectRatio;
        this.symmetryVertical = symmetryVertical;
        this.componentCount = componentCount;
        this.textComponentCount = textComponentCount;
        this.textLineCount = textLineCount;
        this.largeComponentCount = largeComponentCount;
        this.largestComponentRatio = largestComponentRatio;
        this.layers = layers;
        this.ui = ui;
    }

    public static ImageFeatures build(Bitmap source, String name) {
        Bitmap small = scale(source, SIDE);
        int w = small.getWidth();
        int h = small.getHeight();
        int n = Math.max(1, w * h);
        AnalysisLayers layers = AnalysisLayers.build(source);
        UiLayoutAnalysis ui = UiLayoutAnalysis.analyze(source);

        Map<Integer, Integer> colorBins = new HashMap<>();
        long edgeSum = 0;
        long detailSum = 0;
        long satSum = 0;
        long brightSum = 0;
        int skinLike = 0;
        int oldTextLike = 0;
        int dark = 0;
        int bright = 0;
        int nearWhite = 0;
        int nearBlack = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = small.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int l = luma(c);
                brightSum += l;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                satSum += max - min;
                if (l < 45) dark++;
                if (l > 210) bright++;
                if (l > 235 && max - min < 18) nearWhite++;
                if (l < 28 && max - min < 18) nearBlack++;
                if (isSkinLike(r, g, b)) skinLike++;
                int bin = Color.rgb((r / 32) * 32, (g / 32) * 32, (b / 32) * 32);
                colorBins.put(bin, colorBins.containsKey(bin) ? colorBins.get(bin) + 1 : 1);

                if (x > 0 && y > 0 && x < w - 1 && y < h - 1) {
                    int e = Math.abs(luma(small.getPixel(x + 1, y)) - luma(small.getPixel(x - 1, y)))
                          + Math.abs(luma(small.getPixel(x, y + 1)) - luma(small.getPixel(x, y - 1)));
                    edgeSum += e;
                    if (e > 90) oldTextLike++;
                    detailSum += Math.min(255, e);
                }
            }
        }

        List<ColorCount> palette = topColors(colorBins, n, 8);
        float edgeDensity = Math.max(clamp01(edgeSum / (float) (n * 255)), layers.edgeDensity);
        float detailDensity = clamp01(detailSum / (float) (n * 170));
        float skinRatio = skinLike / (float) n;
        float oldTextRatio = oldTextLike / (float) n;
        float realTextRatio = Math.max(layers.realTextScore, ui.isUiScreenshot ? Math.min(0.45f, ui.textScore * 0.45f) : 0f);
        float glyphRatio = layers.glyphScore;
        float logoScore = layers.logoScore;
        float darkRatio = dark / (float) n;
        float brightRatio = bright / (float) n;
        float avgSat = satSum / (float) (n * 255);
        float avgBright = brightSum / (float) (n * 255);
        float paletteCompactness = palette.isEmpty() ? 0f : palette.get(0).ratio;
        float whitePaperRatio = nearWhite / (float) n;
        float blackInkRatio = nearBlack / (float) n;

        return new ImageFeatures(name, source.getWidth(), source.getHeight(), w, h, palette, edgeDensity,
                detailDensity, skinRatio, oldTextRatio, avgSat, avgBright, paletteCompactness, darkRatio,
                brightRatio, whitePaperRatio, blackInkRatio, realTextRatio, glyphRatio, logoScore,
                layers.saliencyDensity, layers.centralObjectRatio, layers.symmetryVertical, layers.componentCount,
                layers.textComponentCount, layers.textLineCount, layers.largeComponentCount,
                layers.largestComponentRatio, layers, ui);
    }

    public boolean hasNaturalSceneTexture() {
        return detailDensity > 0.18f && edgeDensity > 0.12f && paletteCompactness < 0.42f && componentCount > 18;
    }

    public boolean hasCharacterSignal() {
        return skinRatio > 0.08f && (centralObjectRatio > 0.10f || symmetryVertical > 0.38f) && edgeDensity > 0.10f;
    }

    private static Bitmap scale(Bitmap source, int maxSide) {
        float scale = Math.min(1f, maxSide / (float) Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, Math.round(source.getWidth() * scale));
        int h = Math.max(1, Math.round(source.getHeight() * scale));
        return Bitmap.createScaledBitmap(source, w, h, true);
    }

    private static List<ColorCount> topColors(Map<Integer, Integer> bins, int total, int maxCount) {
        ArrayList<ColorCount> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : bins.entrySet()) list.add(new ColorCount(e.getKey(), e.getValue(), e.getValue() / (float) total));
        Collections.sort(list, new Comparator<ColorCount>() { @Override public int compare(ColorCount a, ColorCount b) { return b.count - a.count; } });
        if (list.size() > maxCount) return new ArrayList<>(list.subList(0, maxCount));
        return list;
    }

    private static boolean isSkinLike(int r, int g, int b) { return r > 75 && g > 40 && b > 25 && r > g && g > b && (r - b) > 35 && Math.abs(r - g) > 10; }
    private static int luma(int c) { return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
