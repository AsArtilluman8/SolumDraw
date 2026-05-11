package com.solum.draw.cv;

public final class CvRegion {
    public final String role;
    public final float left;
    public final float top;
    public final float width;
    public final float height;
    public final float cx;
    public final float cy;
    public final int area;
    public final float importance;

    public CvRegion(String role, float left, float top, float width, float height, int area, float importance) {
        this.role = role;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.cx = left + width * 0.5f;
        this.cy = top + height * 0.5f;
        this.area = area;
        this.importance = importance;
    }

    public String toJson(String indent) {
        return indent + "{"
                + "\"role\":\"" + escape(role) + "\","
                + "\"left\":" + left + ","
                + "\"top\":" + top + ","
                + "\"width\":" + width + ","
                + "\"height\":" + height + ","
                + "\"cx\":" + cx + ","
                + "\"cy\":" + cy + ","
                + "\"area\":" + area + ","
                + "\"importance\":" + importance
                + "}";
    }

    private static String escape(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
