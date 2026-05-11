package com.solum.draw.cv;

import android.graphics.Bitmap;

public interface CvBackend {
    String name();
    boolean isNativeAccelerated();
    CvResult analyze(Bitmap source);
}
