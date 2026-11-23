package com.deweb.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * DeWEB Desktop Android shell.
 *
 * Loads the public Web desktop and injects a wallet provider into the WebView's
 * cross-origin iframes. The wallet is still a MOCK in this build; this APK is
 * for validating the shell + injection path on a real phone.
 */
public class MainActivity extends Activity {
    private static final String TAG = "DeWebShell";
    private static final String UPDATE_PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEHyZJvwsbPcJRtKTTa28olCSnuU8cmuNr4ee2C4fiCDI4F0Z98GTMX4Sd/0cgiImE/M4IDr2MmCw73jfZZVLFdQ==";
    // Never trust "same as whatever is currently installed" alone: a fake
    // first-install baseline would otherwise accept fake updates.
    private static final String OFFICIAL_CERT_SHA256 = "dfbfdc03a95f478c7ebdee3573ceacea174acae13fcaae9bcebec67546d03ac0";
    private static final String OFFICIAL_MAIN_URL = BuildConfig.WEB_URL;
    private static final String MAIN_URL = OFFICIAL_MAIN_URL;

    /**
     * Exact origins and subdomain patterns for the six built-ins plus known
     * DeWEB gateways. Production should replace this with a user-approved,
     * per-origin list. Wildcards match subdomains of the named host.
     */
    private static final Set<String> CLOUD_ALLOWED_ORIGINS = new HashSet<>(Collections.singletonList(
            "https://app.dewebdesk.link"
    ));

    private static final Set<String> ALLOWED_ORIGINS = new HashSet<>(Arrays.asList(
            "https://app.dewebdesk.link",
            "https://dewebdesk.link",
            "https://*.tapekit.org",
            "https://tapeout.net",
            "https://*.tapeout.net",
            "https://*.tapeout.site",
            "https://*.tapeoutscan.io",
            "https://*.firsto.ai",
            "https://tapeout.link",
            "https://*.tapeout.link",
            "https://tapecode.ai",
            "https://*.tapecode.ai",
            "https://tapehub.ai",
            "https://*.tapehub.ai"
    ));

