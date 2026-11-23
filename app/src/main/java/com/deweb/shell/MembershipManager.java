package com.deweb.shell;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Circuit-only Pro entitlement.
 *
 * Pro = Alpha free until 2026-12-31 OR DeWEBProResolver.isPro(address).
 * The old 1 BEM lifetime contracts are not referenced.
 */
public final class MembershipManager {
    public static final String CIRCUIT_RESOLVER = "0xd86fBB4Db54FCc2F5Be19F699b020d614998f59f";
    public static final long ALPHA_FREE_UNTIL = 1798732799000L; // 2026-12-31 23:59:59 Asia/Shanghai
    public static final int XLAYER_CHAIN_ID = 196;

    private static final String PREF = "deweb_shell";
    private static final String KEY_ADDRESS = "last_wallet_address";
    private static final String KEY_CIRCUIT = "member_circuit";
    private static final String KEY_CHECKED = "member_circuit_checked_at";
    private static final String SELECTOR_IS_PRO = "0x41b99a80"; // isPro(address)

    private static final String[] XLAYER_RPCS = new String[]{
            "https://rpc.xlayer.tech",
            "https://xlayer.drpc.org",
            "https://196.rpc.thirdweb.com/"
    };

    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);

    private MembershipManager() {}

    public interface Callback {
        void onResult(boolean ok, String message, long expiry);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean configured() {
        return isAddress(CIRCUIT_RESOLVER);
    }

    private static boolean isAddress(String a) {
        return a != null && a.matches("^0x[0-9a-fA-F]{40}$")
                && !a.equalsIgnoreCase("0x0000000000000000000000000000000000000000");
    }

    public static String address(Context context) {
        return prefs(context).getString(KEY_ADDRESS, "");
    }

    public static void saveAddress(Context context, String address) {
        if (address == null || !address.matches("^0x[0-9a-fA-F]{40}$")) return;
        String normalized = address.toLowerCase();
        SharedPreferences p = prefs(context);
        String old = p.getString(KEY_ADDRESS, "");
        if (!normalized.equals(old)) {
            p.edit()
                    .putString(KEY_ADDRESS, normalized)
                    .remove(KEY_CIRCUIT)
                    .remove(KEY_CHECKED)
                    .apply();
        }
    }

    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_ADDRESS)
                .remove(KEY_CIRCUIT)
                .remove(KEY_CHECKED)
                .apply();
    }

    public static boolean isAlphaFree() {
        return System.currentTimeMillis() < ALPHA_FREE_UNTIL;
    }

    /** Alpha expiry only; circuit membership has no expiry. */
    public static long expiry(Context context) {
        return isAlphaFree() ? ALPHA_FREE_UNTIL : 0L;
    }

    public static boolean isPro(Context context) {
        String addr = address(context);
        if (addr.isEmpty()) return false;
        if (isAlphaFree()) return true;
        return prefs(context).getBoolean(KEY_CIRCUIT, false);
    }

    public static boolean isCircuitPro(Context context) {
        String addr = address(context);
        return !addr.isEmpty() && prefs(context).getBoolean(KEY_CIRCUIT, false);
    }

    public static String summary(Context context) {
        String addr = address(context);
        if (addr.isEmpty()) return "会员：先连接 OKX 钱包";
        String shortAddr = addr.substring(0, 6) + "…" + addr.substring(addr.length() - 4);
        if (!configured()) return "会员：电路 Resolver 未配置 · " + shortAddr;
        if (isCircuitPro(context)) return "Pro：电路会员 · " + shortAddr;
        if (isAlphaFree()) return "Pro：Alpha 测试期免费（至 2026-12-31） · " + shortAddr;
        return "会员：未开通 · " + shortAddr;
    }

    public static void refresh(Context context, Callback callback) {
        if (!REFRESHING.compareAndSet(false, true)) {
            new Thread(() -> {
                try { while (REFRESHING.get()) Thread.sleep(80); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
                refresh(context, callback);
            }, "deweb-member-wait").start();
            return;
        }
        final String addr = address(context);
        if (addr.isEmpty()) {
            REFRESHING.set(false);
            callback.onResult(false, "先连接 OKX 钱包", 0);
            return;
        }
        if (!configured()) {
            REFRESHING.set(false);
            callback.onResult(false, "电路 Resolver 未配置", 0);
            return;
        }
        SharedPreferences pref = prefs(context);
        boolean cached = pref.getBoolean(KEY_CIRCUIT, false);
        long checkedAt = pref.getLong(KEY_CHECKED, 0L);
        if (cached && System.currentTimeMillis() - checkedAt < 5 * 60 * 1000L) {
            REFRESHING.set(false);
            callback.onResult(true, "电路会员", 0);
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            final int[] yes = {0}, no = {0}, errors = {0};
            final String[] lastError = {"RPC 不可用"};
            final CountDownLatch latch = new CountDownLatch(XLAYER_RPCS.length);
            for (String endpoint : XLAYER_RPCS) {
                new Thread(() -> {
                    try {
                        boolean active = fetchCircuitPro(endpoint, CIRCUIT_RESOLVER, addr);
                        synchronized (yes) { if (active) yes[0]++; else no[0]++; }
                    } catch (Throwable t) {
                        synchronized (lastError) {
                            errors[0]++;
                            lastError[0] = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                        }
                    } finally {
                        latch.countDown();
                    }
                }, "deweb-circuit-rpc").start();
            }
            try { latch.await(4500, TimeUnit.MILLISECONDS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            try {
                // Require two independent sources (2 RPCs, or 1 RPC + API).
                // A single hijacked RPC must not be able to grant Pro.
                boolean confirmed = yes[0] >= 2;
                if (!confirmed) {
                    boolean viaApi = false;
                    try { viaApi = fetchCircuitViaApi(addr); } catch (Throwable ignored) {}
                    // The Worker API runs its own RPC consensus server-side.
                    // If every local RPC timed out, do not throw that result away.
                    boolean noRpcCompleted = (yes[0] + no[0] + errors[0]) == 0;
                    boolean allRpcErrored = errors[0] == XLAYER_RPCS.length;
                    if (viaApi && (yes[0] >= 1 || noRpcCompleted || allRpcErrored)) confirmed = true;
                }
                boolean oldCached = pref.getBoolean(KEY_CIRCUIT, false);
                if (confirmed) {
                    pref.edit().putBoolean(KEY_CIRCUIT, true)
                            .putLong(KEY_CHECKED, System.currentTimeMillis()).apply();
                    callback.onResult(true, "电路会员已生效", 0);
                } else if (oldCached) {
                    callback.onResult(true, "电路会员已确认；本次校验未完成", 0);
                } else if (no[0] >= 2 && errors[0] == 0) {
                    pref.edit().putBoolean(KEY_CIRCUIT, false)
                            .putLong(KEY_CHECKED, System.currentTimeMillis()).apply();
                    if (isAlphaFree()) callback.onResult(true, "Alpha 测试期免费 Pro", ALPHA_FREE_UNTIL);
                    else callback.onResult(true, "未开通电路会员", 0);
                } else {
                    if (isAlphaFree()) callback.onResult(true, "Alpha 测试期免费 Pro", ALPHA_FREE_UNTIL);
                    else callback.onResult(false, "电路会员验证未完成：" + lastError[0], 0);
                }
            } finally {
                REFRESHING.set(false);
            }
        }, "deweb-membership-circuit").start();
    }

    private static boolean fetchCircuitViaApi(String addr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.dewebdesk.link/api/membership/check?address=" + addr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) return false;
            StringBuilder sb = new StringBuilder();
            try (InputStream is = conn.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject out = new JSONObject(sb.toString());
            return out.optBoolean("active", false) && "circuit".equalsIgnoreCase(out.optString("source", ""));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean fetchCircuitPro(String rpc, String contract, String addr) throws Exception {
        String data = SELECTOR_IS_PRO + "000000000000000000000000" + addr.substring(2).toLowerCase();
        JSONObject callObj = new JSONObject();
        callObj.put("to", contract);
        callObj.put("data", data);
        org.json.JSONArray callParams = new org.json.JSONArray();
        callParams.put(callObj);
        callParams.put("latest");
        JSONObject res = new JSONObject(rpc(rpc, "eth_call", callParams.toString()));
        String hex = res.optString("result", "");
        if (!hex.startsWith("0x")) throw new Exception("bad isPro result");
        return new BigInteger(hex.substring(2).isEmpty() ? "0" : hex.substring(2), 16).signum() != 0;
    }

    private static String rpc(String endpoint, String method, String paramsJson) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            JSONObject bodyObj = new JSONObject();
            bodyObj.put("jsonrpc", "2.0");
            bodyObj.put("id", 1);
            bodyObj.put("method", method);
            bodyObj.put("params", new org.json.JSONArray(paramsJson));
            String body = bodyObj.toString();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (InputStream is = conn.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject out = new JSONObject(sb.toString());
            if (out.has("error")) throw new Exception("RPC error");
            return out.optString("result", "");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
