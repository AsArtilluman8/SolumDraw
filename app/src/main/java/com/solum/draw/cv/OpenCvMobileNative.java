package com.solum.draw.cv;

public final class OpenCvMobileNative {
    private static boolean loaded;
    private static Throwable loadError;

    static {
        try {
            System.loadLibrary("solumdraw_cv");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            loadError = t;
        }
    }

    private OpenCvMobileNative() {}

    public static boolean isLoaded() { return loaded; }

    public static String loadErrorMessage() { return loadError == null ? "" : loadError.getClass().getSimpleName() + ": " + loadError.getMessage(); }

    public static String backendNameSafe() {
        if (!loaded) return "opencv-mobile not loaded: " + loadErrorMessage();
        try { return nativeBackendName(); } catch (Throwable t) { return "opencv-mobile call failed: " + t.getMessage(); }
    }

    public static int smokeTestSafe() {
        if (!loaded) return -1;
        try { return nativeSmokeTest(); } catch (Throwable t) { return -2; }
    }

    public static native String nativeBackendName();
    public static native int nativeSmokeTest();
}
