package com.solum.draw.planner;

import android.graphics.Color;
import android.graphics.PointF;
import java.util.Locale;

public final class LiveRouteJson {
    private LiveRouteJson() {}

    public static String toJson(StrokePlan plan, int speedMultiplier) {
        if (plan == null) return "{}";
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"version\": \"30c_live_route_v2\",\n");
        b.append("  \"sourceWidth\": ").append(plan.sourceWidth).append(",\n");
        b.append("  \"sourceHeight\": ").append(plan.sourceHeight).append(",\n");
        b.append("  \"mode\": \"").append(esc(plan.mode)).append("\",\n");
        b.append("  \"speedMultiplier\": ").append(speedMultiplier).append(",\n");
        b.append("  \"totalActions\": ").append(plan.actions.size()).append(",\n");
        b.append("  \"estimatedMs\": ").append(estimatedMs(plan, speedMultiplier)).append(",\n");
        b.append("  \"actions\": [\n");

        for (int i = 0; i < plan.actions.size(); i++) {
            StrokeAction a = plan.actions.get(i);
            PointF p0 = firstPoint(a);
            PointF p1 = lastPoint(a);

            b.append("    {");
            b.append("\"i\":").append(i).append(',');
            b.append("\"stage\":\"").append(esc(a.stage)).append("\",");
            b.append("\"startX\":").append(num(p0.x)).append(',');
            b.append("\"startY\":").append(num(p0.y)).append(',');
            b.append("\"endX\":").append(num(p1.x)).append(',');
            b.append("\"endY\":").append(num(p1.y)).append(',');
            b.append("\"durationMs\":").append(durationFor(a, speedMultiplier)).append(',');
            b.append("\"brushSize\":").append(num(a.size)).append(',');
            b.append("\"color\":\"").append(colorHex(a.color)).append("\"");
            b.append("}");
            if (i + 1 < plan.actions.size()) b.append(',');
            b.append('\n');
        }

        b.append("  ]\n");
        b.append("}\n");
        return b.toString();
    }

    public static long estimatedMs(StrokePlan plan, int speedMultiplier) {
        if (plan == null) return 0L;
        long total = 0L;
        for (StrokeAction a : plan.actions) total += durationFor(a, speedMultiplier);
        return total;
    }

    private static int durationFor(StrokeAction a, int speedMultiplier) {
        int speed = Math.max(1, speedMultiplier);
        int base;
        if (a == null || a.stage == null) base = 14;
        else if (a.stage.startsWith("SCULPTOR")) base = 18;
        else if (a.stage.startsWith("POTTER")) base = 14;
        else if (a.stage.startsWith("GRINDER")) base = 10;
        else if (a.stage.startsWith("POLISHER")) base = 8;
        else base = 12;
        return Math.max(1, base / speed);
    }

    private static PointF firstPoint(StrokeAction a) {
        if (a == null || a.path == null || a.path.isEmpty()) return new PointF(0f, 0f);
        return a.path.get(0);
    }

    private static PointF lastPoint(StrokeAction a) {
        if (a == null || a.path == null || a.path.isEmpty()) return new PointF(0f, 0f);
        return a.path.get(a.path.size() - 1);
    }

    private static String colorHex(int c) {
        return String.format(Locale.US, "#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c));
    }

    private static String num(float v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
