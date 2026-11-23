package com.deweb.shell;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Last verified main-document bytes; every read is re-hashed against chain. */
public final class TapeCache {
    private static final String DATA = "tape-main.verified.html";
    private static final String META = "tape-main.verified.json";

    private TapeCache() {}

    public static String readEtag(Context c) {
        try {
            File f = new File(c.getFilesDir(), META);
            if (!f.exists()) return "";
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            in.close();
            JSONObject o = new JSONObject(out.toString("UTF-8"));
            return o.optString("etag", "");
        } catch (Throwable t) { return ""; }
    }

    public static org.json.JSONObject readMeta(Context c) {
        try {
            File f = new File(c.getFilesDir(), META);
            if (!f.exists()) return new org.json.JSONObject();
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            in.close();
            return new org.json.JSONObject(out.toString("UTF-8"));
        } catch (Throwable t) { return new org.json.JSONObject(); }
    }

    public static byte[] read(Context c) {
        try {
            File f = new File(c.getFilesDir(), DATA);
            if (!f.exists()) return null;
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            in.close();
            return out.toByteArray();
        } catch (Throwable t) { return null; }
    }

    public static boolean verifyMeta(Context c, byte[] page, org.json.JSONObject meta) {
        return TapeCacheHmac.verify(c, page, meta);
    }

    public static void writeMeta(Context c, org.json.JSONObject meta) {
        if (meta == null) return;
        try {
            TapeCacheHmac.signMeta(meta);
            File dir = c.getFilesDir();
            File metaTmp = new File(dir, META + ".tmp");
            FileOutputStream mo = new FileOutputStream(metaTmp);
            mo.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            mo.getFD().sync();
            mo.close();
            File metaDst = new File(dir, META);
            if (!metaTmp.renameTo(metaDst)) metaTmp.delete();
        } catch (Throwable ignored) {}
    }

    public static void write(Context c, byte[] bytes, String metaJson) {
        try {
            File dir = c.getFilesDir();
            File tmp = new File(dir, DATA + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(bytes);
            out.getFD().sync();
            out.close();
            File dst = new File(dir, DATA);
            if (!tmp.renameTo(dst)) { tmp.delete(); return; }
            org.json.JSONObject meta = new org.json.JSONObject(metaJson == null ? "{}" : metaJson);
            TapeCacheHmac.sign(meta, bytes);
            File metaTmp = new File(dir, META + ".tmp");
            FileOutputStream mo = new FileOutputStream(metaTmp);
            mo.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            mo.getFD().sync();
            mo.close();
            File metaDst = new File(dir, META);
            if (!metaTmp.renameTo(metaDst)) metaTmp.delete();
        } catch (Throwable ignored) {}
    }
}
