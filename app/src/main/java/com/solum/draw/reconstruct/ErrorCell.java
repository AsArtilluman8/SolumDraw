package com.solum.draw.reconstruct;

public final class ErrorCell {
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final long totalError;
    public final int averageError;

    public ErrorCell(int x, int y, int width, int height, long totalError, int averageError) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.totalError = totalError;
        this.averageError = averageError;
    }

    public String summary() {
        return "cell x=" + x + " y=" + y + " w=" + width + " h=" + height + " total=" + totalError + " avg=" + averageError;
    }
}
