package com.solum.draw.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class VisionResult {
    public final String provider;
    public final boolean available;
    public final String message;
    public final List<VisionLabel> labels;
    public final List<VisionObject> objects;

    public VisionResult(String provider, boolean available, String message, List<VisionLabel> labels, List<VisionObject> objects) {
        this.provider = provider == null ? "unknown" : provider;
        this.available = available;
        this.message = message == null ? "" : message;
        this.labels = labels == null ? new ArrayList<>() : labels;
        this.objects = objects == null ? new ArrayList<>() : objects;
        Collections.sort(this.objects, new Comparator<VisionObject>() {
            @Override public int compare(VisionObject a, VisionObject b) {
                return Float.compare(b.mainScore(), a.mainScore());
            }
        });
    }

    public static VisionResult unavailable(String provider, String message) {
        return new VisionResult(provider, false, message, new ArrayList<VisionLabel>(), new ArrayList<VisionObject>());
    }

    public VisionObject mainObject() {
        return objects.isEmpty() ? null : objects.get(0);
    }

    public String summaryRu() {
        StringBuilder b = new StringBuilder();
        b.append(provider).append(available ? " готов" : " недоступен");
        if (message.length() > 0) b.append(": ").append(message);
        b.append("\nОбъектов: ").append(objects.size()).append(" | labels: ").append(labels.size());

        if (!labels.isEmpty()) {
            b.append("\nLabels: ");
            int n = Math.min(6, labels.size());
            for (int i = 0; i < n; i++) {
                if (i > 0) b.append(", ");
                b.append(labels.get(i).shortText());
            }
        }

        if (!objects.isEmpty()) {
            VisionObject m = mainObject();
            b.append("\nГлавный объект: ").append(m.label)
             .append(" | score ").append(Math.round(m.mainScore() * 100f))
             .append(" | area ").append(Math.round(m.area() * 100f)).append("%");
        }

        return b.toString();
    }
}
