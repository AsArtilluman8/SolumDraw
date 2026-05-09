package com.solum.draw.reconstruct;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ErrorMap {
    private final int cellSize;
    private final List<ErrorCell> cells;

    private ErrorMap(int cellSize, List<ErrorCell> cells) {
        this.cellSize = cellSize;
        this.cells = new ArrayList<>(cells);
    }

    public static ErrorMap build(TargetImage target, VirtualCanvas canvas, int cellSize) {
        int width = Math.min(target.width(), canvas.width());
        int height = Math.min(target.height(), canvas.height());
        List<ErrorCell> cells = new ArrayList<>();

        for (int y = 0; y < height; y += cellSize) {
            for (int x = 0; x < width; x += cellSize) {
                int w = Math.min(cellSize, width - x);
                int h = Math.min(cellSize, height - y);
                long total = 0;
                int count = Math.max(1, w * h);
                for (int yy = y; yy < y + h; yy++) {
                    for (int xx = x; xx < x + w; xx++) {
                        total += colorDistance(target.colorAt(xx, yy), canvas.colorAt(xx, yy));
                    }
                }
                int avg = (int) (total / count);
                cells.add(new ErrorCell(x, y, w, h, total, avg));
            }
        }

        Collections.sort(cells, new Comparator<ErrorCell>() {
            @Override public int compare(ErrorCell a, ErrorCell b) {
                if (a.averageError != b.averageError) return b.averageError - a.averageError;
                return Long.compare(b.totalError, a.totalError);
            }
        });
        return new ErrorMap(cellSize, cells);
    }

    public int cellSize() {
        return cellSize;
    }

    public List<ErrorCell> topCells(int count) {
        int n = Math.min(count, cells.size());
        return new ArrayList<>(cells.subList(0, n));
    }

    public List<ErrorCell> topCellsAbove(int count, int minimumAverageError) {
        List<ErrorCell> out = new ArrayList<>();
        for (ErrorCell cell : cells) {
            if (cell.averageError >= minimumAverageError) {
                out.add(cell);
                if (out.size() >= count) break;
            }
        }
        return out;
    }

    public String summary(int topCount) {
        StringBuilder out = new StringBuilder();
        out.append("errorMap cellSize=").append(cellSize).append(" cells=").append(cells.size());
        List<ErrorCell> top = topCells(topCount);
        for (int i = 0; i < top.size(); i++) {
            out.append(" | top").append(i + 1).append(": ").append(top.get(i).summary());
        }
        return out.toString();
    }

    private static int colorDistance(int a, int b) {
        int dr = Color.red(a) - Color.red(b);
        int dg = Color.green(a) - Color.green(b);
        int db = Color.blue(a) - Color.blue(b);
        return Math.abs(dr) + Math.abs(dg) + Math.abs(db);
    }
}
