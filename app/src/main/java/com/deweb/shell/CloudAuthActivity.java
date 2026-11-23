package com.deweb.shell;

import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cloud sign-in wrapper around the shared WalletHost.
 * Only eth_requestAccounts and personal_sign are issued here.
 */
public class CloudAuthActivity extends Activity {
    public static final String EXTRA_MESSAGE = "cloud_sign_message";
    public static final String EXTRA_MEMBERSHIP = "cloud_membership";
    public static final String EXTRA_ADDRESS = "cloud_address";
    public static final String EXTRA_SIGNATURE = "cloud_signature";
    public static final String EXTRA_ERROR = "cloud_error";

    private static final String ORIGIN = "https://cloud.dewebdesk.link";
    private static final String CONNECT_ID = "cloud-connect";
    private static final String SIGN_ID = "cloud-sign";

    private FrameLayout root;
    private TextView statusView;
    private WalletHost host;
    private String message;
    private String address;
    private boolean finished;
    private boolean membershipMode;
    private boolean connectConfirmed;
    private boolean connectPromptShown;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyPrivacyFlag();
        message = getIntent().getStringExtra(EXTRA_MESSAGE);
        membershipMode = getIntent().getBooleanExtra(EXTRA_MEMBERSHIP, false);
        FrameLayout shade = new FrameLayout(this);
        shade.setPadding(dp(14), dp(34), dp(14), dp(34));
        root = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF707100B);
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), 0x556B8F7E);
        root.setBackground(cardBg);
        root.setClipToOutline(true);
        shade.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(shade);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFE6F2EA);
        statusView.setTextSize(11f);
        statusView.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusView.setBackgroundColor(0xCC07100B);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.BOTTOM;
        root.addView(statusView, slp);

        Button close = new Button(this);
        close.setText("取消");
        close.setTextSize(11f);
        close.setAllCaps(false);
        close.setBackgroundColor(0xCC101D15);
        close.setTextColor(0xFFE6F2EA);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(72), dp(38));
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.topMargin = dp(10);
        clp.rightMargin = dp(10);
        root.addView(close, clp);
        close.setFilterTouchesWhenObscured(true);
        close.setOnClickListener(v -> cancel("User cancelled"));

        if (membershipMode) statusView.setVisibility(View.GONE);
        statusView.setText("正在加载钱包服务…");
        try {
            host = new WalletHost(this, root, new WalletHost.Listener() {
                @Override public void onWalletEvent(String type, JSONObject msg) {
                    if ("focus".equals(type)) return;
                    if ("membership_changed".equals(type)) refreshMembership();
                }
                @Override public void onWalletDebug(String text) {
                    if (text != null && text.startsWith("DEWEBDBG ")) status(text.substring(8).trim());
                }
                @Override public void onOpenUrl(Uri uri) { openExternal(uri); }
                @Override public void onReady() {
                    if (membershipMode) {
                        status("会员中心已就绪");
                        host.evaluate("window.__dewebOpenMembership && window.__dewebOpenMembership()");
                    } else {
                        if (connectPromptShown || finished) return;
                        if (message != null && !isValidCloudSignMessage(message)) {
                            fail("签名消息格式不允许");
                            return;
                        }
                        connectPromptShown = true;
                        if (message != null) {
                            status("请确认云同步登录");
                            showGlassDialog("DeWEB 云同步登录",
                                    "接下来只签名一次云同步登录消息，不会发起交易，也不会授权代币。",
                                    "继续", () -> {
                                        connectConfirmed = true;
                                        prepareConnect();
                                    });
                        } else {
                            status("请确认连接 OKX 钱包");
                            showGlassDialog("连接 OKX 钱包",
                                    "DeWEB 云同步需要读取钱包地址，不会发起交易，也不会授权代币。",
                                    "连接", () -> {
                                        connectConfirmed = true;
                                        prepareConnect();
                                    });
                        }
                    }
                }
            });
            SharedPreferences authSp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
            boolean resetSession = authSp.getBoolean("wallet_reset_pending", false);
            if (resetSession) authSp.edit().remove("wallet_reset_pending").apply();
            host.start(resetSession);
            statusView.bringToFront();
            close.bringToFront();
        } catch (Throwable t) {
            fail("钱包服务不可用");
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void status(String text) {
        if (statusView != null && !finished) statusView.setText("v" + BuildConfig.VERSION_NAME + " · " + (text == null ? "" : text));
    }

    private void showGlassDialog(String titleText, String bodyText, String primaryText, Runnable onPrimary) {
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

        View accent = new View(this);
        GradientDrawable accentBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF56E6B0, 0x0056E6B0});
        accentBg.setCornerRadius(dp(2));
        accent.setBackground(accentBg);
        panel.addView(accent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(19f);
        title.setTextColor(0xFFEAF8F1);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(16);
        panel.addView(title, titleLp);

        TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextSize(12.5f);
        body.setTextColor(0xFFB8D6C7);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(10);
        panel.addView(body, bodyLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(22);
        panel.addView(actions, actionsLp);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(12f);
        cancelBtn.setAllCaps(false);
        cancelBtn.setTextColor(0xFFB8D6C7);
        cancelBtn.setBackgroundColor(0x00000000);
        cancelBtn.setFilterTouchesWhenObscured(true);
        cancelBtn.setOnClickListener(v -> {
            dialog.dismiss();
            cancel("User cancelled");
        });
        actions.addView(cancelBtn, new LinearLayout.LayoutParams(dp(76), dp(44)));

        Button primary = new Button(this);
        primary.setText(primaryText);
        primary.setTextSize(13f);
        primary.setAllCaps(false);
        primary.setTextColor(0xFF03150D);
        primary.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable primaryBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF56E6B0, 0xFF2FC79A});
        primaryBg.setCornerRadius(dp(14));
        primary.setBackground(primaryBg);
        primary.setFilterTouchesWhenObscured(true);
        primary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPrimary != null) onPrimary.run();
        });
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(dp(112), dp(44));
        primaryLp.leftMargin = dp(8);
        actions.addView(primary, primaryLp);

        shade.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        GlassUi.hardenTree(panel);
        dialog.setContentView(shade);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> cancel("User cancelled"));
        dialog.show();
        GlassUi.secureDialog(dialog, shouldSecureWindow());
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

    private void prepareConnect() {
        try {
            JSONObject req = baseRequest(CONNECT_ID, "eth_requestAccounts");
            req.put("params", new JSONArray());
            // 登录签名场景必须强制刷新 OKX 会话；否则缓存连接会让 personal_sign 没有签名界面。
            if (message != null && !message.isEmpty()) { req.put("strict", true); req.put("forceConnect", true); }
            host.request(req, msg -> {
                if (finished) return;
                if (msg.has("error")) {
                    fail(errorText(msg));
                    return;
                }
                Object r = msg.opt("result");
                if (r instanceof JSONArray && ((JSONArray) r).length() > 0) address = ((JSONArray) r).optString(0);
                if (address == null || address.isEmpty()) {
                    fail("未获取到钱包地址");
                    return;
                }
                MembershipManager.saveAddress(CloudAuthActivity.this, address);
                refreshMembership();
                if (message == null || message.isEmpty()) {
                    finishOk();
                } else {
                    prepareSign();
                }
            });
        } catch (Throwable t) { fail("打开钱包失败"); }
    }

    private static boolean isValidCloudSignMessage(String message) {
        if (message == null || message.length() > 420) return false;
        return message.matches("^DeWEB Cloud Sync\\nAddress: 0x[0-9a-fA-F]{40}\\nNonce: [0-9a-fA-F]{32}\\nIssued At: [^\\n]+\\n(?:Origin: [^\\n]{1,200}\\n)?Chain: BSC$");
    }

    private static String cloudMessageAddress(String message) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?m)^Address: (0x[0-9a-fA-F]{40})$").matcher(message);
            return m.find() ? m.group(1) : "";
        } catch (Throwable t) { return ""; }
    }

    private void prepareSign() {
        try {
            if (!isValidCloudSignMessage(message)) { fail("签名消息格式不允许"); return; }
            if (address == null || !address.equalsIgnoreCase(cloudMessageAddress(message))) {
                fail("签名地址与钱包地址不一致");
                return;
            }
            if (!connectConfirmed) {
                showGlassDialog("DeWEB 云同步登录",
                        "接下来只签名一次云同步登录消息，不会发起交易，也不会授权代币。",
                        "继续", () -> { connectConfirmed = true; prepareSign(); });
                return;
            }
            status("请在 OKX 中确认签名…");
            JSONObject req = baseRequest(SIGN_ID, "personal_sign");
            req.put("params", new JSONArray().put(message).put(address));
            host.request(req, msg -> {
                if (finished) return;
                if (msg.has("error")) { fail(errorText(msg)); return; }
                String sig = msg.optString("result", "");
                if (sig == null || sig.isEmpty()) { fail("签名结果为空"); return; }
                finishOk(sig);
            });
        } catch (Throwable t) { fail("签名请求失败"); }
    }

    private JSONObject baseRequest(String id, String method) throws Exception {
        JSONObject req = new JSONObject();
        req.put("id", id);
        req.put("origin", ORIGIN);
        req.put("method", method);
        req.put("chainHint", "56");
        req.put("strict", false);
        req.put("siteApproved", true);
        req.put("nativeConfirmed", true);
        req.put("windowId", "deweb-cloud");
        return req;
    }

    private String errorText(JSONObject msg) {
        try {
            JSONObject e = msg.optJSONObject("error");
            return e == null ? "钱包操作失败" : e.optString("message", "钱包操作失败");
        } catch (Throwable t) { return "钱包操作失败"; }
    }

    private void openExternal(Uri uri) {
        try {
            if (uri == null) return;
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            boolean okScheme = scheme.equals("okx") || scheme.equals("okxwallet") || scheme.equals("okxweb3");
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            boolean okHttps = "https".equals(scheme) && (host.equals("web3link.okx.com") || host.equals("link.okx.com") || (host.equals("web3.okx.com") && uri.getPath() != null && uri.getPath().startsWith("/ul/connect")));
            if (!okScheme && !okHttps) return;
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {}
    }

    private void finishOk() { finishOk(""); }
    private void refreshMembership() {
        try {
            if (address != null && !address.isEmpty()) MembershipManager.saveAddress(this, address);
            MembershipManager.refresh(this, (ok, msg, expiry) -> SiteActivity.notifyMembershipChanged());
        } catch (Throwable ignored) {}
    }

    private void finishOk(String sig) {
        if (finished) return;
        refreshMembership();
        finished = true;
        Intent out = new Intent();
        out.putExtra(EXTRA_ADDRESS, address == null ? "" : address);
        out.putExtra(EXTRA_SIGNATURE, sig == null ? "" : sig);
        setResult(RESULT_OK, out);
        finish();
    }

    private void cancel(String error) { fail(error); }
    private void fail(String error) {
        if (finished) return;
        finished = true;
        Intent out = new Intent();
        out.putExtra(EXTRA_ERROR, error == null ? "钱包登录失败" : error);
        setResult(RESULT_CANCELED, out);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPrivacyFlag();
    }

    private boolean shouldSecureWindow() {
        boolean appLocked = false;
        try {
            SharedPreferences sp = getSharedPreferences("deweb_shell", MODE_PRIVATE);
            appLocked = sp.getBoolean("site_locked", false);
            if (appLocked) {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                appLocked = km != null && km.isDeviceSecure();
            }
        } catch (Throwable ignored) {}
        return PrivacyPrefs.isEnabled(this) || appLocked;
    }

    private void applyPrivacyFlag() {
        try {
            if (shouldSecureWindow()) {
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onBackPressed() { cancel("User cancelled"); }

    @Override
    protected void onDestroy() {
        try { getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); } catch (Throwable ignored) {}
        finished = true;
        handler.removeCallbacksAndMessages(null);
        if (host != null) { try { host.destroy(); } catch (Throwable ignored) {} host = null; }
        super.onDestroy();
    }
}
