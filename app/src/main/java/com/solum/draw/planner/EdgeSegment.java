package com.solum.draw.planner;

import android.graphics.Point;
import java.util.ArrayList;
import java.util.List;

public final class EdgeSegment {
    public final int color;
    public final int strength;
    public final List<Point> points;

    public EdgeSegment(int color, int strength, List<Point> points) {
        this.color = color;
        this.strength = strength;
        this.points = new ArrayList<>(points);
    }
}
