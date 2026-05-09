package com.solum.draw.analyze;

import android.graphics.Color;

public final class ColorCount {
    public final int color;
    public final int count;
    public final float ratio;

    public ColorCount(int color, int count, float ratio) {
        this.color = color;
        this.count = count;
        this.ratio = ratio;
    }

    public String hex() {
        return String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
    }
}
