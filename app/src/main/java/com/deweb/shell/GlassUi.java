package com.deweb.shell;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

/** Lightweight liquid-glass surfaces for the native shell. */
public final class GlassUi {
    private GlassUi() {}

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static Drawable background(Context c) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFF8FCF9, 0xFFEAF5EE, 0xFFF3FAF5});
        return g;
    }

    public static Drawable opaqueLock(Context c) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF07100B, 0xFF0A1710, 0xFF07100B});
        return g;
    }

    public static Drawable panel(Context c, int radiusDp, boolean dark) {
        int r = dp(c, radiusDp);
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                dark
                        ? new int[]{0xE60C1A13, 0xCC07100B, 0xE60B1811}
                        : new int[]{0xE6FFFFFF, 0xBFF1FAF4, 0xD9FFFFFF});
        base.setCornerRadius(r);
        base.setStroke(dp(c, 1), dark ? 0x55FFFFFF : 0xE6FFFFFF);

        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                dark
                        ? new int[]{0x33FFFFFF, 0x0FFFFFFF, 0x00000000}
                        : new int[]{0x66FFFFFF, 0x22FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(r);

        GradientDrawable rim = new GradientDrawable();
        rim.setColor(Color.TRANSPARENT);
        rim.setCornerRadius(r);
        rim.setStroke(dp(c, 1), dark ? 0x22FFFFFF : 0x336B8F7E);

        LayerDrawable layer = new LayerDrawable(new Drawable[]{base, sheen, rim});
        layer.setLayerInset(0, 0, 0, 0, 0);
        layer.setLayerInset(1, dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1));
        layer.setLayerInset(2, dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1));
        return layer;
    }

    public static Drawable bar(Context c, boolean dark) {
        return panel(c, 18, dark);
    }

    public static Drawable button(Context c, int radiusDp, boolean dark) {
        Drawable normal = panel(c, radiusDp, dark);
        int ripple = dark ? 0x33FFFFFF : 0x33000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), normal, null);
    }

    public static Drawable titleBar(Context c) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2FFFFFF, 0xE6F1FAF4});
        g.setCornerRadii(new float[]{
                dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16), 0, 0, 0, 0});
        g.setStroke(dp(c, 1), 0x88FFFFFF);
        return g;
    }

    private static boolean lowRam(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            return am != null && am.isLowRamDevice();
        } catch (Throwable ignored) { return false; }
    }

    public static void hardenTree(android.view.View root) {
        if (root == null) return;
        root.setFilterTouchesWhenObscured(true);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            root.setOnTouchListener((v, event) -> {
                // setFilterTouchesWhenObscured only covers fully obscured windows on
                // older implementations; reject partially obscured touches too.
                return (event.getFlags() & android.view.MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0
                        || (event.getFlags() & android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0;
            });
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) hardenTree(g.getChildAt(i));
        }
    }

    /** Activity FLAG_SECURE does not propagate to a Dialog's own window. */
    public static void secureDialog(Dialog dialog, boolean secure) {
        if (dialog == null) return;
        try {
            Window w = dialog.getWindow();
            if (w == null) return;
            if (secure) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } catch (Throwable ignored) {}
    }

    public static void blurDialog(Dialog dialog) {
        if (dialog == null) return;
        try {
            if (lowRam(dialog.getContext())) return;
            Window w = dialog.getWindow();
            if (w == null) return;
            if (Build.VERSION.SDK_INT >= 31) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                w.setBackgroundBlurRadius(dp(dialog.getContext(), 22));
            }
        } catch (Throwable ignored) {}
    }
}
