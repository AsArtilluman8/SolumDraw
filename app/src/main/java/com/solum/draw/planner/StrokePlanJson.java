package com.solum.draw.planner;

import android.graphics.PointF;

public final class StrokePlanJson {
    private StrokePlanJson() {}

    public static String toJson(StrokePlan plan) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"app\": \"SolumDraw\",\n");
        out.append("  \"patch\": \"01_clean_foundation\",\n");
        out.append("  \"mode\": \"").append(plan.mode).append("\",\n");
        out.append("  \"sourceWidth\": ").append(plan.sourceWidth).append(",\n");
        out.append("  \"sourceHeight\": ").append(plan.sourceHeight).append(",\n");
        out.append("  \"actions\": [\n");
        for (int i = 0; i < plan.actions.size(); i++) {
            StrokeAction action = plan.actions.get(i);
            out.append("    {");
            out.append("\"stage\": \"").append(action.stage).append("\", ");
            out.append("\"color\": \"#").append(String.format("%06X", 0xFFFFFF & action.color)).append("\", ");
            out.append("\"size\": ").append(action.size).append(", ");
            out.append("\"path\": [");
            for (int j = 0; j < action.path.size(); j++) {
                PointF p = action.path.get(j);
                out.append("[").append(Math.round(p.x)).append(",").append(Math.round(p.y)).append("]");
                if (j + 1 < action.path.size()) out.append(",");
            }
            out.append("]}");
            if (i + 1 < plan.actions.size()) out.append(",");
            out.append("\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }
}
