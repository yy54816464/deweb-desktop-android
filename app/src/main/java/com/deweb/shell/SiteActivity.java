package com.deweb.shell;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Two free-floating site windows with the native wallet bridge.
 *
 * Each window is a draggable/resizable card containing its own WebView.
 * Both WebViews get bridge.js and the same native dewebBridge listener.
 */
public class SiteActivity extends Activity {
    private static final String TAG = "DeWebSite";
    private static WeakReference<SiteActivity> currentRef;
    private static final List<WeakReference<SiteActivity>> liveRefs = new CopyOnWriteArrayList<>();
    /** Beta diagnostics: last computed screenshot-protection state per live site instance. */
    private static final Map<String, String> SCREENSHOT_STATES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String MAIN_URL = "https://app.dewebdesk.link/?v=cloud-ui-1";
    private static final Set<String> BG_ORIGINS = new HashSet<>(Arrays.asList("https://app.dewebdesk.link", "https://dewebdesk.link"));
    private static final String WALLET_ORIGIN = "https://appassets.androidplatform.net";
    private static final String WALLET_URL = WALLET_ORIGIN + "/assets/wallet/android-wallet.html?v=local1";
    private FrameLayout rootFrame;
    private FrameLayout siteCanvas;
    private WebView mainBackground;
    private LinearLayout minimizedTray;
    private boolean trayUserMoved;
    private boolean trayMovedThisGesture;
    private float trayDownX, trayDownY;
    private int trayStartTop, trayStartRight;
    private TextView status;
    private Button splitButton;
    private Button deskButton;
    private LinearLayout topBar;
    private FrameLayout lockOverlay;
    private SiteWindow lockedWindow;
    private static final int REQ_UNLOCK = 7001;
    private static final int REQ_OVERLAY = 7002;
    private final ArrayList<FloatingSession> floatingSessions = new ArrayList<>();
    private WindowManager floatingManager;
    private LinearLayout capsuleTray;
    private WindowManager.LayoutParams capsuleTrayParams;
    private final Map<FloatingSession, TextView> capsuleChips = new HashMap<>();

    private TextView capsuleTrayClose;
    private View floatingBackdrop;
    private WindowManager.LayoutParams floatingBackdropParams;
    private SharedPreferences desktopPrefs;
    private JSONObject desktopModes;
    private String defaultUa;
    private static final String DESKTOP_MAP = "site_desktop_mode_v1";
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private WebFileChooser fileChooser;
    private WebView webView;
    private WebView webView2;
    private WebView activeWebView;
    private SiteWindow window1;
    private SiteWindow window2;
    private SiteWindow window3;
    private SiteWindow window4;
    private String startUrl;

    private WebView walletWeb;
    private boolean walletPageReady;
    private long lastGestureAt;
    private boolean interactivePending;
    private final ArrayDeque<JSONObject> walletQueue = new ArrayDeque<>();
    private final Map<String, PendingWallet> walletPending = new HashMap<>();
    // Approvals are app-global (one shared preference key), so they must be
    // shared across every live SiteActivity. Per-instance copies let a second
    // window save its stale set over an approval granted in another window.
    private static final Set<String> nativeApproved = new HashSet<>();
    private static final Map<String, Long> nativeApprovedExpiry = new HashMap<>();
    private static final long SITE_AUTH_TTL_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final String WALLET_REVOKE_KEY = "wallet_revoke_origins";
    private final Set<String> floatingApproved = new HashSet<>();
    private static volatile TapeVerifier.Result bgVerifiedPage;
    private static volatile long bgVerifiedAt;
    private boolean floatingNextNativeConfirmed;
    private boolean floatingKeepForRequest;
    private boolean floatingConnectInProgress;
    // 测试版专用：把钱包装进悬浮窗
    private FloatingSession walletFloatHost;
    private boolean walletAttachedToFloat;
    private final ArrayDeque<AuthRequest> authQueue = new ArrayDeque<>();
    private boolean authDialogShowing;
    private boolean membershipRequested;
    private Boolean lastProState;
    private SharedPreferences authPrefs;

    private String startChain = "56";
    private boolean splitMode = false;
    private boolean backdropShown;
    private String cachedChainHint = "56";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_PENDING_WALLET = 20;
    private static final int MAX_AUTH_QUEUE = 8;
    private static final int MAX_WALLET_QUEUE = 20;
    private int walletSeq;
    private int walletWindowSeq;
    private volatile long walletCanceledAt;
    private JSONArray cachedAccounts;
    private String cachedChain;
    private Button walletCancel;
    private WebViewAssetLoader walletAssetLoader;
    private Dialog siteAuthDialog;
    private boolean siteForeground;
    private boolean walletHidesFloats;
    private final ArrayList<FloatingSession> walletHiddenFloats = new ArrayList<>();
    private String activeAuthOrigin;

    @Override
    protected void onResume() {
        super.onResume();
        siteForeground = true;
        syncLockUi();
        applyScreenshotProtection();
        checkMembershipExpiry();
        syncFloatingFeature();
        MembershipManager.refresh(this, (ok, message, expiry) ->
                runOnUiThread(this::checkMembershipExpiry));
    }

    public static void notifyMembershipChanged() {
        try {
            SiteActivity a = currentRef == null ? null : currentRef.get();
            if (a != null) a.runOnUiThread(a::checkMembershipExpiry);
        } catch (Throwable ignored) {}
    }

