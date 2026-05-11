package com.solum.draw.vision;

import java.util.ArrayList;

public final class VisionDecisionEngine {
    public static final class Decision {
        public final String datasetClass;
        public final int confidence;
        public final String summary;
        public final String topLine;
        public final String reason;

        Decision(String datasetClass, int confidence, String summary, String topLine, String reason) {
            this.datasetClass = datasetClass == null ? "unknown" : datasetClass;
            this.confidence = confidence;
            this.summary = summary == null ? "" : summary;
            this.topLine = topLine == null ? "" : topLine;
            this.reason = reason == null ? "" : reason;
        }
    }

    private VisionDecisionEngine() {}

    public static Decision analyze(Object labels, Object objects, String jsonHint, String extraHint) {
        String hint = safe(jsonHint) + " " + safe(extraHint);

        DatasetClassRouter.Route route = DatasetClassRouter.route(labels, objects, hint);

        StringBuilder top = new StringBuilder();
        ArrayList<DatasetClassRouter.Candidate> list = route.top;
        for (int i = 0; i < list.size(); i++) {
            DatasetClassRouter.Candidate c = list.get(i);
            if (i > 0) top.append(" | ");
            top.append(c.name).append(" ").append(c.score);
        }

        String summary =
                "dataset_class: " + route.predicted + " | score " + route.confidence + "\n" +
                "top5: " + top + "\n" +
                "router_reason: " + cut(route.reason, 260);

        return new Decision(route.predicted, route.confidence, summary, top.toString(), route.reason);
    }

    public static String uiBlock(Object labels, Object objects, String jsonHint, String extraHint) {
        Decision d = analyze(labels, objects, jsonHint, extraHint);
        return d.summary;
    }

    public static String csvLine(Object labels, Object objects, String jsonHint, String extraHint) {
        Decision d = analyze(labels, objects, jsonHint, extraHint);
        return d.datasetClass + "," + d.confidence + "," + quote(d.topLine) + "," + quote(cut(d.reason, 300));
    }

    private static String quote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\"";
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
