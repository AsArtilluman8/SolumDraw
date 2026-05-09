package com.solum.draw.image;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.solum.draw.debug.RuntimeLog;
import java.io.InputStream;

public final class SafeBitmapLoader {
    public static final int MAX_SIDE = 1600;
    public static final int MAX_PIXELS = 1600 * 1600;

    private SafeBitmapLoader() {}

    public static Result load(ContentResolver resolver, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsStream = resolver.openInputStream(uri);
        if (boundsStream == null) throw new IllegalArgumentException("Cannot open image stream");
        try {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } finally {
            boundsStream.close();
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("Unsupported image or empty bounds: " + bounds.outWidth + "x" + bounds.outHeight);
        }

        int sample = chooseSampleSize(bounds.outWidth, bounds.outHeight);
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;

        InputStream imageStream = resolver.openInputStream(uri);
        if (imageStream == null) throw new IllegalArgumentException("Cannot reopen image stream");
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeStream(imageStream, null, decode);
        } finally {
            imageStream.close();
        }

        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap decode returned null");
        }

        Result result = new Result(bitmap, bounds.outWidth, bounds.outHeight, sample);
        RuntimeLog.line("image_load", result.summary());
        return result;
    }

    private static int chooseSampleSize(int width, int height) {
        int sample = 1;
        while (true) {
            int w = Math.max(1, width / sample);
            int h = Math.max(1, height / sample);
            if (w <= MAX_SIDE && h <= MAX_SIDE && (w * h) <= MAX_PIXELS) {
                return sample;
            }
            sample *= 2;
        }
    }

    public static final class Result {
        public final Bitmap bitmap;
        public final int originalWidth;
        public final int originalHeight;
        public final int sampleSize;

        public Result(Bitmap bitmap, int originalWidth, int originalHeight, int sampleSize) {
            this.bitmap = bitmap;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
            this.sampleSize = sampleSize;
        }

        public String summary() {
            return "original=" + originalWidth + "x" + originalHeight
                    + " decoded=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " sample=" + sampleSize;
        }
    }
}
