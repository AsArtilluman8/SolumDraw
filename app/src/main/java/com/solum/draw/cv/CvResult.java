package com.solum.draw.cv;

import java.util.ArrayList;
import java.util.List;

public final class CvResult {
    public final int width;
    public final int height;
    public final float edgeDensity;
    public final float contourDensity;
    public final int islandCount;
    public final List<CvRegion> regions;
    public final String backendName;
    public final String summary;

    public CvResult(int width, int height, float edgeDensity, float contourDensity, int islandCount, List<CvRegion> regions, String backendName, String summary) {
        this.width = width;
        this.height = height;
        this.edgeDensity = edgeDensity;
        this.contourDensity = contourDensity;
        this.islandCount = islandCount;
        this.regions = regions == null ? new ArrayList<CvRegion>() : regions;
        this.backendName = backendName;
        this.summary = summary;
    }

    public String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"backend\": \"").append(escape(backendName)).append("\",\n");
        b.append("  \"width\": ").append(width).append(",\n");
        b.append("  \"height\": ").append(height).append(",\n");
        b.append("  \"edgeDensity\": ").append(edgeDensity).append(",\n");
        b.append("  \"contourDensity\": ").append(contourDensity).append(",\n");
        b.append("  \"islandCount\": ").append(islandCount).append(",\n");
        b.append("  \"summary\": \"").append(escape(summary)).append("\",\n");
        b.append("  \"regions\": [\n");
        for (int i = 0; i < regions.size(); i++) {
            if (i > 0) b.append(",\n");
            b.append(regions.get(i).toJson("    "));
        }
        b.append("\n  ]\n}");
        return b.toString();
    }

    private static String escape(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
