package com.solum.draw.reconstruct;

import android.graphics.Color;
import android.graphics.PointF;
import com.solum.draw.planner.DrawMode;
import com.solum.draw.planner.StrokeAction;
import com.solum.draw.planner.StrokePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ResidualPlanner {
    private static final int TARGET_SIDE = 192;
    private static final int CELL_SIZE = 16;

    private ResidualPlanner() {}

    public static Result addResidualStrokes(android.graphics.Bitmap source, StrokePlan basePlan, DrawMode mode, int canvasWidth, int canvasHeight) {
        TargetImage target = new TargetImage(source, TARGET_SIDE);
        VirtualCanvas virtualCanvas = new VirtualCanvas(target.width(), target.height());

        float sx = target.width() / (float) Math.max(1, canvasWidth);
        float sy = target.height() / (float) Math.max(1, canvasHeight);
        for (StrokeAction action : basePlan.actions) {
            virtualCanvas.apply(scaleStroke(action, sx, sy));
        }

        ErrorMap beforeMap = ErrorMap.build(target, virtualCanvas, CELL_SIZE);
        ReconstructionMetrics beforeMetrics = ReconstructionMetrics.compare(target, virtualCanvas);
        List<StrokeAction> residual = buildResidualActions(target, beforeMap, mode, canvasWidth, canvasHeight);

        for (StrokeAction action : residual) {
            virtualCanvas.apply(scaleStroke(action, sx, sy));
        }

        ReconstructionMetrics afterMetrics = ReconstructionMetrics.compare(target, virtualCanvas);
        List<StrokeAction> all = new ArrayList<>(basePlan.actions);
        all.addAll(residual);
        StrokePlan plan = new StrokePlan(basePlan.sourceWidth, basePlan.sourceHeight, basePlan.mode + "+RESIDUAL", all);
        return new Result(plan, residual.size(), beforeMetrics.averageError, afterMetrics.averageError, beforeMap.summary(3));
    }

    private static List<StrokeAction> buildResidualActions(TargetImage target, ErrorMap errorMap, DrawMode mode, int canvasWidth, int canvasHeight) {
        int limit = mode == DrawMode.HUMAN_FAST ? 60 : 110;
        int minError = mode == DrawMode.HUMAN_FAST ? 150 : 115;
        List<ErrorCell> cells = errorMap.topCellsAbove(limit, minError);
        List<StrokeAction> actions = new ArrayList<>();
        Random random = new Random(4040L + cells.size() + mode.ordinal() * 17L);

        int index = 0;
        for (ErrorCell cell : cells) {
            int color = averageTargetColor(target, cell);
            String stage = index < limit * 0.55f ? "GRINDER_RESIDUAL_CELL" : "POLISHER_RESIDUAL_CELL";
            float brush = mode == DrawMode.HUMAN_FAST ? 3.4f : 2.8f;
            if (cell.averageError > 300) brush += 1.5f;
            List<PointF> path = residualCellPath(cell, target.width(), target.height(), canvasWidth, canvasHeight, mode, random, index);
            if (path.size() > 1) {
                actions.add(new StrokeAction(stage, color, brush, path));
            }
            index++;
        }
        return actions;
    }

    private static List<PointF> residualCellPath(ErrorCell cell, int targetWidth, int targetHeight, int canvasWidth, int canvasHeight, DrawMode mode, Random random, int index) {
        List<PointF> path = new ArrayList<>();
        float x0 = (cell.x / (float) targetWidth) * canvasWidth;
        float y0 = (cell.y / (float) targetHeight) * canvasHeight;
        float x1 = ((cell.x + cell.width) / (float) targetWidth) * canvasWidth;
        float y1 = ((cell.y + cell.height) / (float) targetHeight) * canvasHeight;
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1) * 0.5f;

        if (mode == DrawMode.HUMAN_FAST || index % 3 == 0) {
            path.add(new PointF(x0, cy + wobble(random, 2.0f)));
            path.add(new PointF(x1, cy + wobble(random, 2.0f)));
        } else if (index % 3 == 1) {
            path.add(new PointF(cx + wobble(random, 2.0f), y0));
            path.add(new PointF(cx + wobble(random, 2.0f), y1));
        } else {
            path.add(new PointF(x0, y0));
            path.add(new PointF(cx + wobble(random, 1.5f), cy + wobble(random, 1.5f)));
            path.add(new PointF(x1, y1));
        }
        return path;
    }

    private static int averageTargetColor(TargetImage target, ErrorCell cell) {
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int y = cell.y; y < cell.y + cell.height; y += 2) {
            for (int x = cell.x; x < cell.x + cell.width; x += 2) {
                int c = target.colorAt(x, y);
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                n++;
            }
        }
        if (n <= 0) n = 1;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    private static StrokeAction scaleStroke(StrokeAction action, float sx, float sy) {
        ArrayList<PointF> scaled = new ArrayList<>();
        for (PointF p : action.path) {
            scaled.add(new PointF(p.x * sx, p.y * sy));
        }
        return new StrokeAction(action.stage, action.color, Math.max(1f, action.size * Math.max(sx, sy)), scaled);
    }

    private static float wobble(Random random, float amount) {
        return (random.nextFloat() - 0.5f) * amount;
    }

    public static final class Result {
        public final StrokePlan plan;
        public final int residualActions;
        public final int beforeAverageError;
        public final int afterAverageError;
        public final String errorMapSummary;

        public Result(StrokePlan plan, int residualActions, int beforeAverageError, int afterAverageError, String errorMapSummary) {
            this.plan = plan;
            this.residualActions = residualActions;
            this.beforeAverageError = beforeAverageError;
            this.afterAverageError = afterAverageError;
            this.errorMapSummary = errorMapSummary;
        }

        public String summary() {
            return "residualActions=" + residualActions + " avgError " + beforeAverageError + "->" + afterAverageError;
        }
    }
}
