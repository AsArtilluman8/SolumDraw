package com.solum.draw.debug;

import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RuntimeLog {
    private RuntimeLog() {}

    public static void line(String tag, String message) {
        try {
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_runtime_log.txt");
            PrintWriter writer = new PrintWriter(new FileWriter(out, true));
            writer.println(time() + " " + tag + " " + message + " " + memory());
            writer.close();
        } catch (Throwable ignored) {
        }
    }

    public static void error(String tag, Throwable throwable) {
        try {
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "solumdraw_runtime_log.txt");
            PrintWriter writer = new PrintWriter(new FileWriter(out, true));
            writer.println(time() + " ERROR " + tag + " " + memory());
            throwable.printStackTrace(writer);
            writer.close();
        } catch (Throwable ignored) {
        }
    }

    public static String memory() {
        Runtime r = Runtime.getRuntime();
        long used = r.totalMemory() - r.freeMemory();
        return "mem used=" + mb(used) + " total=" + mb(r.totalMemory()) + " max=" + mb(r.maxMemory()) + "MB";
    }

    private static String time() {
        return new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US).format(new Date());
    }

    private static long mb(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
