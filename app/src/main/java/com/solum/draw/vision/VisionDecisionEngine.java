package com.solum.draw.vision;

import com.solum.draw.vision.profile.AxisScorer;
import com.solum.draw.vision.profile.DatasetClasses;
import com.solum.draw.vision.profile.ImageProfile;
import com.solum.draw.vision.profile.ShadowClassRouter;
import com.solum.draw.vision.profile.VisionRouterConfig;
import com.solum.draw.vision.profile.VisualFeatureVector;

import java.util.ArrayList;
import java.util.List;

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
        return analyze(labels, objects, jsonHint, extraHint, null);
    }

    public static Decision analyze(Object labels, Object objects, String jsonHint, String extraHint, VisualFeatureVector features) {
        String hint = safe(jsonHint) + " " + safe(extraHint);

        DatasetClassRouter.Route route = DatasetClassRouter.route(labels, objects, hint);

        StringBuilder top = new StringBuilder();
        ArrayList<DatasetClassRouter.Candidate> list = route.top;
        for (int i = 0; i < list.size(); i++) {
            DatasetClassRouter.Candidate c = list.get(i);
            if (i > 0) top.append(" | ");
            top.append(c.name).append(" ").append(c.score);
        }

        String topLine = top.toString();
        String summary =
                "dataset_class: " + route.predicted + " | score " + route.confidence + "\n" +
                "top5: " + topLine + "\n" +
                "router_reason: " + cut(route.reason, 260);

        Decision oldDecision = new Decision(route.predicted, route.confidence, summary, topLine, route.reason);
        return maybeUseClassRouterForPredict(oldDecision, labels, objects, hint, features);
    }

    public static String uiBlock(Object labels, Object objects, String jsonHint, String extraHint) {
        Decision d = analyze(labels, objects, jsonHint, extraHint);
        return d.summary;
    }

    public static String uiBlock(Object labels, Object objects, String jsonHint, String extraHint, VisualFeatureVector features) {
        Decision d = analyze(labels, objects, jsonHint, extraHint, features);
        return d.summary;
    }

    public static String csvLine(Object labels, Object objects, String jsonHint, String extraHint) {
        Decision d = analyze(labels, objects, jsonHint, extraHint);
        return d.datasetClass + "," + d.confidence + "," + quote(d.topLine) + "," + quote(cut(d.reason, 300));
    }

    private static Decision maybeUseClassRouterForPredict(
            Decision oldDecision,
            Object labels,
            Object objects,
            String hint,
            VisualFeatureVector features
    ) {
        if (!VisionRouterConfig.USE_CLASS_ROUTER_FOR_PREDICT) return oldDecision;
        if (oldDecision == null) return oldDecision;

        try {
            ImageProfile profile = new ImageProfile();
            profile.features = features == null ? new VisualFeatureVector() : features;
            profile.rawPredicted = oldDecision.datasetClass;

            AxisScorer.score(profile, labels, objects, hint);
            ShadowClassRouter.route(profile, oldDecision.datasetClass, splitTop3(oldDecision.topLine));

            if (!DatasetClasses.isValid(profile.shadowFinalClass)) return oldDecision;
            if (DatasetClasses.isForbidden(profile.shadowFinalClass)) return oldDecision;

            String newTopLine = joinTop3(profile.shadowTop3);
            if (newTopLine.length() == 0) newTopLine = oldDecision.topLine;

            String newReason = oldDecision.reason
                    + " | classRouterPredict=on old=" + oldDecision.datasetClass
                    + " shadow=" + profile.shadowFinalClass
                    + " shadowTop3=" + newTopLine;

            String newSummary = oldDecision.summary
                    + "\nclass_router_predict: " + profile.shadowFinalClass
                    + " | top3: " + newTopLine;

            return new Decision(
                    profile.shadowFinalClass,
                    Math.max(1, oldDecision.confidence),
                    newSummary,
                    newTopLine,
                    newReason
            );
        } catch (Throwable ignored) {
            return oldDecision;
        }
    }

    private static List<String> splitTop3(String topLine) {
        ArrayList<String> out = new ArrayList<>();
        if (topLine == null) return out;

        String cleaned = topLine.replace("|", " ");
        String[] parts = cleaned.split("\\s+");
        for (String part : parts) {
            if (DatasetClasses.isValid(part) && !out.contains(part)) out.add(part);
            if (out.size() >= 3) break;
        }
        return out;
    }

    private static String joinTop3(List<String> top3) {
        if (top3 == null || top3.isEmpty()) return "";

        StringBuilder b = new StringBuilder();
        int count = 0;
        for (String c : top3) {
            if (!DatasetClasses.isValid(c)) continue;
            if (b.length() > 0) b.append(" | ");
            b.append(c);
            count++;
            if (count >= 3) break;
        }
        return b.toString();
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
