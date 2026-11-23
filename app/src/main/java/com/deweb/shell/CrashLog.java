package com.deweb.shell;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/** Writes uncaught Java exceptions to app-specific external storage. */
public final class CrashLog {
    private CrashLog() {}

    public static void install(final Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                File dir = context.getExternalFilesDir(null);
                if (dir == null) dir = context.getFilesDir();
                File out = new File(dir, "last-crash.txt");
                java.io.StringWriter sw = new java.io.StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println("thread: " + thread.getName());
                error.printStackTrace(pw);
                pw.close();
                String safe = sanitize(sw.toString());
                try (FileWriter fw = new FileWriter(out, false)) {
                    fw.write(safe);
                }
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static String sanitize(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replaceAll("https?://\\S+", "[url]");
        s = s.replaceAll("(?i)(token|secret|key|auth|authorization|cookie|session|password)=([^&\\s]+)", "$1=[redacted]");
        s = s.replaceAll("0x[0-9a-fA-F]{40,64}", "0x[redacted]");
        if (s.length() > 8000) s = s.substring(0, 8000) + "\n[truncated]";
        return s;
    }

    public static void clearOnNewVersion(Context context) {
        try {
            int version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            android.content.SharedPreferences sp = context.getSharedPreferences("deweb_shell", Context.MODE_PRIVATE);
            int seen = sp.getInt("crash_version", -1);
            if (seen != version) {
                File dir = context.getExternalFilesDir(null);
                if (dir == null) dir = context.getFilesDir();
                new File(dir, "last-crash.txt").delete();
                sp.edit().putInt("crash_version", version).apply();
            }
        } catch (Throwable ignored) {}
    }

    public static String read(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) dir = context.getFilesDir();
            File in = new File(dir, "last-crash.txt");
            if (!in.exists()) return null;
            byte[] data = new byte[(int) in.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(in);
            int n = fis.read(data);
            fis.close();
            return new String(data, 0, Math.max(n, 0), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    public static void clear(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) dir = context.getFilesDir();
            new File(dir, "last-crash.txt").delete();
        } catch (Throwable ignored) {}
    }
}
