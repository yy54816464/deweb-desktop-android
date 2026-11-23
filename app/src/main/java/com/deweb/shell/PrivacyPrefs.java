package com.deweb.shell;

import android.content.Context;
import android.content.SharedPreferences;

/** Global screenshot / recents protection switch. Lock and wallet always override it. */
public final class PrivacyPrefs {
    private static final String PREF = "deweb_shell";
    private static final String KEY = "privacy_screenshot";
    private PrivacyPrefs() {}
    public static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }
    public static void setEnabled(Context c, boolean enabled) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply();
    }
}
