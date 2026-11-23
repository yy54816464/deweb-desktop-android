package com.deweb.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import androidx.webkit.WebViewCompat;

/** Warns when the system WebView is too old to be trusted with a wallet UI. */
public final class WebViewGuard {
    private static final int MIN_MAJOR = 100;
    private WebViewGuard() {}

    public static void warnIfOld(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            PackageInfo info = WebViewCompat.getCurrentWebViewPackage(activity);
            if (info == null || info.versionName == null) return;
            String version = info.versionName;
            int major = 0;
            try {
                String first = version.split("\\.")[0];
                major = Integer.parseInt(first);
            } catch (Throwable ignored) {}
            if (major <= 0 || major >= MIN_MAJOR) return;
            SharedPreferences sp = activity.getSharedPreferences("deweb_shell", Activity.MODE_PRIVATE);
            if (version.equals(sp.getString("webview_warned_version", ""))) return;
            sp.edit().putString("webview_warned_version", version).apply();
            new AlertDialog.Builder(activity)
                    .setTitle("WebView 版本较低")
                    .setMessage("当前 WebView 版本：" + version
                            + "\n\nDeWEB 的钱包和站点运行在系统 WebView 中。版本过低可能缺少安全补丁。\n\n建议通过应用市场更新 Android System WebView；华为设备请更新系统 WebView。")
                    .setPositiveButton("仍然继续", null)
                    .show();
        } catch (Throwable ignored) {}
    }
}
