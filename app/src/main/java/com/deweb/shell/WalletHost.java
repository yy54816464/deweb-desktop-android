package com.deweb.shell;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reusable wallet page host, modeled exactly on the proven SiteActivity wallet
 * path. Cloud auth and site windows can share this implementation.
 */
public class WalletHost {
    public interface Listener {
        void onWalletEvent(String type, JSONObject message);
        void onWalletDebug(String message);
        void onOpenUrl(Uri uri);
        void onReady();
    }
    public interface Response {
        void onResult(JSONObject message);
    }

    public static final String WALLET_ORIGIN = "https://appassets.androidplatform.net";
    private static final String WALLET_URL = WALLET_ORIGIN + "/assets/wallet/android-wallet.html?v=cloud3";
    private static final String BRIDGE = "dewebWalletBridge";

    private final Activity activity;
    private final FrameLayout container;
    private final Listener listener;
    private final ArrayDeque<JSONObject> queue = new ArrayDeque<>();
    private final Map<String, Response> pending = new HashMap<>();
    private WebView web;
    private WebViewAssetLoader loader;
    private boolean ready;

    public WalletHost(Activity activity, FrameLayout container, Listener listener) {
        this.activity = activity;
        this.container = container;
        this.listener = listener;
    }

    public WebView webView() { return web; }

    public void start() { start(false); }

    public void start(boolean resetSession) {
        if (web != null) return;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            throw new IllegalStateException("WebView wallet bridge unavailable");
        }
        web = new WebView(activity);
        web.setBackgroundColor(Color.rgb(7, 16, 11));
        web.setVisibility(View.VISIBLE);
        web.setFilterTouchesWhenObscured(true);
        container.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
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
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (android.os.Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);

        Set<String> origins = new HashSet<>(Arrays.asList(WALLET_ORIGIN));
        WebViewCompat.addDocumentStartJavaScript(web, readAsset("wallet-shim.js"), origins);
        WebViewCompat.addWebMessageListener(web, BRIDGE, origins,
                (view, message, sourceOrigin, isMainFrame, reply) -> onMessage(message));

        loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(activity))
                .build();
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                if (cm != null && listener != null) listener.onWalletDebug(cm.message());
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
@Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader == null ? null : loader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isOkxUri(uri)) { if (listener != null) listener.onOpenUrl(uri); return true; }
                if (isWalletUrl(uri)) return false;
                return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && isWalletUrl(Uri.parse(url))) {
                    ready = true;
                    if (listener != null) listener.onReady();
                    flush();
                }
            }
        });
        web.clearCache(true);
        web.loadUrl(WALLET_URL + (resetSession ? "&reset=1" : ""));
    }

    public void prepare(JSONObject req, Response response) {
        String id = req.optString("id");
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("wallet request id required");
        pending.put(id, response);
    }

    public void request(JSONObject req, Response response) {
        String id = req.optString("id");
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("wallet request id required");
        pending.put(id, response);
        if (ready && web != null) dispatch(req);
        else queue.add(req);
    }

    public void injectConnectButton(JSONObject req) {
        try {
            String b64 = b64(req.toString());
            String js = "(function(){try{"
                    + "if(document.getElementById('dewebCloudConnect'))return;"
                    + "var b=document.createElement('button');b.id='dewebCloudConnect';b.textContent='连接 OKX 钱包';"
                    + "b.style.cssText='position:fixed;left:24px;right:24px;bottom:30px;z-index:99999;padding:17px;border:0;border-radius:14px;background:#56e6b0;color:#03150d;font:700 15px ui-monospace,monospace;box-shadow:0 12px 30px rgba(0,0,0,.35)';"
                    + "b.onclick=function(){b.disabled=true;b.textContent='正在连接…';try{window.__dewebWalletRequest(JSON.parse(atob('" + b64 + "')));}catch(e){b.textContent='连接失败：'+e.message;}};"
                    + "document.body.appendChild(b);"
                    + "}catch(e){}})();";
            if (web != null) web.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    public void evaluate(String js) {
        if (web != null) web.evaluateJavascript(js, null);
    }

    private void flush() {
        if (!ready || web == null) return;
        while (!queue.isEmpty()) dispatch(queue.poll());
    }

    private void dispatch(JSONObject req) {
        if (web == null) return;
        try {
            String b64 = b64(req.toString());
            web.evaluateJavascript("window.__dewebWalletRequest(JSON.parse(atob('" + b64 + "')));", null);
        } catch (Throwable ignored) {}
    }

    private void onMessage(WebMessageCompat message) {
        try {
            JSONObject msg = new JSONObject(message.getData() == null ? "{}" : message.getData());
            String type = msg.optString("type");
            if (type != null && !type.isEmpty()) {
                if ("debug".equals(type)) {
                    if (listener != null) listener.onWalletDebug(msg.optString("message"));
                    return;
                }
                if ("focus".equals(type)) {
                    if (listener != null) listener.onWalletEvent("focus", msg);
                    return;
                }
                if ("accounts".equals(type) || "chain".equals(type) || "open_url".equals(type)
                        || "membership_changed".equals(type)) {
                    if ("open_url".equals(type) && listener != null) {
                        listener.onOpenUrl(Uri.parse(msg.optString("url")));
                    }
                    if (listener != null) listener.onWalletEvent(type, msg);
                    return;
                }
            }
            String id = msg.optString("id");
            if (id == null || id.isEmpty()) return;
            Response cb = pending.remove(id);
            if (cb != null) cb.onResult(msg);
        } catch (Throwable ignored) {}
    }

    private static String b64(String s) {
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private String readAsset(String name) {
        try {
            InputStream in = activity.getAssets().open(name);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
            r.close();
            return b.toString();
        } catch (Throwable t) { return ""; }
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

    public void destroy() {
        ready = false;
        queue.clear();
        pending.clear();
        if (web != null) {
            try { web.loadUrl("about:blank"); web.destroy(); } catch (Throwable ignored) {}
            web = null;
        }
    }
}
