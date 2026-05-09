package com.solum.draw.debug;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashLogger implements Thread.UncaughtExceptionHandler {
    private static final String FILE_PREFIX = "solumdraw_crash_";
    private static Thread.UncaughtExceptionHandler previousHandler;
    private static Context appContext;

    private CrashLogger() {}

    public static void install(Context context) {
        appContext = context.getApplicationContext();
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new CrashLogger());
        writeBootMarker("CrashLogger installed");
    }

    @Override public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            writeCrash(thread, throwable);
        } catch (Throwable ignored) {
            // Last-resort crash handler. Never crash while writing crash logs.
        }

        if (previousHandler != null) {
            previousHandler.uncaughtException(thread, throwable);
        } else {
            System.exit(2);
        }
    }

    public static void logHandledError(String label, Throwable throwable) {
        try {
            File out = new File(downloadDir(), FILE_PREFIX + timestamp() + "_handled.txt");
            PrintWriter writer = new PrintWriter(new FileWriter(out));
            writer.println("SolumDraw handled error");
            writer.println("label: " + label);
            writer.println("time: " + new Date());
            writer.println(deviceInfo());
            writer.println();
            throwable.printStackTrace(writer);
            writer.close();
        } catch (Throwable ignored) {
        }
    }

    public static void writeBootMarker(String message) {
        try {
            File out = new File(downloadDir(), "solumdraw_last_boot.txt");
            PrintWriter writer = new PrintWriter(new FileWriter(out, false));
            writer.println("SolumDraw boot marker");
            writer.println("message: " + message);
            writer.println("time: " + new Date());
            writer.println(deviceInfo());
            writer.close();
        } catch (Throwable ignored) {
        }
    }

    private static void writeCrash(Thread thread, Throwable throwable) throws Exception {
        File out = new File(downloadDir(), FILE_PREFIX + timestamp() + ".txt");
        PrintWriter writer = new PrintWriter(new FileWriter(out));
        writer.println("SolumDraw fatal crash");
        writer.println("time: " + new Date());
        writer.println("thread: " + thread.getName());
        writer.println(deviceInfo());
        writer.println();
        writer.println("stacktrace:");
        throwable.printStackTrace(writer);
        writer.println();
        writer.println("cause chain:");
        Throwable cause = throwable.getCause();
        int depth = 0;
        while (cause != null && depth < 12) {
            writer.println("cause[" + depth + "] " + cause.getClass().getName() + ": " + cause.getMessage());
            cause = cause.getCause();
            depth++;
        }
        writer.close();
    }

    private static File downloadDir() {
        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
            return publicDownloads;
        }
        if (appContext != null) {
            File privateDownloads = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (privateDownloads != null && (privateDownloads.exists() || privateDownloads.mkdirs())) {
                return privateDownloads;
            }
        }
        return new File(".");
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static String deviceInfo() {
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);
        writer.println("app: SolumDraw");
        writer.println("android: " + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT);
        writer.println("manufacturer: " + Build.MANUFACTURER);
        writer.println("model: " + Build.MODEL);
        writer.println("device: " + Build.DEVICE);
        writer.println("brand: " + Build.BRAND);
        writer.println("supportedAbis: " + join(Build.SUPPORTED_ABIS));
        writer.flush();
        return sw.toString();
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        return out.toString();
    }
}
