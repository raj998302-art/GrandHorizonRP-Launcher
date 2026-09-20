package com.grandhorizonrp.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Central logging + crash capture for the GHRP from-scratch launcher.
 * Everything is mirrored into files (filesDir/logs) so device-side issues
 * can be diagnosed from a exported report.
 */
public final class GHRPLog {
    public static final String TAG = "GHRP";

    private static Context sAppContext;
    private static File sLogDir;
    private static final Object WRITE_LOCK = new Object();

    private GHRPLog() {
    }

    public static void init(Context context) {
        sAppContext = context.getApplicationContext();
        try {
            sLogDir = new File(sAppContext.getFilesDir(), "logs");
            //noinspection ResultOfMethodCallIgnored
            sLogDir.mkdirs();
        } catch (Throwable t) {
            Log.e(TAG, "log dir init failed", t);
        }
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    FileWriter fw = new FileWriter(new File(sLogDir, "crash.txt"), true);
                    PrintWriter pw = new PrintWriter(fw);
                    pw.println("=== " + now() + " thread=" + thread.getName() + " ===");
                    throwable.printStackTrace(pw);
                    pw.close();
                } catch (Throwable ignored) {
                }
                Log.e(TAG, "FATAL on " + thread.getName(), throwable);
            }
        });
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static void toFile(String level, String msg, Throwable t) {
        if (sLogDir == null) return;
        try {
            synchronized (WRITE_LOCK) {
                FileWriter fw = new FileWriter(new File(sLogDir, "launcher.txt"), true);
                PrintWriter pw = new PrintWriter(fw);
                pw.println(now() + " " + level + " " + msg);
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    pw.println(sw.toString());
                }
                pw.close();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        toFile("I", msg, null);
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        toFile("W", msg, null);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        toFile("E", msg, t);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
        toFile("E", msg, null);
    }

    /** Log calls that originate from native engine threads. */
    public static void nativecb(String method, String detail) {
        Log.d(TAG, "[native->java] " + method + (detail == null ? "" : " " + detail));
    }
}
