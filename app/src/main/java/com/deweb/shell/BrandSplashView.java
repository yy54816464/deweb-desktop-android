package com.deweb.shell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Liquid-glass startup / verification screen. */
public final class BrandSplashView extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextView statusView;
    private boolean dismissed;

    public BrandSplashView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);

        FrameLayout center = new FrameLayout(context);
        addView(center, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        FrameLayout orb = new FrameLayout(context);
        GradientDrawable orbBg = new GradientDrawable();
        orbBg.setShape(GradientDrawable.OVAL);
        orbBg.setColor(0xB8FFFFFF);
        orbBg.setStroke(dp(1), 0xEEFFFFFF);
        orb.setBackground(orbBg);
        orb.setElevation(dp(12));
        FrameLayout.LayoutParams orbLp = new FrameLayout.LayoutParams(dp(270), dp(270));
        orbLp.gravity = Gravity.CENTER_HORIZONTAL;
        center.addView(orb, orbLp);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(192), dp(192));
        iconLp.gravity = Gravity.CENTER;
        orb.addView(icon, iconLp);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams wordsLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        wordsLp.gravity = Gravity.CENTER_HORIZONTAL;
        wordsLp.topMargin = dp(300);
        center.addView(words, wordsLp);

        TextView word = new TextView(context);
        SpannableString sp = new SpannableString("DeWEB DESK");
        sp.setSpan(new ForegroundColorSpan(0xFF0B0D0D), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new ForegroundColorSpan(0xFFFF5A2D), 6, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        word.setText(sp);
        word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        word.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        word.setLetterSpacing(-0.04f);
        words.addView(word);

        TextView tag = new TextView(context);
        tag.setText("ON-CHAIN DESKTOP");
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        tag.setTextColor(0xFF7B7468);
        tag.setLetterSpacing(0.32f);
        tag.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tagLp.topMargin = dp(8);
        words.addView(tag, tagLp);

        LinearLayout load = new LinearLayout(context);
        load.setOrientation(LinearLayout.HORIZONTAL);
        load.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable loadBg = new GradientDrawable();
        loadBg.setColor(0xB8FFFFFF);
        loadBg.setCornerRadius(dp(22));
        loadBg.setStroke(dp(1), 0xEEFFFFFF);
        load.setBackground(loadBg);
        load.setPadding(dp(15), dp(10), dp(15), dp(10));
        FrameLayout.LayoutParams loadLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        loadLp.topMargin = dp(382);
        center.addView(load, loadLp);

        View dot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFFFF5A2D);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotLp.rightMargin = dp(10);
        load.addView(dot, dotLp);

        statusView = new TextView(context);
        statusView.setText("VERIFYING CONTAINER");
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        statusView.setTextColor(0xFF635D53);
        statusView.setLetterSpacing(0.18f);
        statusView.setTypeface(Typeface.MONOSPACE);
        load.addView(statusView);

        TextView foot = new TextView(context);
        foot.setText("CONTAINER  ·  DESKTOP  ·  WEBVIEW");
        foot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        foot.setTextColor(0xFF8A8175);
        foot.setLetterSpacing(0.30f);
        foot.setTypeface(Typeface.MONOSPACE);
        LayoutParams footLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        footLp.bottomMargin = dp(38);
        addView(foot, footLp);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    public void setStatus(String s) {
        if (statusView != null && s != null) statusView.setText(s);
    }

    public void dismiss() {
        if (dismissed) return;
        dismissed = true;
        animate().alpha(0f).setDuration(360).withEndAction(() -> setVisibility(View.GONE)).start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        paint.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFFFBF8F1, 0xFFF2EDE3, 0xFFF8F3EA},
                new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * .12f, h * .06f, w * .62f,
                new int[]{0x5CFF9A70, 0x00FF9A70}, null, Shader.TileMode.CLAMP));
        c.drawCircle(w * .12f, h * .06f, w * .62f, paint);
        paint.setShader(new RadialGradient(w * .92f, h * .22f, w * .58f,
                new int[]{0x4E9DBEFF, 0x009DBEFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(w * .92f, h * .22f, w * .58f, paint);
        paint.setShader(null);
        paint.setColor(0x0D0B0D0D);
        paint.setStrokeWidth(dp(1));
        int step = dp(32);
        for (int x = 0; x < w; x += step) c.drawLine(x, 0, x, h, paint);
        for (int y = 0; y < h; y += step) c.drawLine(0, y, w, y, paint);
    }
}
