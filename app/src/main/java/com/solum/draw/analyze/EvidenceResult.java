package com.solum.draw.analyze;

import java.util.ArrayList;
import java.util.List;

public final class EvidenceResult {
    public final String className;
    public float score;
    public float confidence;
    public final List<String> positive = new ArrayList<>();
    public final List<String> negative = new ArrayList<>();

    public EvidenceResult(String className) {
        this.className = className;
    }

    public EvidenceResult add(float amount, String why) {
        score += amount;
        if (why != null && why.length() > 0) positive.add(why);
        return this;
    }

    public EvidenceResult sub(float amount, String why) {
        score -= amount;
        if (why != null && why.length() > 0) negative.add(why);
        return this;
    }

    public EvidenceResult finish(float confidenceHint) {
        score = clamp01(score);
        confidence = clamp01(score * 0.72f + confidenceHint * 0.28f);
        return this;
    }

    public String compact() {
        return className + "=" + Math.round(score * 100f) + "%";
    }

    public String whyShort() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < positive.size() && i < 3; i++) {
            if (b.length() > 0) b.append("; ");
            b.append("+").append(positive.get(i));
        }
        for (int i = 0; i < negative.size() && i < 2; i++) {
            if (b.length() > 0) b.append("; ");
            b.append("-").append(negative.get(i));
        }
        return b.toString();
    }

    public String toJson() {
        return "{\"class\":\"" + esc(className) + "\",\"score\":" + num(score)
                + ",\"confidence\":" + num(confidence) + ",\"positive\":" + array(positive)
                + ",\"negative\":" + array(negative) + "}";
    }

    private static String array(List<String> list) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(',');
            b.append("\"").append(esc(list.get(i))).append("\"");
        }
        b.append(']');
        return b.toString();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static String num(float v) { return String.format(java.util.Locale.US, "%.4f", v); }
    private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
}
