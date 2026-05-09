package com.solum.draw.planner;

import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;

public final class StrokeAction {
    public final String stage;
    public final int color;
    public final float size;
    public final List<PointF> path;

    public StrokeAction(String stage, int color, float size, List<PointF> path) {
        this.stage = stage;
        this.color = color;
        this.size = size;
        this.path = new ArrayList<>(path);
    }
}
