package com.deweb.shell;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Allows only known external URI schemes; blocks file/content/intent/javascript. */
public final class IntentGuard {
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "mailto", "tel", "okx", "okxwallet", "okxweb3",
            "wc", "metamask", "bnc", "binance", "trust", "coinbase", "rainbow"
    ));

    private IntentGuard() {}

    public static boolean open(Activity activity, Uri uri) {
        if (activity == null || uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!ALLOWED.contains(scheme)) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
