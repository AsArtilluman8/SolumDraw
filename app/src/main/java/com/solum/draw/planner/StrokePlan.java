package com.solum.draw.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StrokePlan {
    public final int sourceWidth;
    public final int sourceHeight;
    public final String mode;
    public final List<StrokeAction> actions;

    public StrokePlan(int sourceWidth, int sourceHeight, String mode, List<StrokeAction> actions) {
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.mode = mode;
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
    }

    public int countStagePrefix(String prefix) {
        int count = 0;
        for (StrokeAction action : actions) {
            if (action.stage.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }
}