    private WebView webView;
    private BrandSplashView splashView;
    private boolean officialFallback;
    private static final String LEGAL_VERSION = "2026-11-18-v1";
    private volatile TapeVerifier.Result verifiedMain;
    private volatile CountDownLatch verifyLatch = new CountDownLatch(1);
    private FrameLayout appLockOverlay;
    private FrameLayout appShell;
    private View unverifiedOverlay;
    private static final int REQ_APP_UNLOCK = 9001;
    private static final int REQ_CLOUD_AUTH = 9002;
    private String pendingCloudAuthId;
    private String pendingCloudAuthMessage;
    private String pendingCloudAuthUrl;
    private boolean cloudAuthBusy;
    private volatile boolean selfTestIframeSeen;
    private volatile boolean selfTestIframeMainFrame;
    private volatile boolean selfTestBadMessageRejected;
    private volatile boolean selfTestNavLeak;
    private volatile boolean selfTestNavTested;
    private long lastProOpenAt;
    private TextView status;
    private WebFileChooser fileChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainRef = new WeakReference<>(this);
        fileChooser = new WebFileChooser(this);
        CrashLog.install(this);
        CrashLog.clearOnNewVersion(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(GlassUi.background(this));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackground(GlassUi.bar(this, false));
        topBar.setPadding(dp(12), 0, dp(8), 0);

        ImageView brandIcon = new ImageView(this);
        brandIcon.setImageResource(R.mipmap.ic_launcher);
        brandIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        iconLp.setMargins(0, 0, dp(8), 0);
        topBar.addView(brandIcon, iconLp);

        LinearLayout brandCol = new LinearLayout(this);
        brandCol.setOrientation(LinearLayout.VERTICAL);
        brandCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView brandWord = new TextView(this);
        android.text.SpannableString brandSp = new android.text.SpannableString("DeWEB DESK");
        brandSp.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(11, 13, 13)), 0, 5, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brandSp.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(255, 90, 45)), 6, 10, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brandWord.setText(brandSp);
        brandWord.setTextSize(10f);
        brandWord.setGravity(Gravity.CENTER_VERTICAL);
        brandWord.setTypeface(Typeface.DEFAULT_BOLD);
        brandWord.setLetterSpacing(-0.02f);
        brandCol.addView(brandWord, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextSize(8f);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextColor(Color.rgb(91, 127, 110));
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setLetterSpacing(0.06f);
        status.setText("正在验证主容器…");
        brandCol.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topBar.addView(brandCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView settings = new TextView(this);
        settings.setText("⚙ 设置");
        settings.setTextSize(12f);
        settings.setGravity(Gravity.CENTER);
        settings.setIncludeFontPadding(false);
        settings.setPadding(0, 0, 0, 0);
        settings.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        settings.setClickable(true);
        settings.setFocusable(true);
        settings.setTextColor(Color.rgb(21, 37, 29));
        GradientDrawable settingsBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2FFFFFF, 0xD9F4FFF9});
        settingsBg.setCornerRadius(dp(16));
        settingsBg.setStroke(dp(1), 0xCCFFFFFF);
        settings.setBackground(GlassUi.button(this, 16, false));
        settings.setElevation(dp(4));
        settings.setOnClickListener(v -> showWalletDialog());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(78), dp(38));
        settingsLp.setMargins(0, 0, dp(10), 0);
        topBar.addView(settings, settingsLp);

        GlassUi.hardenTree(topBar);
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        webView = new WebView(this);
        webView.setFilterTouchesWhenObscured(true);
        webView.setBackgroundColor(Color.TRANSPARENT);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
            return insets;
        });

        FrameLayout shell = new FrameLayout(this);
        appShell = shell;
        shell.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(shell);
        splashView = new BrandSplashView(this);
        shell.addView(splashView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (appLockRequested()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
            buildAppLock(shell);
        }
        SharedPreferences walletPrefs = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        if (!walletPrefs.getBoolean("wallet_auth_notice_v1", false)) {
            walletPrefs.edit().remove("quick_mode").putBoolean("wallet_auth_notice_v1", true).apply();
        }
        root.requestApplyInsets();
        try {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {}
        root.requestApplyInsets();
        if (legalAccepted()) {
            startAppAfterConsent();
        } else {
            showLegalConsentDialog();
        }
        root.post(() -> WebViewGuard.warnIfOld(this));
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
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
        webView.setWebChromeClient(fileChooser.chromeClient());
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);

        webView.setWebViewClient(new WebViewClient() {
@Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    Uri u = request.getUrl();
                    if (!request.isForMainFrame() || u == null) return null;
                    if (!"https".equalsIgnoreCase(u.getScheme())) return null;
                    if (!"app.dewebdesk.link".equalsIgnoreCase(u.getHost())) return null;
                    String path = u.getPath();
                    if (path != null && !path.isEmpty() && !"/".equals(path)) {
                        return localAssetResponse("path-locked.html", "path-blocked");
                    }
                    try { verifyLatch.await(20, TimeUnit.SECONDS); } catch (Throwable ignored) {}
                    TapeVerifier.Result vr = verifiedMain;
                    if (vr == null || !vr.ok || vr.bytes == null) {
                        officialFallback = true;
                        runOnUiThread(() -> showUnverifiedBanner());
                        // Never execute the unverified live page: it shares the
                        // official origin and can read localStorage/cloud tokens.
                        return localAssetResponse("fallback-locked.html", "locked");
                    }
                    final boolean wasFallback = officialFallback;
                    if (wasFallback) runOnUiThread(MainActivity.this::removeUnverifiedOverlay);
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "text/html; charset=utf-8");
                    headers.put("Cache-Control", "no-store");
                    headers.put("X-Tape-Verified", "1");
                    headers.put("X-Tape-Mode", "client");
                    headers.put("X-Tape-Sha256", vr.meta.optString("officialSha256", ""));
                    headers.put("X-Tape-Block", String.valueOf(vr.meta.optLong("block", 0)));
                    // frame-ancestors inside a <meta> CSP is ignored by browsers;
                    // an HTTP header makes it effective. Keep the same sources as
                    // the page meta so nothing breaks.
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
                            new ByteArrayInputStream(vr.bytes));
                } catch (Throwable t) {
                    return localAssetResponse("fallback-locked.html", "locked");
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                if (request.isForMainFrame() && "https".equals(scheme)
                        && "app.dewebdesk.link".equalsIgnoreCase(uri.getHost())) {
                    String p = uri.getPath();
                    if (p != null && !p.isEmpty() && !"/".equals(p)) {
                        view.loadUrl(OFFICIAL_MAIN_URL);
                        return true;
                    }
                }
                // Third-party http(s) main-frame links must not navigate inside
                // the verified DeWEB shell; open them as a site window instead.
                if (request.isForMainFrame() && ("http".equals(scheme) || "https".equals(scheme))) {
                    String host = uri.getHost();
                    if (host != null && !"app.dewebdesk.link".equalsIgnoreCase(host)) {
                        try {
                            if (SiteActivity.focusExistingSite(uri.toString())) return true;
                            Intent i = new Intent(MainActivity.this, SiteActivity.class);
                            i.putExtra("url", uri.toString());
                            i.putExtra("chain", "web");
                            startActivity(i);
                            return true;
                        } catch (Throwable ignored) {}
                    }
                }
                if (scheme.equals("http") || scheme.equals("https")) return false;
                if (!IntentGuard.open(MainActivity.this, uri)) {
                    Log.w(TAG, "blocked external scheme " + scheme);
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && !officialFallback) {
                    officialFallback = true;
                    status.setText("未验证预览 · 请关闭重启");
                    showUnverifiedBanner();
                    webView.loadUrl(OFFICIAL_MAIN_URL);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && !officialFallback && errorResponse != null && errorResponse.getStatusCode() >= 500) {
                    officialFallback = true;
                    status.setText("未验证预览 · 请关闭重启");
                    showUnverifiedBanner();
                    webView.loadUrl(OFFICIAL_MAIN_URL);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!url.startsWith("about:")) {
                    status.setText("DeWEB Desktop · " + Uri.parse(url).getHost());
                    hideSplash();
                    injectLocalVerify();
                    if (officialFallback) showUnverifiedBanner();
                }
            }
        });
    }

    private void hideSplash() {
        if (splashView == null) return;
        splashView.setStatus("CONTAINER READY");
        splashView.postDelayed(() -> {
            if (splashView != null) splashView.dismiss();
        }, 260);
    }

    private void loadDesktop() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            status.setText("WebView does not support WEB_MESSAGE_LISTENER");
            webView.loadUrl(MAIN_URL);
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            status.setText("WebView does not support DOCUMENT_START_SCRIPT");
            webView.loadUrl(MAIN_URL);
            return;
        }

        String bridgeJs = readAsset("bridge.js");
        WebViewCompat.addDocumentStartJavaScript(webView, readAsset("page-fix.js"), ALLOWED_ORIGINS);
        WebViewCompat.addDocumentStartJavaScript(webView, readAsset("verify-badge.js"), ALLOWED_ORIGINS);
        WebViewCompat.addDocumentStartJavaScript(webView, readAsset("pro-bridge.js"), ALLOWED_ORIGINS);
        WebViewCompat.addDocumentStartJavaScript(webView, bridgeJs, ALLOWED_ORIGINS);
        WebViewCompat.addDocumentStartJavaScript(webView, readAsset("cloud-auth-bridge.js"), CLOUD_ALLOWED_ORIGINS);
        WebViewCompat.addWebMessageListener(
                webView,
                "dewebCloudAuthNative",
                CLOUD_ALLOWED_ORIGINS,
                (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                        onCloudAuthMessage(message, sourceOrigin, isMainFrame)
        );
        WebViewCompat.addWebMessageListener(
                webView,
                "dewebBridge",
                ALLOWED_ORIGINS,
                (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                        onBridgeMessage(message, sourceOrigin, isMainFrame, replyProxy)
        );
        webView.loadUrl(MAIN_URL);
        webView.post(this::showLastCrash);
    }

    private boolean legalAccepted() {
        return LEGAL_VERSION.equals(getSharedPreferences("deweb_shell", MODE_PRIVATE)
                .getString("legal_accepted_version", ""));
    }

    private void startAppAfterConsent() {
        startMainVerification();
        configureWebView();
        loadDesktop();
        MembershipManager.refresh(this, (ok, message, expiry) -> {});
        if (appShell != null) appShell.postDelayed(this::maybeAutoCheckUpdates, 3000);
    }

    private void showLegalConsentDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(10));
        box.setBackgroundColor(0xFFF4FBF7);
        TextView intro = new TextView(this);
        intro.setText("使用 DeWEB Desktop 前，请阅读并同意以下文件。DeWEB 是非托管网页容器，不保管资产，不控制第三方网站或链上内容。");
        intro.setTextSize(12f);
        intro.setTextColor(Color.rgb(21, 37, 29));
        box.addView(intro);

        String[] docs = {"用户协议", "可接受使用政策", "隐私政策"};
        for (int i = 0; i < docs.length; i++) {
            final int which = i;
            Button b = new Button(this);
            b.setText(docs[i]);
            b.setAllCaps(false);
            b.setTextSize(12f);
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v -> showLegalDoc(which));
            box.addView(b);
        }
        CheckBox agree = new CheckBox(this);
        agree.setText("我已阅读并同意《用户协议》《可接受使用政策》《隐私政策》");
        agree.setTextSize(12f);
        agree.setTextColor(Color.rgb(21, 37, 29));
        agree.setPadding(dp(2), dp(6), dp(2), dp(6));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF4FBF7);
        scroll.addView(box);
        LinearLayout legalRoot = new LinearLayout(this);
        legalRoot.setBackgroundColor(0xFFF4FBF7);
        legalRoot.setOrientation(LinearLayout.VERTICAL);
        int legalScrollH = Math.min(dp(280), (int) (getResources().getDisplayMetrics().heightPixels * 0.39f));
        legalRoot.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, legalScrollH));
        LinearLayout legalFooter = new LinearLayout(this);
        legalFooter.setOrientation(LinearLayout.VERTICAL);
        legalFooter.setPadding(dp(18), dp(2), dp(18), dp(4));
        legalFooter.setBackgroundColor(0xFFF4FBF7);
        legalFooter.addView(agree, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        legalRoot.addView(legalFooter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        GlassUi.hardenTree(legalRoot);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("使用前请阅读")
                .setView(legalRoot)
                .setNegativeButton("退出", (d, w) -> finish())
                .setPositiveButton("同意并继续", null)
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            agree.setOnCheckedChangeListener((b, checked) -> positive.setEnabled(checked));
            positive.setOnClickListener(v -> {
                if (!agree.isChecked()) return;
                getSharedPreferences("deweb_shell", MODE_PRIVATE).edit()
                        .putString("legal_accepted_version", LEGAL_VERSION).apply();
                dialog.dismiss();
                startAppAfterConsent();
            });
        });
        dialog.show();
        GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || appLockRequested());
    }

    private String legalDocText(int which) {
        if (which == 0) {
            return "生效日期：2026-11-18\n版本：2026-11-18-v1\n\n"
                    + "1. 服务说明\nDeWEB Desktop 是本地网页容器/浏览器工具，用于访问第三方网站和链上容器。应用本身不提供交易撮合、资产托管或投资建议。\n\n"
                    + "2. 非托管\n应用不保管私钥、助记词或资产。所有签名、转账和授权由用户通过 OKX 钱包等第三方钱包确认。链上交易不可撤销。\n\n"
                    + "3. 用户资格与合规\n用户应具备完全民事行为能力，并遵守所在地法律。禁止将应用用于违法活动。\n\n"
                    + "4. 云同步与 Pro\n云同步和 Pro 为可选服务。Alpha 测试期间 Pro 免费开放，有效期至 2026-12-31，不构成永久权益。测试期后，Pro 可通过持有符合条件的 DeWEB 电路 NFT 获得；具体条件以应用内说明为准。官方云服务可因违规、滥用或安全原因暂停或终止。\n\n"
                    + "5. 开源\n源代码可能按开源许可证发布；开源许可证与官方服务条款分别适用。第三方修改或部署不受本协议控制。\n\n"
                    + "6. 第三方内容\n应用不控制第三方网站、链上内容、钱包或网关。用户应自行判断并承担使用风险。\n\n"
                    + "7. 知识产权\n应用名称、品牌和界面等归开发者或权利人所有；第三方内容归其权利人。\n\n"
                    + "8. 服务变更与终止\n应用可按现状变更或停止；维护、网络故障和链上拥堵可能导致服务中断。\n\n"
                    + "9. 免责与责任限制\n在法律允许范围内，开发者不对间接损失、数据丢失、链上交易或第三方行为负责。本条不排除依法不能排除的责任。\n\n"
                    + "10. 协议更新\n协议更新后会在应用内提示；继续使用视为接受。不同意应停止使用。\n\n"
                    + "11. 适用法律与争议\n适用法律、管辖和运营主体以正式发布主体所在地为准，发布前应由律师审阅。";
        }
        if (which == 1) {
            return "生效日期：2026-11-18\n版本：2026-11-18-v1\n\n"
                    + "使用 DeWEB Desktop 时，禁止从事下列行为：\n\n"
                    + "1. 诈骗、钓鱼、仿冒、虚假宣传或未经授权转移他人资产。\n"
                    + "2. 洗钱、恐怖融资、制裁规避或其他违反金融监管规定的活动。\n"
                    + "3. 未经授权访问系统、传播恶意软件、发起网络攻击或侵犯他人隐私。\n"
                    + "4. 传播儿童性虐待材料、暴力、恐怖主义或其它违法内容。\n"
                    + "5. 侵犯知识产权、商业秘密或其他合法权益。\n"
                    + "6. 赌博、非法集资、传销或其他所在地法律禁止的活动。\n"
                    + "7. 规避应用安全机制、批量滥用云服务或干扰其他用户。\n\n"
                    + "违规处理：官方云同步、Pro 或相关服务可被暂停或终止；涉嫌违法时，开发者可在法律要求范围内配合主管机关。开源代码和第三方部署不由本政策控制。";
        }
        return "生效日期：2026-11-18\n版本：2026-11-18-v1\n\n"
                + "1. 我们处理的数据\n钱包地址、云同步的站点/文件夹/桌面位置/壁纸、必要的设备和日志信息。应用不收集私钥、助记词或钱包密码。\n\n"
                + "2. 本地优先\n站点、布局和壁纸优先保存在本机。只有用户主动开启云同步并登录后，相关数据才上传。\n\n"
                + "3. 云同步数据\n站点、布局和壁纸由用户在浏览器端使用云同步密码进行端到端加密后上传；服务端仅保存密文，无法读取内容。服务端不保存云同步密码。请勿在备注中填写敏感信息。\n\n"
                + "4. 用途\n提供云同步、Pro 状态校验、故障排查和安全防护，不用于出售用户数据。\n\n"
                + "5. 共享\n为提供云服务，数据会由 Cloudflare 基础设施处理；钱包交互由 OKX 钱包处理。除法律要求或安全必要外，不向无关第三方披露。\n\n"
                + "6. 保留与删除\n云同步数据保留至用户删除或服务终止；用户可在应用内断开云同步或联系运营方请求删除。\n\n"
                + "7. 安全\n采用 HTTPS、精确来源校验、会话签名和访问控制；但任何系统都无法保证绝对安全。\n\n"
                + "8. 儿童\n本应用不面向儿童；未成年人应在监护人指导下使用。\n\n"
                + "9. 权利\n用户可请求访问、更正或删除其云同步数据；具体方式和运营主体以正式发布信息为准。\n\n"
                + "10. 更新\n隐私政策更新后会在应用内提示。";
    }

    private String disclaimerText() {
        return "生效日期：2026-11-17\n\n"
                + "1. DeWEB Desktop 是非托管网页容器/工具，不保管私钥、助记词或资产，不控制第三方网站、链上内容或用户钱包。\n\n"
                + "2. 用户应遵守所在司法辖区法律，不得利用本应用从事诈骗、洗钱、制裁规避、赌博、传播恶意软件、侵犯他人权利、传播违法内容等违法活动。\n\n"
                + "3. 用户对访问或发布的内容、钱包签名、链上交易及其后果自行负责；链上交易不可撤销。\n\n"
                + "4. 官方云同步/Pro 服务可对违规账号暂停或终止；开源代码及第三方部署不受本声明控制。\n\n"
                + "5. 本应用按“现状”提供，不承诺无错误或不中断；在法律允许范围内，开发者不对因使用或无法使用本应用产生的间接损失承担责任。\n\n"
                + "6. 本声明不构成法律意见；如有疑问，请咨询律师并遵守当地法律。";
    }

    private void showLegalIndex() {
        String text = "【用户协议】\n\n" + legalDocText(0)
                + "\n\n────────────────\n\n【可接受使用政策】\n\n" + legalDocText(1)
                + "\n\n────────────────\n\n【隐私政策】\n\n" + legalDocText(2)
                + "\n\n────────────────\n\n【免责声明与使用条款】\n\n" + disclaimerText();
        showHardenedDialog(new AlertDialog.Builder(this)
                .setTitle("用户协议 / 使用政策 / 隐私政策 / 免责声明")
                .setMessage(text)
                .setPositiveButton("关闭", null));
    }

    private void showLegalDoc(int which) {
        String title;
        String text;
        if (which == 0) {
            title = "用户协议";
            text = "生效日期：2026-11-18\n版本：2026-11-18-v1\n\n"
                    + "1. 服务说明\nDeWEB Desktop 是本地网页容器/浏览器工具，用于访问第三方网站和链上容器。应用本身不提供交易撮合、资产托管或投资建议。\n\n"
                    + "2. 非托管\n应用不保管私钥、助记词或资产。所有签名、转账和授权由用户通过 OKX 钱包等第三方钱包确认。链上交易不可撤销。\n\n"
                    + "3. 用户资格与合规\n用户应具备完全民事行为能力，并遵守所在地法律。禁止将应用用于违法活动。\n\n"
                    + "4. 云同步与 Pro\n云同步和 Pro 为可选服务。Alpha 测试期间 Pro 免费开放，有效期至 2026-12-31，不构成永久权益。测试期后，Pro 可通过持有符合条件的 DeWEB 电路 NFT 获得；具体条件以应用内说明为准。官方云服务可因违规、滥用或安全原因暂停或终止。\n\n"
                    + "5. 开源\n源代码可能按开源许可证发布；开源许可证与官方服务条款分别适用。第三方修改或部署不受本协议控制。\n\n"
                    + "6. 第三方内容\n应用不控制第三方网站、链上内容、钱包或网关。用户应自行判断并承担使用风险。\n\n"
                    + "7. 知识产权\n应用名称、品牌和界面等归开发者或权利人所有；第三方内容归其权利人。\n\n"
                    + "8. 服务变更与终止\n应用可按现状变更或停止；维护、网络故障和链上拥堵可能导致服务中断。\n\n"
                    + "9. 免责与责任限制\n在法律允许范围内，开发者不对间接损失、数据丢失、链上交易或第三方行为负责。本条不排除依法不能排除的责任。\n\n"
                    + "10. 协议更新\n协议更新后会在应用内提示；继续使用视为接受。不同意应停止使用。\n\n"
                    + "11. 适用法律与争议\n适用法律、管辖和运营主体以正式发布主体所在地为准，发布前应由律师审阅。";
        } else if (which == 1) {
            title = "可接受使用政策";
            text = "生效日期：2026-11-18\n版本：2026-11-18-v1\n\n"
                    + "使用 DeWEB Desktop 时，禁止从事下列行为：\n\n"
                    + "1. 诈骗、钓鱼、仿冒、虚假宣传或未经授权转移他人资产。\n"
                    + "2. 洗钱、恐怖融资、制裁规避或其他违反金融监管规定的活动。\n"
                    + "3. 未经授权访问系统、传播恶意软件、发起网络攻击或侵犯他人隐私。\n"
                    + "4. 传播儿童性虐待材料、暴力、恐怖主义或其它违法内容。\n"
                    + "5. 侵犯知识产权、商业秘密或其他合法权益。\n"
                    + "6. 赌博、非法集资、传销或其他所在地法律禁止的活动。\n"
                    + "7. 规避应用安全机制、批量滥用云服务或干扰其他用户。\n\n"
                    + "违规处理：官方云同步、Pro 或相关服务可被暂停或终止；涉嫌违法时，开发者可在法律要求范围内配合主管机关。开源代码和第三方部署不由本政策控制。";
        } else {
            title = "隐私政策";
            text = "生效日期：2026-11-18\n版本：2026-11-18-v1\n\n"
                    + "1. 我们处理的数据\n钱包地址、云同步的站点/文件夹/桌面位置/壁纸、必要的设备和日志信息。应用不收集私钥、助记词或钱包密码。\n\n"
                    + "2. 本地优先\n站点、布局和壁纸优先保存在本机。只有用户主动开启云同步并登录后，相关数据才上传。\n\n"
                    + "3. 云同步数据\n站点、布局和壁纸由用户在浏览器端使用云同步密码进行端到端加密后上传；服务端仅保存密文，无法读取内容。服务端不保存云同步密码。请勿在备注中填写敏感信息。\n\n"
                    + "4. 用途\n提供云同步、Pro 状态校验、故障排查和安全防护，不用于出售用户数据。\n\n"
                    + "5. 共享\n为提供云服务，数据会由 Cloudflare 基础设施处理；钱包交互由 OKX 钱包处理。除法律要求或安全必要外，不向无关第三方披露。\n\n"
                    + "6. 保留与删除\n云同步数据保留至用户删除或服务终止；用户可在应用内断开云同步或联系运营方请求删除。\n\n"
                    + "7. 安全\n采用 HTTPS、精确来源校验、会话签名和访问控制；但任何系统都无法保证绝对安全。\n\n"
                    + "8. 儿童\n本应用不面向儿童；未成年人应在监护人指导下使用。\n\n"
                    + "9. 权利\n用户可请求访问、更正或删除其云同步数据；具体方式和运营主体以正式发布信息为准。\n\n"
                    + "10. 更新\n隐私政策更新后会在应用内提示。";
        }
        showHardenedDialog(new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton("关闭", null));
    }

    private void showWalletDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        GlassUi.blurDialog(dialog);
        dialog.setOnDismissListener(d -> {
            try {
                SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
                if (sp.getBoolean("return_to_site_after_settings", false)) {
                    sp.edit().remove("return_to_site_after_settings").apply();
                    Intent i = new Intent(MainActivity.this, SiteActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(i);
                }
            } catch (Throwable ignored) {}
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFAFFFFFF, 0xE6F4FFF9, 0xF7FFFFFF});
        panelBg.setCornerRadius(dp(24));
        panelBg.setStroke(dp(1), 0xCCFFFFFF);
        panel.setBackground(GlassUi.panel(this, 24, false));
        panel.setClipToOutline(true);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));

        FrameLayout headRow = new FrameLayout(this);
        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(21, 37, 29));
        headRow.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL));
        TextView closeX = new TextView(this);
        closeX.setText("\u2715");
        closeX.setTextSize(22f);
        closeX.setGravity(Gravity.CENTER);
        closeX.setTextColor(Color.rgb(21, 37, 29));
        closeX.setContentDescription("关闭");
        closeX.setClickable(true);
        closeX.setFocusable(true);
        closeX.setPadding(dp(8), 0, dp(8), 0);
        closeX.setOnClickListener(v -> dialog.dismiss());
        headRow.addView(closeX, new FrameLayout.LayoutParams(
                dp(40), dp(40), Gravity.END | Gravity.CENTER_VERTICAL));
        panel.addView(headRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("钱包、悬浮窗、隐私与站点授权");
        sub.setTextSize(12f);
        sub.setTextColor(Color.rgb(60, 91, 75));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(14);
        panel.addView(sub, subLp);

        android.widget.Switch privacy = new android.widget.Switch(this);
        privacy.setText("禁止截屏和最近任务预览");
        privacy.setTextSize(12f);
        privacy.setTextColor(Color.rgb(21, 37, 29));
        privacy.setChecked(PrivacyPrefs.isEnabled(this));
        privacy.setFilterTouchesWhenObscured(true);
        privacy.setOnCheckedChangeListener((buttonView, checked) -> {
            PrivacyPrefs.setEnabled(this, checked);
            applyScreenshotProtection();
            SiteActivity.notifyPrivacyChanged();
            Toast.makeText(this, checked ? "已开启截屏保护" : "已关闭截屏保护", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyLp.bottomMargin = dp(12);
        panel.addView(privacy, privacyLp);

        android.widget.Switch floating = new android.widget.Switch(this);
        floating.setText("启用悬浮窗");
        floating.setTextSize(12f);
        floating.setTextColor(Color.rgb(21, 37, 29));
        floating.setChecked(SiteActivity.isFloatingEnabled(this));
        floating.setOnCheckedChangeListener((buttonView, checked) -> {
            getSharedPreferences("deweb_shell", MODE_PRIVATE).edit()
                    .putBoolean("floating_enabled", checked).apply();
            if (!checked) SiteActivity.disableFloating();
            Toast.makeText(this, checked ? "已启用悬浮窗" : "已关闭悬浮窗", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams floatingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        floatingLp.bottomMargin = dp(12);
        panel.addView(floating, floatingLp);

        Button disconnect = new Button(this);
        disconnect.setText("断开所有钱包连接");
        disconnect.setTextSize(13f);
        disconnect.setAllCaps(false);
        disconnect.setTextColor(Color.rgb(150, 35, 48));
        GradientDrawable disconnectBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCCFFF2F2, 0x99FFE5E5});
        disconnectBg.setCornerRadius(dp(15));
        disconnectBg.setStroke(dp(1), 0x99E8A0A8);
        disconnect.setBackground(disconnectBg);
        disconnect.setOnClickListener(v -> {
            disconnectAllWallets(true);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams disconnectLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        disconnectLp.topMargin = dp(2);
        disconnectLp.bottomMargin = dp(8);
        panel.addView(disconnect, disconnectLp);

        Button sites = new Button(this);
        sites.setText("查看已授权站点");
        sites.setTextSize(13f);
        sites.setAllCaps(false);
        sites.setTextColor(Color.rgb(21, 37, 29));
        GradientDrawable sitesBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE6FFFFFF, 0xB3F4FFF9});
        sitesBg.setCornerRadius(dp(15));
        sitesBg.setStroke(dp(1), 0x99FFFFFF);
        sites.setBackground(sitesBg);
        sites.setOnClickListener(v -> {
            dialog.dismiss();
            showApprovedSites();
        });
        LinearLayout.LayoutParams sitesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        sitesLp.bottomMargin = dp(8);
        panel.addView(sites, sitesLp);

        if (BuildConfig.DEBUG) {
            Button selfTest = new Button(this);
            selfTest.setText("安全自测（Debug）");
            selfTest.setTextSize(13f);
            selfTest.setAllCaps(false);
            selfTest.setTextColor(Color.rgb(21, 37, 29));
            GradientDrawable testBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xE6FFFFFF, 0xB3F4FFF9});
            testBg.setCornerRadius(dp(15));
            testBg.setStroke(dp(1), 0x99FFFFFF);
            selfTest.setBackground(testBg);
            selfTest.setOnClickListener(v -> {
                dialog.dismiss();
                runSecuritySelfTest();
            });
            LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            testLp.bottomMargin = dp(8);
            panel.addView(selfTest, testLp);
        }

        if (BuildConfig.IS_BETA) {
            Button diag = new Button(this);
            diag.setText("截屏/应用锁诊断（复制）");
            diag.setTextSize(12f);
            diag.setAllCaps(false);
            diag.setTextColor(Color.rgb(21, 37, 29));
            GradientDrawable diagBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xE6FFFFFF, 0xB3F4FFF9});
            diagBg.setCornerRadius(dp(15));
            diagBg.setStroke(dp(1), 0x99FFFFFF);
            diag.setBackground(diagBg);
            diag.setOnClickListener(v -> {
                String state = screenshotDiagnosticsText();
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("DeWEB diag", state));
                    Toast.makeText(this, "诊断信息已复制，请粘贴发回", Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    Toast.makeText(this, "复制失败：" + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                }
            });
            LinearLayout.LayoutParams diagLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            diagLp.bottomMargin = dp(8);
            panel.addView(diag, diagLp);
        }

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setTextSize(13f);
        cancel.setAllCaps(false);
        cancel.setTextColor(Color.rgb(21, 37, 29));
        GradientDrawable cancelBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99FFFFFF, 0x66F4FFF9});
        cancelBg.setCornerRadius(dp(15));
        cancelBg.setStroke(dp(1), 0x99FFFFFF);
        cancel.setBackground(cancelBg);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        cancelLp.topMargin = dp(4);
        panel.addView(cancel, cancelLp);

        TextView memberText = new TextView(this);
        memberText.setText(MembershipManager.summary(this));
        memberText.setTextSize(11f);
        memberText.setTypeface(Typeface.MONOSPACE);
        memberText.setTextColor(Color.rgb(35, 75, 58));
        LinearLayout.LayoutParams memberLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        memberLp.topMargin = dp(8);
        memberLp.bottomMargin = dp(6);
        panel.addView(memberText, memberLp);
        MembershipManager.refresh(this, (ok, message, expiry) -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) memberText.setText(MembershipManager.summary(this));
        }));

        Button memberRefresh = new Button(this);
        memberRefresh.setText("刷新会员状态");
        memberRefresh.setTextSize(12f);
        memberRefresh.setAllCaps(false);
        memberRefresh.setTextColor(Color.rgb(21, 37, 29));
        GradientDrawable memberBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99FFFFFF, 0x66F4FFF9});
        memberBg.setCornerRadius(dp(14));
        memberBg.setStroke(dp(1), 0x99FFFFFF);
        memberRefresh.setBackground(memberBg);
        memberRefresh.setOnClickListener(v -> {
            memberText.setText("正在读取链上会员...");
            MembershipManager.refresh(this, (ok, message, expiry) -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                memberText.setText(MembershipManager.summary(this));
                Toast.makeText(this, ok ? message : "会员读取失败：" + message, Toast.LENGTH_SHORT).show();
            }));
        });
        panel.addView(memberRefresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        View divider = new View(this);
        divider.setBackgroundColor(0x33000000);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.topMargin = dp(10);
        dividerLp.bottomMargin = dp(10);
        panel.addView(divider, dividerLp);

        String versionName = "?";
        int versionCode = 0;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
        } catch (Throwable ignored) {}
        TextView versionText = new TextView(this);
        versionText.setText("版本 " + versionName + " (" + versionCode + ")");
        versionText.setTextSize(11f);
        versionText.setTypeface(Typeface.MONOSPACE);
        versionText.setTextColor(Color.rgb(60, 91, 75));
        panel.addView(versionText);

        android.widget.Switch autoUpdate = new android.widget.Switch(this);
        autoUpdate.setText("自动检查更新");
        autoUpdate.setTextSize(12f);
        autoUpdate.setTextColor(Color.rgb(21, 37, 29));
        autoUpdate.setChecked(getSharedPreferences("deweb_shell", MODE_PRIVATE).getBoolean("auto_update_check", true));
        autoUpdate.setOnCheckedChangeListener((buttonView, checked) ->
                getSharedPreferences("deweb_shell", MODE_PRIVATE).edit().putBoolean("auto_update_check", checked).apply());
        panel.addView(autoUpdate);

        Button checkUpdate = new Button(this);
        checkUpdate.setText("检查版本更新");
        checkUpdate.setTextSize(12f);
        checkUpdate.setAllCaps(false);
        checkUpdate.setTextColor(Color.rgb(21, 37, 29));
        GradientDrawable checkBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99FFFFFF, 0x66F4FFF9});
        checkBg.setCornerRadius(dp(14));
        checkBg.setStroke(dp(1), 0x99FFFFFF);
        checkUpdate.setBackground(checkBg);
        checkUpdate.setOnClickListener(v -> checkUpdates(true, versionText));
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        checkLp.topMargin = dp(6);
        panel.addView(checkUpdate, checkLp);

        if (BuildConfig.IS_BETA) {
            Button bgNavTest = new Button(this);
            bgNavTest.setText("后台导航测试（Beta）");
            bgNavTest.setTextSize(12f);
            bgNavTest.setAllCaps(false);
            bgNavTest.setTextColor(Color.rgb(21, 37, 29));
            GradientDrawable bgTestBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x99FFFFFF, 0x66F4FFF9});
            bgTestBg.setCornerRadius(dp(14));
            bgTestBg.setStroke(dp(1), 0x99FFFFFF);
            bgNavTest.setBackground(bgTestBg);
            bgNavTest.setOnClickListener(v -> SiteActivity.testBackgroundNavigation(msg ->
                    runOnUiThread(() -> showHardenedDialog(new AlertDialog.Builder(this)
                            .setTitle("后台导航测试")
                            .setMessage(msg)
                            .setPositiveButton("知道了", null)))));
            LinearLayout.LayoutParams bgTestLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            bgTestLp.topMargin = dp(6);
            panel.addView(bgNavTest, bgTestLp);
        }

        Button legalCombined = new Button(this);
        legalCombined.setText("用户协议 / 使用政策 / 隐私政策 / 免责声明");
        legalCombined.setTextSize(12f);
        legalCombined.setAllCaps(false);
        legalCombined.setTextColor(Color.rgb(21, 37, 29));
        GradientDrawable legalCombinedBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE6FFFFFF, 0xB3F4FFF9});
        legalCombinedBg.setCornerRadius(dp(14));
        legalCombinedBg.setStroke(dp(1), 0x99FFFFFF);
        legalCombined.setBackground(legalCombinedBg);
        legalCombined.setOnClickListener(v -> showLegalIndex());
        LinearLayout.LayoutParams legalCombinedLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        legalCombinedLp.topMargin = dp(6);
        legalCombinedLp.bottomMargin = dp(8);
        panel.addView(legalCombined, legalCombinedLp);

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(false);
        settingsScroll.addView(panel);
        GlassUi.hardenTree(settingsScroll);
        dialog.setContentView(settingsScroll);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.28f);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.9f);
            lp.height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.84f);
            lp.gravity = Gravity.CENTER;
            window.setAttributes(lp);
        }

        dialog.show();
        GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || appLockRequested());
    }

    private void showDisclaimer() {
        String text = "生效日期：2026-11-17\n\n"
                + "1. DeWEB Desktop 是非托管网页容器/工具，不保管私钥、助记词或资产，不控制第三方网站、链上内容或用户钱包。\n\n"
                + "2. 用户应遵守所在司法辖区法律，不得利用本应用从事诈骗、洗钱、制裁规避、赌博、传播恶意软件、侵犯他人权利、传播违法内容等违法活动。\n\n"
                + "3. 用户对访问或发布的内容、钱包签名、链上交易及其后果自行负责；链上交易不可撤销。\n\n"
                + "4. 官方云同步/Pro 服务可对违规账号暂停或终止；开源代码及第三方部署不受本声明控制。\n\n"
                + "5. 本应用按“现状”提供，不承诺无错误或不中断；在法律允许范围内，开发者不对因使用或无法使用本应用产生的间接损失承担责任。\n\n"
                + "6. 本声明不构成法律意见；如有疑问，请咨询律师并遵守当地法律。";
        new AlertDialog.Builder(this)
                .setTitle("免责声明与使用条款")
                .setMessage(text)
                .setPositiveButton("我已阅读", null)
                .show();
    }

    private void showApprovedSites() {
        SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        Set<String> origins = new HashSet<>();
        long now = System.currentTimeMillis();
        try {
            JSONObject expiry = new JSONObject(sp.getString("native_approved_expiry", "{}"));
            for (String origin : sp.getStringSet("native_approved_origins", new HashSet<>())) {
                if (expiry.optLong(origin, 0L) > now) origins.add(origin);
            }
        } catch (Throwable ignored) {}
        if (origins.isEmpty()) {
            Toast.makeText(this, "暂无已授权站点", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = origins.toArray(new String[0]);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        GlassUi.blurDialog(dialog);
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

        TextView title = new TextView(this);
        title.setText("已授权站点");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFEAF7EF);
        panel.addView(title);

        TextView sub = new TextView(this);
        sub.setText("点击站点可单独撤销授权");
        sub.setTextSize(11f);
        sub.setTextColor(0x99C9DED2);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(12);
        panel.addView(sub, subLp);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (String origin : items) {
            Button row = new Button(this);
            row.setText(origin);
            row.setTextSize(10.5f);
            row.setAllCaps(false);
            row.setTextColor(0xFFE6F2EA);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            GradientDrawable rowBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x33FFFFFF, 0x14FFFFFF});
            rowBg.setCornerRadius(dp(14));
            rowBg.setStroke(dp(1), 0x33FFFFFF);
            row.setBackground(rowBg);
            row.setOnClickListener(v -> {
                Set<String> next = new HashSet<>(sp.getStringSet("native_approved_origins", new HashSet<>()));
                next.remove(origin);
                JSONObject expiry;
                try { expiry = new JSONObject(sp.getString("native_approved_expiry", "{}")); } catch (Throwable t) { expiry = new JSONObject(); }
                expiry.remove(origin);
                sp.edit().putStringSet("native_approved_origins", next).putString("native_approved_expiry", expiry.toString()).apply();
                SiteActivity.revokeNativeApproval(this, origin);
                Toast.makeText(this, "已撤销：" + origin, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            rowLp.bottomMargin = dp(7);
            list.addView(row, rowLp);
        }

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.addView(list);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.60f);
        int listHeight = Math.min(items.length * dp(51), maxHeight);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight);
        scrollLp.bottomMargin = dp(6);
        panel.addView(scroller, scrollLp);

        Button close = new Button(this);
        close.setText("关闭");
        close.setTextSize(12f);
        close.setAllCaps(false);
        close.setTextColor(0xFF03150D);
        GradientDrawable closeBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF8AF2C8, 0xFF56E6B0});
        closeBg.setCornerRadius(dp(14));
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        closeLp.topMargin = dp(8);
        panel.addView(close, closeLp);

        GlassUi.hardenTree(panel);
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
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
        GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || appLockRequested());
    }

    private static final String CLOUD_API = "https://api.dewebdesk.link";
    private static WeakReference<MainActivity> mainRef = new WeakReference<>(null);

    private void clearCloudSessionCookies() {
        clearCloudSessionCookies(() -> {
            try { if (webView != null) webView.loadUrl(OFFICIAL_MAIN_URL); } catch (Throwable ignored) {}
        });
    }

    private static void expireCloudCookieValues() {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setCookie(CLOUD_API, "__Host-deweb_session=; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=0");
            cm.setCookie(CLOUD_API, "deweb_session=; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=0");
            try { cm.flush(); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static boolean cloudCookieStillPresent() {
        try {
            String c = CookieManager.getInstance().getCookie(CLOUD_API);
            return c != null && c.contains("__Host-deweb_session=");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Expire and verify; safe on a background thread (up to ~240ms). */
    private static void expireCloudCookiesAndVerify() {
        for (int i = 0; i < 3; i++) {
            expireCloudCookieValues();
            try { Thread.sleep(80); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            if (!cloudCookieStillPresent()) return;
        }
        expireCloudCookieValues();
    }

    /** Drops the cloud cookies without server calls or a page reload. */
    private static void expireCloudCookiesNow() {
        expireCloudCookieValues();
        try {
            new Thread(MainActivity::expireCloudCookiesAndVerify, "cloud-cookie-verify").start();
        } catch (Throwable ignored) {}
    }

    /** Best-effort server logout with the cookie captured before local expiry. */
    private static void revokeCloudSessionAsync(String cookie) {
        if (cookie == null || cookie.isEmpty()) return;
        try {
            new Thread(() -> {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(CLOUD_API + "/api/auth/logout").openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("Cookie", cookie);
                    c.getOutputStream().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    c.getResponseCode();
                    c.disconnect();
                } catch (Throwable ignored) {}
            }, "cloud-logout-native").start();
        } catch (Throwable ignored) {}
    }

    /** Clears the cloud cookie and best-effort revokes the server token. */
    private static void clearCloudSessionCookies(Runnable after, Runnable failed) {
        new Thread(() -> {
            String cookie = null;
            try { cookie = CookieManager.getInstance().getCookie(CLOUD_API); } catch (Throwable ignored) {}
            boolean localSessionMayExist = cookie != null && !cookie.isEmpty();
            boolean revoked = !localSessionMayExist;
            for (int attempt = 0; localSessionMayExist && attempt < 3 && !revoked; attempt++) {
                boolean retry = false;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(CLOUD_API + "/api/auth/logout").openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("Cookie", cookie);
                    c.getOutputStream().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    int code = c.getResponseCode();
                    c.disconnect();
                    if (code >= 200 && code < 300) revoked = true;
                    else if (code == 401) {
                        // No live session left (often the page already logged out).
                        // Retrying this only adds the 600/1200/1800ms sleeps.
                        revoked = true;
                    } else if (code >= 500) {
                        retry = true;
                    }
                } catch (Throwable t) {
                    retry = true;
                }
                if (!revoked && retry && attempt < 2) {
                    try { Thread.sleep(600L * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                if (!retry) break;
            }
            // Server-side revoke is best effort. Do not leave the user visibly
            // "logged out" locally while the cookie remains; always clear local
            // session, and report a failed server revoke to the caller.
            if (!revoked && failed != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(failed);
            // Expire + read back before reloading the page; otherwise a slow
            // CookieManager could restore the session on the reload.
            expireCloudCookiesAndVerify();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    try { SiteActivity.notifyCloudDisconnected(); } catch (Throwable ignored) {}
                    if (after != null) after.run();
                } catch (Throwable ignored) { if (after != null) after.run(); }
            });
        }, "deweb-cloud-clear").start();
    }

    private static void clearCloudSessionCookies(Runnable after) { clearCloudSessionCookies(after, null); }

    /** If the native cloud cookie belongs to a different wallet, invalidate it. */
    public static void enforceCloudSessionForWallet(String wallet, Runnable onMismatch) {
        if (wallet == null || wallet.isEmpty()) return;
        final String wanted = wallet.toLowerCase();
        new Thread(() -> {
            String cookie = null;
            try { cookie = CookieManager.getInstance().getCookie(CLOUD_API); } catch (Throwable ignored) {}
            if (cookie == null || cookie.isEmpty()) return;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(CLOUD_API + "/api/auth/session").openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestMethod("GET");
                c.setRequestProperty("Cookie", cookie);
                int code = c.getResponseCode();
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[2048]; int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close(); c.disconnect();
                if (code != 200) return;
                String body = bos.toString("UTF-8");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"address\":\"([^\"]+)\"").matcher(body);
                if (!m.find()) return;
                String session = m.group(1).toLowerCase();
                if (session.equals(wanted)) return;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try { if (onMismatch != null) onMismatch.run(); } catch (Throwable ignored) {}
                    clearCloudSessionForSiteDisconnect();
                });
            } catch (Throwable ignored) {}
        }, "deweb-cloud-wallet-check").start();
    }

    public static void clearCloudSessionForSiteDisconnect() { clearCloudSessionForSiteDisconnect(null); }

    public static void clearCloudSessionForSiteDisconnect(Runnable failed) {
        clearCloudSessionCookies(() -> {
            MainActivity m = mainRef == null ? null : mainRef.get();
            if (m == null) return;
            m.runOnUiThread(() -> {
                try {
                    if (m.webView != null) {
                        m.webView.evaluateJavascript("(function(){try{window.__dewebLiveWalletAddress='';window.__dewebCloudAddress='';localStorage.removeItem('deweb.cloud.membership.token.v1');window.dispatchEvent(new Event('deweb-cloud-disconnected'));}catch(e){}})();", null);
                        m.webView.loadUrl(OFFICIAL_MAIN_URL);
                    }
                } catch (Throwable ignored) {}
            });
        }, failed);
    }

    private void disconnectAllWallets(boolean toast) {
        SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        Set<String> approved = new HashSet<>(sp.getStringSet("native_approved_origins", new HashSet<>()));
        Set<String> revoked = new HashSet<>(sp.getStringSet("wallet_revoke_origins", new HashSet<>()));
        revoked.addAll(approved);
        SiteActivity.mergeRevokedOrigins(sp, revoked);
        sp.edit().putBoolean("wallet_reset_pending", true)
                .remove("native_approved_origins").remove("native_approved_expiry").apply();
        SiteActivity.disconnectAllInstances();
        MembershipManager.clear(this);
        SiteActivity.notifyMembershipChanged();
        try {
            if (webView != null) {
                webView.evaluateJavascript(
                        "(function(){try{window.__dewebLiveWalletAddress='';window.__dewebCloudAddress='';" +
                        "try{localStorage.removeItem('deweb.cloud.membership.token.v1');}catch(e){}" +
                        "try{if(typeof window.disconnectWallet==='function'){var p=window.disconnectWallet();if(p&&p.catch)p.catch(function(){});}}catch(e){}" +
                        "try{window.dispatchEvent(new Event('deweb-cloud-disconnected'));}catch(e){}" +
                        "try{fetch('https://api.dewebdesk.link/api/auth/logout',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'},body:'{}'}).catch(function(){});}catch(e){}" +
                        "}catch(e){}})();",
                        null);
            }
        } catch (Throwable ignored) {}
        clearCloudSessionCookies();
        if (toast) Toast.makeText(this, "已断开所有钱包连接", Toast.LENGTH_SHORT).show();
    }

    private String versionStatusBase() {
        return "版本 " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
    }

    private static final String DOWNLOAD_PAGE = "https://download.dewebdesk.link/";
    private static final long UPDATE_CHECK_INTERVAL = 24L * 60L * 60L * 1000L;

    private int[] versionParts(String v) {
        try {
            String s = String.valueOf(v).trim().replaceFirst("^[vV]", "");
            String[] p = s.split("[^0-9]+");
            int[] out = new int[3];
            for (int i = 0; i < out.length && i < p.length; i++) out[i] = Integer.parseInt(p[i]);
            return out;
        } catch (Throwable t) { return new int[]{0, 0, 0}; }
    }

    private boolean versionNewer(String latest, String current) {
        int[] a = versionParts(latest), b = versionParts(current);
        for (int i = 0; i < 3; i++) {
            if (a[i] > b[i]) return true;
            if (a[i] < b[i]) return false;
        }
        return false;
    }

    private void maybeAutoCheckUpdates() {
        try {
            SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
            if (!sp.getBoolean("auto_update_check", true)) return;
            long last = sp.getLong("last_update_check", 0L);
            if (System.currentTimeMillis() - last < UPDATE_CHECK_INTERVAL) return;
            sp.edit().putLong("last_update_check", System.currentTimeMillis()).apply();
            checkUpdates(false, null);
        } catch (Throwable ignored) {}
    }

    private void checkUpdates(boolean manual, TextView status) {
        checkForUpdate(status);
    }

    private void checkForUpdate(TextView status) {
        if (status != null) status.setText(versionStatusBase() + " · 正在检查更新...");
        new Thread(() -> {
            int latestCode = 0;
            String latestName = "";
            String apk = "";
            String notes = "";
            String sha256 = "";
            String signature = "";
            String error = null;
            try {
                URL url = new URL(updateManifestUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "DeWEB-Desktop-Android");
                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject obj = new JSONObject(sb.toString());
                latestCode = obj.optInt("versionCode", 0);
                latestName = obj.optString("versionName", "");
                apk = obj.optString("apk", "");
                notes = obj.optString("notes", "");
                sha256 = obj.optString("sha256", "").toLowerCase();
                signature = obj.optString("signature", "");
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final int current;
            try {
                current = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Throwable t) {
                return;
            }
            final int latest = latestCode;
            final String name = latestName;
            final String download = apk;
            final String note = notes;
            final String hash = sha256;
            final String sig = signature;
            final String err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (err != null) {
                    setUpdateStatus(status, versionStatusBase() + " · 检查失败");
                    Toast.makeText(this, "检查更新失败，请检查网络", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (latest > current) {
                    if (!verifyUpdateSignature(latest, name, download, hash, sig)) {
                        showHardenedDialog(new AlertDialog.Builder(this)
                                .setTitle("已拒绝更新")
                                .setMessage("更新清单签名无效或缺失，已阻止自动更新。请从官方渠道手动更新。")
                                .setPositiveButton("知道了", null));
                        return;
                    }
                    setUpdateStatus(status, versionStatusBase() + " · 发现新版本 " + name);
                    String detail = (note.isEmpty() ? "请更新到最新版本。" : note)
                            + "\n\nAPK SHA-256: " + hash
                            + "\n证书: " + OFFICIAL_CERT_SHA256
                            + "\n\n将打开 " + DOWNLOAD_PAGE + " 下载页；App 不下载、不安装。";
                    AlertDialog.Builder b = new AlertDialog.Builder(this)
                            .setTitle("发现新版本 " + name)
                            .setMessage(detail)
                            .setPositiveButton("打开下载页", (d, w) -> openUpdatePage())
                            .setNegativeButton("稍后", null);
                    showHardenedDialog(b);
                } else {
                    setUpdateStatus(status, versionStatusBase() + " · 已是最新版本 " + name);
                    Toast.makeText(this, "已是最新版本", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void openUpdatePage() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_PAGE));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开下载页", Toast.LENGTH_LONG).show();
        }
    }

    private boolean verifyUpdateSignature(int versionCode, String versionName, String apkUrl, String sha256, String signature) {
        try {
            if (signature == null || signature.isEmpty() || sha256 == null || !sha256.matches("^[0-9a-f]{64}$")) return false;
            String canonical = versionCode + "\n" + versionName + "\n" + apkUrl + "\n" + sha256;
            byte[] pub = Base64.decode(UPDATE_PUBLIC_KEY, Base64.DEFAULT);
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(pub));
            java.security.Signature verifier = java.security.Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.decode(signature, Base64.DEFAULT));
        } catch (Throwable t) {
            return false;
        }
    }

    private String updateManifestUrl() {
        return BuildConfig.UPDATE_MANIFEST;
    }

    /** Update/alert dialogs get the same partial-obscuring protection as panels. */
    private void showHardenedDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.show();
        try { GlassUi.hardenTree(dialog.getWindow().getDecorView()); } catch (Throwable ignored) {}
        try { GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || appLockRequested()); } catch (Throwable ignored) {}
    }

    private void setUpdateStatus(TextView status, String text) {
        if (status == null) return;
        runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; status.setText(text); });
    }

    private void buildAppLock(FrameLayout shell) {
        appLockOverlay = new FrameLayout(this);
        appLockOverlay.setBackground(GlassUi.background(this));
        appLockOverlay.setClickable(true);
        appLockOverlay.setFocusable(true);
        appLockOverlay.setFilterTouchesWhenObscured(true);
        appLockOverlay.setOnClickListener(v -> {});
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
        title.setText("DeWEB 已锁定");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFEAF7EF);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        TextView sub = new TextView(this);
        sub.setText("验证设备锁屏后继续");
        sub.setTextSize(11f);
        sub.setTextColor(0x99C9DED2);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        box.addView(sub, subLp);
        Button unlock = new Button(this);
        unlock.setText("解锁");
        unlock.setTextSize(13f);
        unlock.setAllCaps(false);
        unlock.setTextColor(0xFF03150D);
        GradientDrawable unlockBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF8AF2C8, 0xFF56E6B0});
        unlockBg.setCornerRadius(dp(14));
        unlock.setBackground(unlockBg);
        unlock.setFilterTouchesWhenObscured(true);
        unlock.setOnClickListener(v -> requestAppUnlock());
        LinearLayout.LayoutParams unlockLp = new LinearLayout.LayoutParams(dp(140), dp(44));
        unlockLp.topMargin = dp(18);
        box.addView(unlock, unlockLp);
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.gravity = Gravity.CENTER;
        appLockOverlay.addView(box, boxLp);
        shell.addView(appLockOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        appLockOverlay.bringToFront();
    }

    private void requestAppUnlock() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            Toast.makeText(this, "请先设置手机锁屏密码", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = km.createConfirmDeviceCredentialIntent("解锁 DeWEB", "验证设备锁屏");
            if (intent == null) {
                Toast.makeText(this, "无法启动设备验证", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(intent, REQ_APP_UNLOCK);
        } catch (Throwable t) {
            Toast.makeText(this, "无法启动设备验证", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishAppUnlock() {
        if (appLockOverlay != null) appLockOverlay.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(true);
        getSharedPreferences("deweb_shell", MODE_PRIVATE).edit().putBoolean("site_locked", false).apply();
        applyScreenshotProtection();
        Toast.makeText(this, "已解锁", Toast.LENGTH_SHORT).show();
    }

    private void showLastCrash() {
        String crash = CrashLog.read(this);
        if (crash == null) return;
        CrashLog.clear(this);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        GlassUi.blurDialog(dialog);
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

        TextView title = new TextView(this);
        title.setText("上次崩溃日志");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFEAF7EF);
        panel.addView(title);

        TextView log = new TextView(this);
        log.setText(crash);
        log.setTextSize(10.5f);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextColor(0xCCD8E8DE);
        log.setTextIsSelectable(true);
        log.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(0x33000000);
        logBg.setCornerRadius(dp(14));
        logBg.setStroke(dp(1), 0x22FFFFFF);
        log.setBackground(logBg);

        ScrollView scroller = new ScrollView(this);
        scroller.addView(log);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.60f);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        scrollLp.topMargin = dp(12);
        panel.addView(scroller, scrollLp);

        Button close = new Button(this);
        close.setText("关闭");
        close.setTextSize(12f);
        close.setAllCaps(false);
        close.setTextColor(0xFF03150D);
        GradientDrawable closeBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF8AF2C8, 0xFF56E6B0});
        closeBg.setCornerRadius(dp(14));
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        closeLp.topMargin = dp(10);
        panel.addView(close, closeLp);
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
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
        GlassUi.secureDialog(dialog, PrivacyPrefs.isEnabled(this) || appLockRequested());
    }

    private boolean isMainOrigin(Uri u) {
        if (u == null) return false;
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getPort() != -1) return false;
        return "app.dewebdesk.link".equalsIgnoreCase(u.getHost());
    }

    private void onCloudAuthMessage(WebMessageCompat message, Uri sourceOrigin, boolean isMainFrame) {
        try {
            JSONObject testReq = new JSONObject(message.getData() == null ? "{}" : message.getData());
            if (BuildConfig.DEBUG && "__deweb_selftest_iframe__".equals(testReq.optString("id"))) {
                selfTestIframeSeen = true;
                selfTestIframeMainFrame = isMainFrame;
                return;
            }
        } catch (Throwable ignored) {}
        if (!isMainFrame || !isMainOrigin(sourceOrigin)) return;
        try {
            JSONObject req = new JSONObject(message.getData() == null ? "{}" : message.getData());
            String id = req.optString("id");
            String method = req.optString("method");
            if (id == null || id.length() == 0 || id.length() > 128) return;
            if (!"connect".equals(method) && !"sign".equals(method) && !"disconnect".equals(method)) return;
            if ("disconnect".equals(method)) {
                // Cloud sign-out must also drop the native wallet session cache
                // and any wallet connection held by site windows.
                getSharedPreferences("deweb_shell", MODE_PRIVATE)
                        .edit().putBoolean("wallet_reset_pending", true).apply();
                // Drop the HttpOnly cloud cookie immediately. Capture it first
                // so the server token can also be revoked best-effort.
                String cloudCookie = null;
                try { cloudCookie = CookieManager.getInstance().getCookie(CLOUD_API); } catch (Throwable ignored) {}
                expireCloudCookiesNow();
                revokeCloudSessionAsync(cloudCookie);
                MembershipManager.clear(this);
                SiteActivity.notifyMembershipChanged();
                SiteActivity.notifyCloudDisconnected();
                runOnUiThread(SiteActivity::disconnectAllInstances);
                postCloudAuthResult(id, "", "", "");
                return;
            }
            String signMessage = req.optString("message", "");
            if ("sign".equals(method) && !isValidCloudSignMessage(signMessage)) {
                if (BuildConfig.DEBUG && "__deweb_selftest_bad__".equals(id)) selfTestBadMessageRejected = true;
                return;
            }
            if (BuildConfig.DEBUG && "__deweb_selftest_bad__".equals(id)) return;
            startCloudAuth(id, "sign".equals(method) ? signMessage : null);
        } catch (Throwable ignored) {}
    }

    private void runSecuritySelfTest() {
        if (!BuildConfig.DEBUG) return;
        selfTestIframeSeen = false;
        selfTestIframeMainFrame = false;
        selfTestBadMessageRejected = false;
        selfTestNavLeak = false;
        selfTestNavTested = false;

        final boolean forgedRejected = !isValidCloudSignMessage("ATTACK: arbitrary personal_sign");
        final boolean validAccepted = isValidCloudSignMessage(
                "DeWEB Cloud Sync\nAddress: 0x0000000000000000000000000000000000000001\n" +
                "Nonce: 0123456789abcdef0123456789abcdef\n" +
                "Issued At: 2026-01-01T00:00:00.000Z\nChain: BSC");
        final boolean originApp = isMainOrigin(Uri.parse("https://app.dewebdesk.link/"));
        final boolean originLegacyRejected = !isMainOrigin(Uri.parse("https://deweb-web.pages.dev/"));
        final boolean originEvilRejected = !isMainOrigin(Uri.parse("https://evil.example/"));

        final String[] xssResult = {"pending"};
        if (webView != null) {
            String iframeJs = "(function(){try{var f=document.createElement('iframe');" +
                    "f.style.cssText='position:fixed;left:-9999px;top:0;width:240px;height:120px';" +
                    "var p='<scr'+'ipt>try{if(window.dewebCloudAuthNative){" +
                    "window.dewebCloudAuthNative.postMessage(JSON.stringify({id:\"__deweb_selftest_iframe__\",method:\"sign\",message:\"x\"}));" +
                    "}}catch(e){}<'+ '/scr'+'ipt>';" +
                    "f.srcdoc=p;document.body.appendChild(f);return 'ok';}catch(e){return 'err:'+e.message;}})()";
            String xssJs = "(function(){try{if(typeof apps==='undefined'||typeof render!=='function')return 'skip';" +
                    "document.title='DEWEB_SELFTEST_CLEAN';" +
                    "apps.push({name:'__deweb_xss_test',chain:'xlayer',files:'<img src=x onerror=\"document.title=1\">',cpuName:'xss'});" +
                    "render();var t=document.title;" +
                    "apps=apps.filter(function(a){return a&&a.name!=='__deweb_xss_test';});render();return t;" +
                    "}catch(e){return 'error:'+e.message;}})()";
            String badJs = "try{window.dewebCloudAuthNative.postMessage(JSON.stringify({id:'__deweb_selftest_bad__',method:'sign',message:'ATTACK'}));}catch(e){}";
            webView.evaluateJavascript(iframeJs, null);
            webView.evaluateJavascript(badJs, null);
            webView.evaluateJavascript(xssJs, value -> xssResult[0] = value == null ? "null" : value);

            final String originalUrl = webView.getUrl();
            final String oldPendingUrl = pendingCloudAuthUrl;
            if (originalUrl != null) {
                pendingCloudAuthUrl = originalUrl;
                String navSetup = "(function(){try{window.__selftestNavLeak=false;" +
                        "window.__selftestOrigResult=window.__dewebCloudAuthResult;" +
                        "window.__dewebCloudAuthResult=function(id,a,s,e){" +
                        "if(id==='__selftest_nav__')window.__selftestNavLeak=true;" +
                        "try{return window.__selftestOrigResult.apply(this,arguments)}catch(_){}};" +
                        "location.hash='#selftest-nav';return 'ok';}catch(e){return 'err:'+e.message;}})()";
                webView.evaluateJavascript(navSetup, null);
                webView.postDelayed(() -> {
                    postCloudAuthResult("__selftest_nav__", "0x0000000000000000000000000000000000000001",
                            "SIGNATURE_SHOULD_NOT_LEAK", "");
                    webView.postDelayed(() -> {
                        String remove = "(function(){var v=!!window.__selftestNavLeak;try{" +
                                "history.replaceState({},''," + JSONObject.quote(originalUrl) + ");" +
                                "window.__dewebCloudAuthResult=window.__selftestOrigResult;" +
                                "}catch(e){}return v;})()";
                        webView.evaluateJavascript(remove, value -> {
                            selfTestNavLeak = "true".equals(value);
                            selfTestNavTested = true;
                        });
                        pendingCloudAuthUrl = oldPendingUrl;
                    }, 450);
                }, 250);
            }
            webView.postDelayed(() -> showSelfTestResult(forgedRejected && selfTestBadMessageRejected, validAccepted, originApp,
                    originLegacyRejected, originEvilRejected, xssResult[0], selfTestNavTested, selfTestNavLeak), 1600);
        }
    }

    private void showSelfTestResult(boolean forgedRejected, boolean validAccepted, boolean originApp,
                                    boolean originLegacyRejected, boolean originEvilRejected,
                                    String xssRaw, boolean navTested, boolean navLeak) {
        boolean iframePass = !selfTestIframeSeen || !selfTestIframeMainFrame;
        String xss = xssRaw == null ? "null" : xssRaw.replace("\"", "");
        boolean xssPass = xss.contains("DEWEB_SELFTEST_CLEAN");
        boolean xssSkip = xss.contains("skip") || xss.contains("error:");
        StringBuilder b = new StringBuilder();
        b.append(forgedRejected ? "PASS" : "FAIL").append("  伪造签名消息拒绝\n");
        b.append(validAccepted ? "PASS" : "FAIL").append("  合法云登录消息格式\n");
        b.append(originApp ? "PASS" : "FAIL").append("  app.dewebdesk.link 主域\n");
        b.append(originLegacyRejected ? "PASS" : "FAIL").append("  旧 Pages 域拒绝\n");
        b.append(originEvilRejected ? "PASS" : "FAIL").append("  任意外域拒绝\n");
        b.append(iframePass ? "PASS" : "FAIL").append("  iframe 未作为主框架调用").append(selfTestIframeSeen ? "（已拒绝）" : "（未触达）").append("\n");
        b.append(xssSkip ? "SKIP" : (xssPass ? "PASS" : "FAIL")).append("  files 字段不执行 HTML\n");
        b.append(!navTested ? "SKIP" : (navLeak ? "FAIL" : "PASS")).append("  认证结果不注入变更页面");
        if (xssSkip) b.append("\n      ").append(xss);
        new AlertDialog.Builder(this)
                .setTitle("DeWEB " + BuildConfig.VERSION_NAME + " · 安全自测")
                .setMessage(b.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private static boolean isValidCloudSignMessage(String message) {
        if (message == null || message.length() > 420) return false;
        return message.matches("^DeWEB Cloud Sync\nAddress: 0x[0-9a-fA-F]{40}\nNonce: [0-9a-fA-F]{32}\nIssued At: [^\\n]+\n(?:Origin: [^\\n]{1,200}\n)?Chain: BSC$");
    }

    private void onBridgeMessage(WebMessageCompat message, Uri sourceOrigin, boolean isMainFrame, JavaScriptReplyProxy replyProxy) {
        String raw = message.getData();
        try {
            JSONObject req = new JSONObject(raw == null ? "{}" : raw);
            String id = req.optString("id");
            String method = req.optString("method");
            String who = (isMainFrame ? "main " : "iframe ") + sourceOrigin + " ";
            Log.d(TAG, who + method);
            runOnUiThread(() -> status.setText(who + method));

            if ("deweb_open_site".equals(method)) {
                if (!isMainFrame || !isMainOrigin(sourceOrigin)) return;
                JSONArray p = req.optJSONArray("params");
                String target = p != null ? p.optString(0) : "";
                String chain = p != null ? p.optString(1, "web") : "web";
                Uri u = Uri.parse(target);
                if (("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost() != null) {
                    if (SiteActivity.focusExistingSite(u.toString())) return;
                    Intent i = new Intent(this, SiteActivity.class);
                    i.putExtra("url", u.toString());
                    i.putExtra("chain", chain);
                    i.putExtra("desktop", true);
                    startActivity(i);
                }
                return;
            }

            if ("deweb_open_pro".equals(method)) {
                if (!isMainFrame || !isMainOrigin(sourceOrigin)) return;
                if (req.optInt("bridgeVersion", 0) != 1) {
                    Log.w(TAG, "pro bridge version mismatch");
                    return;
                }
                JSONArray prms = req.optJSONArray("params");
                if (prms != null && prms.length() > 0) {
                    Log.w(TAG, "pro bridge params rejected");
                    return;
                }
                long nowPro = System.currentTimeMillis();
                if (nowPro - lastProOpenAt < 4000) return;
                lastProOpenAt = nowPro;
                try {
                    Intent i = new Intent(MainActivity.this, CloudAuthActivity.class);
                    i.putExtra(CloudAuthActivity.EXTRA_MEMBERSHIP, true);
                    startActivity(i);
                } catch (Throwable ignored) {}
                return;
            }

            JSONObject out = new JSONObject();
            out.put("id", id);
            if ("eth_requestAccounts".equals(method) || "eth_accounts".equals(method)) {
                out.put("result", new JSONArray());
            } else if ("eth_chainId".equals(method)) {
                out.put("result", "0x38");
            } else if ("net_version".equals(method)) {
                out.put("result", "56");
            } else {
                JSONObject err = new JSONObject();
                err.put("code", 4100);
                err.put("message", "钱包只在站点窗口可用，主界面不提供签名或交易");
                out.put("error", err);
            }
            replyProxy.postMessage(out.toString());
        } catch (Exception e) {
            Log.e(TAG, "bridge parse failed", e);
            runOnUiThread(() -> status.setText("bridge error: " + e.getMessage()));
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable glassBar() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF2FFFFFF, 0xE6F4FFF9, 0xF2FFFFFF});
        g.setCornerRadii(new float[]{0, 0, 0, 0, dp(18), dp(18), dp(18), dp(18)});
        g.setStroke(dp(1), 0x55FFFFFF);
        return g;
    }

    private WebResourceResponse localAssetResponse(String asset, String mode) {
        try {
            byte[] bytes = readAsset(asset).getBytes(StandardCharsets.UTF_8);
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/html; charset=utf-8");
            headers.put("Cache-Control", "no-store");
            headers.put("X-Tape-Mode", mode);
            return new WebResourceResponse("text/html", "utf-8", 200, "OK", headers,
                    new ByteArrayInputStream(bytes));
        } catch (Throwable t) {
            try {
                return new WebResourceResponse("text/html", "utf-8", 200, "Locked",
                        new HashMap<>(), new ByteArrayInputStream(
                        "<!doctype html><meta charset='utf-8'><body style='background:#07100b;color:#e6f2ea;font:14px monospace;padding:24px'>页面校验失败，已锁定。</body>".getBytes(StandardCharsets.UTF_8)));
            } catch (Throwable ignored) { return null; }
        }
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

    private static String repeatHex(String unit, int count) {
        StringBuilder sb = new StringBuilder(unit.length() * count);
        for (int i = 0; i < count; i++) sb.append(unit);
        return sb.toString();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void removeUnverifiedOverlay() {
        if (unverifiedOverlay == null || appShell == null) return;
        try { appShell.removeView(unverifiedOverlay); } catch (Throwable ignored) {}
        unverifiedOverlay = null;
    }

    private void retryMainVerification() {
        removeUnverifiedOverlay();
        officialFallback = false;
        verifiedMain = null;
        verifyLatch = new CountDownLatch(1);
        startMainVerification();
        if (webView != null) webView.reload();
    }

    private void showUnverifiedBanner() {
        if (isFinishing() || isDestroyed() || appShell == null || unverifiedOverlay != null) return;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xB3000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(18));
        card.setBackground(GlassUi.panel(this, 22, true));
        card.setClickable(true);
        card.setFocusable(true);

        TextView title = new TextView(this);
        title.setText("未验证页面 · 仅预览");
        title.setTextSize(19f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFEAF8F1);
        card.addView(title);

        TextView body = new TextView(this);
        body.setText("链上校验失败，当前页面已锁定，不能操作。\n\n请完全关闭应用后重新打开；也可以点“重试”重新校验。");
        body.setTextSize(13f);
        body.setTextColor(0xFFCFE8DC);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(12);
        card.addView(body, bodyLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(18);
        card.addView(actions, actionsLp);

        Button close = new Button(this);
        close.setText("关闭应用");
        close.setTextSize(13f);
        close.setAllCaps(false);
        close.setTextColor(0xFFEAF8F1);
        close.setBackground(GlassUi.button(this, 14, true));
        close.setOnClickListener(v -> finishAffinity());
        actions.addView(close, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button retry = new Button(this);
        retry.setText("重试");
        retry.setTextSize(13f);
        retry.setAllCaps(false);
        retry.setTextColor(0xFF03150D);
        retry.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable retryBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF56E6B0, 0xFF2FC79A});
        retryBg.setCornerRadius(dp(14));
        retry.setBackground(retryBg);
        retry.setOnClickListener(v -> retryMainVerification());
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        retryLp.leftMargin = dp(10);
        actions.addView(retry, retryLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        cardLp.leftMargin = dp(24);
        cardLp.rightMargin = dp(24);
        overlay.addView(card, cardLp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        appShell.addView(overlay, lp);
        unverifiedOverlay = overlay;
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
     * never be unlocked (requestAppUnlock only asks the user to set a screen
     * lock), so clear it instead of leaving FLAG_SECURE stuck on forever.
     */
    private boolean appLockRequested() {
        SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
        if (!sp.getBoolean("site_locked", false)) return false;
        if (deviceIsSecure()) return true;
        sp.edit().putBoolean("site_locked", false).apply();
        Toast.makeText(this, "设备未设置锁屏密码，应用锁已自动解除", Toast.LENGTH_LONG).show();
        return false;
    }

    private void applyScreenshotProtection() {
        boolean locked = appLockRequested();
        boolean secure = locked || PrivacyPrefs.isEnabled(this);
        if (secure) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(true);
        }
    }

    /**
     * Beta-only readout of the real screenshot-protection state. The settings
     * switch alone cannot tell whether another window/overlay still carries
     * FLAG_SECURE, so capture every layer here and copy it for reporting.
     */
    private String screenshotDiagnosticsText() {
        boolean mainSecure = false;
        try {
            mainSecure = (getWindow().getAttributes().flags
                    & WindowManager.LayoutParams.FLAG_SECURE) != 0;
        } catch (Throwable ignored) {}
        String overlay = appLockOverlay == null ? "null"
                : (appLockOverlay.getVisibility() == View.VISIBLE ? "visible" : "gone");
        StringBuilder sb = new StringBuilder();
        sb.append("DeWEB ").append(BuildConfig.VERSION_NAME)
                .append(" beta=").append(BuildConfig.IS_BETA)
                .append(" api=").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("privacy=").append(PrivacyPrefs.isEnabled(this) ? "on" : "off").append('\n');
        sb.append("site_locked=").append(getSharedPreferences("deweb_shell", MODE_PRIVATE)
                .getBoolean("site_locked", false)).append('\n');
        sb.append("device_secure=").append(deviceIsSecure()).append('\n');
        sb.append("main_secure=").append(mainSecure).append('\n');
        sb.append("main_lockOverlay=").append(overlay).append('\n');
        sb.append(SiteActivity.screenshotDiagnostics());
        return sb.toString();
    }

    private void syncAppLock() {
        if (appShell == null || isFinishing() || isDestroyed()) return;
        boolean locked = appLockRequested();
        if (locked) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
            if (appLockOverlay == null) buildAppLock(appShell);
            else {
                appLockOverlay.setVisibility(View.VISIBLE);
                appLockOverlay.bringToFront();
            }
        } else if (!locked && appLockOverlay != null) {
            appLockOverlay.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(true);
        }
        applyScreenshotProtection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncAppLock();
        applyScreenshotProtection();
        SiteActivity.notifyPrivacyChanged();
        try {
            SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
            if (sp.getBoolean("open_main_settings_once", false)) {
                sp.edit().remove("open_main_settings_once").apply();
                showWalletDialog();
            }
            final String url = sp.getString("pending_add_url", null);
            final String title = sp.getString("pending_add_title", "");
            if (url != null && url.startsWith("http")) {
                sp.edit().remove("pending_add_url").remove("pending_add_title").apply();
                final String js = "(function(){try{var add=window.addWebShortcutNow||window.addWebShortcut;add({url:" + JSONObject.quote(url) + ",title:" + JSONObject.quote(title) + "});}catch(e){}})()";
                webView.post(() -> webView.evaluateJavascript(js, null));
            }
        } catch (Exception e) {
            Log.w(TAG, "pending add failed", e);
        }
    }

    private class CloudProBridge {
        @JavascriptInterface
        public void open() {
            runOnUiThread(() -> {
                try {
                    if (!isMainOrigin(webView == null ? null : Uri.parse(webView.getUrl()))) return;
                    Intent i = new Intent(MainActivity.this, CloudAuthActivity.class);
                    i.putExtra(CloudAuthActivity.EXTRA_MEMBERSHIP, true);
                    startActivity(i);
                } catch (Throwable ignored) {}
            });
        }
    }

    private void startCloudAuth(String id, String message) {
        runOnUiThread(() -> {
            try {
                String currentUrl = webView == null ? null : webView.getUrl();
                if (currentUrl == null) return;
                Uri current = Uri.parse(currentUrl);
                if (!isMainOrigin(current)) return;
                if (message != null && !isValidCloudSignMessage(message)) return;
                if (cloudAuthBusy) {
                    postCloudAuthResult(id, "", "", "钱包登录已在进行中");
                    return;
                }
                cloudAuthBusy = true;
                pendingCloudAuthId = id;
                pendingCloudAuthMessage = message;
                pendingCloudAuthUrl = webView.getUrl();
                Intent i = new Intent(MainActivity.this, CloudAuthActivity.class);
                if (message != null) i.putExtra(CloudAuthActivity.EXTRA_MESSAGE, message);
                startActivityForResult(i, REQ_CLOUD_AUTH);
            } catch (Throwable t) {
                cloudAuthBusy = false;
                pendingCloudAuthId = null;
                pendingCloudAuthMessage = null;
                pendingCloudAuthUrl = null;
                postCloudAuthResult(id, "", "", "无法打开钱包登录");
            }
        });
    }

    private void postCloudAuthResult(String id, String address, String signature, String error) {
        try {
            if (webView == null) return;
            String currentUrl = webView.getUrl();
            if (currentUrl == null) return;
            Uri current = Uri.parse(currentUrl);
            if (!isMainOrigin(current)) return;
            if (pendingCloudAuthUrl != null && !currentUrl.equals(pendingCloudAuthUrl)) return;
            String js = "window.__dewebLiveWalletAddress=" + JSONObject.quote(address) + ";"
                    + "window.__dewebCloudAuthResult(" + JSONObject.quote(id) + ","
                    + JSONObject.quote(address) + "," + JSONObject.quote(signature) + ","
                    + JSONObject.quote(error) + ");";
            webView.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CLOUD_AUTH) {
            String id = pendingCloudAuthId == null ? "" : pendingCloudAuthId;
            String address = data == null ? "" : data.getStringExtra(CloudAuthActivity.EXTRA_ADDRESS);
            String signature = data == null ? "" : data.getStringExtra(CloudAuthActivity.EXTRA_SIGNATURE);
            String error = data == null ? "" : data.getStringExtra(CloudAuthActivity.EXTRA_ERROR);
            if (resultCode != RESULT_OK && (error == null || error.isEmpty())) error = "钱包登录已取消";
            postCloudAuthResult(id, address == null ? "" : address, signature == null ? "" : signature, error == null ? "" : error);
            pendingCloudAuthId = null;
            pendingCloudAuthMessage = null;
            pendingCloudAuthUrl = null;
            cloudAuthBusy = false;
            return;
        }
        if (requestCode == REQ_APP_UNLOCK) {
            if (resultCode == RESULT_OK) finishAppUnlock();
            else Toast.makeText(this, "未解锁", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fileChooser != null && fileChooser.onActivityResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startMainVerification() {
        new Thread(() -> {
            try {
                byte[] cached = TapeCache.read(this);
                String etag = TapeCache.readEtag(this);
                boolean forceFresh = true;
                if (cached != null && cached.length > 0) {
                    org.json.JSONObject cachedMeta = TapeCache.readMeta(this);
                    if (!TapeCache.verifyMeta(this, cached, cachedMeta)) cachedMeta = new org.json.JSONObject();
                    forceFresh = cachedMeta.optInt("appVersionCode", -1) != BuildConfig.VERSION_CODE;
                    if (!forceFresh) {
                        TapeVerifier.Result cr = TapeVerifier.verifyCached(cached, cachedMeta);
                        if (cr != null && cr.ok) {
                            verifiedMain = cr;
                            if (!cr.meta.optBoolean("cacheHit", true)) persistChainMeta(cachedMeta, cr.meta);
                            refreshVerifiedInBackground(etag, cached);
                            return;
                        }
                    }
                }
                verifiedMain = TapeVerifier.fetchAndVerify("https://app.dewebdesk.link/", forceFresh ? "" : etag, forceFresh ? null : cached);
                if (verifiedMain != null && verifiedMain.ok && verifiedMain.bytes != null) {
                    try {
                        verifiedMain.meta.put("chainCheckedAt", System.currentTimeMillis());
                        verifiedMain.meta.put("appVersionCode", BuildConfig.VERSION_CODE);
                    } catch (Throwable ignored) {}
                    TapeCache.write(this, verifiedMain.bytes, verifiedMain.meta.toString());
                }
            } catch (Throwable ignored) {
                verifiedMain = null;
            } finally {
                verifyLatch.countDown();
            }
        }, "deweb-local-verify").start();
    }

    private void persistChainMeta(org.json.JSONObject oldMeta, org.json.JSONObject freshMeta) {
        try {
            org.json.JSONObject merged = new org.json.JSONObject(oldMeta == null ? "{}" : oldMeta.toString());
            String[] keys = {"chainHash", "chainSize", "chunks", "chainCheckedAt", "officialSha256", "officialBytes"};
            for (String k : keys) {
                if (freshMeta.has(k)) merged.put(k, freshMeta.get(k));
            }
            TapeCache.writeMeta(this, merged);
        } catch (Throwable ignored) {}
    }

    /** Stale-while-revalidate: serve fresh local cache, refresh it off the UI path. */
    private void refreshVerifiedInBackground(String etag, byte[] cached) {
        new Thread(() -> {
            try {
                TapeVerifier.Result fresh = TapeVerifier.fetchAndVerify("https://app.dewebdesk.link/", "", null);
                if (fresh != null && fresh.ok && fresh.bytes != null) {
                    try { fresh.meta.put("chainCheckedAt", System.currentTimeMillis()); fresh.meta.put("appVersionCode", BuildConfig.VERSION_CODE); } catch (Throwable ignored) {}
                    TapeVerifier.Result old = verifiedMain;
                    boolean changed = old == null || old.bytes == null || !java.util.Arrays.equals(old.bytes, fresh.bytes);
                    verifiedMain = fresh;
                    TapeCache.write(this, fresh.bytes, fresh.meta.toString());
                    if (changed) {
                        runOnUiThread(() -> {
                            try {
                                if (webView != null && !isFinishing()) {
                                    webView.loadUrl(MAIN_URL + "&r=" + System.currentTimeMillis());
                                }
                            } catch (Throwable ignored) {}
                        });
                    }
                }
            } catch (Throwable ignored) {}
        }, "deweb-verify-refresh").start();
    }

    private void injectLocalVerify() {
        try {
            TapeVerifier.Result vr = verifiedMain;
            org.json.JSONObject meta = vr == null ? new org.json.JSONObject() : vr.meta;
            String js = "window.__dewebSetLocalVerify(" + meta.toString() + ");";
            if (webView != null) webView.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    private void scheduleOfficialVerify() {
        new Thread(() -> {
            try {
                final org.json.JSONObject result = TapeVerifier.verifyOfficial("https://app.dewebdesk.link/");
                runOnUiThread(() -> {
                    try {
                        if (webView != null) webView.evaluateJavascript(
                                "window.__dewebSetOfficialVerify(" + result.toString() + ");", null);
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        }, "deweb-official-verify").start();
    }

    @Override
    protected void onDestroy() {
        try { if (mainRef != null && mainRef.get() == this) mainRef.clear(); } catch (Throwable ignored) {}
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