    private void checkMembershipExpiry() {
        boolean pro = MembershipManager.isPro(this);
        if (lastProState == null) {
            lastProState = pro;
            return;
        }
        if (lastProState && !pro) {
            Toast.makeText(this, "会员已到期，现有窗口保留；新建第 3/4 个窗口受限", Toast.LENGTH_LONG).show();
        }
        lastProState = pro;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileChooser = new WebFileChooser(this);
        CrashLog.install(this);
        currentRef = new WeakReference<>(this);
        liveRefs.add(new WeakReference<>(this));
        desktopPrefs = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        try { desktopModes = new JSONObject(desktopPrefs.getString(DESKTOP_MAP, "{}")); } catch (Throwable t) { desktopModes = new JSONObject(); }
        defaultUa = WebSettings.getDefaultUserAgent(this);
        authPrefs = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        nativeApproved.addAll(authPrefs.getStringSet("native_approved_origins", new HashSet<>()));
        try {
            JSONObject ex = new JSONObject(authPrefs.getString("native_approved_expiry", "{}"));
            long now = System.currentTimeMillis();
            java.util.Iterator<String> it = ex.keys();
            while (it.hasNext()) {
                String key = it.next();
                long value = ex.optLong(key, 0L);
                if (value > now) nativeApprovedExpiry.put(key, value);
                else nativeApproved.remove(key);
            }
        } catch (Throwable ignored) {}
        saveNativeApproved();
        startUrl = getIntent().getStringExtra("url");
        startChain = normalizeChain(getIntent().getStringExtra("chain"));
        cachedChainHint = startChain;
        Uri u = startUrl == null ? null : Uri.parse(startUrl);
        if (u == null || u.getHost() == null || (!"http".equals(u.getScheme()) && !"https".equals(u.getScheme()))) {
            finish();
            return;
        }
        boolean startDesktop = getIntent() == null || getIntent().getBooleanExtra("desktop", true);
        try {
            desktopModes.put(u.getHost(), startDesktop);
            desktopPrefs.edit().putString(DESKTOP_MAP, desktopModes.toString()).apply();
        } catch (Throwable ignored) {}

        rootFrame = new FrameLayout(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(GlassUi.background(this));
        rootFrame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(glassBar());
        bar.setElevation(dp(8));
        bar.setPadding(dp(14), dp(6), dp(10), dp(6));
        topBar = bar;

        status = new TextView(this);
        status.setTextSize(11f);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextColor(Color.rgb(21, 37, 29));
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setText("DeWEB site · " + siteUrlText(startUrl));
        ImageView siteBrandIcon = new ImageView(this);
        siteBrandIcon.setImageResource(R.mipmap.ic_launcher);
        siteBrandIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams siteIconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        siteIconLp.setMargins(0, 0, dp(7), 0);
        bar.addView(siteBrandIcon, siteIconLp);
        bar.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button home = navButton("⌂", "回到主站，站点缩小");
        home.setOnClickListener(v -> goHome());
        bar.addView(home);

        Button add = navButton("＋", "添加此站点到桌面");
        add.setOnClickListener(v -> addCurrentToDesktop());
        bar.addView(add);

        splitButton = navButton("▥", "新建第二个窗口");
        splitButton.setTextSize(18f);
        splitButton.setOnClickListener(v -> toggleSecondWindow());




        Button settingsNav = navButton("⚙", "设置");
        settingsNav.setTextSize(14f);
        settingsNav.setOnClickListener(v -> showSiteSettings());
        bar.addView(settingsNav);

        Button close = navButton("×", "关闭站点");
        close.setOnClickListener(v -> finish());
        bar.addView(close);

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        siteCanvas = new FrameLayout(this);
        siteCanvas.setClipChildren(false);
        siteCanvas.setBackground(GlassUi.background(this));
        root.addView(siteCanvas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mainBackground = new WebView(this);
        mainBackground.setBackgroundColor(Color.TRANSPARENT);
        siteCanvas.addView(mainBackground, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configureBackground(mainBackground);
        // Main site remains the base surface; no separate desktop-mode shell.
        mainBackground.setVisibility(View.VISIBLE);
        mainBackground.setAlpha(1f);

        minimizedTray = new LinearLayout(this);
        minimizedTray.setOrientation(LinearLayout.HORIZONTAL);
        minimizedTray.setGravity(Gravity.CENTER);
        minimizedTray.setPadding(dp(6), dp(4), dp(6), dp(4));
        minimizedTray.setBackground(liquidGlass());
        minimizedTray.setElevation(dp(14));
        minimizedTray.setVisibility(View.GONE);
        FrameLayout.LayoutParams trayLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trayLp.gravity = Gravity.TOP | Gravity.END;
        trayLp.topMargin = dp(72);
        trayLp.rightMargin = dp(12);
        siteCanvas.addView(minimizedTray, trayLp);
        minimizedTray.setOnTouchListener((v, e) -> handleTrayTouch(e, null));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            if (walletWeb != null) walletWeb.setPadding(0, top, 0, bottom);
            return insets;
        });

        setContentView(rootFrame);
        buildLockOverlay();
        if (appLockRequested()) {
            lockOverlay.setVisibility(View.VISIBLE);
            lockOverlay.bringToFront();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        }
        root.requestApplyInsets();
        siteCanvas.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) applySplitLayout();
        });
        try {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {}

        final String restoreState = savedInstanceState == null ? null : savedInstanceState.getString("window_state");
        siteCanvas.post(() -> {
            if (isFinishing() || isDestroyed() || window1 != null) return;
            if (restoreState != null && !restoreState.isEmpty()) {
                if (restoreWindowState(restoreState)) return;
            }
            try {
                window1 = createWindow(startUrl, true, startChain);
                if (window1 != null) {
                    webView = window1.web;
                    activeWebView = webView;
                    setActive(window1);
                    applySplitLayout();
                    ensureWallet();
                }
            } catch (Throwable t) {
                Log.e(TAG, "create first window failed", t);
                showCrash(t);
            }
        });
    }

    private static TapeVerifier.Result verifiedBackgroundPage() {
        long now = System.currentTimeMillis();
        TapeVerifier.Result r = bgVerifiedPage;
        if (r != null && r.ok && r.bytes != null && now - bgVerifiedAt < 60 * 1000L) return r;
        r = TapeVerifier.fetchAndVerify(MAIN_URL);
        bgVerifiedPage = r;
        bgVerifiedAt = now;
        return r;
    }

    private WebResourceResponse lockedBackgroundResponse() {
        try {
            byte[] bytes = readAsset("fallback-locked.html").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.util.HashMap<String, String> headers = new java.util.HashMap<>();
            headers.put("Content-Type", "text/html; charset=utf-8");
            headers.put("Cache-Control", "no-store");
            headers.put("X-Tape-Mode", "locked");
            return new WebResourceResponse("text/html", "utf-8", 200, "Locked", headers,
                    new java.io.ByteArrayInputStream(bytes));
        } catch (Throwable t) {
            try {
                return new WebResourceResponse("text/html", "utf-8", 200, "Locked",
                        new java.util.HashMap<>(), new java.io.ByteArrayInputStream(
                        "<!doctype html><meta charset='utf-8'><body style='background:#07100b;color:#e6f2ea;font:14px monospace;padding:24px'>页面校验失败，已锁定。</body>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Throwable ignored) { return null; }
        }
    }

    private WebResourceResponse verifiedBackgroundResponse(TapeVerifier.Result vr) {
        try {
            java.util.HashMap<String, String> headers = new java.util.HashMap<>();
            headers.put("Content-Type", "text/html; charset=utf-8");
            headers.put("Cache-Control", "no-store");
            headers.put("X-Tape-Verified", "1");
            headers.put("X-Tape-Mode", "background-client");
            headers.put("X-Tape-Sha256", vr.meta.optString("officialSha256", ""));
            headers.put("X-Tape-Block", String.valueOf(vr.meta.optLong("block", 0)));
            headers.put("Content-Security-Policy",
                    "default-src 'self'; base-uri 'none'; object-src 'none'; form-action 'none'; " +
                    "frame-ancestors 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' https: data: blob:; font-src 'self' data: https:; " +
                    "connect-src 'self' https://api.dewebdesk.link https://relay1.taperelay.com:4246 " +
                    "https://relay2.taperelay.com:4246 https://relay3.taperelay.com:4246 " +
                    "https://relay4.taperelay.com:4246 https://relay5.taperelay.com:4246 " +
                    "https://rpc.xlayer.tech https://xlayer.drpc.org https://196.rpc.thirdweb.com/ " +
                    "https://bsc-dataseed.binance.org https://bsc-rpc.publicnode.com " +
                    "https://ethereum-rpc.publicnode.com; frame-src https:; " +
                    "media-src 'self' https: blob:; worker-src 'self' blob:");
            headers.put("X-Frame-Options", "DENY");
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("Referrer-Policy", "no-referrer");
            return new WebResourceResponse("text/html", "utf-8", 200, "OK", headers,
                    new java.io.ByteArrayInputStream(vr.bytes));
        } catch (Throwable t) {
            return lockedBackgroundResponse();
        }
    }

    /** Beta-only diagnostic: try a third-party main-frame navigation on the background WebView. */
    public static void testBackgroundNavigation(java.util.function.Consumer<String> callback) {
        SiteActivity a = currentRef == null ? null : currentRef.get();
        if (a == null || a.isFinishing()) {
            callback.accept("没有打开的后台页面；先打开一个站点窗口再测试。");
            return;
        }
        a.runOnUiThread(() -> {
            WebView bg = a.mainBackground;
            if (bg == null) {
                callback.accept("后台 WebView 未初始化。");
                return;
            }
            String before = null;
            try { before = bg.getUrl(); } catch (Throwable ignored) {}
            final String official = before != null && (before.startsWith("https://app.dewebdesk.link") || before.startsWith("https://dewebdesk.link"))
                    ? before : "https://app.dewebdesk.link/";
            // Use a real anchor click: WebView.loadUrl() from the app bypasses
            // shouldOverrideUrlLoading, so the old test produced false FAILs.
            String js = "(function(){try{var a=document.createElement('a');"
                    + "a.href='https://example.com/__deweb_nav_test_" + System.currentTimeMillis() + "';"
                    + "a.textContent='x';(document.body||document.documentElement).appendChild(a);a.click();"
                    + "}catch(e){}})()";
            try { bg.evaluateJavascript(js, null); } catch (Throwable t) {
                callback.accept("测试启动失败：" + t.getMessage());
                return;
            }
            bg.postDelayed(() -> {
                String after = null;
                try { after = bg.getUrl(); } catch (Throwable ignored) {}
                boolean blocked = after == null || after.startsWith("https://app.dewebdesk.link") || after.startsWith("https://dewebdesk.link");
                try { if (!blocked) bg.loadUrl(official); } catch (Throwable ignored) {}
                callback.accept(blocked
                        ? "PASS：后台主框架仍停留在官方来源，未跳转 example.com。\n当前：" + after
                        : "FAIL：后台主框架跳到了第三方。\n当前：" + after + "\n已恢复官方页面。");
            }, 2000);
        });
    }

    private void configureBackground(WebView bg) {
        bg.setFilterTouchesWhenObscured(true);
        WebSettings s = bg.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        bg.setWebChromeClient(fileChooser.chromeClient());
        try {
            WebViewCompat.addDocumentStartJavaScript(bg, readAsset("page-fix.js"), BG_ORIGINS);
            WebViewCompat.addDocumentStartJavaScript(bg, readAsset("background-bridge.js"), BG_ORIGINS);
            WebViewCompat.addWebMessageListener(
                    bg,
                    "dewebBridge",
                    BG_ORIGINS,
                    (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                            onBackgroundMessage(message, sourceOrigin, isMainFrame)
            );
        } catch (Throwable t) {
            Log.e(TAG, "background bridge failed", t);
        }
        bg.setWebViewClient(new WebViewClient() {
@Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                // Keep the background official page on official origins; do not
                // let a hidden navigation turn it into a third-party shell.
                if (request.isForMainFrame() && (scheme.equals("http") || scheme.equals("https"))) {
                    String host = uri.getHost();
                    if (host == null || !BG_ORIGINS.contains("https://" + host)) return true;
                }
                if (scheme.equals("http") || scheme.equals("https")) return false;
                try { if (!IntentGuard.open(SiteActivity.this, uri)) return true; } catch (Exception e) {}
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    if (!request.isForMainFrame()) return null;
                    Uri u = request.getUrl();
                    if (u == null || !"https".equalsIgnoreCase(u.getScheme()) || !"app.dewebdesk.link".equalsIgnoreCase(u.getHost())) return null;
                    String path = u.getPath();
                    if (path != null && !path.isEmpty() && !"/".equals(path)) return lockedBackgroundResponse();
                    TapeVerifier.Result vr = verifiedBackgroundPage();
                    if (vr == null || !vr.ok || vr.bytes == null) return lockedBackgroundResponse();
                    return verifiedBackgroundResponse(vr);
                } catch (Throwable t) {
                    return lockedBackgroundResponse();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (minimizedTray != null && minimizedTray.getVisibility() == View.VISIBLE) positionMinimizedTray();
            }
        });
        bg.loadUrl(MAIN_URL);
    }

    private boolean isNativeApproved(String origin) {
        Long exp = nativeApprovedExpiry.get(origin);
        if (exp == null || exp <= System.currentTimeMillis()) {
            if (nativeApproved.remove(origin)) saveNativeApproved();
            nativeApprovedExpiry.remove(origin);
            return false;
        }
        return true;
    }

    private void approveNative(String origin) {
        Set<String> revoked = new HashSet<>(authPrefs.getStringSet(WALLET_REVOKE_KEY, new HashSet<>()));
        revoked.remove(origin);
        mergeRevokedOrigins(authPrefs, revoked);
        nativeApproved.add(origin);
        nativeApprovedExpiry.put(origin, System.currentTimeMillis() + SITE_AUTH_TTL_MS);
        saveNativeApproved();
    }

    private void saveNativeApproved() {
        try {
            Set<String> revoked = authPrefs.getStringSet(WALLET_REVOKE_KEY, new HashSet<>());
            nativeApproved.removeAll(revoked);
            nativeApprovedExpiry.keySet().removeAll(revoked);
            JSONObject ex = new JSONObject();
            for (Map.Entry<String, Long> e : nativeApprovedExpiry.entrySet()) ex.put(e.getKey(), e.getValue());
            authPrefs.edit()
                    .putStringSet("native_approved_origins", new HashSet<>(nativeApproved))
                    .putString("native_approved_expiry", ex.toString())
                    .apply();
        } catch (Throwable ignored) {}
    }

    private static final String WALLET_REVOKE_TS_KEY = "wallet_revoke_origins_ts";
    private static final int MAX_REVOKED_ORIGINS = 200;

    /**
     * Keeps the revocation tombstone set bounded. Tombstones are needed until
     * the old wallet page's localStorage approval is gone; beyond 200 origins
     * the oldest ones are dropped.
     */
    public static Set<String> mergeRevokedOrigins(SharedPreferences sp, Set<String> revoked) {
        try {
            JSONObject ts = new JSONObject(sp.getString(WALLET_REVOKE_TS_KEY, "{}"));
            long now = System.currentTimeMillis();
            for (String o : revoked) {
                if (!ts.has(o)) ts.put(o, now);
            }
            java.util.List<String> stale = new ArrayList<>();
            java.util.Iterator<String> it = ts.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!revoked.contains(k)) stale.add(k);
            }
            for (String k : stale) ts.remove(k);

            if (revoked.size() > MAX_REVOKED_ORIGINS) {
                java.util.List<String> all = new ArrayList<>(revoked);
                all.sort((a, b) -> Long.compare(ts.optLong(a, 0L), ts.optLong(b, 0L)));
                java.util.Set<String> keep = new HashSet<>(all.subList(all.size() - MAX_REVOKED_ORIGINS, all.size()));
                for (String o : all) {
                    if (!keep.contains(o)) ts.remove(o);
                }
                revoked.clear();
                revoked.addAll(keep);
            }
            sp.edit().putStringSet(WALLET_REVOKE_KEY, revoked)
                    .putString(WALLET_REVOKE_TS_KEY, ts.toString()).apply();
        } catch (Throwable ignored) {}
        return revoked;
    }

    private void onBackgroundMessage(WebMessageCompat message, Uri sourceOrigin, boolean isMainFrame) {
        try {
            if (isSiteLocked() || !isMainFrame) return;
            JSONObject req = new JSONObject(message.getData() == null ? "{}" : message.getData());
            if (!"deweb_open_site".equals(req.optString("method"))) return;
            JSONArray params = req.optJSONArray("params");
            String target = params == null ? "" : params.optString(0);
            Uri uri = Uri.parse(target);
            if (uri.getHost() == null || (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))) return;
            String chain = params == null ? "56" : normalizeChain(params.optString(1));
            openFromBackground(uri.toString(), chain);
        } catch (Throwable t) {
            Log.e(TAG, "background message failed", t);
        }
    }

    private void openFromBackground(String url) {
        openFromBackground(url, "56");
    }

    private void openFromBackground(String url, String chainHint) {
        try {
            if (window1 == null) {
                window1 = createWindow(url, true, chainHint);
                if (window1 != null) {
                    webView = window1.web;
                    activeWebView = webView;
                    setActive(window1);
                }
            } else {
                addSecondWindow(url, chainHint);
            }
        } catch (Throwable t) {
            Log.e(TAG, "open from background failed", t);
            Toast.makeText(this, "打开窗口失败", Toast.LENGTH_SHORT).show();
        }
    }

    private SiteWindow createDirectWindow(String url) {
        SiteWindow win = new SiteWindow();
        WebView web = new WebView(this);
        win.web = web;
        siteCanvas.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        win.chainHint = "56";
        configureSiteWebView(web, win, url);
        return win;
    }

    private void showCrash(Throwable t) {
        try {
            String msg = Log.getStackTraceString(t);
            if (msg.length() > 1800) msg = msg.substring(0, 1800);
            AlertDialog crashDialog = new AlertDialog.Builder(this)
                    .setTitle("窗口创建失败")
                    .setMessage(msg)
                    .setPositiveButton("关闭", (d, w) -> finish())
                    .show();
            try { GlassUi.hardenTree(crashDialog.getWindow().getDecorView()); } catch (Throwable ignored) {}
            try { GlassUi.secureDialog(crashDialog, PrivacyPrefs.isEnabled(this) || isSiteLocked()); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            finish();
        }
    }

    private SiteWindow createWindow(String url, boolean primary) {
        return createWindow(url, primary, "56", false);
    }

    private SiteWindow createWindow(String url, boolean primary, String chainHint) {
        return createWindow(url, primary, chainHint, false);
    }

    private SiteWindow createWindow(String url, boolean primary, String chainHint, boolean forceConnect) {
        if (!isSafeSiteUrl(url)) return null;
        SiteWindow win = new SiteWindow();
        win.forceConnect = forceConnect;
        win.walletWindowId = "win-" + (++walletWindowSeq) + "-" + System.currentTimeMillis();
        win.chainHint = normalizeChain(chainHint);

        FrameLayout card = new FrameLayout(this);
        card.setBackground(GlassUi.panel(this, 16, false));
        card.setElevation(dp(10));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        card.addView(inner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackground(GlassUi.titleBar(this));
        titleBar.setPadding(dp(12), 0, dp(4), 0);
        inner.addView(titleBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        TextView label = new TextView(this);
        label.setTextSize(11f);
        label.setTypeface(Typeface.MONOSPACE);
        label.setTextColor(Color.rgb(21, 37, 29));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setText(siteUrlText(url));
        titleBar.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button lockBtn = navButton("锁", "锁定窗口");
        lockBtn.setTextSize(10f);
        lockBtn.setOnClickListener(v -> lockCurrentWindow());
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        lockLp.setMargins(dp(2), 0, dp(2), 0);
        titleBar.addView(lockBtn, lockLp);

        Button floatBtn = navButton("浮", "悬浮此窗口");
        floatBtn.setTextSize(10f);
        floatBtn.setVisibility(View.VISIBLE);
        floatBtn.setOnClickListener(v -> confirmFloating(win));
        win.floatButton = floatBtn;
        LinearLayout.LayoutParams floatLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        floatLp.setMargins(dp(2), 0, dp(2), 0);
        titleBar.addView(floatBtn, floatLp);

        Button maximize = navButton("□", "最大化窗口");
        maximize.setTextSize(16f);
        maximize.setOnClickListener(v -> maximizeWindow(win));
        win.maxButton = maximize;
        LinearLayout.LayoutParams maxLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        maxLp.setMargins(dp(2), 0, dp(2), 0);
        titleBar.addView(maximize, maxLp);

        Button minimize = navButton("—", "最小化窗口");
        win.minButton = minimize;
        minimize.setTextSize(16f);
        minimize.setOnClickListener(v -> minimizeWindow(win));
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        minLp.setMargins(dp(2), 0, dp(2), 0);
        titleBar.addView(minimize, minLp);

        Button close = navButton("×", "关闭此窗口");
        close.setTextSize(18f);
        close.setOnClickListener(v -> closeWindow(win));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        closeLp.setMargins(dp(4), 0, dp(2), 0);
        titleBar.addView(close, closeLp);

        final WebView web = new WebView(this);
        inner.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        win.card = card;
        win.inner = inner;
        win.label = label;
        win.web = web;

        titleBar.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int startLeft, startTop;
            private int startW, startH;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                setActive(win);
                if (splitMode) return false;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (win.maximized) {
                        win.maximized = false;
                        if (win.maxButton != null) win.maxButton.setText("□");
                    }
                    if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT || lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                        lp.width = card.getWidth();
                        lp.height = card.getHeight();
                        card.setLayoutParams(lp);
                    }
                    startX = e.getRawX();
                    startY = e.getRawY();
                    startLeft = lp.leftMargin;
                    startTop = lp.topMargin;
                    startW = lp.width;
                    startH = lp.height;
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int maxL = Math.max(0, siteCanvas.getWidth() - startW);
                    int maxT = Math.max(0, siteCanvas.getHeight() - startH);
                    lp.leftMargin = Math.max(0, Math.min(maxL, startLeft + Math.round(e.getRawX() - startX)));
                    lp.topMargin = Math.max(0, Math.min(maxT, startTop + Math.round(e.getRawY() - startY)));
                    card.setLayoutParams(lp);
                    return true;
                }
                return false;
            }
        });

        web.setOnTouchListener((v, e) -> {
            setActive(win);
            card.bringToFront();
            return false;
        });

        final View resize = new View(this);
        GradientDrawable handle = new GradientDrawable();
        handle.setColor(0xAAFFFFFF);
        handle.setCornerRadius(dp(6));
        handle.setStroke(dp(1), 0x886B8F7E);
        resize.setBackground(handle);
        resize.setAlpha(0.45f);
        FrameLayout.LayoutParams resizeLp = new FrameLayout.LayoutParams(dp(22), dp(22));
        resizeLp.gravity = Gravity.BOTTOM | Gravity.END;
        resizeLp.setMargins(0, 0, dp(4), dp(4));
        card.addView(resize, resizeLp);
        win.resizeView = resize;
        resize.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int startW, startH;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                setActive(win);
                if (splitMode) return false;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT || lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                        lp.width = card.getWidth();
                        lp.height = card.getHeight();
                        card.setLayoutParams(lp);
                    }
                    startX = e.getRawX();
                    startY = e.getRawY();
                    startW = lp.width;
                    startH = lp.height;
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int maxW = Math.max(dp(220), siteCanvas.getWidth() - lp.leftMargin);
                    int maxH = Math.max(dp(180), siteCanvas.getHeight() - lp.topMargin);
                    lp.width = Math.max(dp(260), Math.min(maxW, startW + Math.round(e.getRawX() - startX)));
                    lp.height = Math.max(dp(190), Math.min(maxH, startH + Math.round(e.getRawY() - startY)));
                    card.setLayoutParams(lp);
                    return true;
                }
                return false;
            }
        });

        FrameLayout.LayoutParams lp;
        int cw = Math.max(dp(300), siteCanvas.getWidth());
        int ch = Math.max(dp(300), siteCanvas.getHeight());
        if (primary) {
            int w = Math.round(cw * 0.94f);
            int h = Math.round(ch * 0.62f);
            lp = new FrameLayout.LayoutParams(w, h);
            lp.leftMargin = Math.max(0, (cw - w) / 2);
            lp.topMargin = Math.max(0, Math.round(ch * 0.08f));
        } else {
            int w = Math.round(cw * 0.86f);
            int h = Math.round(ch * 0.52f);
            lp = new FrameLayout.LayoutParams(w, h);
            lp.leftMargin = Math.max(0, (cw - w) / 2);
            lp.topMargin = Math.max(0, ch - h - dp(6));
        }
        siteCanvas.addView(card, lp);
        configureSiteWebView(web, win, url);
        return win;
    }

    private void buildLockOverlay() {
        lockOverlay = new FrameLayout(this);
        lockOverlay.setBackground(GlassUi.opaqueLock(this));
        lockOverlay.setVisibility(View.GONE);
        lockOverlay.setClickable(true);
        lockOverlay.setFocusable(true);
        lockOverlay.setFilterTouchesWhenObscured(true);
        lockOverlay.setOnClickListener(v -> {});
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF20D1D15, 0xEE08110C, 0xF20A1710});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), 0x55FFFFFF);
        box.setBackground(GlassUi.panel(this, 26, true));
        TextView title = new TextView(this);
        title.setText("窗口已锁定");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFEAF7EF);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        TextView sub = new TextView(this);
        sub.setText("内容已隐藏，解锁后继续使用");
        sub.setTextSize(11f);
        sub.setTextColor(0x99C9DED2);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        box.addView(sub, subLp);
        Button unlock = navButton("解锁", "解锁窗口");
        unlock.setTextSize(13f);
        unlock.setLayoutParams(new LinearLayout.LayoutParams(dp(140), dp(44)));
        unlock.setFilterTouchesWhenObscured(true);
        unlock.setOnClickListener(v -> requestUnlock());
        LinearLayout.LayoutParams unlockLp = new LinearLayout.LayoutParams(dp(140), dp(44));
        unlockLp.topMargin = dp(18);
        box.addView(unlock, unlockLp);
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.gravity = Gravity.CENTER;
        lockOverlay.addView(box, boxLp);
        rootFrame.addView(lockOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private boolean deviceIsSecure() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            return km != null && km.isDeviceSecure();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Reads the persisted app-lock flag. A lock with no device credential can
     * never be unlocked (requestUnlock only asks the user to set a screen lock),
     * so clear it instead of leaving FLAG_SECURE stuck on forever.
     */
    private boolean appLockRequested() {
        SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        if (!sp.getBoolean("site_locked", false)) return false;
        if (deviceIsSecure()) return true;
        sp.edit().putBoolean("site_locked", false).apply();
        Toast.makeText(this, "设备未设置锁屏密码，应用锁已自动解除", Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean isSiteLocked() {
        return appLockRequested()
                || (lockOverlay != null && lockOverlay.getVisibility() == View.VISIBLE);
    }

    private void lockCurrentWindow() {
        SiteWindow win = null;
        for (SiteWindow w : allWindows()) {
            if (w.web != null && w.web == activeWebView) { win = w; break; }
        }
        if (win == null) {
            Toast.makeText(this, "先点一个窗口", Toast.LENGTH_SHORT).show();
            return;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            Toast.makeText(this, "请先给手机设置锁屏密码，再使用窗口锁定", Toast.LENGTH_LONG).show();
            return;
        }
        doLockWindow(win);
    }

    private void doLockWindow(SiteWindow win) {
        lockedWindow = win;
        getSharedPreferences("deweb_shell", MODE_PRIVATE).edit().putBoolean("site_locked", true).apply();
        applyLockUi();
        Toast.makeText(this, "已锁定；解锁需要系统锁屏验证", Toast.LENGTH_LONG).show();
    }

    private void applyScreenshotProtection() {
        boolean walletVisible = walletWeb != null && walletWeb.getVisibility() == View.VISIBLE;
        boolean locked = isSiteLocked();
        boolean privacy = PrivacyPrefs.isEnabled(this);
        // The wallet normally fills the whole activity, so allowing it to be
        // screenshotted only exposes the wallet page. When it is mounted inside
        // a floating window it covers just that float, and the activity behind
        // must stay protected.
        boolean walletCoversActivity = walletVisible && !walletAttachedToFloat;
        boolean secure = locked || (privacy && !walletCoversActivity);
        if (secure) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(true);
        }
        // Floating windows carry site content, not just the wallet. Opening the
        // wallet must never make the other floats capturable.
        boolean floatingSecure = locked || privacy;
        FloatingSession walletHost = (walletVisible && walletAttachedToFloat
                && walletFloatHost != null && !walletFloatHost.minimized) ? walletFloatHost : null;
        updateFloatingScreenshot(floatingSecure, walletHost);
        recordScreenshotState(secure, walletVisible);
    }

    private void recordScreenshotState(boolean secure, boolean walletVisible) {
        try {
            int floats = floatingSessions.size();
            int floatsSecure = 0;
            for (FloatingSession f : new ArrayList<>(floatingSessions)) {
                if (f != null && f.params != null
                        && (f.params.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) floatsSecure++;
            }
            boolean traySecure = capsuleTrayParams != null
                    && (capsuleTrayParams.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0;
            String id = Integer.toHexString(System.identityHashCode(this));
            SCREENSHOT_STATES.put(id, "site#" + id + " secure=" + secure
                    + " lockOverlay=" + (lockOverlay != null && lockOverlay.getVisibility() == View.VISIBLE)
                    + " walletVisible=" + walletVisible
                    + " floats=" + floats + " floatsSecure=" + floatsSecure
                    + " traySecure=" + traySecure);
        } catch (Throwable ignored) {}
    }

    /** Beta diagnostics snapshot: one line per live site window. */
    public static String screenshotDiagnostics() {
        if (SCREENSHOT_STATES.isEmpty()) return "sites=0";
        List<String> keys = new ArrayList<>(SCREENSHOT_STATES.keySet());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder("sites=").append(keys.size());
        for (String k : keys) {
            String v = SCREENSHOT_STATES.get(k);
            if (v != null) sb.append('\n').append(v);
        }
        return sb.toString();
    }

    /**
     * Privacy is a single global switch, but each site window and each floating
     * overlay keeps its own FLAG_SECURE. Without this broadcast, toggling the
     * switch in one place left other windows/overlays secure until they resumed,
     * which looks like "screenshots blocked although the switch is off".
     */
    public static void notifyPrivacyChanged() {
        for (WeakReference<SiteActivity> ref : new ArrayList<>(liveRefs)) {
            SiteActivity a = ref == null ? null : ref.get();
            if (a == null) { liveRefs.remove(ref); continue; }
            a.runOnUiThread(a::applyScreenshotProtection);
        }
    }

    private void updateFloatingScreenshot(boolean secure, FloatingSession walletExempt) {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            if (f.params == null) continue;
            // A native auth/tx summary overlay must always be screenshot-protected,
            // even when the wallet-visible exception is active for this float.
            boolean fSecure = (secure && f != walletExempt) || f.authOverlay != null;
            if (fSecure) f.params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
            else f.params.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
            try {
                if (f.root != null && f.root.getParent() != null && floatingManager != null)
                    floatingManager.updateViewLayout(f.root, f.params);
            } catch (Throwable ignored) {}
        }
        if (capsuleTrayParams != null) {
            if (secure) capsuleTrayParams.flags |= WindowManager.LayoutParams.FLAG_SECURE;
            else capsuleTrayParams.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
            try {
                if (capsuleTray != null && floatingManager != null)
                    floatingManager.updateViewLayout(capsuleTray, capsuleTrayParams);
            } catch (Throwable ignored) {}
        }
    }

    private void applyLockUi() {
        cancelForLock();
        hideFloatingForLock();
        if (lockOverlay != null) {
            lockOverlay.setVisibility(View.VISIBLE);
            lockOverlay.bringToFront();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        updateFloatingScreenshot(true, null);
    }

    private void syncLockUi() {
        if (isFinishing() || isDestroyed() || lockOverlay == null) return;
        boolean locked = appLockRequested();
        if (locked && lockOverlay.getVisibility() != View.VISIBLE) {
            applyLockUi();
        } else if (!locked && lockOverlay.getVisibility() == View.VISIBLE) {
            lockOverlay.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(true);
            restoreFloatingAfterLock();
        }
    }

    private void cancelForLock() {
        walletCanceledAt = System.currentTimeMillis();
        walletPending.clear();
        walletQueue.clear();
        authQueue.clear();
        authDialogShowing = false;
        interactivePending = false;
        floatingKeepForRequest = false;
        floatingConnectInProgress = false;
        floatingNextNativeConfirmed = false;
        floatingApproved.clear();
        hideWallet();
        if (walletWeb != null) {
            try { walletWeb.stopLoading(); } catch (Throwable ignored) {}
        }
        if (siteAuthDialog != null) {
            try { siteAuthDialog.dismiss(); } catch (Throwable ignored) {}
            siteAuthDialog = null;
        }
        for (FloatingSession f : new ArrayList<>(floatingSessions)) clearFloatingAuth(f);
    }

    private void hideFloatingForLock() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            f.wasMinimizedBeforeLock = f.minimized;
            f.hiddenForLock = true;
            clearFloatingAuth(f);
            try {
                if (f.root != null && floatingManager != null) floatingManager.removeView(f.root);
            } catch (Throwable ignored) {}
        }
        removeFloatingBackdrop();
        capsuleChips.clear();
        if (capsuleTray != null) {
            try { if (floatingManager != null) floatingManager.removeView(capsuleTray); } catch (Throwable ignored) {}
            capsuleTray = null;
            capsuleTrayClose = null;
            capsuleTrayParams = null;
        }
    }

    private void restoreFloatingAfterLock() {
        if (isFinishing() || isDestroyed() || floatingSessions.isEmpty()) return;
        if (floatingManager == null) floatingManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (hasExpandedFloating()) ensureFloatingBackdrop();
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            if (!f.hiddenForLock || f.root == null) continue;
            f.hiddenForLock = false;
            try {
                if (f.wasMinimizedBeforeLock) {
                    f.minimized = true;
                    f.root.setVisibility(View.GONE);
                    f.params.width = 1;
                    f.params.height = 1;
                    f.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    floatingManager.addView(f.root, f.params);
                    addCapsuleChip(f);
                } else {
                    f.minimized = false;
                    f.root.setVisibility(View.VISIBLE);
                    f.params.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                    floatingManager.addView(f.root, f.params);
                }
            } catch (Throwable ignored) {}
        }
        updateFloatingBackdrop();
        bringCapsuleTrayToFront();
    }

    private void requestUnlock() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            Toast.makeText(this, "请先给手机设置锁屏密码", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = km.createConfirmDeviceCredentialIntent("解锁窗口", "验证设备锁屏");
            if (intent == null) {
                Toast.makeText(this, "无法启动设备验证", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(intent, REQ_UNLOCK);
        } catch (Throwable t) {
            Toast.makeText(this, "无法启动设备验证", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmFloating(SiteWindow win) {
        if (!isFloatingEnabled(this)) {
            Toast.makeText(this, "请先在设置中启用悬浮窗", Toast.LENGTH_SHORT).show();
            showSiteSettings();
            return;
        }
        if (win == null || win.web == null) return;
        String url = win.web.getUrl();
        if (url == null || url.startsWith("about:")) url = startUrl;
        String origin = originKey(Uri.parse(url == null ? "" : url));
        SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        Set<String> approved = new HashSet<>(sp.getStringSet("floating_approved_origins", new HashSet<>()));
        if (approved.contains(origin)) {
            setActive(win);
            requestFloatingWindow();
            return;
        }
        final String safeOrigin = origin;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        GlassUi.blurDialog(dialog);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(18));
        panel.setBackground(GlassUi.panel(this, 24, true));
        panel.setFilterTouchesWhenObscured(true);

        TextView title = new TextView(this);
        title.setText("悬浮此站点？");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFEAF7EF);
        panel.addView(title);

        TextView body = new TextView(this);
        body.setText("该窗口会显示在其他应用上方。");
        body.setTextSize(12f);
        body.setTextColor(0xCCD8E8DE);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(10);
        panel.addView(body, bodyLp);

        TextView originBox = new TextView(this);
        originBox.setText("站点：\n" + safeOrigin);
        originBox.setTextSize(11f);
        originBox.setTypeface(Typeface.MONOSPACE);
        originBox.setTextColor(0xFFEAF7EF);
        originBox.setTextIsSelectable(true);
        originBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        originBox.setBackground(GlassUi.button(this, 14, true));
        LinearLayout.LayoutParams originLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        originLp.topMargin = dp(12);
        panel.addView(originBox, originLp);

        TextView confirm = new TextView(this);
        confirm.setText("确认这是你信任的站点后再继续。");
        confirm.setTextSize(11f);
        confirm.setTextColor(0x99C9DED2);
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confirmLp.topMargin = dp(12);
        panel.addView(confirm, confirmLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setAllCaps(false);
        cancel.setTextSize(12f);
        cancel.setTextColor(0xFFE6F2EA);
        cancel.setBackground(GlassUi.button(this, 14, true));
        cancel.setFilterTouchesWhenObscured(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(80), dp(42)));

        Button go = new Button(this);
        go.setText("继续悬浮");
        go.setAllCaps(false);
        go.setTextSize(12f);
        go.setTextColor(0xFF03150D);
        GradientDrawable goBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF8AF2C8, 0xFF56E6B0});
        goBg.setCornerRadius(dp(14));
        go.setBackground(goBg);
        go.setFilterTouchesWhenObscured(true);
        go.setOnClickListener(v -> {
            dialog.dismiss();
            approved.add(safeOrigin);
            sp.edit().putStringSet("floating_approved_origins", approved).apply();
            setActive(win);
            requestFloatingWindow();
        });
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(110), dp(42));
        goLp.leftMargin = dp(8);
        actions.addView(go, goLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(16);
        panel.addView(actions, actionsLp);

        GlassUi.hardenTree(panel);
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.6f;
            window.setAttributes(lp);
        }
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        });
        dialog.show();
        GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || isSiteLocked());
    }

    private void requestFloatingWindow() {
        if (!isFloatingEnabled(this)) {
            Toast.makeText(this, "请先在设置中启用悬浮窗", Toast.LENGTH_SHORT).show();
            showSiteSettings();
            return;
        }
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "系统不支持悬浮窗", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
            } catch (Throwable t) {
                Toast.makeText(this, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        createFloatingWindow();
    }

    private FloatingSession findFloating(SiteWindow win) {
        for (FloatingSession f : floatingSessions) if (f.window == win) return f;
        return null;
    }

    private boolean hasFloating() {
        return !floatingSessions.isEmpty();
    }

    private int screenWidthPx() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeightPx() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int maxFloatingWidth() {
        return Math.max(dp(220), screenWidthPx() - dp(12));
    }

    private int maxFloatingHeight() {
        return Math.max(dp(260), screenHeightPx() - floatingBottomSafeInset() - dp(16));
    }

    private void clampFloating(FloatingSession f) {
        if (f == null || f.params == null) return;
        f.params.width = Math.min(Math.max(f.params.width, dp(220)), maxFloatingWidth());
        f.params.height = Math.min(Math.max(f.params.height, dp(260)), maxFloatingHeight());
        int maxX = Math.max(0, screenWidthPx() - f.params.width);
        int maxY = Math.max(0, screenHeightPx() - floatingBottomSafeInset() - f.params.height);
        f.params.x = Math.max(0, Math.min(f.params.x, maxX));
        f.params.y = Math.max(0, Math.min(f.params.y, maxY));
    }

    private void addFloatingEdge(FrameLayout root, int gravity, int width, int height) {
        View edge = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setStroke(dp(1), 0x66CFEFE0);
        g.setCornerRadius(dp(12));
        edge.setBackground(g);
        edge.setClickable(false);
        edge.setFocusable(false);
        edge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.gravity = gravity;
        root.addView(edge, lp);
    }

    private void addFloatingEdges(FrameLayout root) {
        addFloatingEdge(root, Gravity.START, dp(2), ViewGroup.LayoutParams.MATCH_PARENT);
        addFloatingEdge(root, Gravity.END, dp(2), ViewGroup.LayoutParams.MATCH_PARENT);
        addFloatingEdge(root, Gravity.BOTTOM, ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
    }

    private int floatingBottomSafeInset() {
        return dp(48);
    }

    private boolean ensureFloatingBackdrop() {
        if (floatingManager == null) return false;
        if (floatingBackdrop != null) return false;
        try {
            View v = new View(this);
            v.setBackgroundColor(Color.TRANSPARENT);
            v.setOnClickListener(x -> collapseExpandedFloating());
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            floatingBackdropParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            floatingBackdropParams.gravity = Gravity.TOP | Gravity.START;
            floatingBackdrop = v;
            floatingManager.addView(floatingBackdrop, floatingBackdropParams);
            return true;
        } catch (Throwable t) {
            floatingBackdrop = null;
            floatingBackdropParams = null;
            return false;
        }
    }

    private void removeFloatingBackdrop() {
        if (floatingBackdrop == null || floatingManager == null) {
            floatingBackdrop = null;
            floatingBackdropParams = null;
            return;
        }
        try { floatingManager.removeView(floatingBackdrop); } catch (Throwable ignored) {}
        floatingBackdrop = null;
        floatingBackdropParams = null;
    }

    private boolean hasExpandedFloating() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            if (f != null && !f.minimized && !f.hiddenForLock && f.root != null) return true;
        }
        return false;
    }

    private void updateFloatingBackdrop() {
        boolean any = hasExpandedFloating();
        if (!any) {
            removeFloatingBackdrop();
            return;
        }
        boolean added = ensureFloatingBackdrop();
        if (added) {
            for (FloatingSession f : new ArrayList<>(floatingSessions)) {
                if (f != null && !f.minimized && !f.hiddenForLock) bringRootFront(f);
            }
        }
    }

    private void collapseExpandedFloating() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            if (f != null && !f.minimized) minimizeFloating(f);
        }
    }

    private void createFloatingWindow() {
        SiteWindow win = null;
        for (SiteWindow w : allWindows()) {
            if (w.web != null && w.web == activeWebView) { win = w; break; }
        }
        if (win == null || win.web == null) {
            Toast.makeText(this, "先点一个窗口", Toast.LENGTH_SHORT).show();
            return;
        }
        FloatingSession existing = findFloating(win);
        if (existing != null) {
            expandFloating(existing);
            return;
        }
        if (!MembershipManager.isPro(this)) {
            if (!floatingSessions.isEmpty()) {
                Toast.makeText(this, "Pro 会员可同时悬浮多个窗口", Toast.LENGTH_LONG).show();
                return;
            }
        } else if (floatingSessions.size() >= 4) {
            Toast.makeText(this, "Pro 最多 4 个悬浮窗", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            FloatingSession f = new FloatingSession();
            f.window = win;
            f.web = win.web;
            f.parent = (ViewGroup) f.web.getParent();
            f.webParams = f.web.getLayoutParams();
            if (f.parent != null) f.parent.removeView(f.web);
            if (win.card != null) win.card.setVisibility(View.GONE);

            if (floatingManager == null) floatingManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            f.root = new FrameLayout(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF207100B);
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), 0x66FFFFFF);
            f.root.setBackground(GlassUi.panel(this, 18, true));
            f.root.setClipToOutline(true);
            f.column = new LinearLayout(this);
            f.column.setOrientation(LinearLayout.VERTICAL);
            f.root.addView(f.column, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(12), 0, dp(4), 0);
            bar.setBackground(GlassUi.bar(this, true));
            TextView label = new TextView(this);
            label.setText(siteUrlText(f.web.getUrl() == null ? startUrl : f.web.getUrl())); label.setEllipsize(TextUtils.TruncateAt.END);
            win.label = label;
            label.setTextSize(10f);
            label.setTextColor(0xFFE6F2EA);
            label.setSingleLine(true);
            bar.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView min = new TextView(this);
            min.setText("—");
            min.setTextSize(20f);
            min.setTextColor(0xFFE6F2EA);
            min.setGravity(Gravity.CENTER);
            bar.addView(min, new LinearLayout.LayoutParams(dp(38), dp(36)));
            TextView max = new TextView(this);
            max.setText("□");
            max.setTextSize(16f);
            max.setTextColor(0xFFE6F2EA);
            max.setGravity(Gravity.CENTER);
            bar.addView(max, new LinearLayout.LayoutParams(dp(38), dp(36)));
            TextView restore = new TextView(this);
            restore.setText("还原");
            restore.setTextSize(11f);
            restore.setTextColor(0xFF56E6B0);
            restore.setGravity(Gravity.CENTER);
            bar.addView(restore, new LinearLayout.LayoutParams(dp(48), dp(36)));
            TextView settings = new TextView(this);
            settings.setText("⚙");
            settings.setTextSize(15f);
            settings.setTextColor(0xFFE6F2EA);
            settings.setGravity(Gravity.CENTER);
            settings.setBackground(GlassUi.button(this, 11, true));
            settings.setOnClickListener(v -> showFloatingSettings(f));
            bar.addView(settings, new LinearLayout.LayoutParams(dp(32), dp(36)));

            TextView close = new TextView(this);
            close.setText("×");
            close.setTextSize(22f);
            close.setTextColor(0xFFE6F2EA);
            close.setGravity(Gravity.CENTER);
            bar.addView(close, new LinearLayout.LayoutParams(dp(40), dp(36)));
            GlassUi.hardenTree(min);
            GlassUi.hardenTree(max);
            GlassUi.hardenTree(restore);
            GlassUi.hardenTree(settings);
            GlassUi.hardenTree(close);
            f.column.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

            FrameLayout content = new FrameLayout(this);
            content.setBackground(GlassUi.background(this));
            content.addView(f.web, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            f.column.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            f.resize = new TextView(this);
            f.resize.setText("◢");
            f.resize.setTextSize(18f);
            f.resize.setTextColor(0x99E6F2EA);
            f.resize.setGravity(Gravity.CENTER);
            f.resize.setBackgroundColor(0x33000000);
            FrameLayout.LayoutParams resizeLp = new FrameLayout.LayoutParams(dp(34), dp(34));
            resizeLp.gravity = Gravity.BOTTOM | Gravity.END;
            f.root.addView(f.resize, resizeLp);

            f.capsule = new TextView(this);
            f.capsule.setText(siteUrlText(f.web.getUrl() == null ? startUrl : f.web.getUrl()));
            f.capsule.setTextSize(11f);
            f.capsule.setTextColor(0xFFE6F2EA);
            f.capsule.setSingleLine(true);
            f.capsule.setGravity(Gravity.CENTER_VERTICAL);
            f.capsule.setPadding(dp(14), 0, dp(14), 0);
            GradientDrawable capsuleBg = new GradientDrawable();
            capsuleBg.setColor(0xF20D1D15);
            capsuleBg.setCornerRadius(dp(22));
            capsuleBg.setStroke(dp(1), 0x66FFFFFF);
            f.capsule.setBackground(capsuleBg);
            f.capsule.setVisibility(View.GONE);
            GlassUi.hardenTree(f.capsule);
            f.root.addView(f.capsule, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            addFloatingEdges(f.root);
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            f.params = new WindowManager.LayoutParams(
                    Math.min(dp(320), maxFloatingWidth()), Math.min(dp(420), maxFloatingHeight()), type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            f.params.gravity = Gravity.TOP | Gravity.START;
            f.params.x = dp(18) + floatingSessions.size() * dp(18);
            f.params.y = dp(140) + floatingSessions.size() * dp(18);
            f.normW = f.params.width;
            f.normH = f.params.height;
            f.normX = f.params.x;
            f.normY = f.params.y;
            floatingSessions.add(f);
            ensureFloatingBackdrop();
            floatingManager.addView(f.root, f.params);
            f.root.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE && !f.minimized) {
                    minimizeFloating(f);
                    return true;
                }
                return false;
            });
            clampFloating(f);

            restore.setOnClickListener(v -> { restoreFloating(f); bringSiteToFront(); });
            close.setOnClickListener(v -> { restoreFloating(f); bringSiteToFront(); });
            min.setOnClickListener(v -> minimizeFloating(f));
            max.setOnClickListener(v -> toggleMaximizeFloating(f));
            f.capsule.setOnClickListener(v -> expandFloating(f));
            dragHandle(bar, f);
            dragResize(f.resize, f);
            for (FloatingSession other : new ArrayList<>(floatingSessions)) {
                if (other != f && !other.minimized) minimizeFloating(other);
            }
            expandFloating(f, false);
            Toast.makeText(this, "悬浮窗已开启；其他窗口已收进胶囊托盘", Toast.LENGTH_SHORT).show();
            applyScreenshotProtection();
        } catch (Throwable t) {
            android.util.Log.e("DeWEB", "floating create failed", t);
            Toast.makeText(this, "悬浮窗创建失败：" + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void dragHandle(View handle, FloatingSession f) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                WindowManager.LayoutParams p = f.params;
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = p.x; startY = p.y;
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    p.x = startX + Math.round(e.getRawX() - downX);
                    p.y = startY + Math.round(e.getRawY() - downY);
                    clampFloating(f);
                    try { floatingManager.updateViewLayout(f.root, p); } catch (Throwable ignored) {}
                    return true;
                }
                return false;
            }
        });
    }

    private void dragResize(View handle, FloatingSession f) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startW, startH;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                WindowManager.LayoutParams p = f.params;
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY();
                    startW = p.width; startH = p.height;
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    p.width = Math.max(dp(220), startW + Math.round(e.getRawX() - downX));
                    p.height = Math.max(dp(260), startH + Math.round(e.getRawY() - downY));
                    clampFloating(f);
                    try { floatingManager.updateViewLayout(f.root, p); } catch (Throwable ignored) {}
                    return true;
                }
                return false;
            }
        });
    }

    private void minimizeFloating(FloatingSession f) {
        if (f == null || f.params == null || f.minimized) return;
        f.minimized = true;
        dismissFloatingSettings(f);
        clearFloatingAuth(f);
        clampFloating(f);
        f.normW = f.params.width;
        f.normH = f.params.height;
        f.normX = f.params.x;
        f.normY = f.params.y;
        if (f.root != null) f.root.setVisibility(View.GONE);
        f.params.width = 1;
        f.params.height = 1;
        f.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { floatingManager.updateViewLayout(f.root, f.params); } catch (Throwable ignored) {}
        addCapsuleChip(f);
        updateFloatingBackdrop();
    }

    private void expandFloating(FloatingSession f) {
        expandFloating(f, true);
    }

    private void expandFloating(FloatingSession f, boolean minimizeOthers) {
        if (f == null || f.params == null) return;
        if (minimizeOthers) {
            for (FloatingSession other : new ArrayList<>(floatingSessions)) {
                if (other != f && !other.minimized) minimizeFloating(other);
            }
        }
        if (f.minimized) {
            f.minimized = false;
            removeCapsuleChip(f);
            if (f.normW > 0) f.params.width = f.normW;
            if (f.normH > 0) f.params.height = f.normH;
            f.params.x = f.normX;
            f.params.y = f.normY;
            f.params.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            if (f.root != null) f.root.setVisibility(View.VISIBLE);
            clampFloating(f);
            try { floatingManager.updateViewLayout(f.root, f.params); } catch (Throwable ignored) {}
        }
        ensureFloatingBackdrop();
        bringRootFront(f);
        bringCapsuleTrayToFront();
        updateFloatingBackdrop();
    }

    private void bringRootFront(FloatingSession f) {
        try {
            floatingManager.removeView(f.root);
            floatingManager.addView(f.root, f.params);
        } catch (Throwable ignored) {}
    }

    private void addCapsuleChip(FloatingSession f) {
        if (capsuleChips.containsKey(f)) return;
        try {
            if (capsuleTray == null) {
                capsuleTray = new LinearLayout(this);
                capsuleTray.setOrientation(LinearLayout.HORIZONTAL);
                capsuleTray.setGravity(Gravity.CENTER_VERTICAL);
                capsuleTray.setPadding(dp(6), dp(4), dp(6), dp(4));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xF20D1D15);
                bg.setCornerRadius(dp(22));
                bg.setStroke(dp(1), 0x66FFFFFF);
                capsuleTray.setBackground(GlassUi.bar(this, true));
                TextView handle = new TextView(this);
                handle.setText("⠿");
                handle.setTextSize(16f);
                handle.setTextColor(0xFF8AA695);
                handle.setPadding(dp(4), 0, dp(6), 0);
                capsuleTray.addView(handle);
                capsuleTrayClose = new TextView(this);
                capsuleTrayClose.setText("×");
                capsuleTrayClose.setTextSize(18f);
                capsuleTrayClose.setTextColor(0xFFE6F2EA);
                capsuleTrayClose.setGravity(Gravity.CENTER);
                capsuleTrayClose.setPadding(dp(8), 0, dp(6), 0);
                capsuleTrayClose.setOnClickListener(v -> {
                    restoreAllFloating();
                    bringSiteToFront();
                });
                LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(32), dp(34));
                closeLp.leftMargin = dp(4);
                capsuleTray.addView(capsuleTrayClose, closeLp);
                capsuleTrayParams = new WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(46),
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                capsuleTrayParams.gravity = Gravity.TOP | Gravity.START;
                capsuleTrayParams.x = dp(18);
                capsuleTrayParams.y = dp(110);
                dragHandle(handle, capsuleTray, capsuleTrayParams);
                floatingManager.addView(capsuleTray, capsuleTrayParams);
            }
            TextView chip = new TextView(this);
            chip.setText(siteUrlText(f.web.getUrl() == null ? startUrl : f.web.getUrl()));
            chip.setTextSize(11f);
            chip.setTextColor(0xFFE6F2EA);
            chip.setSingleLine(true);
            chip.setMaxWidth(dp(120));
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setGravity(Gravity.CENTER);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(0x33FFFFFF);
            chipBg.setCornerRadius(dp(16));
            chip.setBackground(GlassUi.button(this, 16, true));
            chip.setOnClickListener(v -> expandFloating(f));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            chipLp.leftMargin = dp(5);
            capsuleTray.addView(chip, chipLp);
            capsuleChips.put(f, chip);
            applyScreenshotProtection();
            if (capsuleTrayClose != null) {
                capsuleTray.removeView(capsuleTrayClose);
                LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(32), dp(34));
                closeLp.leftMargin = dp(4);
                capsuleTray.addView(capsuleTrayClose, closeLp);
            }
            bringCapsuleTrayToFront();
        } catch (Throwable ignored) {}
    }

    private void removeCapsuleChip(FloatingSession f) {
        TextView chip = capsuleChips.remove(f);
        if (chip != null && capsuleTray != null) {
            try { capsuleTray.removeView(chip); } catch (Throwable ignored) {}
            if (capsuleChips.isEmpty()) {
                try { floatingManager.removeView(capsuleTray); } catch (Throwable ignored) {}
                capsuleTray = null;
                capsuleTrayParams = null;
                capsuleTrayClose = null;
            }
        }
    }

    private void bringCapsuleTrayToFront() {
        if (capsuleTray == null || capsuleTrayParams == null) return;
        try {
            floatingManager.removeView(capsuleTray);
            floatingManager.addView(capsuleTray, capsuleTrayParams);
        } catch (Throwable ignored) {}
    }

    private void dragHandle(View handle, View target, WindowManager.LayoutParams params) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = params.x; startY = params.y;
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    params.x = startX + Math.round(e.getRawX() - downX);
                    params.y = Math.max(0, startY + Math.round(e.getRawY() - downY));
                    try { floatingManager.updateViewLayout(target, params); } catch (Throwable ignored) {}
                    return true;
                }
                return false;
            }
        });
    }

    private void toggleMaximizeFloating(FloatingSession f) {
        if (f == null || f.params == null) return;
        if (f.capsule != null && f.capsule.getVisibility() == View.VISIBLE) expandFloating(f);
        if (f.maximized) {
            f.params.width = f.normW > 0 ? f.normW : dp(320);
            f.params.height = f.normH > 0 ? f.normH : dp(420);
            f.params.x = f.normX;
            f.params.y = f.normY;
            f.maximized = false;
            clampFloating(f);
        } else {
            f.normW = f.params.width;
            f.normH = f.params.height;
            f.normX = f.params.x;
            f.normY = f.params.y;
            f.params.width = maxFloatingWidth();
            f.params.height = Math.min(maxFloatingHeight(), (int) (screenHeightPx() * 0.82f));
            f.params.x = 0;
            f.params.y = 0;
            f.maximized = true;
        }
        try { floatingManager.updateViewLayout(f.root, f.params); } catch (Throwable ignored) {}
    }

    private void clearFloatingAuth(FloatingSession f) {
        if (f == null || f.authOverlay == null || f.root == null) return;
        try { f.root.removeView(f.authOverlay); } catch (Throwable ignored) {}
        f.authOverlay = null;
    }

    private void minimizeAllFloatingForOkx() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            f.expandedBeforeOkx = !f.minimized;
            minimizeFloating(f);
        }
    }

    private void restoreAfterOkx() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            if (f.expandedBeforeOkx) expandFloating(f, false);
            f.expandedBeforeOkx = false;
        }
    }

    private void minimizeAllFloating() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) minimizeFloating(f);
    }

    private void expandAllFloating() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) expandFloating(f, false);
    }

    private void bringSiteToFront() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                am.moveTaskToFront(getTaskId(), 0);
                return;
            }
        } catch (Throwable ignored) {}
        try {
            Intent i = new Intent(this, SiteActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Throwable ignored) {}
    }

    private void restoreFloating(FloatingSession f) {
        if (f == null) return;
        removeCapsuleChip(f);
        try {
            if (f.web != null && f.parent != null) {
                if (f.web.getParent() instanceof ViewGroup) ((ViewGroup) f.web.getParent()).removeView(f.web);
                if (f.webParams != null) f.parent.addView(f.web, f.webParams);
                else f.parent.addView(f.web);
            }
            if (f.window != null && f.window.card != null) f.window.card.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) {}
        try {
            if (f.root != null && floatingManager != null) floatingManager.removeView(f.root);
        } catch (Throwable ignored) {}
        floatingSessions.remove(f);
        if (f.window != null && !isFinishing() && !isDestroyed()) setActive(f.window);
        updateFloatingBackdrop();
        if (floatingSessions.isEmpty()) floatingManager = null;
    }

    private void disposeFloatingOnDestroy() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            try { if (f.root != null && floatingManager != null) floatingManager.removeView(f.root); } catch (Throwable ignored) {}
        }
        floatingSessions.clear();
        removeFloatingBackdrop();
        capsuleChips.clear();
        try {
            if (capsuleTray != null && floatingManager != null) floatingManager.removeView(capsuleTray);
        } catch (Throwable ignored) {}
        capsuleTray = null;
        capsuleTrayClose = null;
        capsuleTrayParams = null;
        floatingManager = null;
    }

    private void restoreAllFloating() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) restoreFloating(f);
    }

    private void maybeRefloatAfterWallet(PendingWallet pending) {
        // Product decision C: after OKX, keep the capsule; the user taps it to expand.
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "系统不支持画中画", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "系统不支持画中画", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(3, 4))
                    .build();
            boolean ok = enterPictureInPictureMode(params);
            if (!ok) Toast.makeText(this, "当前系统不允许画中画", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "进入画中画失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (topBar != null) topBar.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
    }

    private void setActive(SiteWindow win) {
        if (win == null || win.web == null) return;
        activeWebView = win.web;
        if (win.card != null) win.card.bringToFront();
        String url;
        try { url = win.web.getUrl(); } catch (Throwable t) { url = null; }
        String host = hostOf(url == null ? "" : url);
        if (host.isEmpty()) host = hostOf(startUrl);
        if (status != null && !isFinishing() && !isDestroyed()) {
            status.setText("DeWEB site · " + siteUrlText(url == null ? startUrl : url));
        }
        updateDeskButton(host);
    }

    private boolean isDesktopHost(String host) {
        return host != null && desktopModes != null && desktopModes.optBoolean(host, false);
    }

    private void updateDeskButton(String host) {
        if (deskButton == null) return;
        deskButton.setText(isDesktopHost(host) ? "手机" : "桌面");
    }

    private void applySiteMode(WebView wv, String host) {
        if (wv == null) return;
        boolean desktop = isDesktopHost(host);
        WebSettings s = wv.getSettings();
        s.setUserAgentString(desktop ? DESKTOP_UA : defaultUa);
        s.setUseWideViewPort(desktop);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        wv.setInitialScale(desktop ? 50 : 0);
    }

    private void toggleSiteMode() {
        SiteWindow win = null;
        for (SiteWindow w : allWindows()) {
            if (w.web != null && w.web == activeWebView) { win = w; break; }
        }
        if (win == null || win.web == null) return;
        String url = win.web.getUrl();
        if (url == null || url.startsWith("about:")) url = startUrl;
        String host = hostOf(url);
        if (host.isEmpty()) return;
        boolean next = !isDesktopHost(host);
        try {
            desktopModes.put(host, next);
            desktopPrefs.edit().putString(DESKTOP_MAP, desktopModes.toString()).apply();
        } catch (Throwable ignored) {}
        applySiteMode(win.web, host);
        win.web.reload();
        updateDeskButton(host);
        Toast.makeText(this, next ? "已切换桌面版：" + host : "已切换手机版：" + host, Toast.LENGTH_SHORT).show();
    }

    private void configureSiteWebView(WebView wv, SiteWindow win, String url) {
        wv.setFilterTouchesWhenObscured(true);
        wv.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        applySiteMode(wv, hostOf(url));

        wv.setWebChromeClient(fileChooser.chromeClient());
        wv.setWebViewClient(new WebViewClient() {
@Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())) return true;
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                if (scheme.equals("http") || scheme.equals("https")) {
                    if (win.label != null) win.label.setText(siteUrlText(uri.toString()));
                    if (activeWebView == view) status.setText("DeWEB site · " + siteUrlText(uri.toString()));
                    return false;
                }
                if (isWalletScheme(scheme)) {
                    if (!isDesktopHost(hostOf(view.getUrl() == null ? startUrl : view.getUrl()))) {
                        Toast.makeText(SiteActivity.this, "非桌面站点禁止连接钱包", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (!hasRecentGesture()) return true;
                }
                IntentGuard.open(SiteActivity.this, uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String startedUrl, Bitmap favicon) {
                if (startedUrl != null && !startedUrl.startsWith("about:")) {
                    if (win.label != null) win.label.setText(siteUrlText(startedUrl));
                    if (activeWebView == view) status.setText("DeWEB site · " + siteUrlText(startedUrl));
                }
            }

            @Override
            public void onPageFinished(WebView view, String finishedUrl) {
                if (finishedUrl != null && !finishedUrl.startsWith("about:")) {
                    if (win.label != null) win.label.setText(siteUrlText(finishedUrl));
                    if (activeWebView == view) {
                        status.setText("DeWEB site · " + siteUrlText(finishedUrl));
                        updateDeskButton(hostOf(finishedUrl));
                    }
                }
            }
        });

        wv.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastGestureAt = System.currentTimeMillis();
                if (interactivePending && hasRecentGesture() && lastGestureAt > walletCanceledAt) showWallet();
            }
            return false;
        });

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            status.setText("WebView bridge feature unsupported");
            wv.loadUrl(url);
            return;
        }
        // Both modes inject the provider into any HTTPS origin. Strict mode
        // differs by requiring a per-origin approval in the wallet page; it no
        // longer relies on a hard-coded site whitelist.
        if (!isDesktopHost(hostOf(url))) {
            wv.loadUrl(url);
            return;
        }
        Set<String> origins = new HashSet<>();
        String onlyOrigin = siteOrigin(url);
        if (onlyOrigin != null) origins.add(onlyOrigin);
        if (origins.isEmpty()) {
            wv.loadUrl(url);
            return;
        }
        try {
            WebViewCompat.addDocumentStartJavaScript(wv, "window.__dewebChainHint='" + win.chainHint + "';", origins);
            WebViewCompat.addDocumentStartJavaScript(wv, readAsset("bridge.js"), origins);
            WebViewCompat.addWebMessageListener(
                    wv,
                    "dewebBridge",
                    origins,
                    (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                            onBridgeMessage(message, sourceOrigin, isMainFrame, replyProxy, win)
            );
        } catch (Throwable t) {
            Log.w(TAG, "bridge attach failed with selected origins", t);
            if (true) {
                try {
                    WebViewCompat.addDocumentStartJavaScript(wv, "window.__dewebChainHint='" + win.chainHint + "';", origins);
                    WebViewCompat.addDocumentStartJavaScript(wv, readAsset("bridge.js"), origins);
                    WebViewCompat.addWebMessageListener(
                            wv,
                            "dewebBridge",
                            origins,
                            (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                                    onBridgeMessage(message, sourceOrigin, isMainFrame, replyProxy, win)
                    );
                } catch (Throwable t2) {
                    Log.e(TAG, "strict fallback failed", t2);
                }
            }
        }
        wv.loadUrl(url);
    }

    private void toggleSecondWindow() {
        if (window1 == null || window1.web == null) return;
        if (window2 == null) {
            showSecondUrlDialog();
            return;
        }
        if (window2.chip != null) { restoreWindow(window2); splitButton.setText("▣"); return; }
        boolean show = window2.card.getVisibility() != View.VISIBLE;
        window2.card.setVisibility(show ? View.VISIBLE : View.GONE);
        splitButton.setText(show ? "▣" : "▥");
        splitButton.setContentDescription(show ? "隐藏第二个窗口" : "显示第二个窗口");
        if (show) {
            window2.card.bringToFront();
            setActive(window2);
        } else if (activeWebView == window2.web) {
            setActive(window1);
        }
    }

    private ArrayList<SiteWindow> allWindows() {
        ArrayList<SiteWindow> list = new ArrayList<>();
        if (window1 != null) list.add(window1);
        if (window2 != null) list.add(window2);
        if (window3 != null) list.add(window3);
        if (window4 != null) list.add(window4);
        return list;
    }

    private int windowCount() {
        return allWindows().size();
    }

    private boolean focusExistingByHost(String url) {
        try {
            String host = hostOf(url);
            if (host.isEmpty()) return false;
            for (SiteWindow w : allWindows()) {
                if (w == null || w.web == null) continue;
                String h = hostOf(w.web.getUrl());
                if (host.equalsIgnoreCase(h)) {
                    FloatingSession floating = findFloating(w);
                    if (floating != null) {
                        restoreFloating(floating);
                    } else {
                        if (w.chip != null) restoreWindow(w);
                        if (w.card != null) w.card.setVisibility(View.VISIBLE);
                    }
                    setActive(w);
                    Toast.makeText(this, "该应用已打开，已切换回主站窗口", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean focusExistingSite(String url) {
        SiteActivity activity = currentRef == null ? null : currentRef.get();
        if (activity == null) return false;
        boolean found = activity.hasOpenHost(url);
        if (found) activity.runOnUiThread(() -> activity.focusExistingByHost(url));
        return found;
    }

    private boolean hasOpenHost(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) return false;
        for (SiteWindow w : allWindows()) {
            if (w != null && w.web != null && host.equalsIgnoreCase(hostOf(w.web.getUrl()))) return true;
        }
        return false;
    }

    private void applySplitLayout() {
        // split-screen experiment reverted; keep call sites harmless
    }

    private void showSecondUrlDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://example.com");
        String current = activeWebView != null ? activeWebView.getUrl() : startUrl;
        input.setText(current == null ? "https://" : current);
        input.setSelection(input.getText().length());
        AlertDialog urlDialog = new AlertDialog.Builder(this)
                .setTitle("新建窗口")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("打开", (d, which) -> {
                    String target = input.getText().toString().trim();
                    Uri uri = Uri.parse(target);
                    if (uri.getHost() == null || (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))) {
                        Toast.makeText(this, "请输入 http/https 地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addSecondWindow(uri.toString(), activeChainHint());
                })
                .show();
        try { GlassUi.hardenTree(urlDialog.getWindow().getDecorView()); } catch (Throwable ignored) {}
        try { GlassUi.secureDialog(urlDialog, PrivacyPrefs.isEnabled(this) || isSiteLocked()); } catch (Throwable ignored) {}
    }

    private void addSecondWindow(String url) {
        addSecondWindow(url, activeChainHint());
    }

    private int maxWindows() {
        return MembershipManager.isPro(this) ? 4 : 2;
    }

    private void addSecondWindow(String url, String chainHint) {
        if (!isSafeSiteUrl(url)) {
            Toast.makeText(this, "只支持 http/https 地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (focusExistingByHost(url)) return;
        int count = windowCount();
        int max = maxWindows();
        if (count >= max) {
            Toast.makeText(this, max < 4 ? "Pro 会员可打开 4 个窗口" : "最多 4 个窗口", Toast.LENGTH_SHORT).show();
            return;
        }
        SiteWindow created;
        try {
            created = createWindow(url, false, chainHint, true);
        } catch (Throwable t) {
            Log.e(TAG, "create window failed", t);
            Toast.makeText(this, "窗口创建失败", Toast.LENGTH_SHORT).show();
            return;
        }
        if (created == null) return;
        if (window1 == null) {
            window1 = created;
            webView = created.web;
        } else if (window2 == null) {
            window2 = created;
            webView2 = created.web;
        } else if (window3 == null) {
            window3 = created;
        } else {
            window4 = created;
        }
        created.card.bringToFront();
        setActive(created);
        applySplitLayout();
        if (window2 != null) {
            splitButton.setText("▣");
            splitButton.setContentDescription("隐藏第二个窗口");
        }
    }

    private void maximizeWindow(SiteWindow win) {
        if (win == null || win.card == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) win.card.getLayoutParams();
        if (!win.maximized) {
            win.preLeft = lp.leftMargin;
            win.preTop = lp.topMargin;
            win.preWidth = lp.width;
            win.preHeight = lp.height;
            lp.leftMargin = 0;
            lp.topMargin = 0;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            win.maximized = true;
            if (win.maxButton != null) win.maxButton.setText("❐");
        } else {
            lp.leftMargin = win.preLeft;
            lp.topMargin = win.preTop;
            lp.width = win.preWidth;
            lp.height = win.preHeight;
            win.maximized = false;
            if (win.maxButton != null) win.maxButton.setText("□");
        }
        win.card.setLayoutParams(lp);
        win.card.bringToFront();
        setActive(win);
    }

    private boolean handleTrayTouch(MotionEvent e, SiteWindow tapWin) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) minimizedTray.getLayoutParams();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trayDownX = e.getRawX();
                trayDownY = e.getRawY();
                trayStartTop = lp.topMargin;
                trayStartRight = lp.rightMargin;
                trayMovedThisGesture = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - trayDownX;
                float dy = e.getRawY() - trayDownY;
                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                    trayMovedThisGesture = true;
                    trayUserMoved = true;
                }
                if (trayMovedThisGesture) {
                    int maxRight = Math.max(0, siteCanvas.getWidth() - minimizedTray.getWidth());
                    int maxTop = Math.max(0, siteCanvas.getHeight() - minimizedTray.getHeight());
                    lp.rightMargin = Math.max(0, Math.min(maxRight, trayStartRight - Math.round(dx)));
                    lp.topMargin = Math.max(0, Math.min(maxTop, trayStartTop + Math.round(dy)));
                    minimizedTray.setLayoutParams(lp);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!trayMovedThisGesture && tapWin != null) restoreWindow(tapWin);
                return true;
            case MotionEvent.ACTION_CANCEL:
                trayMovedThisGesture = false;
                return true;
            default:
                return false;
        }
    }

    private void positionMinimizedTray() {
        if (mainBackground == null || minimizedTray == null || trayUserMoved) return;
        String js = "(function(){var e=document.querySelector('.mgreet');if(!e)return '';var r=e.getBoundingClientRect();return Math.round(r.top)+','+Math.round(r.height);})()";
        mainBackground.evaluateJavascript(js, value -> {
            try {
                if (value == null) return;
                String raw = value.replace("\"", "").trim();
                String[] parts = raw.split(",");
                if (parts.length != 2) return;
                int top = Integer.parseInt(parts[0].trim());
                int h = Integer.parseInt(parts[1].trim());
                if (top <= 0 || h <= 0) return;
                int centered = top + Math.max(0, (h - dp(36)) / 2);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) minimizedTray.getLayoutParams();
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.topMargin = Math.max(dp(6), centered);
                lp.rightMargin = dp(12);
                minimizedTray.setLayoutParams(lp);
            } catch (Throwable ignored) {}
        });
    }

    private void goHome() {
        try {
            minimizeAllFloating();
            for (SiteWindow w : allWindows()) {
                if (w != null && w.card != null && !w.minimized && findFloating(w) == null) minimizeWindow(w);
            }
            if (mainBackground != null) {
                mainBackground.setVisibility(View.VISIBLE);
                mainBackground.bringToFront();
            }
            activeWebView = null;
            if (status != null) status.setText("DeWEB Desktop");
            if (minimizedTray != null && minimizedTray.getChildCount() > 0) {
                minimizedTray.setVisibility(View.VISIBLE);
                minimizedTray.bringToFront();
            }
            hideWallet();
        } catch (Throwable ignored) {}
    }

    private void minimizeWindow(SiteWindow win) {
        if (win == null || win.card == null || win.chip != null) return;
        if (findFloating(win) != null) return;
        win.card.setVisibility(View.GONE);
        win.minimized = true;
        Button chip = new Button(this);
        chip.setText(siteUrlText(win.web.getUrl()));
        chip.setTextSize(12f);
        chip.setAllCaps(false);
        chip.setTextColor(Color.rgb(21, 37, 29));
        chip.setMaxWidth(dp(130));
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setBackground(glassChip());
        chip.setElevation(dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(4), 0, dp(4), 0);
        chip.setLayoutParams(lp);
        chip.setOnTouchListener((v, e) -> handleTrayTouch(e, win));
        win.chip = chip;
        minimizedTray.addView(chip);
        minimizedTray.setVisibility(View.VISIBLE);
        minimizedTray.bringToFront();
        positionMinimizedTray();
        if (activeWebView == win.web) {
            activeWebView = null;
            status.setText("DeWEB Desktop");
        }
    }

    private void restoreWindow(SiteWindow win) {
        if (win == null || win.card == null) return;
        removeChip(win);
        win.minimized = false;
        win.card.setVisibility(View.VISIBLE);
        win.card.bringToFront();
        setActive(win);
    }

    private void removeChip(SiteWindow win) {
        if (win == null || win.chip == null) return;
        minimizedTray.removeView(win.chip);
        win.chip = null;
        win.minimized = false;
        if (minimizedTray.getChildCount() == 0) {
            minimizedTray.setVisibility(View.GONE);
            trayUserMoved = false;
        }
    }

    private void closeWindow(SiteWindow win) {
        if (win == null) return;
        removeChip(win);
        if (win == window1) {
            destroyWindow(window1);
            window1 = window2;
            window2 = window3;
            window3 = window4;
            window4 = null;
        } else if (win == window2) {
            destroyWindow(window2);
            window2 = window3;
            window3 = window4;
            window4 = null;
        } else if (win == window3) {
            destroyWindow(window3);
            window3 = window4;
            window4 = null;
        } else if (win == window4) {
            destroyWindow(window4);
            window4 = null;
        }
        if (window1 == null) {
            finish();
            return;
        }
        webView = window1.web;
        webView2 = window2 == null ? null : window2.web;
        SiteWindow focus = null;
        if (findFloating(window1) == null) {
            focus = window1;
        } else {
            for (SiteWindow w : allWindows()) {
                if (findFloating(w) == null) { focus = w; break; }
            }
        }
        if (focus != null) {
            if (focus.chip != null || focus.minimized) {
                restoreWindow(focus);
            } else {
                if (focus.card != null) focus.card.setVisibility(View.VISIBLE);
                setActive(focus);
            }
        } else {
            for (SiteWindow w : allWindows()) {
                if (w.card != null) w.card.setVisibility(View.GONE);
            }
            activeWebView = null;
            if (status != null) status.setText("DeWEB Desktop");
        }
        applySplitLayout();
        splitButton.setText(window2 == null ? "▥" : "▣");
    }

    private void destroyWindow(SiteWindow win) {
        try {
            removeChip(win);
            if (win.card != null) siteCanvas.removeView(win.card);
            win.web.loadUrl("about:blank");
            win.web.destroy();
        } catch (Exception e) {}
    }

    private void onBridgeMessage(WebMessageCompat message, Uri sourceOrigin, boolean isMainFrame, JavaScriptReplyProxy replyProxy, SiteWindow win) {
        try {
            JSONObject req = new JSONObject(message.getData() == null ? "{}" : message.getData());
            String siteId = req.optString("id");
            String method = req.optString("method");
            if (sourceOrigin == null || !"https".equalsIgnoreCase(sourceOrigin.getScheme())) return;
            if (isSiteLocked()) {
                sendBridgeError(replyProxy, siteId, 4001, "窗口已锁定");
                return;
            }
            if (isWalletPolicyMethod(method) && !isDesktopHost(sourceOrigin.getHost())) {
                sendBridgeError(replyProxy, siteId, 4100, "非桌面站点禁止连接钱包");
                return;
            }
            if (!isMainFrame && (isInteractiveWalletMethod(method) || "deweb_user_gesture".equals(method))) {
                sendBridgeError(replyProxy, siteId, 4100, "请在站点主页面操作钱包");
                return;
            }
            Log.d(TAG, (isMainFrame ? "main " : "frame ") + sourceOrigin + " " + method);
            runOnUiThread(() -> status.setText((isMainFrame ? "main " : "frame ") + sourceOrigin + " · " + method));

            if ("deweb_open_site".equals(method)) {
                if (!isMainFrame) return;
                JSONArray p = req.optJSONArray("params");
                final String target = p == null ? "" : p.optString(0);
                if (!isSafeSiteUrl(target)) {
                    sendBridgeError(replyProxy, siteId, -32602, "Invalid site URL");
                    return;
                }
                final String chain = p == null ? activeChainHint() : normalizeChain(p.optString(1));
                runOnUiThread(() -> {
                    if (target != null && !target.isEmpty()) addSecondWindow(target, chain);
                });
                return;
            }
            if ("deweb_user_gesture".equals(method)) {
                // Native touch is authoritative; page JS must not be able to spoof a gesture.
                if (interactivePending && hasRecentGesture() && lastGestureAt > walletCanceledAt) showWallet();
                return;
            }

            String originKey = originKey(sourceOrigin);
            String approvalKey = originKey + "|" + siteId;
            boolean connectMethod = "eth_requestAccounts".equals(method) || "wallet_requestPermissions".equals(method);
            if (!connectMethod && isInteractiveWalletMethod(method) && !isNativeApproved(originKey)) {
                sendBridgeError(replyProxy, siteId, 4100, "请先连接并授权该站点");
                return;
            }
            FloatingSession fs = findFloating(win);
            if (fs != null && connectMethod) {
                if (!floatingConnectInProgress) {
                    floatingConnectInProgress = true;
                    walletFloatHost = fs;
                    expandFloating(fs);
                }

            } else if (fs != null && isInteractiveWalletMethod(method)) {
                if (cachedAccounts == null || cachedAccounts.length() == 0) {
                    restoreAllFloating();
                    bringSiteToFront();
                } else if (!floatingApproved.contains(approvalKey)) {
                    expandFloating(fs);
                    showFloatingWalletAuth(fs, message, sourceOrigin, isMainFrame, replyProxy, win, siteId, approvalKey, method, req);
                    return;
                }
            }
            if (connectMethod && !isNativeApproved(originKey)) {
                if (!hasRecentGesture()) {
                    sendBridgeError(replyProxy, siteId, 4001, "请点击站点连接按钮后再试");
                    return;
                }
                askNativeSiteAuth(message, sourceOrigin, isMainFrame, replyProxy, win, originKey, siteId);
                return;
            }
            if (walletPending.size() >= MAX_PENDING_WALLET) {
                sendBridgeError(replyProxy, siteId, 429, "钱包请求过多，请稍后再试");
                return;
            }
            String chainHint = win == null ? cachedChainHint : win.chainHint;
            String walletId = "w" + (++walletSeq);
            JSONObject walletReq = new JSONObject();
            walletReq.put("id", walletId);
            walletReq.put("origin", sourceOrigin == null ? "unknown" : sourceOrigin.toString());
            walletReq.put("method", method);
            walletReq.put("params", req.optJSONArray("params") == null ? new JSONArray() : req.optJSONArray("params"));
            walletReq.put("chainHint", chainHint);
            walletReq.put("windowId", win == null ? "unknown" : win.walletWindowId);
            // Strict mode reuses the current OKX session; only an empty wallet
            // session (handled by wallet-service) starts a fresh connection.
            walletReq.put("forceConnect", false);
            walletReq.put("strict", true);
            walletReq.put("siteApproved", isNativeApproved(originKey));
            boolean nativeConfirmed = floatingNextNativeConfirmed || floatingApproved.remove(approvalKey);
            floatingNextNativeConfirmed = false;
            walletReq.put("nativeConfirmed", nativeConfirmed);

            String switchTo = null;
            if ("wallet_switchEthereumChain".equals(method)) {
                JSONArray sp = req.optJSONArray("params");
                JSONObject first = sp == null ? null : sp.optJSONObject(0);
                if (first != null) switchTo = normalizeChain(first.optString("chainId", ""));
            }
            walletPending.put(walletId, new PendingWallet(siteId, method, replyProxy, win, switchTo));
            ensureWallet();
            if (walletPageReady) {
                dispatchWallet(walletReq);
            } else if (walletQueue.size() >= MAX_WALLET_QUEUE) {
                walletPending.remove(walletId);
                sendBridgeError(replyProxy, siteId, 429, "钱包请求队列已满");
                return;
            } else {
                walletQueue.add(walletReq);
            }
            if (isInteractiveWalletMethod(method)) {
                interactivePending = true;
                if (!nativeConfirmed && hasRecentGesture() && lastGestureAt > walletCanceledAt) showWallet();
            }

            mainHandler.postDelayed(() -> {
                PendingWallet expired = walletPending.remove(walletId);
                if (expired != null) {
                    interactivePending = false;
                    floatingKeepForRequest = false;
                    floatingConnectInProgress = false;
                    detachWalletFromFloat();
                    maybeRefloatAfterWallet(expired);
                    sendSiteError(expired, -32603, "wallet request timed out");
                }
            }, 180000);
        } catch (Exception e) {
            Log.e(TAG, "bridge failed", e);
        }
    }

    private String describeFloatingRequest(String method, JSONObject req) {
        StringBuilder b = new StringBuilder();
        JSONArray params = req.optJSONArray("params");
        b.append("⚠️ ");
        if ("eth_sendTransaction".equals(method)) b.append("这是链上交易，不是登录。");
        else if ("personal_sign".equals(method) || "eth_signTypedData_v4".equals(method)) b.append("这是签名请求，不是登录。");
        else b.append("这是钱包请求。");
        b.append("\n\n");
        if ("eth_sendTransaction".equals(method)) {
            JSONObject tx = params == null ? null : params.optJSONObject(0);
            if (tx != null) {
                b.append("交互地址：").append(tx.optString("to", "(创建合约)")).append("\n");
                String value = tx.optString("value", "0x0");
                try { b.append("附带 value：").append(new BigInteger(value.startsWith("0x") ? value.substring(2) : value, 16)).append(" wei\n"); } catch (Throwable ignored) {}
                String data = tx.optString("data", "0x").toLowerCase();
                if (data.length() >= 10) {
                    String sel = data.substring(0, 10);
                    String body = data.substring(10);
                    String name = "未知合约调用";
                    if ("0xa9059cbb".equals(sel)) {
                        name = "transfer";
                        b.append("代币接收地址：").append(addressOfWord(word(body, 0))).append("\n");
                        b.append("数量（最小单位）：").append(bigOfWord(word(body, 1))).append("\n");
                    } else if ("0x095ea7b3".equals(sel)) {
                        name = "approve";
                        b.append("被授权地址：").append(addressOfWord(word(body, 0))).append("\n");
                        b.append(unlimited(word(body, 1)) ? "⚠️ 无限额度授权：对方可转走该代币全部余额\n" : "授权额度（最小单位）：" + bigOfWord(word(body, 1)) + "\n");
                    } else if ("0x39509351".equals(sel)) {
                        name = "increaseAllowance";
                        b.append("被授权地址：").append(addressOfWord(word(body, 0))).append("\n");
                        b.append(unlimited(word(body, 1)) ? "⚠️ 无限额度授权\n" : "增加额度（最小单位）：" + bigOfWord(word(body, 1)) + "\n");
                    } else if ("0x23b872dd".equals(sel)) {
                        name = "transferFrom";
                        b.append("从：").append(addressOfWord(word(body, 0))).append("\n");
                        b.append("到：").append(addressOfWord(word(body, 1))).append("\n");
                        b.append("数量（最小单位）：").append(bigOfWord(word(body, 2))).append("\n");
                    } else if ("0xa22cb465".equals(sel)) {
                        name = "setApprovalForAll";
                        b.append("操作者：").append(addressOfWord(word(body, 0))).append("\n");
                        b.append(nonZero(word(body, 1)) ? "⚠️ 授权操作者管理你的全部 NFT\n" : "取消全部 NFT 授权\n");
                    } else if ("0x42842e0e".equals(sel)) {
                        name = "safeTransferFrom";
                        b.append("从：").append(addressOfWord(word(body, 0))).append("\n");
                        b.append("到：").append(addressOfWord(word(body, 1))).append("\n");
                        b.append("Token ID：").append(bigOfWord(word(body, 2))).append("\n");
                    }
                    b.append("函数：").append(name).append(" (").append(sel).append(")\n");
                    if ("0x095ea7b3".equals(sel) || "0x39509351".equals(sel) || "0xa22cb465".equals(sel))
                        b.append("⚠️ 请确认你是否真的需要这类授权。\n");
                    else if ("未知合约调用".equals(name))
                        b.append("⚠️ 无法解码该合约调用，请确认来源可靠。\n");
                    b.append("\n完整 data：\n").append(data.length() > 1600 ? data.substring(0, 1600) + "…" : data);
                } else {
                    b.append("data：空\n用途：普通转账，请核对收款地址和金额。");
                }
            }
        } else if (params != null) {
            String first = params.optString(0, "");
            if (first.startsWith("0x")) {
                String decoded = decodeHexText(first);
                b.append(decoded.isEmpty() ? "消息：不可读二进制数据\n" : "消息文本：" + decoded + "\n");
                if (decoded.isEmpty()) b.append("⚠️ 不可读消息，可能包含授权内容。\n");
                if (first.length() > 400) b.append("消息 Hex：").append(first, 0, 400).append("…\n");
            } else {
                b.append("数据：").append(first);
                if (first.length() > 1600) b.append("…");
            }
            if ("eth_signTypedData_v4".equals(method)) {
                try {
                    JSONObject td = new JSONObject(first);
                    String primary = td.optString("primaryType", "");
                    b.append("\n类型：").append(primary);
                    if (primary.toLowerCase().contains("permit"))
                        b.append("\n⚠️ 这是 Permit/授权类签名，可能允许对方动用你的代币。");
                } catch (Throwable ignored) {}
            }
        }
        b.append("\n\n完整参数：\n");
        String raw = params == null ? "[]" : params.toString();
        b.append(raw.length() > 1600 ? raw.substring(0, 1600) + "…" : raw);
        return b.toString();
    }

    private String word(String body, int index) {
        int start = index * 64;
        if (body == null || start < 0 || start + 64 > body.length()) return "";
        return body.substring(start, start + 64);
    }

    private String addressOfWord(String word) {
        if (word == null || word.isEmpty()) return "";
        return "0x" + word.substring(Math.max(0, word.length() - 40));
    }

    private String bigOfWord(String word) {
        try { return new BigInteger(word.isEmpty() ? "0" : word, 16).toString(); }
        catch (Throwable t) { return "0"; }
    }

    private boolean nonZero(String word) {
        if (word == null) return false;
        return word.replace("0", "").length() > 0;
    }

    private boolean unlimited(String word) {
        if (word == null || word.isEmpty()) return false;
        try { return new BigInteger(word, 16).compareTo(BigInteger.ONE.shiftLeft(255)) >= 0; }
        catch (Throwable t) { return word.matches("f{64}"); }
    }

    private String decodeHexText(String hex) {
        try {
            String h = hex.startsWith("0x") ? hex.substring(2) : hex;
            int n = h.length() / 2;
            byte[] bytes = new byte[n];
            for (int i = 0; i < n; i++) bytes[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
            String text = new String(bytes, StandardCharsets.UTF_8);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= 32 || c == '\n' || c == '\r' || c == '\t') out.append(c);
            }
            return out.toString();
        } catch (Throwable t) { return ""; }
    }

    private void showFloatingWalletAuth(FloatingSession f, WebMessageCompat message, Uri sourceOrigin, boolean isMainFrame,
                                        JavaScriptReplyProxy reply, SiteWindow win, String siteId, String approvalKey,
                                        String method, JSONObject req) {
        if (f == null || f.root == null) {
            sendBridgeError(reply, siteId, -32603, "floating window closed");
            return;
        }
        if (f.authOverlay != null) return;
        String origin = sourceOrigin == null ? "unknown" : sourceOrigin.toString();
        FrameLayout auth = new FrameLayout(this);
        auth.setBackgroundColor(0xF207100B);
        auth.setElevation(dp(24));
        auth.setClickable(true);
        auth.setFocusable(true);
        auth.setOnClickListener(v -> {});
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF20D1D15, 0xEE08110C});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x55FFFFFF);
        panel.setBackground(GlassUi.panel(this, 18, true));
        TextView title = new TextView(this);
        title.setText("eth_sendTransaction".equals(method) ? "交易授权" : "签名授权");
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFEAF7EF);
        panel.addView(title);
        String paramsText = describeFloatingRequest(method, req);
        TextView info = new TextView(this);
        info.setText(origin + "\n\n" + method + "\n\n" + paramsText);
        info.setTextSize(9.5f);
        info.setTypeface(Typeface.MONOSPACE);
        info.setTextColor(0xCCD8E8DE);
        info.setTextIsSelectable(true);
        info.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(0x33000000);
        infoBg.setCornerRadius(dp(10));
        info.setBackground(infoBg);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(info, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        infoLp.topMargin = dp(8);
        panel.addView(scroller, infoLp);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button deny = new Button(this);
        deny.setText("拒绝");
        deny.setTextSize(10f);
        deny.setAllCaps(false);
        deny.setTextColor(0xFFE6F2EA);
        deny.setFilterTouchesWhenObscured(true);
        deny.setOnClickListener(v -> {
            f.root.removeView(auth);
            f.authOverlay = null;
            applyScreenshotProtection();
            sendBridgeError(reply, siteId, 4001, "User rejected");
        });
        actions.addView(deny, new LinearLayout.LayoutParams(dp(72), dp(36)));
        Button allow = new Button(this);
        allow.setText("继续到 OKX");
        allow.setTextSize(10f);
        allow.setAllCaps(false);
        allow.setTextColor(0xFF03150D);
        allow.setFilterTouchesWhenObscured(true);
        GradientDrawable allowBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF8AF2C8, 0xFF56E6B0});
        allowBg.setCornerRadius(dp(12));
        allow.setBackground(allowBg);
        allow.setOnClickListener(v -> {
            f.root.removeView(auth);
            f.authOverlay = null;
            applyScreenshotProtection();
            floatingApproved.add(approvalKey);
            floatingNextNativeConfirmed = true;
            floatingKeepForRequest = true;
            onBridgeMessage(message, sourceOrigin, isMainFrame, reply, win);
        });
        LinearLayout.LayoutParams allowLp = new LinearLayout.LayoutParams(dp(126), dp(36));
        allowLp.leftMargin = dp(6);
        actions.addView(allow, allowLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(8);
        panel.addView(actions, actionsLp);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        panelLp.setMargins(dp(6), dp(42), dp(6), dp(6));
        auth.addView(panel, panelLp);
        f.authOverlay = auth;
        f.root.addView(auth, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        auth.bringToFront();
        bringRootFront(f);
        applyScreenshotProtection();
    }

    private String originKey(Uri origin) {
        if (origin == null || origin.getHost() == null) return "unknown";
        String scheme = origin.getScheme() == null ? "https" : origin.getScheme();
        String base = scheme + "://" + origin.getHost();
        if (origin.getPort() > 0) base += ":" + origin.getPort();
        return base;
    }

    /** 测试版：把「站点授权」做成悬浮窗内的覆盖层，而不是居中 AlertDialog。 */
    private void showFloatingSiteAuth(FloatingSession f, WebMessageCompat message, Uri origin,
                                      boolean isMainFrame, JavaScriptReplyProxy reply,
                                      SiteWindow win, String key, String siteId) {
        if (f == null || f.root == null) {
            sendBridgeError(reply, siteId, -32603, "floating window closed");
            return;
        }
        if (f.authOverlay != null) {
            sendBridgeError(reply, siteId, 4100, "该站点的授权请求已在进行中");
            return;
        }
        final String originText = origin == null ? "unknown" : origin.toString();
        runOnUiThread(() -> {
            FrameLayout auth = new FrameLayout(this);
            auth.setBackgroundColor(0xF207100B);
            auth.setElevation(dp(24));
            auth.setClickable(true);
            auth.setFocusable(true);
            auth.setOnClickListener(v -> {});

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), dp(14), dp(16), dp(12));
            panel.setBackground(GlassUi.panel(this, 14, true));

            TextView title = new TextView(this);
            title.setText("连接钱包授权");
            title.setTextSize(15f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(0xFFEAF7EF);
            panel.addView(title);

            TextView info = new TextView(this);
            info.setText(originText + "\n\n允许该站点读取你的钱包地址？\n\n交易和签名仍会逐笔确认。");
            info.setTextSize(11f);
            info.setTextColor(0xCCD8E8DE);
            info.setTextIsSelectable(true);
            info.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            infoLp.topMargin = dp(8);
            panel.addView(info, infoLp);

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.END);

            Button deny = new Button(this);
            deny.setText("拒绝");
            deny.setTextSize(11f);
            deny.setAllCaps(false);
            deny.setTextColor(0xFFE6F2EA);
            deny.setFilterTouchesWhenObscured(true);
            deny.setOnClickListener(v -> {
                f.root.removeView(auth);
                f.authOverlay = null;
            applyScreenshotProtection();
                sendBridgeError(reply, siteId, 4001, "User rejected");
            });
            actions.addView(deny, new LinearLayout.LayoutParams(dp(72), dp(36)));

            Button allow = new Button(this);
            allow.setText("允许");
            allow.setTextSize(11f);
            allow.setAllCaps(false);
            allow.setTextColor(0xFF03150D);
            GradientDrawable allowBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF8AF2C8, 0xFF56E6B0});
            allowBg.setCornerRadius(dp(12));
            allow.setBackground(allowBg);
            allow.setFilterTouchesWhenObscured(true);
            allow.setOnClickListener(v -> {
                f.root.removeView(auth);
                f.authOverlay = null;
            applyScreenshotProtection();
                approveNative(key);
                onBridgeMessage(message, origin, isMainFrame, reply, win);
            });
            LinearLayout.LayoutParams allowLp = new LinearLayout.LayoutParams(dp(86), dp(36));
            allowLp.leftMargin = dp(6);
            actions.addView(allow, allowLp);

            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionsLp.topMargin = dp(10);
            panel.addView(actions, actionsLp);

            FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            panelLp.setMargins(dp(6), dp(42), dp(6), dp(6));
            auth.addView(panel, panelLp);

            f.authOverlay = auth;
            f.root.addView(auth, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            auth.bringToFront();
            applyScreenshotProtection();
        });
    }

    /** 测试版：把 walletWeb 挂到悬浮窗内部并显示。 */
    private void showWalletInFloat(FloatingSession f) {
        if (f == null || f.root == null || walletWeb == null) return;
        runOnUiThread(() -> {
            expandFloating(f);
            ViewGroup wp = (ViewGroup) walletWeb.getParent();
            if (wp != f.root) {
                if (wp != null) wp.removeView(walletWeb);
                f.root.addView(walletWeb, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (walletCancel == null) {
                walletCancel = new Button(this);
                walletCancel.setText("取消");
                walletCancel.setTextSize(12f);
                walletCancel.setAllCaps(false);
                walletCancel.setTextColor(Color.rgb(21, 37, 29));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xE6FFFFFF);
                bg.setCornerRadius(dp(14));
                bg.setStroke(dp(1), 0x556B8F7E);
                walletCancel.setBackground(bg);
                walletCancel.setFilterTouchesWhenObscured(true);
                walletCancel.setOnClickListener(v -> cancelWalletRequests());
            }
            ViewGroup cp = (ViewGroup) walletCancel.getParent();
            if (cp != f.root) {
                if (cp != null) cp.removeView(walletCancel);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(64), dp(38));
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.topMargin = dp(10);
                lp.rightMargin = dp(10);
                f.root.addView(walletCancel, lp);
            }
            walletAttachedToFloat = true;
            walletCancel.setVisibility(View.VISIBLE);
            walletWeb.setVisibility(View.VISIBLE);
            walletWeb.bringToFront();
            walletCancel.bringToFront();
            walletWeb.requestFocus();
            applyScreenshotProtection();
        });
    }

    /** 测试版：把 walletWeb / 取消按钮从悬浮窗收回主界面根布局。 */
    private void detachWalletFromFloat() {
        boolean wasAttached = walletAttachedToFloat;
        walletAttachedToFloat = false;
        walletFloatHost = null;
        if (!wasAttached) return;
        runOnUiThread(() -> {
            try {
                if (walletWeb != null) {
                    walletWeb.setVisibility(View.GONE);
                    ViewGroup p = (ViewGroup) walletWeb.getParent();
                    if (p != null) p.removeView(walletWeb);
                    if (walletWeb.getParent() == null) {
                        rootFrame.addView(walletWeb, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                }
                if (walletCancel != null) {
                    walletCancel.setVisibility(View.GONE);
                    ViewGroup p = (ViewGroup) walletCancel.getParent();
                    if (p != null && p != rootFrame) p.removeView(walletCancel);
                }
            } catch (Throwable ignored) {}
        });
    }

    private void askNativeSiteAuth(WebMessageCompat message, Uri origin, boolean isMainFrame,
                                   JavaScriptReplyProxy reply, SiteWindow win, String key, String siteId) {
        FloatingSession fs = findFloating(win);
        if (fs != null) {
            showFloatingSiteAuth(fs, message, origin, isMainFrame, reply, win, key, siteId);
            return;
        }
        AuthRequest ar = new AuthRequest(message, origin, isMainFrame, reply, win, key, siteId);
        if ((authDialogShowing && key != null && key.equals(activeAuthOrigin))
                || authQueue.stream().anyMatch(q -> key != null && key.equals(q.key))) {
            sendBridgeError(reply, siteId, 4100, "该站点授权请求已在进行中");
            return;
        }
        if (authDialogShowing || !authQueue.isEmpty()) {
            if (authQueue.size() >= MAX_AUTH_QUEUE) {
                sendBridgeError(reply, siteId, 429, "授权请求过多，请稍后再试");
                return;
            }
            authQueue.add(ar);
            return;
        }
        showAuthDialog(ar);
    }

    private void drainAuthQueue() {
        while (!authDialogShowing) {
            AuthRequest next = authQueue.poll();
            if (next == null) return;
            if (isNativeApproved(next.key)) {
                onBridgeMessage(next.message, next.origin, next.isMainFrame, next.reply, next.win);
                continue;
            }
            showAuthDialog(next);
            return;
        }
    }

    private void showAuthDialog(AuthRequest ar) {
        authDialogShowing = true;
        activeAuthOrigin = ar.key;
        runOnUiThread(() -> {
            try {
                showSiteAuthDialog(ar.key,
                () -> {
                    authDialogShowing = false;
                    activeAuthOrigin = null;
                    approveNative(ar.key);
                    onBridgeMessage(ar.message, ar.origin, ar.isMainFrame, ar.reply, ar.win);
                    drainAuthQueue();
                },
                () -> {
                    authDialogShowing = false;
                    activeAuthOrigin = null;
                    if (floatingConnectInProgress) {
                        floatingConnectInProgress = false;
                        restoreAfterOkx();
                        bringSiteToFront();
                    }
                    sendBridgeError(ar.reply, ar.siteId, 4001, "User rejected");
                    drainAuthQueue();
                });
            } catch (Throwable t) {
                authDialogShowing = false;
                sendBridgeError(ar.reply, ar.siteId, -32603, "authorization dialog failed");
                drainAuthQueue();
            }
        });
    }

    private void showSiteAuthDialog(String origin, Runnable onAllow, Runnable onDeny) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        GlassUi.blurDialog(dialog);
        siteAuthDialog = dialog;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF20D1D15, 0xEE08110C, 0xF20A1710});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), 0x55FFFFFF);
        panel.setBackground(GlassUi.panel(this, 26, true));
        panel.setClipToOutline(true);
        panel.setElevation(dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("钱包确认");
        title.setTextSize(10f);
        title.setLetterSpacing(0.18f);
        title.setTextColor(0xCCE4F7EA);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(head);

        TextView heading = new TextView(this);
        heading.setText("站点授权");
        heading.setTextSize(18f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(0xFFEAF7EF);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingLp.topMargin = dp(16);
        panel.addView(heading, headingLp);

        TextView originBox = new TextView(this);
        originBox.setText(origin);
        originBox.setTextSize(10.5f);
        originBox.setTypeface(Typeface.MONOSPACE);
        originBox.setTextColor(0xB3E6F2EA);
        originBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable originBg = new GradientDrawable();
        originBg.setColor(0x33000000);
        originBg.setCornerRadius(dp(14));
        originBg.setStroke(dp(1), 0x22FFFFFF);
        originBox.setBackground(originBg);
        LinearLayout.LayoutParams originLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        originLp.topMargin = dp(12);
        panel.addView(originBox, originLp);

        TextView message = new TextView(this);
        message.setText("允许该站点读取你的钱包地址？\n\n交易和签名仍会逐笔确认。");
        message.setTextSize(11f);
        message.setLineSpacing(dp(3), 1f);
        message.setTextColor(0xCCD8E8DE);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageLp.topMargin = dp(14);
        panel.addView(message, messageLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        Button deny = new Button(this);
        deny.setText("拒绝");
        deny.setTextSize(12f);
        deny.setAllCaps(false);
        deny.setTextColor(0xFFEAF7EF);
        GradientDrawable denyBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x3DFFFFFF, 0x14FFFFFF});
        denyBg.setCornerRadius(dp(14));
        denyBg.setStroke(dp(1), 0x44FFFFFF);
        deny.setBackground(denyBg);
        actions.addView(deny, new LinearLayout.LayoutParams(dp(76), dp(44)));

        Button allow = new Button(this);
        allow.setText("允许");
        allow.setTextSize(12f);
        allow.setAllCaps(false);
        allow.setTextColor(0xFF03150D);
        GradientDrawable allowBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF8AF2C8, 0xFF56E6B0});
        allowBg.setCornerRadius(dp(14));
        allow.setBackground(allowBg);
        LinearLayout.LayoutParams allowLp = new LinearLayout.LayoutParams(dp(92), dp(44));
        allowLp.leftMargin = dp(8);
        actions.addView(allow, allowLp);

        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(18);
        panel.addView(actions, actionsLp);

        GlassUi.hardenTree(panel);
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.55f;
            window.setAttributes(lp);
            if (Build.VERSION.SDK_INT >= 31) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.setBackgroundBlurRadius(dp(24));
            }
        }
        final boolean[] settled = {false};
        dialog.setOnDismissListener(d -> {
            if (siteAuthDialog == dialog) siteAuthDialog = null;
            authDialogShowing = false;
            if (!settled[0]) {
                settled[0] = true;
                onDeny.run();
            }
        });
        allow.setFilterTouchesWhenObscured(true);
        allow.setOnClickListener(v -> {
            if (settled[0]) return;
            settled[0] = true;
            dialog.dismiss();
            onAllow.run();
        });
        deny.setFilterTouchesWhenObscured(true);
        deny.setOnClickListener(v -> {
            if (settled[0]) return;
            settled[0] = true;
            dialog.dismiss();
            onDeny.run();
        });
        dialog.setOnCancelListener(d -> {
            if (settled[0]) return;
            settled[0] = true;
            onDeny.run();
        });
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        try {
            dialog.show();
            GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || isSiteLocked());
        } catch (Throwable t) {
            if (!settled[0]) {
                settled[0] = true;
                authDialogShowing = false;
                onDeny.run();
            }
        }
    }

    private static class FloatingSession {
        SiteWindow window;
        WebView web;
        ViewGroup parent;
        ViewGroup.LayoutParams webParams;
        FrameLayout root;
        LinearLayout column;
        TextView capsule;
        View settingsPanel;
        TextView resize;
        FrameLayout authOverlay;
        WindowManager.LayoutParams params;
        int normW, normH, normX, normY;
        boolean maximized;
        boolean minimized;
        boolean expandedBeforeOkx;
        boolean hiddenForLock;
        boolean wasMinimizedBeforeLock;
    }

    private static class AuthRequest {
        final WebMessageCompat message;
        final Uri origin;
        final boolean isMainFrame;
        final JavaScriptReplyProxy reply;
        final SiteWindow win;
        final String key;
        final String siteId;

        AuthRequest(WebMessageCompat message, Uri origin, boolean isMainFrame,
                    JavaScriptReplyProxy reply, SiteWindow win, String key, String siteId) {
            this.message = message;
            this.origin = origin;
            this.isMainFrame = isMainFrame;
            this.reply = reply;
            this.win = win;
            this.key = key;
            this.siteId = siteId;
        }
    }

    private void sendBridgeError(JavaScriptReplyProxy reply, String siteId, int code, String message) {
        try {
            if (reply == null) return;
            JSONObject err = new JSONObject();
            err.put("id", siteId);
            JSONObject e = new JSONObject();
            e.put("code", code);
            e.put("message", message);
            err.put("error", e);
            reply.postMessage(err.toString());
        } catch (Throwable ignored) {}
    }

    private boolean hasRecentGesture() {
        return lastGestureAt > 0 && (System.currentTimeMillis() - lastGestureAt) < 30000;
    }

    private boolean isInteractiveWalletMethod(String method) {
        return "eth_requestAccounts".equals(method)
                || "wallet_requestPermissions".equals(method)
                || "personal_sign".equals(method)
                || "eth_signTypedData_v4".equals(method)
                || "eth_sendTransaction".equals(method)
                || "wallet_switchEthereumChain".equals(method);
    }

    private void dispatchWallet(JSONObject req) {
        if (walletWeb == null) return;
        try {
            String b64 = Base64.encodeToString(req.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            walletWeb.evaluateJavascript("window.__dewebWalletRequest(JSON.parse(atob('" + b64 + "')));", null);
        } catch (Throwable ignored) {}
    }

    private void flushWalletQueue() {
        if (!walletPageReady || walletWeb == null) return;
        while (!walletQueue.isEmpty()) dispatchWallet(walletQueue.poll());
    }

    private void sendSiteError(PendingWallet pending, int code, String message) {
        try {
            JSONObject out = new JSONObject();
            out.put("id", pending.siteId);
            JSONObject err = new JSONObject();
            err.put("code", code);
            err.put("message", message);
            out.put("error", err);
            if (pending.reply != null) pending.reply.postMessage(out.toString());
        } catch (Exception e) {}
    }

    private void ensureWallet() {
        if (walletWeb != null) return;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return;
        }
        walletWeb = new WebView(this);
        walletWeb.setBackgroundColor(Color.WHITE);
        walletWeb.setVisibility(View.GONE);
        walletWeb.setFilterTouchesWhenObscured(true);
        rootFrame.addView(walletWeb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ws = walletWeb.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setAllowFileAccessFromFileURLs(false);
        ws.setAllowUniversalAccessFromFileURLs(false);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setSupportMultipleWindows(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= 26) ws.setSafeBrowsingEnabled(true);

        Set<String> walletOrigins = new HashSet<>(Arrays.asList(WALLET_ORIGIN));
        WebViewCompat.addDocumentStartJavaScript(walletWeb, readAsset("wallet-shim.js"), walletOrigins);
        WebViewCompat.addWebMessageListener(
                walletWeb,
                "dewebWalletBridge",
                walletOrigins,
                (view, message, sourceOrigin, isMainFrame, replyProxy) -> onWalletMessage(message, sourceOrigin)
        );
        if (walletAssetLoader == null) {
            walletAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                    .build();
        }
        walletWeb.setWebChromeClient(fileChooser.chromeClient());
        walletWeb.setWebViewClient(new WebViewClient() {
@Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (walletAssetLoader == null) return null;
                return walletAssetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isOkxUri(uri)) { openOkx(uri); return true; }
                if (isWalletUrl(uri)) return false;
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && isWalletUrl(Uri.parse(url))) {
                    walletPageReady = true;
                    clearPendingWalletRevocations(view);
                    flushWalletQueue();
                    if (membershipRequested) {
                        membershipRequested = false;
                        walletWeb.evaluateJavascript("window.__dewebOpenMembership && window.__dewebOpenMembership()", null);
                    }
                }
            }
        });
        SharedPreferences wsp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        boolean reset = wsp.getBoolean("wallet_reset_pending", false);
        if (reset) wsp.edit().remove("wallet_reset_pending").apply();
        walletWeb.loadUrl(WALLET_URL + (reset ? "&reset=1" : ""));
    }

    private void openMembershipPanel() {
        membershipRequested = true;
        ensureWallet();
        showWallet();
        if (walletPageReady && walletWeb != null) {
            membershipRequested = false;
            walletWeb.evaluateJavascript("window.__dewebOpenMembership && window.__dewebOpenMembership()", null);
        }
    }

    private void hideFloatingForWallet() {
        if (walletHidesFloats) return;
        walletHiddenFloats.clear();
        for (FloatingSession f : new ArrayList<>(floatingSessions)) {
            if (!f.minimized && !f.hiddenForLock) {
                walletHiddenFloats.add(f);
                minimizeFloating(f);
            }
        }
        if (capsuleTray != null) capsuleTray.setVisibility(View.GONE);
        walletHidesFloats = true;
    }

    private void restoreFloatingAfterWallet() {
        if (!walletHidesFloats) return;
        walletHidesFloats = false;
        if (isSiteLocked()) { walletHiddenFloats.clear(); return; }
        if (capsuleTray != null && !capsuleChips.isEmpty()) capsuleTray.setVisibility(View.VISIBLE);
        for (FloatingSession f : new ArrayList<>(walletHiddenFloats)) {
            if (floatingSessions.contains(f) && f.minimized && !f.hiddenForLock) expandFloating(f, false);
        }
        walletHiddenFloats.clear();
    }

    private void dismissFloatingSettings(FloatingSession f) {
        if (f == null || f.settingsPanel == null) return;
        try { if (f.root != null) f.root.removeView(f.settingsPanel); } catch (Throwable ignored) {}
        f.settingsPanel = null;
    }

    private void showFloatingSettings(FloatingSession f) {
        if (f == null || f.root == null) return;
        if (f.settingsPanel != null) {
            f.settingsPanel.bringToFront();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFA0A1611, 0xF207100B});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x66A8C9B8);
        panel.setBackground(bg);
        panel.setClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("悬浮窗设置");
        title.setTextSize(17f);
        title.setTextColor(0xFFEAF8F1);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(22f);
        close.setTextColor(0xFFE6F2EA);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dismissFloatingSettings(f));
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(36)));
        panel.addView(head);

        Switch privacy = new Switch(this);
        privacy.setText("禁止截屏和最近任务预览");
        privacy.setTextSize(13f);
        privacy.setTextColor(0xFFEAF8F1);
        privacy.setChecked(PrivacyPrefs.isEnabled(this));
        privacy.setOnCheckedChangeListener((buttonView, checked) -> {
            PrivacyPrefs.setEnabled(this, checked);
            applyScreenshotProtection();
            SiteActivity.notifyPrivacyChanged();
            Toast.makeText(this, checked ? "已开启截屏保护" : "已关闭截屏保护", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(10);
        panel.addView(privacy, plp);

        Switch floating = new Switch(this);
        floating.setText("启用悬浮窗");
        floating.setTextSize(13f);
        floating.setTextColor(0xFFEAF8F1);
        floating.setChecked(isFloatingEnabled(this));
        floating.setOnCheckedChangeListener((buttonView, checked) -> {
            getSharedPreferences("deweb_shell", MODE_PRIVATE).edit()
                    .putBoolean("floating_enabled", checked).apply();
            if (!checked) disableFloatingNow();
            Toast.makeText(this, checked ? "已启用悬浮窗" : "已关闭悬浮窗", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(8);
        panel.addView(floating, flp);

        Button disconnect = new Button(this);
        disconnect.setText("断开所有钱包连接");
        disconnect.setTextSize(13f);
        disconnect.setAllCaps(false);
        disconnect.setTextColor(0xFFFFB8B8);
        GradientDrawable dbg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FF5A5A, 0x22FF8080});
        dbg.setCornerRadius(dp(14));
        dbg.setStroke(dp(1), 0x99FF8A8A);
        disconnect.setBackground(dbg);
        disconnect.setOnClickListener(v -> {
            disconnectWalletNow();
            dismissFloatingSettings(f);
            Toast.makeText(this, "已断开所有钱包连接", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        dlp.topMargin = dp(12);
        panel.addView(disconnect, dlp);

        Button done = new Button(this);
        done.setText("完成");
        done.setTextSize(13f);
        done.setAllCaps(false);
        done.setTextColor(0xFF03150D);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable doneBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF56E6B0, 0xFF2FC79A});
        doneBg.setCornerRadius(dp(14));
        done.setBackground(doneBg);
        done.setOnClickListener(v -> dismissFloatingSettings(f));
        LinearLayout.LayoutParams donelp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        donelp.topMargin = dp(12);
        panel.addView(done, donelp);

        GlassUi.hardenTree(panel);
        scroll.addView(panel);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = dp(38);
        scroll.setBackgroundColor(0xCC000000);
        f.settingsPanel = scroll;
        f.root.addView(scroll, lp);
    }

    private void showSiteSettings() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout shade = new FrameLayout(this);
        shade.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF20A1611, 0xF207100B});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), 0x66A8C9B8);
        panel.setBackground(bg);
        panel.setPadding(dp(22), dp(20), dp(22), dp(16));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(19f);
        title.setTextColor(0xFFEAF8F1);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        Switch floating = new Switch(this);
        floating.setText("启用悬浮窗");
        floating.setTextSize(13f);
        floating.setTextColor(0xFFEAF8F1);
        floating.setChecked(isFloatingEnabled(this));
        floating.setOnCheckedChangeListener((buttonView, checked) -> {
            getSharedPreferences("deweb_shell", MODE_PRIVATE).edit()
                    .putBoolean("floating_enabled", checked).apply();
            if (!checked) disableFloatingNow();
            Toast.makeText(this, checked ? "已启用悬浮窗" : "已关闭悬浮窗", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(14);
        panel.addView(floating, flp);

        Switch privacy = new Switch(this);
        privacy.setText("禁止截屏和最近任务预览");
        privacy.setTextSize(13f);
        privacy.setTextColor(0xFFEAF8F1);
        privacy.setChecked(PrivacyPrefs.isEnabled(this));
        privacy.setOnCheckedChangeListener((buttonView, checked) -> {
            PrivacyPrefs.setEnabled(this, checked);
            applyScreenshotProtection();
            SiteActivity.notifyPrivacyChanged();
            Toast.makeText(this, checked ? "已开启截屏保护" : "已关闭截屏保护", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        panel.addView(privacy, plp);

        if (BuildConfig.IS_BETA) {
            Button bgNavTest = new Button(this);
            bgNavTest.setText("后台导航测试（Beta）");
            bgNavTest.setTextSize(13f);
            bgNavTest.setAllCaps(false);
            bgNavTest.setTextColor(0xFFEAF8F1);
            GradientDrawable bgTestBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x33FFFFFF, 0x22F4FFF9});
            bgTestBg.setCornerRadius(dp(14));
            bgTestBg.setStroke(dp(1), 0x66A8C9B8);
            bgNavTest.setBackground(bgTestBg);
            bgNavTest.setOnClickListener(v -> {
                bgNavTest.setEnabled(false);
                SiteActivity.testBackgroundNavigation(msg -> runOnUiThread(() -> {
                    bgNavTest.setEnabled(true);
                    AlertDialog result = new AlertDialog.Builder(SiteActivity.this)
                            .setTitle("后台导航测试")
                            .setMessage(msg)
                            .setPositiveButton("知道了", null)
                            .create();
                    result.show();
                    try { GlassUi.secureDialog(result, PrivacyPrefs.isEnabled(SiteActivity.this) || isSiteLocked()); } catch (Throwable ignored) {}
                }));
            });
            LinearLayout.LayoutParams bgTestLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            bgTestLp.topMargin = dp(10);
            panel.addView(bgNavTest, bgTestLp);
        }

        Button mainSettings = new Button(this);
        mainSettings.setText("主站设置（更新/隐私）");
        mainSettings.setTextSize(13f);
        mainSettings.setAllCaps(false);
        mainSettings.setTextColor(0xFFEAF8F1);
        GradientDrawable mainSettingsBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FFFFFF, 0x22F4FFF9});
        mainSettingsBg.setCornerRadius(dp(14));
        mainSettingsBg.setStroke(dp(1), 0x66A8C9B8);
        mainSettings.setBackground(mainSettingsBg);
        mainSettings.setOnClickListener(v -> {
            getSharedPreferences("deweb_shell", MODE_PRIVATE).edit()
                    .putBoolean("open_main_settings_once", true)
                    .putBoolean("return_to_site_after_settings", true).apply();
            Intent i = new Intent(SiteActivity.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams mainSettingsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        mainSettingsLp.topMargin = dp(10);
        panel.addView(mainSettings, mainSettingsLp);

        Button disconnect = new Button(this);
        disconnect.setText("断开所有钱包连接");
        disconnect.setTextSize(13f);
        disconnect.setAllCaps(false);
        disconnect.setTextColor(0xFFFFB8B8);
        GradientDrawable disconnectBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FF5A5A, 0x22FF8080});
        disconnectBg.setCornerRadius(dp(14));
        disconnectBg.setStroke(dp(1), 0x99FF8A8A);
        disconnect.setBackground(disconnectBg);
        disconnect.setOnClickListener(v -> {
            disconnectWalletNow();
            Toast.makeText(this, "已断开所有钱包连接", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams disconnectLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        disconnectLp.topMargin = dp(10);
        panel.addView(disconnect, disconnectLp);

        Button done = new Button(this);
        done.setText("关闭");
        done.setTextSize(13f);
        done.setAllCaps(false);
        done.setTextColor(0xFF03150D);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable doneBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF56E6B0, 0xFF2FC79A});
        doneBg.setCornerRadius(dp(14));
        done.setBackground(doneBg);
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        dlp.topMargin = dp(18);
        panel.addView(done, dlp);

        shade.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        GlassUi.hardenTree(shade);
        dialog.setContentView(shade);
        dialog.show();
        GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || isSiteLocked());
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0x99030A07));
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.dimAmount = 0.45f;
            win.setAttributes(lp);
            int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            win.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public static boolean isFloatingEnabled(Context context) {
        return context != null && context.getSharedPreferences("deweb_shell", Context.MODE_PRIVATE)
                .getBoolean("floating_enabled", false);
    }

    public static void hideFloatingForDialog() {
        SiteActivity activity = currentRef == null ? null : currentRef.get();
        if (activity != null) activity.runOnUiThread(activity::hideFloatingForLock);
    }

    public static void restoreFloatingAfterDialog() {
        SiteActivity activity = currentRef == null ? null : currentRef.get();
        if (activity != null) activity.runOnUiThread(activity::restoreFloatingAfterLock);
    }

    public static void disableFloating() {
        SiteActivity activity = currentRef == null ? null : currentRef.get();
        if (activity != null) activity.runOnUiThread(activity::disableFloatingNow);
    }

    private void disableFloatingNow() {
        for (FloatingSession f : new ArrayList<>(floatingSessions)) restoreFloating(f);
    }

    private void syncFloatingFeature() {
        if (!isFloatingEnabled(this) && !floatingSessions.isEmpty()) disableFloatingNow();
    }

    private void showWallet() {
        if (isSiteLocked()) return;
        if (walletFloatHost != null) {
            showWalletInFloat(walletFloatHost);
            return;
        }
        if (hasFloating() && !floatingKeepForRequest && !floatingConnectInProgress) { hideFloatingForWallet(); }
        if (walletWeb == null) return;
        runOnUiThread(() -> {
            if (walletCancel == null) {
                walletCancel = new Button(this);
                walletCancel.setText("取消");
                walletCancel.setTextSize(12f);
                walletCancel.setAllCaps(false);
                walletCancel.setTextColor(Color.rgb(21, 37, 29));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xE6FFFFFF);
                bg.setCornerRadius(dp(14));
                bg.setStroke(dp(1), 0x556B8F7E);
                walletCancel.setBackground(bg);
                walletCancel.setFilterTouchesWhenObscured(true);
                walletCancel.setOnClickListener(v -> cancelWalletRequests());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(64), dp(38));
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.topMargin = dp(10);
                lp.rightMargin = dp(10);
                rootFrame.addView(walletCancel, lp);
            }
            walletCancel.setVisibility(View.VISIBLE);
            walletCancel.bringToFront();
            walletWeb.setVisibility(View.VISIBLE);
            walletWeb.bringToFront();
            walletCancel.bringToFront();
            walletWeb.requestFocus();
            applyScreenshotProtection();
        });
    }

    private void cancelWalletRequests() {
        try {
            walletCanceledAt = System.currentTimeMillis();
            for (PendingWallet pending : walletPending.values()) {
                sendSiteError(pending, 4001, "User rejected");
            }
            walletPending.clear();
            walletQueue.clear();
            interactivePending = false;
            walletPageReady = false;
            if (walletWeb != null) {
                walletWeb.stopLoading();
                walletWeb.loadUrl(WALLET_URL);
            }
            hideWallet();
            Toast.makeText(this, "已取消连接", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Log.e(TAG, "cancel wallet failed", t);
        }
    }

    private void hideWallet() {
        if (walletAttachedToFloat) { detachWalletFromFloat(); return; }
        if (walletWeb == null) return;
        runOnUiThread(() -> {
            walletWeb.setVisibility(View.GONE);
            if (walletCancel != null) walletCancel.setVisibility(View.GONE);
            restoreFloatingAfterWallet();
            applyScreenshotProtection();
        });
    }


    public static void revokeNativeApproval(String origin) {
        revokeNativeApproval(null, origin);
    }

    public static void revokeNativeApproval(Context context, String origin) {
        if (origin == null || origin.isEmpty()) return;
        if (context != null) {
            try {
                SharedPreferences sp = context.getSharedPreferences("deweb_shell", Context.MODE_PRIVATE);
                Set<String> revoked = new HashSet<>(sp.getStringSet(WALLET_REVOKE_KEY, new HashSet<>()));
                revoked.add(origin);
                mergeRevokedOrigins(sp, revoked);
            } catch (Throwable ignored) {}
        }
        for (WeakReference<SiteActivity> ref : new ArrayList<>(liveRefs)) {
            SiteActivity activity = ref == null ? null : ref.get();
            if (activity == null) { liveRefs.remove(ref); continue; }
            activity.runOnUiThread(() -> {
                activity.nativeApproved.remove(origin);
                activity.nativeApprovedExpiry.remove(origin);
                activity.saveNativeApproved();
                try {
                    if (activity.walletWeb != null) {
                        activity.walletWeb.evaluateJavascript(revokeWalletOriginsJs(new String[]{origin}), null);
                    }
                } catch (Throwable ignored) {}
            });
        }
    }

    private static String revokeWalletOriginsJs(String[] origins) {
        JSONArray arr = new JSONArray();
        for (String o : origins) arr.put(o);
        return "(function(){try{var ks=['deweb.electron.wc.origins','deweb.electron.wc.origins.exp','deweb.electron.wc.originchain'];var r=" + arr.toString() + ";for(var j=0;j<r.length;j++){var o=r[j];var a=JSON.parse(localStorage.getItem(ks[0])||'[]').filter(function(x){return x!==o;});localStorage.setItem(ks[0],JSON.stringify(a));var ex=JSON.parse(localStorage.getItem(ks[1])||'{}');delete ex[o];localStorage.setItem(ks[1],JSON.stringify(ex));var ch=JSON.parse(localStorage.getItem(ks[2])||'{}');delete ch[o];localStorage.setItem(ks[2],JSON.stringify(ch));}}catch(_){}})();";
    }

    private void clearPendingWalletRevocations(WebView view) {
        if (view == null) return;
        SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        Set<String> pending = new HashSet<>(sp.getStringSet(WALLET_REVOKE_KEY, new HashSet<>()));
        if (pending.isEmpty()) return;
        String[] arr = pending.toArray(new String[0]);
        view.evaluateJavascript(revokeWalletOriginsJs(arr), null);
    }

    public static void notifyCloudDisconnected() {
        for (WeakReference<SiteActivity> ref : new ArrayList<>(liveRefs)) {
            SiteActivity a = ref == null ? null : ref.get();
            if (a == null) continue;
            a.runOnUiThread(() -> {
                String js = "(function(){try{window.__dewebCloudAddress='';localStorage.removeItem('deweb.cloud.membership.token.v1');window.dispatchEvent(new Event('deweb-cloud-disconnected'));}catch(e){}})();";
                for (SiteWindow w : a.allWindows()) {
                    if (w == null || w.web == null) continue;
                    try { w.web.evaluateJavascript(js, null); } catch (Throwable ignored) {}
                }
                try { if (a.mainBackground != null) a.mainBackground.evaluateJavascript(js, null); } catch (Throwable ignored) {}
            });
        }
    }

    public static void disconnectAllInstances() {
        for (WeakReference<SiteActivity> ref : new ArrayList<>(liveRefs)) {
            SiteActivity activity = ref == null ? null : ref.get();
            if (activity != null) activity.runOnUiThread(() -> activity.disconnectWalletNow(false));
        }
    }

    private void disconnectWalletNow() { disconnectWalletNow(true); }

    private void broadcastWalletDisconnected() {
        for (SiteWindow w : allWindows()) {
            if (w == null || w.web == null) continue;
            try {
                w.web.evaluateJavascript("(function(){try{window.dispatchEvent(new Event('deweb-wallet-disconnected'));}catch(e){}try{walletAccount=null;walletChain=null;walletUserConnected=false;lastWalletAccount=null;if(window.ethereum&&window.ethereum.emit){window.ethereum.emit('accountsChanged',[]);}if(typeof paintWallet==='function')paintWallet();if(typeof closeWalletModal==='function')closeWalletModal();}catch(e){}})();", null);
            } catch (Throwable ignored) {}
        }
    }

    private void disconnectWalletNow(boolean clearCloud) {
        try {
            if (clearCloud) {
                MainActivity.clearCloudSessionForSiteDisconnect(() -> runOnUiThread(() -> Toast.makeText(this, "云端注销失败，本地已清除，请重新登录", Toast.LENGTH_LONG).show()));
                MembershipManager.clear(this);
            }
            detachWalletFromFloat();
            cancelWalletRequests();
            cachedAccounts = null;
            cachedChain = null;
            nativeApproved.clear();
            nativeApprovedExpiry.clear();
            authPrefs.edit().remove("native_approved_origins").remove("native_approved_expiry").apply();
            walletCanceledAt = System.currentTimeMillis();
            if (walletWeb != null) {
                walletPageReady = false;
                walletWeb.stopLoading();
                walletWeb.loadUrl(WALLET_URL + "&reset=1");
            }
            getSharedPreferences("deweb_shell", MODE_PRIVATE)
                    .edit().putBoolean("wallet_reset_pending", true).apply();
            broadcastWalletDisconnected();
            if (clearCloud) disconnectAllInstances();
        } catch (Throwable t) {
            Log.e(TAG, "disconnect wallet failed", t);
        }
    }

    private void onWalletMessage(WebMessageCompat message, Uri sourceOrigin) {
        try {
            if (isSiteLocked()) return;
            JSONObject msg = new JSONObject(message.getData() == null ? "{}" : message.getData());
            String type = msg.optString("type");
            if ("focus".equals(type)) {
                if (floatingKeepForRequest) return;
                if (hasRecentGesture()) showWallet();
                return;
            }
            if ("accounts".equals(type)) {
                JSONArray list = msg.optJSONArray("accounts");
                boolean had = cachedAccounts != null && cachedAccounts.length() > 0;
                cachedAccounts = (list != null && list.length() > 0) ? list : null;
                if (cachedAccounts != null) {
                    try { MainActivity.enforceCloudSessionForWallet(cachedAccounts.optString(0), null); } catch (Throwable ignored) {}
                } else if (had) {
                    try { MainActivity.clearCloudSessionForSiteDisconnect(); } catch (Throwable ignored) {}
                }
                return;
            }
            if ("chain".equals(type)) {
                cachedChain = msg.optString("chainId");
                return;
            }
            if ("membership_changed".equals(type)) {
                MembershipManager.refresh(this, (ok, result, expiry) -> runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        checkMembershipExpiry();
                        Toast.makeText(this, ok ? "会员状态已更新" : "会员刷新失败：" + result, Toast.LENGTH_SHORT).show();
                    }
                }));
                return;
            }
            if ("open_url".equals(type)) {
                String target = msg.optString("url");
                Uri okx = Uri.parse(target);
                if (isOkxUri(okx)) openOkx(okx);
                return;
            }
            String id = msg.optString("id");
            if (id == null || id.isEmpty()) return;
            PendingWallet pending = walletPending.remove(id);
            if (pending == null) return;

            JSONObject out = new JSONObject();
            out.put("id", pending.siteId);
            if (msg.has("error")) out.put("error", msg.optJSONObject("error"));
            else if (msg.has("result")) out.put("result", msg.opt("result"));
            else out.put("result", JSONObject.NULL);
            if (("eth_requestAccounts".equals(pending.method) || "eth_accounts".equals(pending.method))
                    && msg.optJSONArray("result") != null && msg.optJSONArray("result").length() > 0) {
                cachedAccounts = msg.optJSONArray("result");
                try { MainActivity.enforceCloudSessionForWallet(cachedAccounts == null ? "" : cachedAccounts.optString(0), null); } catch (Throwable ignored) {}
                MembershipManager.saveAddress(this, cachedAccounts.optString(0));
                MembershipManager.refresh(this, (ok, resultMsg, expiry) ->
                        runOnUiThread(this::checkMembershipExpiry));
            }
            if ("eth_chainId".equals(pending.method) && msg.optString("result").startsWith("0x")) {
                cachedChainHint = normalizeChain(msg.optString("result"));
            }
            if ("wallet_switchEthereumChain".equals(pending.method) && !msg.has("error")) {
                String target = normalizeChain(pending.switchTo);
                if (pending.window != null) pending.window.chainHint = target;
                cachedChainHint = target;
            }
            if (pending.reply != null) pending.reply.postMessage(out.toString());
            interactivePending = false;
            floatingKeepForRequest = false;
            floatingConnectInProgress = false;
            maybeRefloatAfterWallet(pending);
            hideWallet();
        } catch (Exception e) {
            Log.e(TAG, "wallet response failed", e);
        }
    }

    private boolean isWalletScheme(String scheme) {
        if (scheme == null) return false;
        String s = scheme.toLowerCase();
        return s.equals("wc") || s.equals("metamask") || s.equals("bnc") || s.equals("binance")
                || s.equals("trust") || s.equals("coinbase") || s.equals("rainbow")
                || s.equals("okx") || s.equals("okxwallet") || s.equals("okxweb3");
    }

    private String siteOrigin(String url) {
        try {
            Uri u = Uri.parse(url == null ? "" : url);
            if (!"https".equalsIgnoreCase(u.getScheme())) return null;
            String host = u.getHost();
            if (host == null || host.isEmpty()) return null;
            if ("appassets.androidplatform.net".equalsIgnoreCase(host)) return null;
            return "https://" + host + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Throwable t) { return null; }
    }

    private boolean isWalletUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();
        return scheme.equals("https") && host.equals("appassets.androidplatform.net")
                && path.startsWith("/assets/wallet/");
    }

    private boolean isOkxUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (scheme.equals("okx") || scheme.equals("okxwallet") || scheme.equals("okxweb3")) return true;
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        return (scheme.equals("http") || scheme.equals("https")) &&
                (host.equals("web3link.okx.com") || host.equals("link.okx.com") ||
                        (host.equals("web3.okx.com") && uri.getPath() != null && uri.getPath().startsWith("/ul/connect")));
    }

    private void openOkx(Uri uri) {
        if (hasFloating()) {
            minimizeAllFloatingForOkx();
            if (siteForeground && !walletAttachedToFloat) bringSiteToFront();
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "未找到 OKX App，请先安装", Toast.LENGTH_SHORT).show();
        }
    }

    private void addCurrentToDesktop() {
        try {
            WebView current = activeWebView != null ? activeWebView : webView;
            if (current == null) return;
            String url = current.getUrl();
            String title = current.getTitle();
            if (url == null || !url.startsWith("http")) return;
            if (title == null || title.isEmpty()) title = Uri.parse(url).getHost();
            final String addUrl = url;
            final String addTitle = title;
            AlertDialog addDialog = new AlertDialog.Builder(this)
                    .setTitle("添加站点")
                    .setMessage("是否添加该站点进入桌面？\n\n本应用不保证任何三方站点的安全性\n\n请用户谨慎添加")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确认添加", (dialog, which) -> {
                        SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
                        sp.edit().putString("pending_add_url", addUrl).putString("pending_add_title", addTitle).apply();
                        finish();
                    })
                    .show();
            try { GlassUi.hardenTree(addDialog.getWindow().getDecorView()); } catch (Throwable ignored) {}
            try { GlassUi.secureDialog(addDialog, PrivacyPrefs.isEnabled(this) || isSiteLocked()); } catch (Throwable ignored) {}
        } catch (Exception e) {
            Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isSafeSiteUrl(String url) {
        try {
            if (url == null) return false;
            Uri u = Uri.parse(url.trim());
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) return false;
            String host = u.getHost();
            if (host == null || host.isEmpty()) return false;
            if ("appassets.androidplatform.net".equalsIgnoreCase(host)) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isWalletPolicyMethod(String method) {
        return "eth_requestAccounts".equals(method)
                || "wallet_requestPermissions".equals(method)
                || "eth_accounts".equals(method)
                || "personal_sign".equals(method)
                || "eth_signTypedData_v4".equals(method)
                || "eth_sendTransaction".equals(method)
                || "wallet_switchEthereumChain".equals(method);
    }

    private String siteUrlText(String raw) {
        try {
            Uri u = Uri.parse(raw == null ? "" : raw);
            String host = u.getHost();
            if (host == null || host.isEmpty()) return raw == null ? "" : raw;
            String path = u.getEncodedPath();
            String query = u.getEncodedQuery();
            return host + ((path == null || path.isEmpty() || "/".equals(path)) ? "" : path)
                    + ((query == null || query.isEmpty()) ? "" : "?" + query);
        } catch (Throwable t) {
            return raw == null ? "" : raw;
        }
    }

    private String hostOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host;
        } catch (Exception e) { return ""; }
    }

    private String readAsset(String name) {
        try (java.io.InputStream is = getAssets().open(name)) {
            byte[] data = new byte[is.available()];
            int n = is.read(data);
            return new String(data, 0, Math.max(n, 0), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            JSONObject root = new JSONObject();
            JSONArray list = new JSONArray();
            if (window1 != null) list.put(snapshotWindow(window1));
            if (window2 != null) list.put(snapshotWindow(window2));
            if (window3 != null) list.put(snapshotWindow(window3));
            if (window4 != null) list.put(snapshotWindow(window4));
            root.put("windows", list);
            int activeIndex = 0;
            ArrayList<SiteWindow> wins = allWindows();
            for (int i = 0; i < wins.size(); i++) if (activeWebView == wins.get(i).web) activeIndex = i;
            root.put("active", activeIndex);
            outState.putString("window_state", root.toString());
        } catch (Throwable t) {
            Log.e(TAG, "save window state failed", t);
        }
    }

    private JSONObject snapshotWindow(SiteWindow win) throws Exception {
        JSONObject o = new JSONObject();
        String url = win.web.getUrl();
        if (url == null || url.startsWith("about:")) url = startUrl;
        o.put("url", url);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) win.card.getLayoutParams();
        if (win.maximized) {
            o.put("left", win.preLeft);
            o.put("top", win.preTop);
            o.put("width", win.preWidth);
            o.put("height", win.preHeight);
        } else {
            o.put("left", lp.leftMargin);
            o.put("top", lp.topMargin);
            o.put("width", lp.width);
            o.put("height", lp.height);
        }
        o.put("chain", win.chainHint);
        o.put("max", win.maximized);
        o.put("min", win.chip != null);
        return o;
    }

    private boolean restoreWindowState(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray list = root.optJSONArray("windows");
            if (list == null || list.length() == 0) return false;
            int active = root.optInt("active", 0);
            for (int i = 0; i < list.length() && i < maxWindows(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url");
                if (url.isEmpty()) continue;
                SiteWindow win = createWindow(url, i == 0, o.optString("chain", "56"));
                if (win == null) continue;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) win.card.getLayoutParams();
                int w = o.optInt("width", lp.width);
                int h = o.optInt("height", lp.height);
                if (w > 0) lp.width = w;
                if (h > 0) lp.height = h;
                lp.leftMargin = Math.max(0, o.optInt("left", lp.leftMargin));
                lp.topMargin = Math.max(0, o.optInt("top", lp.topMargin));
                win.card.setLayoutParams(lp);
                if (i == 0) {
                    window1 = win;
                    webView = win.web;
                } else if (i == 1) {
                    window2 = win;
                    webView2 = win.web;
                    splitButton.setText("▣");
                } else if (i == 2) {
                    window3 = win;
                } else {
                    window4 = win;
                }
                if (o.optBoolean("max", false)) maximizeWindow(win);
                if (o.optBoolean("min", false)) minimizeWindow(win);
            }
            if (window1 == null) return false;
            if (window2 == null) splitButton.setText("▥");
            ArrayList<SiteWindow> restored = allWindows();
            if (active >= 0 && active < restored.size() && restored.get(active).chip == null) setActive(restored.get(active));
            else if (window1.chip == null) setActive(window1);
            applySplitLayout();
            ensureWallet();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "restore window state failed", t);
            return false;
        }
    }

    private boolean sameHost(String a, String b) {
        try {
            String ha = Uri.parse(a == null ? "" : a).getHost();
            String hb = Uri.parse(b == null ? "" : b).getHost();
            return ha != null && ha.equalsIgnoreCase(hb);
        } catch (Throwable t) { return false; }
    }

    private String activeChainHint() {
        if (window1 != null && activeWebView == window1.web) return window1.chainHint;
        if (window2 != null && activeWebView == window2.web) return window2.chainHint;
        if (window3 != null && activeWebView == window3.web) return window3.chainHint;
        if (window4 != null && activeWebView == window4.web) return window4.chainHint;
        return "56";
    }

    private static String chainHex(String raw) {
        String d = normalizeChain(raw);
        return "0x" + Integer.toHexString(Integer.parseInt(d));
    }

    private static String normalizeChain(String raw) {
        if (raw == null) return "56";
        String v = raw.trim().toLowerCase();
        if (v.isEmpty()) return "56";
        if (v.startsWith("0x")) {
            try { return String.valueOf(Integer.parseInt(v.substring(2), 16)); } catch (Exception ignored) { return "56"; }
        }
        if (v.contains("196") || v.contains("xlayer") || v.contains("x-layer")) return "196";
        if (v.equals("1") || v.contains("ethereum") || v.contains("mainnet")) return "1";
        if (v.contains("56") || v.contains("bsc") || v.contains("bnb")) return "56";
        return "56";
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private android.graphics.drawable.Drawable liquidGlass() {
        return GlassUi.panel(this, 22, false);
    }

    private android.graphics.drawable.Drawable glassChip() {
        return GlassUi.button(this, 15, false);
    }

    private android.graphics.drawable.Drawable glassBar() {
        return GlassUi.bar(this, false);
    }

    private Button navButton(String text, String desc) {
        Button b = new Button(this);
        b.setText(text);
        b.setContentDescription(desc);
        b.setTextSize(21f);
        b.setTextColor(Color.rgb(21, 37, 29));
        b.setBackground(GlassUi.button(this, 14, false));
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        GlassUi.hardenTree(b);
        return b;
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!floatingSessions.isEmpty() && !floatingKeepForRequest) {
            for (FloatingSession f : new ArrayList<>(floatingSessions)) {
                if (!f.minimized) minimizeFloating(f);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (lockOverlay != null && lockOverlay.getVisibility() == View.VISIBLE) {
            Toast.makeText(this, "请先解锁", Toast.LENGTH_SHORT).show();
            return;
        }
        if (walletWeb != null && walletWeb.getVisibility() == View.VISIBLE) {
            cancelWalletRequests();
            return;
        }
        WebView current = activeWebView != null ? activeWebView : webView;
        if (current != null && current.canGoBack()) current.goBack();
        else if (mainBackground != null && mainBackground.canGoBack()) mainBackground.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        siteForeground = false;
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) createFloatingWindow();
            else Toast.makeText(this, "未授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestCode == REQ_UNLOCK) {
            if (resultCode == RESULT_OK) {
                finishUnlock();
            } else {
                Toast.makeText(this, "未解锁", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (fileChooser != null && fileChooser.onActivityResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void finishUnlock() {
        if (lockOverlay != null) lockOverlay.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(true);
        getSharedPreferences("deweb_shell", MODE_PRIVATE).edit().putBoolean("site_locked", false).apply();
        lockedWindow = null;
        restoreFloatingAfterLock();
        applyScreenshotProtection();
        Toast.makeText(this, "已解锁", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        disposeFloatingOnDestroy();
        if (currentRef != null && currentRef.get() == this) currentRef.clear();
        liveRefs.removeIf(ref -> { if (ref == null) return true; SiteActivity a = ref.get(); return a == null || a == this; });
        SCREENSHOT_STATES.remove(Integer.toHexString(System.identityHashCode(this)));
        if (walletWeb != null) {
            walletWeb.loadUrl("about:blank");
            walletWeb.destroy();
            walletWeb = null;
        }
        if (mainBackground != null) {
            mainBackground.loadUrl("about:blank");
            mainBackground.destroy();
            mainBackground = null;
        }
        if (window4 != null) destroyWindow(window4);
        if (window3 != null) destroyWindow(window3);
        if (window2 != null) destroyWindow(window2);
        if (window1 != null) destroyWindow(window1);
        window1 = window2 = window3 = window4 = null;
        super.onDestroy();
    }

    private static class SiteWindow {
        FrameLayout card;
        LinearLayout inner;
        TextView label;
        WebView web;
        Button chip;
        Button maxButton;
        Button minButton;
        Button floatButton;
        boolean minimized;
        boolean maximized;
        String chainHint = "56";
        String walletWindowId = "unknown";
        boolean forceConnect;
        View resizeView;
        int preLeft, preTop, preWidth, preHeight;
    }

    private static class PendingWallet {
        final String siteId;
        final String method;
        final JavaScriptReplyProxy reply;
        final SiteWindow window;
        final String switchTo;
        PendingWallet(String siteId, String method, JavaScriptReplyProxy reply, SiteWindow window, String switchTo) {
            this.siteId = siteId;
            this.method = method;
            this.reply = reply;
            this.window = window;
            this.switchTo = switchTo;
        }
    }
}
